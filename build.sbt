val zioVersion = "1.0.9"
val circeVersion = "0.14.1"
val doobieVersion = "0.13.4"
val scala213 = "2.13.6"
val scala212 = "2.12.13"

inThisBuild(
  List(
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala213, scala212),
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
//    mimaPreviousArtifacts := Set("com.github.jatcwang" %% "doobieroll" % "0.1.6"),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.6.1",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.4",
      "com.chuusai" %% "shapeless" % "2.3.7",
      "org.tpolecat" %% "doobie-core" % doobieVersion,
    ),
  )

lazy val coretest = Project("coretest", file("modules/coretest"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "pprint" % "0.6.6",
      "org.flywaydb" % "flyway-core" % "7.9.1",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "dev.zio" %% "zio-test" % zioVersion,
      "dev.zio" %% "zio-test-sbt" % zioVersion,
      "dev.zio" %% "zio-test-magnolia" % zioVersion,
      "dev.zio" %% "zio-interop-cats" % "2.5.1.0",
      "javax.activation" % "activation" % "1.1.1", // Reuqired for DataSource class in JDK 9+
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres-circe" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "org.postgresql" % "postgresql" % "42.2.22",
      "com.softwaremill.quicklens" %% "quicklens" % "1.7.4",
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
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres-circe" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.9.0",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.9.0" % "provided",
      "org.tpolecat" %% "skunk-core" % "0.0.28",
    ),
  )

lazy val docs = project
  .dependsOn(core, coretest)
  .enablePlugins(MicrositesPlugin)
  .settings(
    commonSettings,
    publish / skip := true,
  )
  .settings(
    mdocIn := file("docs/docs"),
    mdocExtraArguments ++= Seq("--noLinkHygiene"),
    micrositeName := "DoobieRoll",
    micrositeDescription := "Collection of utilities for working with Doobie/SQL",
    micrositeUrl := "https://jatcwang.github.io",
    micrositeBaseUrl := "/doobieroll",
    micrositeDocumentationUrl := s"${micrositeBaseUrl.value}/docs/assembler",
    micrositeAuthor := "Jacob Wang",
    micrositeGithubOwner := "jatcwang",
    micrositeGithubRepo := "doobieroll",
    micrositeHighlightTheme := "a11y-light",
    micrositePushSiteWith := GitHub4s,
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
  )
  .settings(
    // Disble any2stringAdd deprecation in md files. Seems like mdoc macro generates code which
    // use implicit conversion to string
    scalacOptions ~= { opts =>
      val extraOpts = Seq("-Wconf:msg=\".*method any2stringadd.*\":i")

      val removes = Set("-Wdead-code", "-Ywarn-dead-code") // we use ??? in various places

      (opts ++ extraOpts).filterNot(removes)
    },
    scalacOptions --= Seq("-Wdead-code", "-Ywarn-dead-code"), // we use ??? in various places
  )

lazy val root = project
  .in(new File("."))
  .aggregate(bench, core, coretest, docs)
  .settings(noPublishSettings)
  .settings(commonSettings)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-Ywarn-macros:after",
  ),
  versionScheme := Some("early-semver"),
  doc / scalacOptions --= Seq("-Xfatal-warnings"),
  scalacOptions --= {
    if (sys.env.contains("CI")) {
      Seq.empty
    } else {
      Seq("-Xfatal-warnings")
    }
  },
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
)

lazy val noPublishSettings = Seq(
  publish / skip := true,
)

ThisBuild / githubWorkflowJavaVersions := Seq("adopt@1.11")
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches :=
  Seq(RefPredicate.StartsWith(Ref.Tag("v")))

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release", "publishMicrosite"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
    ),
  ),
)

val setupJekyllSteps = Seq(
  WorkflowStep.Use(
    UseRef.Public("actions", "setup-ruby", "v1"),
    name = Some("Setup ruby"),
    params = Map("ruby-version" -> "2.7"),
  ),
  WorkflowStep.Run(
    List("gem install jekyll -v 4.1.1"),
    name = Some("Install Jekyll (to build microsite)"),
  ),
)

ThisBuild / githubWorkflowBuildPreamble ++= setupJekyllSteps ++ Seq(
  WorkflowStep.Run(
    List("docker pull postgres:12.3-alpine"),
    name = Some("Pull Postgres image for integration tests"),
  ),
)

ThisBuild / githubWorkflowPublishPreamble ++= setupJekyllSteps

// Add makeMicrosite to the build step
ThisBuild / githubWorkflowBuild ~= { steps =>
  steps.map {
    case w: WorkflowStep.Sbt if w.commands == List("test") =>
      w.copy(commands = List("test", "makeMicrosite"))
    case w => w
  }
}
// Filter out MacOS and Windows cache steps to make yaml less noisy
ThisBuild / githubWorkflowGeneratedCacheSteps ~= { currentSteps =>
  currentSteps.filterNot(wf =>
    wf.cond.exists(str => str.contains("macos") || str.contains("windows")),
  )
}
