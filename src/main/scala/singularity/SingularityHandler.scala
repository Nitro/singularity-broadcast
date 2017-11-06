package singularity

import java.util.Date

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import conf.SingularityConf
import io.circe.generic.auto._
import org.ocpsoft.prettytime.PrettyTime
import util.HttpIO

import scala.concurrent.duration._

trait SingularityHandler {
  def fetchInfo(event: SingularityDeployUpdate): IO[DeployEvent]
}

class SingularityHandlerImpl(val conf: SingularityConf, val httpIO: HttpIO)
    extends SingularityIO {

  def fetchInfo(event: SingularityDeployUpdate): IO[DeployEvent] =
    event.eventType match {
      case "STARTING" =>
        fetch(event).map(DeployStarting)

      case "FINISHED" =>
        fetch(event).map(DeployFinished)
      case other =>
        IO {
          logger.info(s"ignoring: $other")
          IgnoredEvent
        }
    }

  private def fetch(event: SingularityDeployUpdate): IO[DeployInfo] = {
    val docker = event.deploy.containerInfo.docker
    val requestId = event.deploy.requestId
    val newDeployId = event.deployMarker.deployId

    for {
      requestInfoBeforeDeploy <- getRequestInfo(docker.environment, requestId)
      previousDeploy = getActiveDeploy(requestInfoBeforeDeploy) // previous but still active (this assume the event is processed in real time)

      previousDeployId = deployId(previousDeploy) // the previous deployId is still active one.
      previousDeployHistory <- previousDeployId
        .map(deployId => getDeployInfo(docker.environment, requestId, deployId))
        .getOrElse(IO(None))
      prodRequestInfo <- getProdRequest(requestId)
    } yield {
      toDeployInfo(event,
                   prodRequestInfo,
                   previousDeployHistory,
                   docker,
                   requestId,
                   newDeployId,
                   previousDeployId)

    }
  }

  def toDeployInfo(event: SingularityDeployUpdate,
                   prodRequestInfo: Option[SingularityRequestParent],
                   previousDeployHistory: Option[SingularityDeployHistory],
                   docker: SingularityDockerInfo,
                   requestId: String,
                   newDeployId: String,
                   previousDeployId: Option[String]) = {
    val prodActiveDeploy = getActiveDeploy(prodRequestInfo)
    val prodVersion = getActiveVersion(prodRequestInfo)
    val previousDeploy = previousDeployHistory.flatMap(_.deployMarker)
    val previousLastDeployTime = lastDeployTime(previousDeploy)
    val previousImageTag =
      previousDeployHistory.map(_.deploy.containerInfo.docker.imageTag)
    val prodLastDeployTime = lastDeployTime(prodActiveDeploy)
    val singularity = singularityBase(docker.environment.getOrElse("<unknown>"))

    val previousDockerParameters = previousDeployHistory.toSeq
      .flatMap(_.deploy.containerInfo.docker.dockerParameters)
      .toList
      .sortBy(_.key)

    val newDockerParameters = docker.dockerParameters.toList.sortBy(_.key)

    logger.info(s"""
                   |requestId: $requestId
                   |newDeployId: $newDeployId, previousDeploy: $previousDeployId
                   |newDockerImageTAg: ${docker.imageTag}, previousImageTag: $previousImageTag
                   |newDockerParameters:
                   | ${newDockerParameters.map(_.value).mkString("\n ")}
                   |previousDockerParameters:
                   | ${previousDockerParameters.map(_.value).mkString("\n ")}
         """.stripMargin)

    val newEnv = extractEnv(newDockerParameters)
    val previousEnv = extractEnv(previousDockerParameters)

    val diffEnvironmentConf = diffEnvironment(newEnv, previousEnv)

    val newRelicAppName =
      newEnv.filterKeys(_.contains("NEW_RELIC_APP_NAME")).values.headOption

    DeployInfo(
      serviceName = docker.serviceName,
      imageName = docker.imageName,
      newRelicAppName = newRelicAppName,
      newImageTag = docker.imageTag,
      newDeployId = newDeployId,
      previousLastDeployTime = previousLastDeployTime,
      prodLastDeployTime = prodLastDeployTime,
      warningProdLastDeploy =
        warningProdLastDeploy(previousDeploy, prodActiveDeploy),
      previousDeployId = previousDeployId,
      environment = docker.environment,
      status = event.status,
      diffEnvironmentConf = diffEnvironmentConf,
      singularityRequestUrl =
        s"$singularity/request/${event.deployMarker.requestId}",
      singularityProdRequestUrl = prodActiveDeploy.map(d =>
        s"""${conf.prodUrl}/request/${d.requestId}"""),
      singularityNewDeployUrl =
        s"$singularity/request/${event.deployMarker.requestId}/deploy/${event.deployMarker.deployId}",
      singularityPreviousDeployId = previousDeployId.map(id =>
        s"$singularity/request/${event.deployMarker.requestId}/deploy/$id"),
      prodImageTag = prodVersion,
      previousImageTag = previousImageTag
    )
  }

  def deployId(deploy: Option[SingularityDeployMarker]): Option[String] =
    deploy.map(_.deployId)

  def lastDeployTime(
      deployOp: Option[SingularityDeployMarker]): Option[String] =
    deployOp.map { deploy =>
      new PrettyTime().format(new Date(deploy.timestamp))

    }

  val WarningPeriodProd = 7.days

  def warningProdLastDeploy(
      devOp: Option[SingularityDeployMarker],
      prodOp: Option[SingularityDeployMarker]): Boolean = {
    for {
      dev <- devOp
      prod <- prodOp
    } yield {
      val duration = Duration(dev.timestamp - prod.timestamp, MILLISECONDS)
      duration.toDays > WarningPeriodProd.toDays
    }
  }.getOrElse(false)

  def getActiveDeploy(request: Option[SingularityRequestParent])
    : Option[SingularityDeployMarker] = {
    request.flatMap(_.requestDeployState).flatMap(_.activeDeploy)
  }

  def getActiveVersion(
      request: Option[SingularityRequestParent]): Option[String] = {
    request.flatMap(_.activeDeploy).map(_.containerInfo.docker.imageTag)
  }

  private def getProdRequest(
      requestId: String): IO[Option[SingularityRequestParent]] = {
    if (requestId.startsWith("dev_")) {
      val prodRequestId = requestId.replaceFirst("dev_", "prod_")
      getRequestInfo(Some("prod"), prodRequestId)
    } else {
      IO(None)
    }
  }

  def diffEnvironment(newEnv: Map[String, String],
                      previousEnv: Map[String, String]) = {

    val addedKeys = newEnv.keys.toList.diff(previousEnv.keys.toList)
    val removedKeys = previousEnv.keys.toList.diff(newEnv.keys.toList)
    val intersectKeys = newEnv.keys.toList.intersect(previousEnv.keys.toList)

    val addedChanges = newEnv
      .filterKeys(key => addedKeys.contains(key))
      .map {
        case (key, value) =>
          DiffEnvironmentConf(Added, key, value)
      }

    val removedChanges = previousEnv
      .filterKeys(key => removedKeys.contains(key))
      .map {
        case (key, value) =>
          DiffEnvironmentConf(Removed, key, value)
      }

    val changes = {
      val merged = {
        val newIntersect =
          newEnv.filterKeys(key => intersectKeys.contains(key)).toSeq
        val oldIntersect =
          previousEnv.filterKeys(key => intersectKeys.contains(key)).toSeq
        newIntersect ++ oldIntersect
      }

      val groupedByKey = merged.groupBy(_._1).mapValues(_.map(_._2).toList)
      groupedByKey.collect {
        case (key, newValue :: previousValue :: Nil)
            if newValue != previousValue =>
          DiffEnvironmentConf(Changed, key, newValue, Some(previousValue))
      }

    }

    (addedChanges ++ removedChanges ++ changes).toList
  }

  /** Extract Docker Env:
    * input format: {key = "env", value = "JAVA_OPTS=-Xmx128"}
    * output {key="JAVA_OPTS", value="-Xmx128"}
    */
  def extractEnv(
      params: List[SingularityDockerParameter]): Map[String, String] = {
    params
      .filter(_.key == "env")
      .map(_.value)
      .map { value =>
        (value.split("=")(0), value.split("=")(1))
      }
      .toMap
  }

}

