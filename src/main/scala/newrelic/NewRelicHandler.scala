package newrelic

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import conf.{NewRelicConf, SingularityConf}
import io.circe.Json
import org.http4s.Header
import singularity._
import util.JsonUtil._
import io.circe.generic.auto._
import util.HttpIO


/**
  * https://docs.newrelic.com/docs/apm/new-relic-apm/maintenance/recording-deployments
  *
  * @param conf
  * @param httpIO
  */
case class NewRelicHandlerImpl(val conf: NewRelicConf, val httpIO: HttpIO) extends LazyLogging {

  val NewRelicApiApplications = "https://api.newrelic.com/v2/applications.json"
  val XApiHeader = Header("X-Api-Key", conf.apiKey)


  def fetchNewRelicAppId(event: DeployEvent): IO[DeployEvent] = event match {
    case DeployStarting(info) => fetchNewRelicAppId(info).map(DeployStarting)
    case DeployFinished(info) => fetchNewRelicAppId(info).map(DeployFinished)
    case IgnoredEvent => IO(event)
  }


  def fetchNewRelicAppId(deployInfo: DeployInfo): IO[DeployInfo] = {
    val appId = deployInfo.newRelicAppName.map { appName =>
      logger.info(s"Fetching NewRelic App Id for $appName")
      for {
        result <- httpIO.post[NewRelicApplicationList](NewRelicApiApplications, Map("filter[name]" -> appName), XApiHeader)
      } yield {
        result match {
          case Right(list) => list.applications.headOption.map(_.id)
          case Left(ex) =>
            logger.warn(s"Unable to fetch NewRelicId for $appName", ex)
            None
        }
      }
    }.getOrElse(IO(None))

    appId.map { id => deployInfo.copy(newRelicAppId = id) }
  }


  def send(event: DeployEvent): IO[Option[Long]] = {
    val message = event match {
      case DeployStarting(info) =>
        Some((info.newRelicAppId, json(
          s"""
             |{
             |  "deployment": {
             |    "revision": "${info.newImageTag}",
             |    "description": "Starting DeployId: ${info.newDeployId}",
             |    "user": "Singularity"
             |  }
             |}
       """.stripMargin)))
      case DeployFinished(info) =>
        Some((info.newRelicAppId, json(
          s"""
             |{
             |  "deployment": {
             |    "revision": "${info.newImageTag}",
             |    "description": "Finished ${info.newDeployId}",
             |    "user": "Singularity"
             |  }
             |}
       """.stripMargin)))
      case IgnoredEvent => None

    }
    message.collect {
      case (Some(newRelicAppId), deployJson) =>
        for {
          result <- httpIO.postJson[NewRelicDeployResult](s"https://api.newrelic.com/v2/applications/${newRelicAppId}/deployments.json", deployJson, XApiHeader)

        } yield {
          val id = result.map(_.deployment.id)
          logger.info(s"Deploy registered with id '$id' event sent to NewRelic for $newRelicAppId ")
          id
        }

    }.getOrElse(IO(None))
  }
}
