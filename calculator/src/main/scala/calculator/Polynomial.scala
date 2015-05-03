package calculator

import java.lang.Math.sqrt

object Polynomial {
  def computeDelta(a: Signal[Double], b: Signal[Double],
                   c: Signal[Double]): Signal[Double] = {
    Signal {
      (b() * b()) - (4 * a() * c())
    }
  }

  def computeSolutions(a: Signal[Double], b: Signal[Double],
                       c: Signal[Double], delta: Signal[Double]): Signal[Set[Double]] = {
    Signal {
      delta() match {
        case d if d < 0 =>
          Set()
        case d if d == 0 =>
          Set(solve(a(), b(), delta()))
        case d if d > 0 =>
          Set(solve(a(), b(), delta()), solve(a(), b(), delta(), false))
      }
    }
  }

  def solve(a: Double, b: Double, delta: Double, addDelta: Boolean = true): Double = {
    val d: Double = -b
    if (addDelta) {
      (d + sqrt(delta)) / (2 * a)
    } else {
      (d - sqrt(delta)) / (2 * a)
    }
  }
}
