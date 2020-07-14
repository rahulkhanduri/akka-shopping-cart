package cnapp.poc.api

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.util.Timeout
import cnapp.poc.domain.Inventory
import cnapp.poc.domain.Inventory.{Accepted, AddInventory, Confirmation, CreateInventory, GetInventory, Item, Rejected}
import sample.sharding.kafka._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

class InventoryGrpcService(system: ActorSystem[_], shardRegion: ActorRef[Inventory.Command]) extends InventoryService {

  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val sched: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor = system.executionContext

  override def add(in: AddInventoryRequest): Future[AddInventoryResponse] = {
    shardRegion.ask[Confirmation](replyTo => AddInventory(in.itemId,in.quantity,replyTo)).map({
      case Accepted(_) => AddInventoryResponse(in.itemId,"Quantity added successfully")
      case Rejected(reason) => AddInventoryResponse(in.itemId,reason)
    })
  }

  override def create(in: AddInventoryRequest): Future[AddInventoryResponse] = {
    shardRegion.ask[Confirmation](replyTo => CreateInventory(in.itemId,in.quantity,replyTo)).map({
      case Accepted(_) => AddInventoryResponse(in.itemId,"Item created successfully")
      case Rejected(reason) => AddInventoryResponse(in.itemId,reason)
    })
  }

  override def get(in: GetInventoryRequest): Future[GetInventoryResponse] = {
    shardRegion.ask[Item](replyTo => GetInventory(in.itemId,replyTo)).map(i=>GetInventoryResponse(in.itemId,i.quantity))
  }
}
