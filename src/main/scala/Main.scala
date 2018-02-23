import cats.effect.IO
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {

  val builder = BlazeBuilder[IO]
    .bindHttp(8080, "0.0.0.0")
    .mountService(DeployService.service, "/")
    .serve
    .runLast
    .unsafeRunSync()

}
