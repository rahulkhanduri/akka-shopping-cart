addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "0.7.3")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4") // ALPN agent
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.25")
addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.13.0")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.9.0"
