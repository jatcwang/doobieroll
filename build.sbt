val zioVersion = "1.0.0-RC18-2"

val circeVersion = "0.13.0"

lazy val core = Project("core", file("modules/core"))
  .settings(commonSettings)
  .settings(
    name := "Scala Starter",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.1.1",
      "org.scala-lang.modules" %% "scala-collection-contrib" % "0.2.1",
      "com.chuusai" %% "shapeless" % "2.3.3",
      "org.postgresql" % "postgresql" % "42.2.11" % Test,
    ),
  )

lazy val coretest = Project("coretest", file("modules/coretest"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      // FIXME:
      "com.lihaoyi" %% "pprint" % "0.5.9",
      "org.flywaydb" % "flyway-core" % "6.3.2",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "dev.zio" %% "zio-test" % zioVersion,
      "dev.zio" %% "zio-test-sbt" % zioVersion,
      "dev.zio" %% "zio-test-magnolia" % zioVersion,
    )
  )

lazy val bench = Project("bench", file("modules/bench"))
  .dependsOn(core, coretest)
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
    )
  )

lazy val root = project
  .in(new File("."))
  .aggregate(bench, core, coretest)
  .settings(
    publish / skip := true
  )

val scala213 = "2.13.2"
val scala212 = "2.12.11"
lazy val commonSettings = Seq(
  version := "0.1.0",
  scalaVersion := scala213,
  crossScalaVersions := Seq(scala213, scala212),
  scalacOptions ++= Seq(
    "-Ywarn-macros:after"
  ),
  scalacOptions --= {
    if (sys.env.contains("CI")) {
      Seq.empty
    } else {
      Seq("-Xfatal-warnings")
    }
  },
  // FIXME:
//addCompilerPlugin("io.tryp" % "splain" % "0.5.6" cross CrossVersion.patch)
)
