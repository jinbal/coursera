package calculator

import java.lang

import calculator.TweetLength.MaxTweetLength
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FunSuite, _}

@RunWith(classOf[JUnitRunner])
class CalculatorSuite extends FunSuite with ShouldMatchers {

  /** ****************
    * * TWEET LENGTH **
    * *****************/

  def tweetLength(text: String): Int = text.codePointCount(0, text.length)

  test("tweetRemainingCharsCount with a constant signal") {
    val result = TweetLength.tweetRemainingCharsCount(Var("hello world"))
    assert(result() == MaxTweetLength - tweetLength("hello world"))

    val tooLong = "foo" * 200
    val result2 = TweetLength.tweetRemainingCharsCount(Var(tooLong))
    assert(result2() == MaxTweetLength - tweetLength(tooLong))
  }

  test("tweetRemainingCharsCount with a supplementary char") {
    val result = TweetLength.tweetRemainingCharsCount(Var("foo blabla \uD83D\uDCA9 bar"))
    assert(result() == MaxTweetLength - tweetLength("foo blabla \uD83D\uDCA9 bar"))
  }


  test("colorForRemainingCharsCount with a constant signal") {
    val resultGreen1 = TweetLength.colorForRemainingCharsCount(Var(52))
    assert(resultGreen1() == "green")
    val resultGreen2 = TweetLength.colorForRemainingCharsCount(Var(15))
    assert(resultGreen2() == "green")

    val resultOrange1 = TweetLength.colorForRemainingCharsCount(Var(12))
    assert(resultOrange1() == "orange")
    val resultOrange2 = TweetLength.colorForRemainingCharsCount(Var(0))
    assert(resultOrange2() == "orange")

    val resultRed1 = TweetLength.colorForRemainingCharsCount(Var(-1))
    assert(resultRed1() == "red")
    val resultRed2 = TweetLength.colorForRemainingCharsCount(Var(-5))
    assert(resultRed2() == "red")
  }

  test("should compute values") {
    val references: Map[String, Signal[Expr]] = Map(
      "a" -> Signal(Plus(Literal(1), Literal(2))),
      "b" -> Signal(Times(Ref("a"), Literal(2))),
      "c" -> Signal(Divide(Ref("b"), Literal(2))),
      "d" -> Signal(Literal(4)),
      "e" -> Signal(Minus(Ref("d"), Ref("a"))),
      "f" -> Signal(Plus(Ref("a"), Ref("f")))
    )
    val values: Map[String, Signal[Double]] = Calculator.computeValues(references)
    values.get("a").get() should be(3)
    values.get("b").get() should be(6)
    values.get("c").get() should be(3)
    values.get("d").get() should be(4)
    values.get("e").get() should be(1)
    lang.Double.isNaN(values.get("f").get()) should be(true)
  }

  test("should detect simple cyclic references") {
    val plus: Plus = Plus(Ref("b"), Literal(1))
    val times: Times = Times(Literal(2), Ref("a"))
    val references: Map[String, Signal[Expr]] = Map(
      "a" -> Signal(plus),
      "b" -> Signal(times))
    Calculator.hasCyclicReference("a", plus, references) should be(true)
    Calculator.hasCyclicReference("b", times, references) should be(true)
  }

  test("should detect chained cyclic references") {
    val cellA: Plus = Plus(Ref("b"), Literal(1))
    val cellB: Times = Times(Literal(2), Ref("c"))
    val cellC: Ref = Ref("a")
    val references: Map[String, Signal[Expr]] = Map(
      "a" -> Signal(cellA),
      "b" -> Signal(cellB),
      "c" -> Signal(cellC))
    Calculator.hasCyclicReference("a", cellA, references) should be(true)
    Calculator.hasCyclicReference("b", cellB, references) should be(true)
    Calculator.hasCyclicReference("c", cellC, references) should be(true)
  }

  test("should eval plus of refs") {
    val references: Map[String, Signal[Expr]] = Map(
      "ref" -> Signal(Literal(3)),
      "ref2" -> Signal(Literal(5)))
    val refExpr = Plus(Ref("ref"), Ref("ref2"))
    val evalResult = Calculator.eval(refExpr, references)
    evalResult should be(8)
  }

  test("should eval a simple direct Ref") {
    val references: Map[String, Signal[Expr]] = Map("ref" -> Signal(Literal(3)))
    val refExpr = Ref("ref")
    val evalResult = Calculator.eval(refExpr, references)
    evalResult should be(3)
  }

  test("should eval a Literal") {
    val expr = Literal(2)
    val evalResult = Calculator.eval(expr, Map())
    evalResult should be(expr.v)
  }

  test("should eval a Plus Literals") {
    val expr = Plus(Literal(2), Literal(3))
    val evalResult = Calculator.eval(expr, Map())
    evalResult should be(5)
  }

  test("should eval a Minus") {
    val expr = Minus(Literal(2), Literal(3))
    val evalResult = Calculator.eval(expr, Map())
    evalResult should be(-1)
  }

  test("should eval a Times") {
    val expr = Times(Literal(2), Literal(3))
    val evalResult = Calculator.eval(expr, Map())
    evalResult should be(6)
  }

  test("should eval a Divide") {
    val expr = Divide(Literal(6), Literal(3))
    val evalResult = Calculator.eval(expr, Map())
    evalResult should be(2)
  }


}
