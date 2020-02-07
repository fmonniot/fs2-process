lazy val root = (project in file("."))
  .settings(
    organization := "eu.monniot",
    name := "fs2-process",
    version := "0.1.0",
    scalaVersion := "2.13.1",

    libraryDependencies ++= Seq(
      "co.fs2"         %% "fs2-core"                      % "2.2.2",
      "com.zaxxer"     % "nuprocess"                      % "1.2.3",

      "org.scalatest"  %% "scalatest"                     % "3.1.0" % Test,
      "org.scalacheck" %% "scalacheck"                    % "1.14.3" % Test,
      "com.codecommit" %% "cats-effect-testing-scalatest" % "0.4.0" % Test
    ),

    addCompilerPlugin(("org.typelevel" %% "kind-projector" % "0.11.0").cross(CrossVersion.full)),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  )
