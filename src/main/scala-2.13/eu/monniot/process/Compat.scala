package eu.monniot.process

// compat layer to compile on 2.12 & 2.13
private[process] object Compat {
  val CollectionConverters = scala.jdk.CollectionConverters
}
