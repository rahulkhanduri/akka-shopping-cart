package cnapp.poc.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.external.ExternalShardAllocationStrategy
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.kafka.cluster.sharding.KafkaClusterSharding
import cnapp.poc.infrastructure.MongoInventoryRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._
object Inventory {
  def init(system: ActorSystem[_], settings: ProcessorSettings) : Future[ActorRef[Command]] = {
    import system.executionContext
    KafkaClusterSharding(settings.system).messageExtractorNoEnvelope(
      timeout = 10.seconds,
      topic = settings.topics.head,
      entityIdExtractor = (msg: Command) => msg.itemId,
      settings = settings.kafkaConsumerSettings()
    ).map(messageExtractor => {
      system.log.info("Message extractor created. Initializing sharding")
      ClusterSharding(system).init(
        Entity(settings.entityTypeKey)(createBehavior = _ => Inventory(repository = MongoInventoryRepository.getRepository(system)("shopping")))
          .withAllocationStrategy(new ExternalShardAllocationStrategy(system, settings.entityTypeKey.name))
          .withMessageExtractor(messageExtractor))
    })
  }

  final case class State(quantity:Int) extends CborSerializable {
    def add(count: Int):State = copy(quantity=quantity + count)
    def remove(count:Int) = copy(quantity = quantity - count)
    def toItem:Item = Item(quantity=quantity)
    def shouldRemove(count:Int):Boolean = if(quantity>0 && quantity >= count) true else false
  }
  object State {
    val empty = State(0)
  }
  final case class InventoryItemInfo(_id:String,quantity: Int)

  trait Command extends CborSerializable {
    def itemId:String
  }
  final case class GetInventory(itemId:String,replyTo: ActorRef[Item]) extends Command
  final case class CreateInventory(itemId:String,quantity: Int,replyTo:ActorRef[Confirmation]) extends Command
  final case class AddInventory(itemId:String,quantity: Int,replyTo: ActorRef[Confirmation]) extends Command
  final case class DeductInventory(itemId:String,quantity: Int,replyTo: ActorRef[Confirmation]) extends Command

  trait Event extends CborSerializable{
     def itemId:String
  }
  final case class InventoryCreated(itemId:String,quantity: Int) extends Event
  final case class InventoryAdded(itemId:String,quantity: Int) extends Event
  final case class InventoryDeducted(itemId:String,quantity: Int) extends Event

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Inventory")

  final case class Item(quantity: Int) extends CborSerializable

  sealed trait Confirmation extends CborSerializable

  final case class Accepted(item : Item) extends Confirmation

  final case class Rejected(reason: String) extends Confirmation

  def handleEvent(state: State, event: Event): State =
    event match {
      case InventoryAdded(_,quantity) => state.add(quantity)
      case InventoryDeducted(_,quantity) => state.remove(quantity)
    }

  def apply(state: State = State.empty,repository: Repository[InventoryItemInfo]): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage[Command] {
      case DeductInventory(itemId,quantity,replyTo) =>
        var newState = state
        if (quantity <= 0) {
          println(s"Quantity must be greater than zero for item $itemId")
          replyTo ! Rejected("Quantity must be greater than zero")
        }
        else if (!state.shouldRemove(quantity)) {
          println(s"Inventory is npt available for '$itemId' ")
          replyTo ! Rejected(s"Inventory is npt available for $itemId")
        }
        else {
          newState = state.remove(quantity)
          repository.update("inventory",InventoryItemInfo(itemId,newState.quantity)).onComplete({
            case Success(value) => {
              replyTo ! Accepted(newState.toItem)
            }
            case Failure(exception) => Rejected("Not able to update quantity")
          })
        }
        apply(newState,repository)
      case CreateInventory(itemId,quantity,replyTo) =>
        val newState = state.add(quantity)
        repository.insert("inventory",InventoryItemInfo(itemId,newState.quantity)).onComplete({
          case Success(value) => {
            replyTo ! Accepted(newState.toItem)
          }
          case Failure(exception) => replyTo ! Rejected(s"Not able to update quantity $exception")
        })
        apply(newState,repository)
      case AddInventory(itemId,quantity,replyTo) =>
        val newState = state.add(quantity)
        repository.
          update("inventory",InventoryItemInfo(itemId,newState.quantity)).onComplete({
          case Success(value) => {
            replyTo ! Accepted(newState.toItem)
          }
          case Failure(exception) => replyTo ! Rejected(s"Not able to update quantity $exception")
        })
        apply(newState,repository)
      case GetInventory(_,replyTo) => {
        replyTo ! state.toItem
        Behaviors.same
      }
    }
  }


}
