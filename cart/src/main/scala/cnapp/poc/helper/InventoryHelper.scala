package cnapp.poc.helper

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.Materializer
import sample.sharding.kafka.{GetInventoryRequest, InventoryServiceClient}

import scala.concurrent.ExecutionContextExecutor

object InventoryHelper {
  implicit val system: ActorSystem = ActorSystem("UserClient")
  implicit val mat: Materializer = Materializer.createMaterializer(system)
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  val clientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8084).withTls(false)
  val client = InventoryServiceClient(clientSettings)

  def isAvailable(itemId:String,quantity: Int=1) = client.get(GetInventoryRequest(itemId)).map(_.quantity>=quantity)


}
