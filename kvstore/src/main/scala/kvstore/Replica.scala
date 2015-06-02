package kvstore

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import kvstore.Arbiter._
import kvstore.Persistence.{Persist, Persisted, PersistenceException}
import kvstore.Replica._
import kvstore.Replicator.{Snapshot, SnapshotAck}
import scala.concurrent.duration._

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

  var persistenceAcks = Map.empty[Long, (ActorRef, Persist)]
  var retries = Map.empty[Long, Cancellable]


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

  /* TODO Behavior for  the leader role. */
  val leader: Receive = {
    case Insert(key, value, id) =>
      insert(key, value, id)
    case Remove(key, id) =>
      remove(key, id)
    case Get(key, id) =>
      find(key, id)
    case Persisted(key, id) =>
      val ack: (ActorRef) = findSenderToConfirmPersistence(id)
      ack ! OperationAck(id)
    case _ =>
  }

  /* TODO Behavior for the replica role. */
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
    case Persisted(key, id) =>
      val ack: (ActorRef) = findSenderToConfirmPersistence(id)
      ack ! SnapshotAck(key, id)

    case _ =>
  }

  def findSenderToConfirmPersistence(id: Long) = {
    val ack: (ActorRef, Persist) = persistenceAcks.get(id).get
    persistenceAcks -= id
    val cancellable: Cancellable = retries.get(id).get
    cancellable.cancel()
    retries -= id
    ack._1
  }

  def insertSnapshot(key: String, seq: Long, value: String): Unit = {
    kv += (key -> value)
    registerAckAndPersist(key, seq, Some(value))
  }


  def registerAckAndPersist(key: String, seq: Long, value: Option[String]): Unit = {
    val persist: Persist = Persist(key, value, seq)
    updateExpectedSeq(seq)
    persistenceAcks += (seq ->(sender, persist))
    scheduleRetries(seq, persist)
    persistence ! persist
  }

  def removeSnapshot(key: String, seq: Long): Unit = {
    kv -= (key)
    registerAckAndPersist(key,seq,None)
  }

  def find(key: String, id: Long): Unit = {
    sender ! GetResult(key, kv.get(key), id)
  }

  def remove(key: String, id: Long): Unit = {
    kv -= (key)
    registerAckAndPersist(key,id,None)
//    sender ! OperationAck(id)
  }

  def insert(key: String, value: String, id: Long): Unit = {
    kv += (key -> value)
    registerAckAndPersist(key, id, Some(value))
//    sender ! OperationAck(id)
  }

  def scheduleRetries(seq: Long, persist: Persist): Unit = {
    retries += seq -> context.system.scheduler.schedule(100 millis, 100 millis, persistence, persist)
  }


}

