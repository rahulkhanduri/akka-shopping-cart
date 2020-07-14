package cnapp.poc.infrastructure

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import cnapp.poc.domain.Repository
import cnapp.poc.domain.ShoppingCart.Event
import cnapp.poc.infrastructure.UserEventProducer.PushMessage
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ShoppingCartProjectionHandler(tag: String, system: ActorSystem[_],repository: Repository[Event])
    extends Handler[EventEnvelope[Event]] {
  val log = LoggerFactory.getLogger(getClass)

  override def process(envelope: EventEnvelope[Event]): Future[Done] = {
    log.info(
      "EventProcessor({}) consumed {} from {} with seqNr {}",
      tag,
      envelope.event,
      envelope.persistenceId,
      envelope.sequenceNr)
    repository.get("test").foreach(println(_))
    UserEventProducer(system,PushMessage(envelope.event))
    repository.insert("cart",envelope.event)
  }
}
