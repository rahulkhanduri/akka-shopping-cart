package cnapp.poc.infrastructure

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.cluster.typed.{Cluster, SelfUp, Subscribe}
import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import cnapp.poc.api.InventoryGrpcService
import cnapp.poc.domain.{Inventory, ProcessorSettings}
import com.typesafe.config.{Config, ConfigFactory}
import sample.sharding.kafka.InventoryServiceHandler

import scala.concurrent.Future
import scala.util.{Failure, Success}

sealed trait Command
case object NodeMemberUp extends Command
final case class ShardingStarted(region: ActorRef[Inventory.Command]) extends Command
final case class BindingFailed(reason: Throwable) extends Command

object Main {
  def main(args: Array[String]): Unit = {

    def isInt(s: String): Boolean = s.matches("""\d+""")

    args.toList match {
      case single :: Nil if isInt(single) =>
        val nr = single.toInt
        init(2550 + nr, 8550 + nr, 8080 + nr, 9000 + nr)
      case portString :: managementPort :: frontEndPort :: prometheusPort :: Nil
          if isInt(portString) && isInt(managementPort) && isInt(frontEndPort) =>
        init(portString.toInt, managementPort.toInt, frontEndPort.toInt,prometheusPort.toInt)
      case _ =>
        throw new IllegalArgumentException("usage: <remotingPort> <managementPort> <frontEndPort>")
    }
  }

  def init(remotingPort: Int, akkaManagementPort: Int, frontEndPort: Int,prometheusPort: Int): Unit = {
    ActorSystem(Behaviors.setup[Command] {
      ctx =>
        AkkaManagement(ctx.system.toClassic).start()
        val cluster = Cluster(ctx.system)
        val upAdapter = ctx.messageAdapter[SelfUp](_ => NodeMemberUp)
        cluster.subscriptions ! Subscribe(upAdapter, classOf[SelfUp])
        val settings = ProcessorSettings("kafka-to-sharding-processor", ctx.system.toClassic)
        ctx.pipeToSelf(Inventory.init(ctx.system, settings)) {
          case Success(extractor) => ShardingStarted(extractor)
          case Failure(ex) => throw ex
        }
        starting(ctx, None, joinedCluster = false, settings)
    }, "KafkaToSharding", config(remotingPort, akkaManagementPort,prometheusPort))

    def start(ctx: ActorContext[Command], region: ActorRef[Inventory.Command], settings: ProcessorSettings): Behavior[Command] = {
      import ctx.executionContext
      ctx.log.info("Sharding started and joined cluster. Starting event processor")
      val eventProcessor = ctx.spawn[Nothing](InventoryKafkaProcessor(region, settings), "kafka-event-processor")
      val binding: Future[Http.ServerBinding] = startGrpc(ctx.system, frontEndPort, region)
      binding.onComplete {
        case Success(bound) =>
          ctx.log.info("Bound: {}", bound)
        case Failure(t) =>
          ctx.self ! BindingFailed(t)
      }
      running(ctx, binding, eventProcessor)
    }

    def starting(ctx: ActorContext[Command], sharding: Option[ActorRef[Inventory.Command]], joinedCluster: Boolean, settings: ProcessorSettings): Behavior[Command] = Behaviors
      .receive[Command] {
        case (ctx, ShardingStarted(region)) if joinedCluster =>
          ctx.log.info("Sharding has started")
          start(ctx, region, settings)
        case (_, ShardingStarted(region)) =>
          ctx.log.info("Sharding has started")
          starting(ctx, Some(region), joinedCluster, settings)
        case (ctx, NodeMemberUp) if sharding.isDefined =>
          ctx.log.info("Member has joined the cluster")
          start(ctx, sharding.get, settings)
        case (_, NodeMemberUp)  =>
          ctx.log.info("Member has joined the cluster")
          starting(ctx, sharding, joinedCluster = true, settings)
      }

    def running(ctx: ActorContext[Command], binding: Future[Http.ServerBinding], processor: ActorRef[Nothing]): Behavior[Command] =
      Behaviors.receiveMessagePartial[Command] {
        case BindingFailed(t) =>
          ctx.log.error("Failed to bind front end", t)
          Behaviors.stopped
      }.receiveSignal {
        case (ctx, Terminated(`processor`)) =>
          ctx.log.warn("Kafka event processor stopped. Shutting down")
          binding.map(_.unbind())(ctx.executionContext)
          Behaviors.stopped
      }


    def startGrpc(system: ActorSystem[_], frontEndPort: Int, region: ActorRef[Inventory.Command]): Future[Http.ServerBinding] = {
      val mat = Materializer.createMaterializer(system.toClassic)
      val service: HttpRequest => Future[HttpResponse] =
        InventoryServiceHandler(new InventoryGrpcService(system, region))(mat, system.toClassic)
      Http()(system.toClassic).bindAndHandleAsync(
        service,
        interface = "127.0.0.1",
        port = frontEndPort,
        connectionContext = HttpConnectionContext())(mat)

    }

    def config(port: Int, managementPort: Int,prometheusPort:Int): Config =
      ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      akka.management.http.port = $managementPort
      cinnamon.prometheus.http-server.port = $prometheusPort
       """).withFallback(ConfigFactory.load())

  }
}
