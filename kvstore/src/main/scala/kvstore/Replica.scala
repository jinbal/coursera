package kvstore

import akka.actor.{Actor, ActorRef, Props}
import kvstore.Arbiter._
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

  arbiter ! Join

  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  var expectedSeq:Long = 0

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
    case _ =>
  }

  /* TODO Behavior for the replica role. */
  val replica: Receive = {
    case Get(key, id) => find(key, id)
    case Snapshot(key, valueOptions, seq) =>
      if(seq < expectedSeq) {
        sender ! SnapshotAck(key,seq)
      } else if(seq == expectedSeq) {
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
    sender ! SnapshotAck(key, seq)
    expectedSeq =seq+1
  }

  def removeSnapshot(key: String, seq: Long): Unit = {
    kv -= (key)
    sender ! SnapshotAck(key, seq)
    expectedSeq =seq+1
  }

  def find(key: String, id: Long): Unit = {
    sender ! GetResult(key, kv.get(key), id)
  }

  def remove(key: String, id: Long): Unit = {
    kv -= (key)
    sender ! OperationAck(id)
  }

  def insert(key: String, value: String, id: Long): Unit = {
    kv += (key -> value)
    sender ! OperationAck(id)
  }

}

