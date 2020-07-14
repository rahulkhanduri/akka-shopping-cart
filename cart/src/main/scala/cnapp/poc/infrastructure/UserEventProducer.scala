package cnapp.poc.infrastructure

import akka.Done
import akka.actor.typed.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import cnapp.poc.domain.ShoppingCart.{CheckedOut, Event}
import cnapp.poc.domain.{CborSerializable, ProducerConfig}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import org.slf4j.LoggerFactory
import sample.sharding.kafka.serialization.InventoryDeductProto

import scala.concurrent.Future

class UserEventProducer(system: ActorSystem[_]) {

  val log = LoggerFactory.getLogger(getClass)

  val config = system.settings.config.getConfig("akka.kafka.producer")

  val producerConfig = ProducerConfig(system.settings.config.getConfig("shopping.kafka.producer"))

  val producerSettings: ProducerSettings[String, Array[Byte]] =
    ProducerSettings(config, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(producerConfig.bootstrapServers)

  val pushToKafka: CheckedOut => Future[Done] = (event:CheckedOut) => {
        implicit val iSystem = system
        Source(event.summary.items.toSeq)
          .map(item => {
            val message = InventoryDeductProto(item._1,item._2).toByteArray
            log.info("Sending message for cart {} and item {} ",event.cartId,item._1 )
            // rely on the default kafka partitioner to hash the key and distribute among shards
            // the logic of the default partitioner must be replicated in MessageExtractor entityId -> shardId function
            new ProducerRecord[String, Array[Byte]](producerConfig.topic, event.cartId, message)
          })
          .runWith(Producer.plainSink(producerSettings))
      }
//  def init(system: ActorSystem[_]): Unit = {
//    val pool = Routers.pool(poolSize = 4)(
//      // make sure the workers are restarted if they fail
//      Behaviors.supervise(UserEventProducer()).onFailure[Exception](SupervisorStrategy.restart))
//    val router = system..spawn(pool, "worker-pool")
//  }

}

object UserEventProducer{
  val log = LoggerFactory.getLogger(getClass)
  sealed trait Command extends CborSerializable
  final case class PushMessage(msg:Event) extends Command

  def apply(system: ActorSystem[_],pushMessage: PushMessage): Future[Done] = pushMessage match {
      case PushMessage(msg:CheckedOut) => new UserEventProducer(system).pushToKafka(msg)
      case _ => {
        log.info("Kafka push ignored")
        Future.successful(Done)
      }
  }
}
