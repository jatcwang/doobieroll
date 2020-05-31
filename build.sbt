val zioVersion = "1.0.0-RC19-2"

val circeVersion = "0.13.0"
val silencerVersion = "1.7.0"

lazy val core = Project("core", file("modules/core"))
  .settings(commonSettings)
  .settings(
    name := "Scala Starter",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.1.1",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6",
      "com.chuusai" %% "shapeless" % "2.3.3",
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
      "com.softwaremill.quicklens" %% "quicklens" % "1.5.0",
      "org.postgresql" % "postgresql" % "42.2.11" % Test,
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  )

lazy val bench = Project("bench", file("modules/bench"))
  .dependsOn(core, coretest)
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      ),
  )

lazy val root = project
  .in(new File("."))
  .aggregate(bench, core, coretest)
  .settings(
    publish / skip := true,
  )
  .settings(commonSettings)

val scala213 = "2.13.2"
val scala212 = "2.12.11"
lazy val commonSettings = Seq(
  version := "0.1.0",
  scalaVersion := scala212,
  crossScalaVersions := Seq(scala213, scala212),
  scalacOptions ++= Seq(
    "-Ywarn-macros:after",
  ),
  scalacOptions --= {
    if (sys.env.contains("CI")) {
      Seq.empty
    } else {
      Seq("-Xfatal-warnings")
    }
  },
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  libraryDependencies ++= Seq(
    compilerPlugin(
      "com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full,
    ),
    "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full,
  ),
)