trait SingularityIO extends LazyLogging {
  self: SingularityHandlerImpl =>

  def singularityBase(env: String) = env match {
    case "dev"  => conf.devUrl
    case "prod" => conf.prodUrl
    case other =>
      logger.warn(
        s"Unknown environment $other, assuming dev. EnvironmentName required")
      conf.devUrl
  }

  // IO
  def getRequestInfo(envOpt: Option[String],
                     requestId: String): IO[Option[SingularityRequestParent]] =
    envOpt.fold[IO[Option[SingularityRequestParent]]](IO(None)) { env =>
      val url = s"${singularityBase(env)}/api/requests/request/$requestId"
      for {
        res <- httpIO.get[SingularityRequestParent](url)
      } yield {

        res.fold({ ex =>
          logger.warn(s"Unable to find $url ${ex.getMessage}")
          None
        }, Some(_))
      }
    }

  // IO to fetch Deploy info
  def getDeployInfo(envOpt: Option[String],
                    requestId: String,
                    deployId: String): IO[Option[SingularityDeployHistory]] =
    envOpt.fold[IO[Option[SingularityDeployHistory]]](IO(None)) { env =>
      val url =
        s"${singularityBase(env)}/api/history/request/$requestId/deploy/$deployId"
      for {
        res <- httpIO.get[SingularityDeployHistory](url)
      } yield {

        res.fold({ ex =>
          logger.warn(s"Unable to find $url ${ex.getMessage}")
          None
        }, Some(_))
      }
    }

}
