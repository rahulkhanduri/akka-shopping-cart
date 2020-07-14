package client

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.Materializer
import sample.sharding.kafka.{AddInventoryRequest, GetInventoryRequest, InventoryServiceClient}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.io.StdIn
import scala.util.Random


object InventoryLoadTestApp extends App {
  implicit val system: ActorSystem = ActorSystem("UserClient")
  implicit val mat: Materializer = Materializer.createMaterializer(system)
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  val clientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8084).withTls(false)
  val client = InventoryServiceClient(clientSettings)
  var itemId = ""
  var products = List("socks","shoes","shirt","belt","jacket","cap","chair","sofa","bed","door")
  val random = new Random()
  while (itemId != ":q") {
    println("Enter total operation count example create,shocks or add,shocks or :q to quit")
    itemId = StdIn.readLine()
    if (itemId != ":q") {
      (1 to itemId.toInt).foreach(i=>{
        val pid = products(random.nextInt(products.length))+""+random.nextInt(products.length)
        val cresponse = Await.result(client.create(AddInventoryRequest(pid, 1)), Duration.Inf)
        println(s"Response of Product with  id ${cresponse.itemId} is ${cresponse.message}")
        val aresponse = Await.result(client.add(AddInventoryRequest(pid, 1)), Duration.Inf)
        println(s"Response of Product with  id ${aresponse.itemId} is ${aresponse.message}")
        val gresponse = Await.result(client.get(GetInventoryRequest(pid)), Duration.Inf)
        println(s"Response is $gresponse")
      })
    }
  }
  println("Exiting")
  system.terminate()
}
