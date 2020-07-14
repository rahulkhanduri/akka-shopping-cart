package cnapp.poc.infrastructure

import akka.Done
import akka.actor.typed.ActorSystem
import akka.stream.alpakka.mongodb.scaladsl.{MongoSink, MongoSource}
import akka.stream.scaladsl.{Sink, Source}
import cnapp.poc.domain.Repository
import com.mongodb.reactivestreams.client.{MongoClient, MongoClients}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.mongodb.scala.bson.codecs
import cnapp.poc.domain.ShoppingCart.{Event, Summary}

import scala.concurrent.Future
class MongodbRepository(system: ActorSystem[_], client: MongoClient, codec:CodecRegistry,database:String)
  extends Repository[Event] {
  implicit val iSystem = system

  def getCollection =
      (database: String, collection: String) => client.getDatabase(database).getCollection(collection,classOf[Event]).withCodecRegistry(codec)

  override def insert(collection: String, content: Event):Future[Done] = Source.single(content)
      .runWith(MongoSink.insertOne(getCollection(database, collection)))

  override def get(collection: String): Future[Seq[Event]] = MongoSource(getCollection(database, collection)
      .find(classOf[Event])).runWith(Sink.seq)
}
object MongodbRepository {
  import org.mongodb.scala.bson.codecs.Macros._
  val codec = CodecRegistries.fromRegistries(CodecRegistries.fromProviders(classOf[Event],classOf[Summary]), codecs.DEFAULT_CODEC_REGISTRY)
  def getRepository(system: ActorSystem[_])(database:String) = new MongodbRepository(system, MongoClients.create(s"mongodb://" +
        s"${system.settings.config.getString("shopping.mongo.host")}:" +
        s"${system.settings.config.getInt("shopping.mongo.port")}"),codec,database)

}


