val zioVersion = "1.0.0-RC18-2"

lazy val root = Project("root", file("."))
  .settings(commonSettings)
  .settings(
    name := "Scala Starter",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.1.1",
      "org.scala-lang.modules" %% "scala-collection-contrib" % "0.2.1",
      "com.chuusai" %% "shapeless" % "2.3.3",
      // FIXME:
      "com.lihaoyi" %% "pprint" % "0.5.9",
      "org.scalatest" %% "scalatest" % "3.1.1" % Test,
      "com.softwaremill.diffx" %% "diffx-scalatest" % "0.3.28" % Test,
      "org.flywaydb" % "flyway-core" % "6.3.2" % Test,
      "org.postgresql" % "postgresql" % "42.2.11" % Test,
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
    ),
  )

val scala213 = "2.13.2"
val scala212 = "2.12.11"
lazy val commonSettings = Seq(
  version := "0.1.0",
  scalaVersion := scala213,
  crossScalaVersions := Seq(scala213, scala212),
  scalacOptions --= {
    if (sys.env.get("CI").isDefined) {
      Seq.empty
    } else {
      Seq("-Xfatal-warnings")
    }
  },
)
