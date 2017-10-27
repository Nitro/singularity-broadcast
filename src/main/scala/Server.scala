import util.DefaultHttpIO
import cats.data.EitherT
import cats.effect._
import org.http4s._

import org.http4s.dsl.io._
import cats.effect.IO
import cats.effect.LiftIO._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import conf.{Conf, SingularityConf, SlackConfig}

import io.circe._
import io.circe.literal._
import io.circe.generic.auto._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import newrelic.{NewRelicHandlerImpl}
import org.http4s._
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
        _ <- slack.send(slackMsg)

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

  def shouldSkip(deployUpdate: SingularityDeployUpdate): Boolean = {
    if (deployUpdate.deployMarker.requestId.contains("singularity_broadcast")) {
      logger.info(s"Ignoring ${deployUpdate.deployMarker.requestId}")
      true
    } else if (System
                 .currentTimeMillis() - deployUpdate.deployMarker.timestamp > TooOld.toMillis) {
      logger.info(
        s"Dropping ${deployUpdate.deployMarker.requestId} for been too old. now: ${System
          .currentTimeMillis()}, deploy timestamp: ${deployUpdate.deployMarker.timestamp}")
      true
    } else {
      false
    }
  }

}
