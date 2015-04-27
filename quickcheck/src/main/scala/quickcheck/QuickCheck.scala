package quickcheck

import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.scalacheck._

import scala.collection.immutable.::

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("insert into empty adds to the heap") = forAll { i: Int =>
    val h = insert(i, empty)
    findMin(h) == i
  }

  property("findMin in 2 element heap returns min ") = forAll { (a: Int, b: Int) =>
    val h = insert(b, insert(a, empty))
    findMin(h) == {
      if (a <= b) a else b
    }
  }

  property("Calling delete on a single element heap should return empty heap") = forAll { a: Int =>
    val singleElementHeap = insert(a, empty)
    val emptyHeap = deleteMin(singleElementHeap)
    isEmpty(emptyHeap)
  }

  property("findMin of a meld should return min of one or the other") = forAll { (heap: H, secondHeap: H) =>
    val minSecond = findMin(secondHeap)
    val minFirst = findMin(heap)
    val minMeld = findMin(meld(heap, secondHeap))
    minMeld == {
      if (minFirst <= minSecond) minFirst else minSecond
    }
  }

  property("Heaps should have natural ordering") = forAll { heap: H =>
    val ints = convertToInts(Nil, heap)
    isOrdered(ints)
  }

  property("melded lists should have natural ordering") = forAll { (heap: H, secondHeap: H) =>
    isOrdered(convertToInts(Nil, meld(heap, secondHeap)))
  }

  property("melded lists should have natural ordering after delete") = forAll { (heap: H, secondHeap: H) =>
    isOrdered(convertToInts(Nil, deleteMin(meld(heap, secondHeap))))
  }

  property("melded lists should have natural ordering after inserts") = forAll { (heap: H, secondHeap: H) =>
    isOrdered(convertToInts(Nil, deleteMin(meld(heap, secondHeap))))
  }

  property("Calling empty on empty heap should return true") = forAll { heap: H =>
    isEmpty(empty)
  }

  property("isEmpty should never return true for non empty heap") = forAll { heap: H =>
    isEmpty(heap) == false
  }

  property("after adding deleting new min old min should be min") = forAll { heap: H =>
    val min = findMin(heap)
    val newMin = Gen.choose(Int.MinValue, min).sample.get
    val newMinHeap = insert(newMin, heap)
    findMin(deleteMin(newMinHeap)) == min
  }

  property("min should remain the same after insert") = forAll { (heap: H) =>
    val min = findMin(heap)
    val newVal = Gen.choose(min, Int.MaxValue).sample.get
    val newMinHeap = insert(newVal, heap)
    findMin(deleteMin(newMinHeap)) != min
  }


  def convertToInts(list: List[A] = Nil, heap: H): List[A] = isEmpty(heap) match {
    case true => list
    case _ => convertToInts(List.concat(list, List(findMin(heap))), deleteMin(heap))
  }

  def isOrdered(l: List[Int]): Boolean = l match {
    case Nil => true
    case x :: Nil => true
    case x :: xs => x <= xs.head && isOrdered(xs)
  }

  lazy val genHeap: Gen[H] = for {
    v <- Gen.choose(0, 1000)
    heap <- oneOf(const(empty), genHeap, genHeap, genHeap)
  } yield insert(v, heap)

  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)

}
