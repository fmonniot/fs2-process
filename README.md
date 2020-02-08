# fs2-process ![CI](https://github.com/fmonniot/fs2-process/workflows/CI/badge.svg) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/eu.monniot/fs2-process_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/eu.monniot/fs2-process_2.13)

A lightweight wrapper around [NuProcess](https://github.com/brettwooldridge/NuProcess). This library offer a resource-safe, functional API for the Cats Effect / fs2 ecosystem.

Error reporting is currently limited but otherwise the library should be in good shape for use. Do note that
no production deployment have been attempted yet.

## Usage

```sbt
libraryDependencies += "eu.monniot" % "fs2-process" % "<latest-version>"
```

This library is currently published for scala 2.12 and 2.13. NuProcess being a Java library,
there is support for scala.js or native.

What follows is a simple example of spawning a process and reading its standard output:

```scala
import eu.monniot.process.Process
import cats.effect._

object Example extends IOApp {
  override def run(args:  List[String]): IO[ExitCode] = 
    Process.spawn[IO]("ls").use { process =>
      process.stdout
          .through(fs2.text.utf8Decode)
          .compile
          .foldMonoid
          .flatMap(text => IO(println(text)))
    }.map(_ => ExitCase.Success)
}
```

More complex examples can be found in the [`ProcessSpec`](https://github.com/fmonniot/fs2-process/blob/master/src/test/scala/eu/monniot/process/ProcessSpec.scala) test suite.