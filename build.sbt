val root = Project("root", file("."))
  .settings(
    name := "Scala Starter",
    version := "0.1.0",
    scalaVersion := "2.13.1",
    scalacOptions ++= Seq("-language:higherKinds"/*, "-Ypartial-unification"*/),

    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.0.0",

      "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    )
  )
