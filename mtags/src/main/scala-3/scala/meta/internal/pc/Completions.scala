package scala.meta.internal.pc

import dotty.tools.dotc.ast.tpd.{Ident, Apply, Tree, NamedArg, Template, Block, Literal}
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.util.SourcePosition
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.interactive.Completion
import dotty.tools.dotc.core.Symbols.{Symbol, TermSymbol}
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.interactive.Interactive
import dotty.tools.dotc.core.Names.Name

trait Completions {
  def completionPosition(
    pos: SourcePosition,
    path: List[Tree],
    driver: InteractiveDriver,
  )(using ctx: Context): List[Completion] = {
    println(path)
    path match {
      case (ident: Ident) :: (app: Apply) :: _ =>
        ArgCompletion(Some(ident), app, pos, driver).contribute
      case (app: Apply) :: _ =>
        ArgCompletion(None, app, pos, driver).contribute
      case _ =>
        Nil
    }
  }
}

case class ArgCompletion(
  ident: Option[Ident],
  apply: Apply,
  pos: SourcePosition,
  driver: InteractiveDriver,
)(using ctx: Context) {
  println(s"arg completion: ${ident}, ${apply}")
  val method = apply.fun
  method.symbol
  val methodSym = method.symbol
  println(s"methodSym ${methodSym}")

  // paramSymss contains both type params and value params
  val vparamss = methodSym.paramSymss.filter(
    params => params.forall(p => p.isTerm)
  )
  println(s"vparamss ${vparamss}")
  lazy val baseParams = vparamss.headOption.getOrElse(Nil)

  // filter out the arg that we writing.
  // def method(foo: Int, bar: Int) = ???
  // method(fo)
  // filter out Ident(fo) from args.
  val args = ident
    .map(i => apply.args.filterNot(_ == i)).getOrElse(apply.args)
    .filterNot(isUselessLiteral)
  val isNamed: Set[Name] = args
    .zip(baseParams)
    .map {
      case (NamedArg(name, _), _) => name
      case (_, param) => param.name
    }.toSet
  println(s"args: ${args}")
  println(s"isNamed: ${isNamed}")
  lazy val allParams: List[Symbol] = {
    // TODO: exclude synthetic parameters?
    baseParams.filterNot(param => isNamed(param.name))
  }

  println(s"allParams: ${allParams}")
  val prefix = ident.map(_.name.toString).getOrElse("")
  println(s"prefix: ${prefix}")
  lazy val params: List[Symbol] = allParams.filter(param => param.name.startsWith(prefix))
  println(s"params: ${params}")

  private def isUselessLiteral(arg: Tree): Boolean = {
    arg match {
      case Literal(Constant(())) => true // unitLiteral
      case Literal(Constant(null)) => true // nullLiteral
      case _ => false
    }
  }

  def contribute: List[Completion] = {
    val printer = SymbolPrinter()(using ctx)
    params.map(p => {
      Completion(
        s"${p.name.toString} = ",
        printer.typeString(p.info),
        List(p)
      )
    })
  }

}