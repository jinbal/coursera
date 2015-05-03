package calculator

sealed abstract class Expr

final case class Literal(v: Double) extends Expr

final case class Ref(name: String) extends Expr

final case class Plus(a: Expr, b: Expr) extends Expr

final case class Minus(a: Expr, b: Expr) extends Expr

final case class Times(a: Expr, b: Expr) extends Expr

final case class Divide(a: Expr, b: Expr) extends Expr

object Calculator {

  def computeValues(namedExpressions: Map[String, Signal[Expr]]): Map[String, Signal[Double]] = {
    for {
      namedExpression: (String, Signal[Expr]) <- namedExpressions
    } yield {
      (namedExpression._1, Signal {
        hasCyclicReference(namedExpression._1, namedExpression._2(), namedExpressions) match {
          case true => Double.NaN
          case false => eval(namedExpression._2(), namedExpressions)
        }
      })
    }
  }

  def eval(expr: Expr, references: Map[String, Signal[Expr]]): Double = {
    expr match {
      case Literal(v) => v
      case Plus(a, b) => eval(a, references) + eval(b, references)
      case Minus(a, b) => eval(a, references) - eval(b, references)
      case Times(a, b) => eval(a, references) * eval(b, references)
      case Divide(a, b) => eval(a, references) / eval(b, references)
      case Ref(name) => eval(getReferenceExpr(name, references), references)
    }
  }

  def hasCyclicReference(cellName: String, cellExpr: Expr, references: Map[String, Signal[Expr]]): Boolean = {
    cellExpr match {
      case Literal(_) => false
      case Ref(ref) => ref == cellName || hasCyclicReference(cellName, getReferenceExpr(ref, references), references)
      case Plus(a, b) => hasCyclicReference(cellName, a, references) || hasCyclicReference(cellName, b, references)
      case Minus(a, b) => hasCyclicReference(cellName, a, references) || hasCyclicReference(cellName, b, references)
      case Times(a, b) => hasCyclicReference(cellName, a, references) || hasCyclicReference(cellName, b, references)
      case Divide(a, b) => hasCyclicReference(cellName, a, references) || hasCyclicReference(cellName, b, references)
    }
  }


  /** Get the Expr for a referenced variables.
    * If the variable is not known, returns a literal NaN.
    */
  private def getReferenceExpr(name: String, references: Map[String, Signal[Expr]]): Expr = {
    references.get(name).fold[Expr] {
      Literal(Double.NaN)
    } { exprSignal =>
      exprSignal()
    }
  }
}
