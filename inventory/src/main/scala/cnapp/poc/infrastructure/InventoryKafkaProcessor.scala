package cnapp.poc.infrastructure

import akka.Done
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.{ActorSystem, Scheduler}
import akka.kafka.cluster.sharding.KafkaClusterSharding
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka.{CommitterSettings, Subscriptions}
import akka.pattern.retry
import cnapp.poc.domain.Inventory.{Confirmation, DeductInventory}
import cnapp.poc.domain.{Inventory, ProcessorSettings}
import sample.sharding.kafka.serialization.InventoryDeductProto

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

object InventoryKafkaProcessor {

  sealed trait Command

  private case class KafkaConsumerStopped(reason: Try[Any]) extends Command

  def apply(shardRegion: ActorRef[Inventory.Command], processorSettings: ProcessorSettings): Behavior[Nothing] = {
    Behaviors
      .setup[Command] { ctx =>
        implicit val classic: ActorSystem = ctx.system.toClassic
        implicit val ec: ExecutionContextExecutor = ctx.executionContext
        implicit val scheduler: Scheduler = classic.scheduler

        val rebalanceListener = KafkaClusterSharding(classic).rebalanceListener(processorSettings.entityTypeKey)

        val subscription = Subscriptions
          .topics(processorSettings.topics: _*)
          .withRebalanceListener(rebalanceListener.toClassic)

        val stream: Future[Done] = Consumer.sourceWithOffsetContext(processorSettings.kafkaConsumerSettings(), subscription)
          // MapAsync and Retries can be replaced by reliable delivery
          .mapAsync(20) { record =>
            //ctx.log.info(s"user id consumed kafka partition ${record.key()}->${record.partition()}")
            println(s"item id consumed kafka partition ${record.value()}->${record.partition()}")
            retry(() =>
              shardRegion.ask[Confirmation](replyTo => {
                val inventoryDeductProto = InventoryDeductProto.parseFrom(record.value())
                DeductInventory(
                  inventoryDeductProto.itemId,
                  inventoryDeductProto.quantity.toInt,
                  replyTo)
              })(processorSettings.askTimeout, ctx.system.scheduler),
              attempts = 5,
              delay = 1.second
            )
          }
          .runWith(Committer.sinkWithOffsetContext(CommitterSettings(classic)))

        stream.onComplete { result =>
          ctx.self ! KafkaConsumerStopped(result)
        }
        Behaviors.receiveMessage[Command] {
          case KafkaConsumerStopped(reason) =>
            ctx.log.info("Consumer stopped {}", reason)
            Behaviors.stopped
        }
      }
      .narrow
  }


}
