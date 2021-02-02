addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.16")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
// Need to publish locally to include a fix jcmd detection is
//addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.8-SNAPSHOT")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.0")
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.5")
addSbtPlugin("com.47deg" % "sbt-microsites" % "1.3.2")
addSbtPlugin("com.codecommit" % "sbt-github-actions" % "0.9.5")
