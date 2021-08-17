// sbt-tpolecat configures your scalac options according to @tpolecat's recommendations
// where possible, according to the Scala version you are using.
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.20")

// sbt plugin to automate Sonatype releases from CI
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")

// Code formatter for Scala
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")

// Check dependencies, do not commit
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.0")
