package nodescala

import scala.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

import nodescala.NodeScala._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class NodeScalaSuite extends FunSuite with ShouldMatchers with AsyncAssertions {

  test("A Future should always be completed") {
    val always = Future.always(517)

    assert(Await.result(always, 0 nanos) == 517)
  }
  test("A Future should never be completed") {
    val never = Future.never[Int]

    try {
      Await.result(never, 1 second)
      assert(false)
    } catch {
      case t: TimeoutException => // ok!
    }
  }

  test("should convert list of futures into future of list") {
    val futureOfList = Future.all(List(Future.always(1), Future.always(2), Future.always(3)))
    assert(Await.result(futureOfList, 500 millis) == List(1, 2, 3))
  }

  test("should return the first completed future") {
    val firstCompleted = Future.any(List(Future.never[Int], Future.always(55), Future.delay(100 millis)))
    assert(Await.result(firstCompleted, 500 millis) == 55)
  }

  test("should not complete before delay") {
    val delayed = Future.delay(2 seconds)
    try {
      Await.ready(delayed, 1 second)
      fail("future completed early")
    } catch {
      case t: TimeoutException => // ok!
    }
  }

  class DummyExchange(val request: Request) extends Exchange {
    @volatile var response = ""
    val loaded = Promise[String]()

    def write(s: String) {
      response += s
    }

    def close() {
      loaded.success(response)
    }
  }

  class DummyListener(val port: Int, val relativePath: String) extends NodeScala.Listener {
    self =>

    @volatile private var started = false
    var handler: Exchange => Unit = null

    def createContext(h: Exchange => Unit) = this.synchronized {
      assert(started, "is server started?")
      handler = h
    }

    def removeContext() = this.synchronized {
      assert(started, "is server started?")
      handler = null
    }

    def start() = self.synchronized {
      started = true
      new Subscription {
        def unsubscribe() = self.synchronized {
          started = false
        }
      }
    }

    def emit(req: Request) = {
      val exchange = new DummyExchange(req)
      if (handler != null) handler(exchange)
      exchange
    }
  }

  class DummyServer(val port: Int) extends NodeScala {
    self =>
    val listeners = mutable.Map[String, DummyListener]()

    def createListener(relativePath: String) = {
      val l = new DummyListener(port, relativePath)
      listeners(relativePath) = l
      l
    }

    def emit(relativePath: String, req: Request) = this.synchronized {
      val l = listeners(relativePath)
      l.emit(req)
    }
  }

  test("Server should serve requests") {
    val dummy = new DummyServer(8191)
    val dummySubscription = dummy.start("/testDir") {
      request => for (kv <- request.iterator) yield (kv + "\n").toString
    }

    // wait until server is really installed
    Thread.sleep(500)

    def test(req: Request) {
      val webpage = dummy.emit("/testDir", req)
      val content = Await.result(webpage.loaded.future, 1 second)
      val expected = (for (kv <- req.iterator) yield (kv + "\n").toString).mkString
      assert(content == expected, s"'$content' vs. '$expected'")
    }

    test(immutable.Map("StrangeRequest" -> List("Does it work?")))
    test(immutable.Map("StrangeRequest" -> List("It works!")))
    test(immutable.Map("WorksForThree" -> List("Always works. Trust me.")))

    dummySubscription.unsubscribe()
  }

  test("Server should be stoppable if receives infinite  response") {
    val dummy = new DummyServer(8191)
    val dummySubscription = dummy.start("/testDir") {
      request => Iterator.continually("a")
    }

    // wait until server is really installed
    Thread.sleep(500)

    val webpage = dummy.emit("/testDir", Map("Any" -> List("thing")))
    try {
      // let's wait some time
      Await.result(webpage.loaded.future, 1 second)
      fail("infinite response ended")
    } catch {
      case e: TimeoutException =>
    }

    // stop everything
    dummySubscription.unsubscribe()
    Thread.sleep(500)
    webpage.loaded.future.now // should not get NoSuchElementException
  }

  test("A Future should be completed after 1s delay") {
    val w = new Waiter
    val start = System.currentTimeMillis()

    Future.delay(1 second) onComplete { case _ =>
      val duration = System.currentTimeMillis() - start
      duration should (be >= 1000L and be < 1100L)

      w.dismiss()
    }

    w.await(timeout(2 seconds))
  }

  test("Two sequential delays of 1s should delay by 2s") {
    val w = new Waiter
    val start = System.currentTimeMillis()

    val combined = for {
      f1 <- Future.delay(1 second)
      f2 <- Future.delay(1 second)
    } yield ()

    combined onComplete { case _ =>
      val duration = System.currentTimeMillis() - start
      duration should (be >= 2000L and be < 2100L)

      w.dismiss()
    }

    w.await(timeout(3 seconds))
  }
}




