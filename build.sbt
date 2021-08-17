lazy val root = (project in file("."))
  .settings(
    name := "fs2-process",
    scalaVersion := "2.13.6",
    crossScalaVersions := List("2.13.6", "2.12.14"),
    organization := "eu.monniot",
    homepage := Some(url("https://github.com/fmonniot/fs2-process")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "fmonniot",
        "François Monniot",
        "francoismonniot@gmail.com",
        url("https://francois.monniot.eu")
      )
    ),
    libraryDependencies ++= Seq(
      "co.fs2"                 %% "fs2-core"                      % "3.1.0",
      "com.zaxxer"             % "nuprocess"                      % "2.0.1",
      "org.scala-lang.modules" %% "scala-collection-compat"       % "2.5.0",
      "org.scalatest"          %% "scalatest"                     % "3.2.9" % Test,
      "org.typelevel"         %% "cats-effect-testing-scalatest" % "1.2.0" % Test
    ),
    addCompilerPlugin(("org.typelevel" %% "kind-projector" % "0.13.0").cross(CrossVersion.full)),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )
