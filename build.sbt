import com.lightbend.cinnamon.sbt.Cinnamon
import com.lightbend.cinnamon.sbt.Cinnamon.CinnamonKeys.cinnamon

val AkkaVersion = "2.6.6"
val AkkaPersistenceCassandraVersion = "1.0.0"
val AkkaHttpVersion = "10.1.11"
val AkkaProjectionVersion = "0.3"
val AlpakkaKafkaVersion = "2.0.3"
val AkkaManagementVersion = "1.0.5"
val LogbackVersion = "1.2.3"

ThisBuild / organization := "com.lightbend.akka.samples"
ThisBuild / version := "1.0"
ThisBuild / scalaVersion := "2.13.1"
ThisBuild / scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint")
ThisBuild / javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
ThisBuild / resolvers ++= Seq(
    "Akka Snapshots" at "https://repo.akka.io/snapshots",
    Resolver.bintrayRepo("akka", "snapshots")
)
ThisBuild / fork in run := false
// disable parallel tests
ThisBuild / parallelExecution in Test := false
// show full stack traces and test case durations
ThisBuild / testOptions in Test += Tests.Argument("-oDF")
ThisBuild / logBuffered in Test := false
ThisBuild / licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

Global / cancelable := false // ctrl-c
lazy val `akka-shopping-cart` =  project.in(file(".")).aggregate(cart,inventory,client)
lazy val cart = project
  .in(file("cart"))
  .enablePlugins(AkkaGrpcPlugin)
//  .settings(PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value))
  .settings(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-cassandra" % AkkaPersistenceCassandraVersion,
        "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % AkkaPersistenceCassandraVersion,
        "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,
        "com.lightbend.akka" %% "akka-projection-cassandra" % AkkaProjectionVersion,
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "2.0.1",
        "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
        "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
        "org.mongodb.scala" %% "mongo-scala-driver" % "2.9.0",
        "com.github.scullxbones" %% "akka-persistence-mongo-scala" % "3.0.2",
       "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
        "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
        "com.lightbend.akka" %% "akka-projection-testkit" % AkkaProjectionVersion % Test,
        "org.scalatest" %% "scalatest" % "3.1.0" % Test,
        "commons-io" % "commons-io" % "2.4" % Test
    )
  )

lazy val inventory = project
  .in(file("inventory"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent,Cinnamon)
  .settings(javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test")
  .settings(resolvers += "bintray-mkroli" at "https://dl.bintray.com/mkroli/maven")
  .settings(cinnamon in run := true)
  .settings(cinnamon in test := false)
  .settings(libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream-kafka-cluster-sharding" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "2.0.1",
      "org.mongodb.scala" %% "mongo-scala-driver" % "2.9.0",
      "com.emnify.akka.management.ui" %% "akka-management-ui" % "1.1",
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
        // Lightbend Telemetry dependencies
        Cinnamon.library.cinnamonAkka,
        Cinnamon.library.cinnamonAkkaHttp,
        Cinnamon.library.cinnamonJvmMetricsProducer,
        Cinnamon.library.cinnamonPrometheus,
        Cinnamon.library.cinnamonPrometheusHttpServer
  ))

lazy val client = project
  .in(file("client"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion))