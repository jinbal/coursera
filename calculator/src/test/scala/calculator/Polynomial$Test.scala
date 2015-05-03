package calculator

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FunSuite, ShouldMatchers}

@RunWith(classOf[JUnitRunner])
class PolynomialTest extends FunSuite with ShouldMatchers {

  test("should compute solution with 2 values") {
    val a = Signal(5d)
    val b = Signal(6d)
    val c = Signal(1d)
    val delta = Polynomial.computeDelta(a, b, c)
    delta() shouldBe (16d)
    val solutions: Signal[Set[Double]] = Polynomial.computeSolutions(a, b, c, delta)
    solutions().head shouldBe (-0.2d)
    solutions().last shouldBe (-1d)
  }

  test("should compute solution with 1 value - delta is zero") {
    val a = Signal(4d)
    val b = Signal(4d)
    val c = Signal(1d)
    val delta = Polynomial.computeDelta(a, b, c)
    delta() shouldBe (0d)
    val solutions: Signal[Set[Double]] = Polynomial.computeSolutions(a, b, c, delta)
    solutions().head shouldBe (-0.5d)
  }

  test("should compute solution with no values - delta is negative") {
    val a = Signal(5d)
    val b = Signal(6d)
    val c = Signal(2d)
    val delta = Polynomial.computeDelta(a, b, c)
    val solutions: Signal[Set[Double]] = Polynomial.computeSolutions(a, b, c, delta)
    solutions() shouldBe (empty)
  }

}
