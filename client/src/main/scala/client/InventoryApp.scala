package client

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.Materializer
import sample.sharding.kafka.{AddInventoryRequest, GetInventoryRequest, InventoryServiceClient}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.io.StdIn


object InventoryApp extends App {
  implicit val system: ActorSystem = ActorSystem("UserClient")
  implicit val mat: Materializer = Materializer.createMaterializer(system)
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  val clientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8084).withTls(false)
  val client = InventoryServiceClient(clientSettings)
  var itemId = ""
  while (itemId != ":q") {
    println("Enter operation and product name delimit by , example create,shocks or add,shocks or :q to quit")
    itemId = StdIn.readLine()
    if (itemId != ":q") {
      if(itemId.split(",")(0).toLowerCase() == "add"){
        val response = Await.result(client.add(AddInventoryRequest(itemId.split(",")(1),1)),Duration.Inf)
        println(s"Response of Product with  id ${response.itemId} is ${response.message}")
      }
      else if(itemId.split(",")(0).toLowerCase() == "create"){
        val response = Await.result(client.create(AddInventoryRequest(itemId.split(",")(1),1)),Duration.Inf)
        println(s"Response of Product with  id ${response.itemId} is ${response.message}")
      }
      else {
        val response = Await.result(client.get(GetInventoryRequest(itemId.split(",")(1))),Duration.Inf)
        println(s"Response is $response")
      }
      }
  }
  println("Exiting")
  system.terminate()
}
