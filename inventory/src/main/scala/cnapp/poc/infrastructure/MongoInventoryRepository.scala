package cnapp.poc.infrastructure

import akka.Done
import akka.actor.typed.ActorSystem
import akka.stream.alpakka.mongodb.DocumentUpdate
import akka.stream.alpakka.mongodb.scaladsl.{MongoSink, MongoSource}
import akka.stream.scaladsl.{Sink, Source}
import com.mongodb.reactivestreams.client.{MongoClient, MongoClients}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.mongodb.scala.bson.codecs
import cnapp.poc.domain.Inventory.InventoryItemInfo
import cnapp.poc.domain.Repository
import org.mongodb.scala.model.{Filters, Updates}

import scala.concurrent.Future

class MongoInventoryRepository(system: ActorSystem[_], client: MongoClient, codec:CodecRegistry, database:String) extends Repository[InventoryItemInfo] {
  implicit val iSystem = system

  def getCollection =
    (database: String, collection: String) => client.getDatabase(database).getCollection(collection,classOf[InventoryItemInfo]).withCodecRegistry(codec)

  override def insert(collection: String, content: InventoryItemInfo):Future[Done] = {
    println(s"Inserting $content")
    Source.single(content)
      .runWith(MongoSink.insertOne(getCollection(database, collection)))
  }

  override def update(collection: String, content: InventoryItemInfo):Future[Done] = {
    println(s"Updating $content")
    Source.single(content).map(
      i => DocumentUpdate(filter = Filters.eq("_id", i._id), update = Updates.set("quantity", i.quantity))
    ).runWith(MongoSink.updateOne(getCollection(database, collection)))
  }

  override def get(collection: String): Future[Seq[InventoryItemInfo]] = {
    println(s"Finding in $collection")
    MongoSource(getCollection(database, collection)
      .find(classOf[InventoryItemInfo])).runWith(Sink.seq)
  }
}

object MongoInventoryRepository {
  import org.mongodb.scala.bson.codecs.Macros._
  val codec = CodecRegistries.fromRegistries(CodecRegistries.fromProviders(classOf[InventoryItemInfo]), codecs.DEFAULT_CODEC_REGISTRY)
  def getRepository(system: ActorSystem[_])(database:String) = new MongoInventoryRepository(system, MongoClients.create(s"mongodb://" +
        s"${system.settings.config.getString("shopping.mongo.host")}:" +
        s"${system.settings.config.getInt("shopping.mongo.port")}"),codec,database)

}


