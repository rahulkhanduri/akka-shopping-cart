package cnapp.poc.domain

import akka.Done

import scala.concurrent.Future

trait Repository[T] {
  def insert(collection: String, content: T): Future[Done]

  def get(collection: String): Future[Seq[T]]

  def update(collection: String, content: Inventory.InventoryItemInfo): Future[Done]
}
