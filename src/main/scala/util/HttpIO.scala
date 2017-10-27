package util

import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.parse
import org.http4s.{Header, Uri, UrlForm}
import org.http4s.client.UnexpectedStatus
// import org.http4s.client._
//
import cats.effect.IO
import io.circe.{Decoder, Json}
import org.http4s.Method.{POST, GET}
import org.http4s.circe.{jsonOf, _}
import org.http4s.client.blaze.defaultClient
import org.http4s.client.dsl.io._

/**
  * Abstract interface for Side effects IO.
  */
trait HttpIO {
  def get[A: Decoder](url: String): IO[Either[Throwable, A]]

  def postJson[A: Decoder](url: String, json: Json, headers: Header*): IO[Option[A]]
  def postJsonIgnoreResult(url: String, json: Json, headers: Header*): IO[Unit]
  def post[A: Decoder](url: String, params: Map[String,String], headers: Header*):  IO[Either[Throwable, A]]
}

/**
  * Http IO using http4s and Cats-effects
  */
object DefaultHttpIO extends HttpIO with LazyLogging {

  private val httpClient = defaultClient[IO]

  def get[A: Decoder](url: String): IO[Either[Throwable, A]] = {
    implicit val decoder = jsonOf[IO, A]
    logger.info(s"Fetching $url")
    httpClient.expect[A](url).attempt
  }

  def postJson[A: Decoder](url: String, json: Json, headers: Header*): IO[Option[A]] = {
    val uri = Uri.unsafeFromString(url)
    implicit val decoder = jsonOf[IO, A]
    //   implicit val decoder = jsonOf[IO, Json]
    val req = POST(uri, json).map(_.putHeaders(headers: _*))
    logger.info(s"Posting json to $uri, json: $json")
    httpClient.expect[A](req).attempt.map {
      case Right(msg) =>
        logger.info(s"Http response for $url: $msg")
        Some(msg)
      case Left(ex: UnexpectedStatus) =>
        logger.error(s"** Unable to send to $uri ${ex.status}")
        None
      case Left(ex) =>
        logger.error(s"** Unable to send to $uri $ex")
        None
    }
  }

  def postJsonIgnoreResult(url: String, json: Json, headers: Header*): IO[Unit] = {
    val uri = Uri.unsafeFromString(url)
    val req = POST(uri, json).map(_.putHeaders(headers: _*))
    logger.info(s"Posting json to $uri, json: $json")
    httpClient.expect[String](req).attempt.map {
      case Right(msg) =>
        logger.info(s"Http response for $url: $msg")
      case Left(ex: UnexpectedStatus) =>
        logger.error(s"** Unable to send to $uri ${ex.status}")
      case Left(ex) =>
        logger.error(s"** Unable to send to $uri $ex")
    }
  }

  def post[A: Decoder](url: String, params: Map[String,String], headers: Header*):  IO[Either[Throwable, A]] = {
    implicit val decoder = jsonOf[IO, A]
    val uri = Uri.unsafeFromString(url)
    //   implicit val decoder = jsonOf[IO, Json]
    val urlForm = UrlForm(params.mapValues(Seq(_)))
    val req = POST(uri, urlForm).map(_.putHeaders(headers: _*))
    logger.info(s"Posting json to $uri, params: $urlForm")

    httpClient.expect[A](req).attempt
  }
}

object JsonUtil extends LazyLogging {
  def json(s: String): Json = {
    val json = parse(s)
    json.fold({
      ex =>
        logger.error(s"Unable to parse - $ex: \n $s")
        Json.Null
    }, identity)
  }
}