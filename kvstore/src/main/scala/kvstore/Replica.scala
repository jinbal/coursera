package kvstore

import scala.concurrent.duration._

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import kvstore.Arbiter._
import kvstore.Persistence.{Persist, Persisted, PersistenceException}
import kvstore.Replica._
import kvstore.Replicator.{Snapshot, SnapshotAck}

object Replica {

  sealed trait Operation {
    def key: String

    def id: Long
  }

  case class Insert(key: String, value: String, id: Long) extends Operation

  case class Remove(key: String, id: Long) extends Operation

  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply

  case class OperationAck(id: Long) extends OperationReply

  case class OperationFailed(id: Long) extends OperationReply

  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global

  arbiter ! Join

  val persistence = context.actorOf(persistenceProps)

  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: PersistenceException => Restart
  }

  var expectedSeq: Long = 0

  def updateExpectedSeq(seq: Long) = {
    expectedSeq = seq + 1
  }

  def receive = {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  val leader: Receive = {
    case Insert(key, value, id) =>
      insert(key, value, id)
    case Remove(key, id) =>
      remove(key, id)
    case Get(key, id) =>
      find(key, id)
    case Replicas(replicas: Set[ActorRef]) =>
    case _ =>
  }

  val replica: Receive = {
    case Get(key, id) => find(key, id)
    case Snapshot(key, valueOptions, seq) =>
      if (seq < expectedSeq) {
        sender ! SnapshotAck(key, seq)
      } else if (seq == expectedSeq) {
        valueOptions match {
          case Some(value) =>
            insertSnapshot(key, seq, value)
          case None =>
            removeSnapshot(key, seq)
        }
      }
    case _ =>
  }

  def insertSnapshot(key: String, seq: Long, value: String): Unit = {
    kv += (key -> value)
    context.actorOf(Props(classOf[SnapshotHandlerActor], persistence, sender, Persist(key, Some(value), seq)))
  }

  def removeSnapshot(key: String, seq: Long): Unit = {
    kv -= (key)
    context.actorOf(Props(classOf[SnapshotHandlerActor], persistence, sender, Persist(key, None, seq)))
  }

  def find(key: String, id: Long): Unit = {
    sender ! GetResult(key, kv.get(key), id)
  }

  def remove(key: String, id: Long): Unit = {
    kv -= (key)
    context.actorOf(Props(classOf[PersistenceHandlerActor], persistence, sender, Persist(key, None, id)))
  }

  def insert(key: String, value: String, id: Long): Unit = {
    kv += (key -> value)
    context.actorOf(Props(classOf[PersistenceHandlerActor], persistence, sender, Persist(key, Some(value), id)))
  }

  def scheduleRetries(seq: Long, persist: Persist): Unit = {
    retries += seq -> context.system.scheduler.schedule(100 millis, 100 millis, persistence, persist)
  }
}

class PersistenceHandlerActor(persistence: ActorRef, origin: ActorRef, persist: Persist) extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global

  persistence ! persist
  context.setReceiveTimeout(1 second)
  val retrySchedule: Cancellable = context.system.scheduler.schedule(100 millis, 100 millis, persistence, persist)

  def receive = {
    case Persisted(key, id) =>
      retrySchedule.cancel()
      origin ! OperationAck(id)
      self ! PoisonPill
    case ReceiveTimeout =>
      retrySchedule.cancel()
      origin ! OperationFailed(persist.id)
      self ! PoisonPill
  }
}

class SnapshotHandlerActor(persistence: ActorRef, origin: ActorRef, persist: Persist) extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global

  persistence ! persist

  val retrySchedule: Cancellable = context.system.scheduler.schedule(100 millis, 100 millis, persistence, persist)

  def receive = {
    case Persisted(key, id) =>
      retrySchedule.cancel()
      origin ! SnapshotAck(key, id)
      self ! PoisonPill
  }

}

