package kvstore

import akka.actor.{Cancellable, Actor, ActorRef, Props}
import scala.concurrent.duration._

object Replicator {

  case class Replicate(key: String, valueOption: Option[String], id: Long)

  case class Replicated(key: String, id: Long)

  case class Snapshot(key: String, valueOption: Option[String], seq: Long)

  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {

  import Replicator._
  import scala.concurrent.ExecutionContext.Implicits.global

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  // map from sequence number to pair of sender and request
  var acks = Map.empty[Long, (ActorRef, Replicate)]
  var retries = Map.empty[Long,Cancellable]

  var _seqCounter = 0L

  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }


  /* TODO Behavior for the Replicator. */
  def receive: Receive = {
    case Replicate(key, valueOption, id) => sendSnapshotAndScheduleRetries(key, valueOption, id)
    case SnapshotAck(key, seq) => confirmReplication(seq)
    case Snapshot(key, valueOption, seq) if (acks.contains(seq)) => replica ! Snapshot(key, valueOption, seq)
    case _ =>
  }

  def confirmReplication(seq: Long): Unit = {
    val ack: (ActorRef, Replicate) = acks.get(seq).get
    acks -= seq
    ack._1 ! Replicated(ack._2.key, ack._2.id)
    val cancellable: Cancellable = retries.get(seq).get
    cancellable.cancel()
    retries -= seq
  }

  private def sendSnapshotAndScheduleRetries(key: String, valueOption: Option[String], id: Long): Unit = {
    val seq = nextSeq
    acks += (seq ->(sender, Replicate(key, valueOption, id)))
    val snapshot = Snapshot(key, valueOption, seq)
    replica ! snapshot
    scheduleRetries(seq, snapshot)
  }

  def scheduleRetries(seq: Long, snapshot: Snapshot): Unit = {
    retries += seq -> context.system.scheduler.schedule(100 millis, 100 millis, self, snapshot)
  }
}
