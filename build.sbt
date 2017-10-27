organization := "com.nitro"
name := "singularity-broadcast"

scalaVersion := "2.11.11"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Dependencies.all

enablePlugins(ScalafmtPlugin, JavaAppPackaging, DockerPlugin)



packageName in Docker:= "gonitro/singularity-broadcast"
dockerExposedPorts := Seq(8080)
