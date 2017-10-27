import sbt._

object Dependencies {

  val FicusVersion = "1.4.2"
  val catsVersion = "1.0.0-MF"
  val Http4sVersion = "0.18.0-SNAPSHOT"//"0.18.0-M3"
  val CirceVersion = "0.8.0"
  val LogBackVersion = "1.2.3"
  val ScalaLoggingVersion = "3.7.2"
  val PrettyTime = "3.2.7.Final"

  val http4s = Seq(
    "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
    //"com.softwaremill.sttp" %% "core" % "1.0.1"
    "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
    "org.http4s" %% "http4s-circe" % Http4sVersion,
    "org.http4s" %% "http4s-dsl" % Http4sVersion,
    "io.circe" %% "circe-generic" % CirceVersion,
    "io.circe" %% "circe-literal" % CirceVersion,
    "io.circe" %% "circe-generic" % CirceVersion,
    "io.circe" %% "circe-parser"  % CirceVersion
  )

  val others = Seq(
    "org.typelevel" %% "cats-core" % catsVersion,
    "com.iheart" %% "ficus" % FicusVersion,
    "ch.qos.logback" % "logback-classic" % LogBackVersion,
    "org.ocpsoft.prettytime" % "prettytime" % PrettyTime,
    "com.typesafe.scala-logging" %% "scala-logging" % ScalaLoggingVersion
  )

  val all = http4s ++ others
}
