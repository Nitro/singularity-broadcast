import cats.effect.IO
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import conf.Conf
import util.Cache
import io.circe.generic.auto._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import newrelic.NewRelicHandlerImpl
import util.DefaultHttpIO
import org.http4s.{util, _}
import org.http4s.circe._
import org.http4s.dsl.io.{Ok, _}
import singularity._
import slack.SlackHandlerImpl

import scala.concurrent.duration._

object DeployService extends Utils {
  implicit val decoder = jsonOf[IO, SingularityDeployUpdate]

  lazy val service = HttpService[IO] {

    case GET -> Root / "health" => Ok("ok")

    case req @ POST -> Root / "singularity" / "webhook" / "deploy" =>
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
      for {
        deployUpdate <- req.as[SingularityDeployUpdate]
        _ <- processDeployUpdate(deployUpdate)
        resp <- Ok("ok")
      } yield {
        logger.info("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")
        resp
      }

  }

  def processDeployUpdate(deployUpdate: SingularityDeployUpdate): IO[Unit] =
    if (!shouldSkip(deployUpdate)) {
      for {
        info <- singularity.fetchInfo(deployUpdate)
        richEvent <- newRelic.fetchNewRelicAppId(info)
        newRelicDeployId <- newRelic.send(richEvent)

        slackMsg <- IO(slack.buildMessage(richEvent, newRelicDeployId))
        _ <- slack.send(richEvent, slackMsg)

      } yield {
        ()
      }
    } else {
      IO()
    }

}

trait Utils extends LazyLogging {

  val config = ConfigFactory.defaultApplication().resolve()

  val io = DefaultHttpIO

  val conf = config.as[Conf]("singularity_broadcast")

  val singularity = new SingularityHandlerImpl(conf.singularity, io)

  val slack = new SlackHandlerImpl(conf, io)

  val newRelic = NewRelicHandlerImpl(conf.newRelic, io)

  def log(request: Request[IO]) = {
    val body = request.bodyAsText.runFold[String]("")(_ + _).unsafeRunSync()
    logger.info(s"Request body: $body")
  }

  //    def log(json: Json): IO[Unit] = IO {
  //      val pretty = json.pretty(Printer.spaces2)
  //      logger.info(s"Json: $pretty")
  //    }

  val TooOld = 10.minutes

  val ToSkip = Seq("tweety", "singularity_broadcast", "cadvisor")

  def shouldSkip(event: SingularityDeployUpdate): Boolean = {
    val deployId = event.deployMarker.deployId
    val requestId = event.deployMarker.requestId
    val now = System.currentTimeMillis()
    logger.info(s"Evaluating event: ${event.eventType}, requestId: $requestId, deployId: $deployId")
    if (Cache.isAlreadySeen(event)) {
      logger.info(s"Already processed deploy ${event.eventType } for deployId: '$deployId'. Ignoring $requestId")
      true
    } else if (ToSkip.exists(contain => requestId.toLowerCase().contains(contain))) {
      logger.info(s"Ignoring $requestId (in toSkip list)")
      true
    } else if (now - event.deployMarker.timestamp > TooOld.toMillis) {
      logger.info(
        s"Dropping deployId:$deployId, requestId: $requestId for been too old. now: $now, deploy timestamp: ${event.deployMarker.timestamp}")
      true
    } else {
      Cache.remember(event)
      false
    }
  }

}


