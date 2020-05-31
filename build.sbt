val zioVersion = "1.0.0-RC20"

val circeVersion = "0.13.0"
val silencerVersion = "1.7.0"
val doobieVersion = "0.9.0"

inThisBuild(
  List(
    organization := "com.github.jatcwang",
    homepage := Some(url("https://github.com/jatcwang/doobieroll")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jatcwang",
        "Jacob Wang",
        "jatcwang@gmail.com",
        url("https://almostfunctional.com"),
      ),
    ),
  ),
)

lazy val core = Project("core", file("modules/core"))
  .settings(commonSettings)
  .settings(
    name := "doobieroll",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.1.1",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6",
      "com.chuusai" %% "shapeless" % "2.3.3",
    ),
  )

lazy val coretest = Project("coretest", file("modules/coretest"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(noPublishSettings)
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
      "dev.zio" %% "zio-interop-cats" % "2.1.3.0-RC15",
      "org.tpolecat" %% "doobie-core" % doobieVersion,

      "javax.activation" % "activation" % "1.1.1", // Reuqired for DataSource class in JDK 9+
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres-circe" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "org.postgresql" % "postgresql" % "42.2.11",
      "com.softwaremill.quicklens" %% "quicklens" % "1.5.0",
      "com.whisk" %% "docker-testkit-impl-docker-java" % "0.9.9" % "test",
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  )

lazy val bench = Project("bench", file("modules/bench"))
  .dependsOn(core, coretest)
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies ++= Seq(
      ),
  )

lazy val root = project
  .in(new File("."))
  .aggregate(bench, core, coretest)
  .settings(noPublishSettings)
  .settings(commonSettings)

val scala213 = "2.13.2"
val scala212 = "2.12.11"
lazy val commonSettings = Seq(
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

lazy val noPublishSettings = Seq(
  publish / skip := true,
)
