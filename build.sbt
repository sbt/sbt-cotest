sbtPlugin := true

organization := "com.typesafe.sbt"

name := "sbt-cotest"

version := "0.1.0-SNAPSHOT"

publishMavenStyle := false

publishTo <<= isSnapshot { snapshot =>
  if (snapshot) Some(Classpaths.sbtPluginSnapshots) else Some(Classpaths.sbtPluginReleases)
}
