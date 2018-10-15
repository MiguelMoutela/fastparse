import scala.annotation.tailrec
import scala.reflect.macros.blackbox.Context
import language.experimental.macros
package object fastparse {

  type P[+T] = ParsingRun[T]
  type P0 = ParsingRun[Unit]
  val P = ParsingRun
  /**
    * Delimits a named parser. This name will appear in the parser failure
    * messages and stack traces, and by default is taken from the name of the
    * enclosing method.
    */
  def P[T](t: ParsingRun[T])(implicit name: sourcecode.Name, ctx: ParsingRun[_]): ParsingRun[T] = macro MacroImpls.pMacro[T]


  implicit def LiteralStr(s: String)(implicit ctx: ParsingRun[Any]): ParsingRun[Unit] = macro MacroImpls.literalStrMacro


  def startsWithIgnoreCase(src: ParserInput, prefix: IndexedSeq[Char], offset: Int) = {
    @tailrec def rec(i: Int): Boolean = {
      if (i >= prefix.length) true
      else if(!src.isReachable(i + offset)) false
      else {
        val c1: Char = src(i + offset)
        val c2: Char = prefix(i)
        if (c1 != c2 && c1.toLower != c2.toLower) false
        else rec(i + 1)
      }
    }
    rec(0)
  }
  def IgnoreCase(s: String)(implicit ctx: ParsingRun[Any]): ParsingRun[Unit] = {
    val res =
      if (startsWithIgnoreCase(ctx.input, s, ctx.index)) ctx.freshSuccess((), ctx.index + s.length)
      else ctx.freshFailure().asInstanceOf[ParsingRun[Unit]]
    if (ctx.tracingEnabled) ctx.aggregateMsg(() => Util.literalize(s))
    res
  }


  implicit def EagerOpsStr(parse0: String)(implicit ctx: ParsingRun[Any]): fastparse.EagerOps[Unit] = macro MacroImpls.eagerOpsStrMacro

  def parsedSequence[T: c.WeakTypeTag, V: c.WeakTypeTag, R: c.WeakTypeTag]
  (c: Context)
  (other: c.Expr[ParsingRun[V]])
  (s: c.Expr[Implicits.Sequencer[T, V, R]],
   whitespace: c.Expr[ParsingRun[Any] => ParsingRun[Unit]],
   ctx: c.Expr[ParsingRun[_]]): c.Expr[ParsingRun[R]] = {
    import c.universe._
    MacroImpls.parsedSequence0[T, V, R](c)(other, false)(s, Some(whitespace), ctx)
  }

  def parsedSequenceCut[T: c.WeakTypeTag, V: c.WeakTypeTag, R: c.WeakTypeTag]
  (c: Context)
  (other: c.Expr[ParsingRun[V]])
  (s: c.Expr[Implicits.Sequencer[T, V, R]],
   whitespace: c.Expr[ParsingRun[Any] => ParsingRun[Unit]],
   ctx: c.Expr[ParsingRun[_]]): c.Expr[ParsingRun[R]] = {
    import c.universe._
    MacroImpls.parsedSequence0[T, V, R](c)(other, true)(s, Some(whitespace), ctx)
  }
  def parsedSequence1[T: c.WeakTypeTag, V: c.WeakTypeTag, R: c.WeakTypeTag]
  (c: Context)
  (other: c.Expr[ParsingRun[V]])
  (s: c.Expr[Implicits.Sequencer[T, V, R]],
   ctx: c.Expr[ParsingRun[_]]): c.Expr[ParsingRun[R]] = {
    import c.universe._
    MacroImpls.parsedSequence0[T, V, R](c)(other, false)(s, None, ctx)
  }
  def parsedSequenceCut1[T: c.WeakTypeTag, V: c.WeakTypeTag, R: c.WeakTypeTag]
  (c: Context)
  (other: c.Expr[ParsingRun[V]])
  (s: c.Expr[Implicits.Sequencer[T, V, R]],
   ctx: c.Expr[ParsingRun[_]]): c.Expr[ParsingRun[R]] = {
    import c.universe._
    MacroImpls.parsedSequence0[T, V, R](c)(other, true)(s, None, ctx)
  }


  implicit class EagerOps[T](val parse0: ParsingRun[T]) extends AnyVal{

    def ~/[V, R](other: ParsingRun[V])
                (implicit s: Implicits.Sequencer[T, V, R],
                 whitespace: ParsingRun[Any] => ParsingRun[Unit],
                 ctx: ParsingRun[_]): ParsingRun[R] = macro parsedSequenceCut[T, V, R]

    def /(implicit ctx: ParsingRun[_]): ParsingRun[T] = macro MacroImpls.cutMacro[T]

    def ~[V, R](other:  ParsingRun[V])
               (implicit s: Implicits.Sequencer[T, V, R],
                whitespace: ParsingRun[Any] => ParsingRun[Unit],
                ctx: ParsingRun[_]): ParsingRun[R] = macro parsedSequence[T, V, R]


    def ~~/[V, R](other: ParsingRun[V])
                 (implicit s: Implicits.Sequencer[T, V, R],
                  ctx: ParsingRun[_]): ParsingRun[R] = macro parsedSequenceCut1[T, V, R]


    def ~~[V, R](other: ParsingRun[V])
                (implicit s: Implicits.Sequencer[T, V, R],
                 ctx: ParsingRun[_]): ParsingRun[R] = macro parsedSequence1[T, V, R]

    def map[V](f: T => V): ParsingRun[V] = macro MacroImpls.mapMacro[T, V]

    def filter(f: T => Boolean)
              (implicit ctx: ParsingRun[Any]): ParsingRun[T] = macro MacroImpls.filterMacro[T]

    def flatMap[V](f: T => ParsingRun[V]): ParsingRun[V] = macro MacroImpls.flatMapMacro[T, V]

    def |[V >: T](other: ParsingRun[V])
                 (implicit ctx: ParsingRun[Any]): ParsingRun[V] = macro MacroImpls.eitherMacro[T, V]

    def !(implicit ctx: ParsingRun[Any]): ParsingRun[String] = macro MacroImpls.captureMacro

    def ?[V](implicit optioner: Implicits.Optioner[T, V],
             ctx: ParsingRun[Any]): ParsingRun[V] = macro MacroImpls.optionMacro[T, V]
  }



  implicit def ByNameOpsStr(parse0: String)(implicit ctx: ParsingRun[Any]): fastparse.ByNameOps[Unit] =
  macro MacroImpls.byNameOpsStrMacro

  implicit def ByNameOps[T](parse0: => ParsingRun[T]) = new ByNameOps(() => parse0)
  class ByNameOps[T](val parse0: () => ParsingRun[T]) extends AnyVal{

    def repX[V](implicit repeater: Implicits.Repeater[T, V], ctx: ParsingRun[Any]): ParsingRun[V] =
    macro RepImpls.repXMacro1[T, V]
    def repX[V](min: Int = 0,
                sep: => ParsingRun[_] = null,
                max: Int = Int.MaxValue,
                exactly: Int = -1)
               (implicit repeater: Implicits.Repeater[T, V],
                ctx: ParsingRun[Any]): ParsingRun[V] =
      new RepImpls[T](parse0).repX[V](min, sep, max, exactly)
    def repX[V](min: Int,
                sep: => ParsingRun[_])
               (implicit repeater: Implicits.Repeater[T, V],
                ctx: ParsingRun[Any]): ParsingRun[V] =
      new RepImpls[T](parse0).repX[V](min, sep)
    def repX[V](min: Int)
               (implicit repeater: Implicits.Repeater[T, V],
                ctx: ParsingRun[Any]): ParsingRun[V] =
    macro RepImpls.repXMacro2[T, V]

    def rep[V](implicit repeater: Implicits.Repeater[T, V],
               whitespace: ParsingRun[_] => ParsingRun[Unit],
               ctx: ParsingRun[Any]): ParsingRun[V] =
    macro RepImpls.repXMacro1ws[T, V]
    def rep[V](min: Int = 0,
               sep: => ParsingRun[_] = null,
               max: Int = Int.MaxValue,
               exactly: Int = -1)
              (implicit repeater: Implicits.Repeater[T, V],
               whitespace: ParsingRun[_] => ParsingRun[Unit],
               ctx: ParsingRun[Any]): ParsingRun[V] =
      new RepImpls[T](parse0).rep[V](min, sep, max, exactly)
    def rep[V](min: Int,
               sep: => ParsingRun[_])
              (implicit repeater: Implicits.Repeater[T, V],
               whitespace: ParsingRun[_] => ParsingRun[Unit],
               ctx: ParsingRun[Any]): ParsingRun[V] =
      new RepImpls[T](parse0).rep[V](min, sep)

    def rep[V](min: Int)
              (implicit repeater: Implicits.Repeater[T, V],
               whitespace: ParsingRun[_] => ParsingRun[Unit],
               ctx: ParsingRun[Any]): ParsingRun[V] =
    macro RepImpls.repXMacro2ws[T, V]

    def opaque(msg: String)(implicit ctx: ParsingRun[Any]) = {
      val oldIndex = ctx.index
      val res = parse0()
      if (ctx.tracingEnabled){
        if (ctx.traceIndex == oldIndex && !res.isSuccess) {
          ctx.failureStack = Nil
        }
      } else if (!res.isSuccess){
        ctx.failureStack = Nil
        if (ctx.tracingEnabled) ctx.aggregateMsg(() => msg)
        ctx.index = oldIndex
      }
      res
    }


    def unary_!(implicit ctx: ParsingRun[Any]) : ParsingRun[Unit] = {
      val startPos = ctx.index
      val startCut = ctx.cut
      val oldNoCut = ctx.noDropBuffer
      val oldFailureAggregate = ctx.failureAggregate
      ctx.noDropBuffer = true
      parse0()
      ctx.noDropBuffer = oldNoCut
      ctx.failureAggregate = oldFailureAggregate
      val msg = ctx.shortFailureMsg

      val res =
        if (ctx.isSuccess) ctx.freshFailure(startPos)
        else ctx.freshSuccess((), startPos)

      if (ctx.tracingEnabled) ctx.aggregateMsg(() => "!" + msg())
      res.cut = startCut
      res
    }

  }

  implicit def LogOpsStr(parse0: String)(implicit ctx: ParsingRun[Any]): fastparse.LogByNameOps[Unit] =
  macro MacroImpls.logOpsStrMacro
  /**
    * Separated out from [[ByNameOps]] because `.log` isn't easy to make an
    * [[AnyVal]] extension method, but it doesn't matter since `.log` calls
    * are only for use in development while the other [[ByNameOps]] operators
    * are more performance-sensitive
    */
  implicit class  LogByNameOps[T](parse0: => ParsingRun[T])(implicit ctx: ParsingRun[_]) {
    def log(implicit name: sourcecode.Name, logger: Logger = Logger.stdout): ParsingRun[T] = {

      val msg = name.value
      val output = logger.f
      val indent = "  " * ctx.logDepth

      output(s"$indent+$msg:${ctx.input.prettyIndex(ctx.index)}${if (ctx.cut) ", cut" else ""}")
      val depth = ctx.logDepth
      ctx.logDepth += 1
      val startIndex = ctx.index
      parse0
      ctx.logDepth = depth
      val strRes = if (ctx.isSuccess){
        val prettyIndex = ctx.input.prettyIndex(ctx.index)
        s"Success($prettyIndex${if (ctx.cut) ", cut" else ""})"
      } else{
        val trace = Parsed.Failure.formatStack(
          ctx.input,
          (Option(ctx.shortFailureMsg).fold("")(_()) -> ctx.index) :: ctx.failureStack.reverse
        )
        val trailing = ctx.input match{
          case c: IndexedParserInput => Parsed.Failure.formatTrailing(ctx.input, startIndex)
          case _ => ""
        }
        s"Failure($trace ...$trailing${if (ctx.cut) ", cut" else ""})"
      }
      output(s"$indent-$msg:${ctx.input.prettyIndex(startIndex)}:$strRes")
      //        output(s"$indent-$msg:${repr.prettyIndex(cfg.input, index)}:$strRes")
      ctx.asInstanceOf[ParsingRun[T]]
    }

  }

  def &(parse: => ParsingRun[_])(implicit ctx: ParsingRun[_]): ParsingRun[Unit] = {

    val startPos = ctx.index
    val startCut = ctx.cut
    val oldNoCut = ctx.noDropBuffer
    ctx.noDropBuffer = true
    parse
    ctx.noDropBuffer = oldNoCut
    val msg = ctx.shortFailureMsg

    val res =
      if (ctx.isSuccess) ctx.freshSuccess((), startPos)
      else ctx.asInstanceOf[ParsingRun[Unit]]
    if (ctx.tracingEnabled) ctx.setMsg(() => s"&(${msg()})")
    res.cut = startCut
    res

  }

  def End(implicit ctx: ParsingRun[_]): ParsingRun[Unit] = {
    val res =
      if (!ctx.input.isReachable(ctx.index)) ctx.freshSuccess(())
      else ctx.freshFailure().asInstanceOf[ParsingRun[Unit]]
    if (ctx.tracingEnabled) ctx.aggregateMsg(() => "end-of-input")
    res

  }

  def Start(implicit ctx: ParsingRun[_]): ParsingRun[Unit] = {
    val res =
      if (ctx.index == 0) ctx.freshSuccess(())
      else ctx.freshFailure().asInstanceOf[ParsingRun[Unit]]
    if (ctx.tracingEnabled) ctx.aggregateMsg(() => "start-of-input")
    res
  }

  def NoTrace[T](p: => ParsingRun[T], label: String = null)(implicit ctx: ParsingRun[_]): ParsingRun[T] = {
    val preMsg = ctx.failureAggregate
    val res = p
    if (ctx.tracingEnabled && res.index >= res.traceIndex) {
      ctx.failureAggregate = preMsg
      if (label != null) ctx.failureAggregate ::= (() => label)
    }
    res
  }

  def Pass(implicit ctx: ParsingRun[_]): ParsingRun[Unit] = {
    val res = ctx.freshSuccess(())
    if (ctx.tracingEnabled) ctx.setMsg(() => "Pass")
    res
  }

  def Pass[T](v: T)(implicit ctx: ParsingRun[_]): ParsingRun[T] = {
    val res = ctx.freshSuccess(v)
    if (ctx.tracingEnabled) ctx.setMsg(() => "Pass")
    res
  }

  def Fail(implicit ctx: ParsingRun[_]): ParsingRun[Nothing] = {
    val res = ctx.freshFailure()
    if (ctx.tracingEnabled) ctx.setMsg(() => "fail")
    res
  }

  def Index(implicit ctx: ParsingRun[_]): ParsingRun[Int] = {
    val res = ctx.freshSuccess(ctx.index)
    if (ctx.tracingEnabled) ctx.setMsg(() => "Index")
    res
  }

  def AnyChar(implicit ctx: ParsingRun[_]): ParsingRun[Unit] = {
    val res =
      if (!ctx.input.isReachable(ctx.index)) ctx.freshFailure().asInstanceOf[ParsingRun[Unit]]
      else ctx.freshSuccess((), ctx.index + 1)
    if (ctx.tracingEnabled) ctx.aggregateMsg(() => "any-character")
    res
  }
  def SingleChar(implicit ctx: ParsingRun[_]): ParsingRun[Char] = {
    val res =
      if (!ctx.input.isReachable(ctx.index)) ctx.freshFailure().asInstanceOf[ParsingRun[Char]]
      else ctx.freshSuccess(ctx.input(ctx.index), ctx.index + 1)
    if (ctx.tracingEnabled) ctx.aggregateMsg(() => "any-character")
    res
  }
  def CharPred(p: Char => Boolean)(implicit ctx: ParsingRun[_]): ParsingRun[Unit] = {
    val res =
      if (!(ctx.input.isReachable(ctx.index) && p(ctx.input(ctx.index)))) ctx.freshFailure().asInstanceOf[ParsingRun[Unit]]
      else ctx.freshSuccess((), ctx.index + 1)
    if (ctx.tracingEnabled) ctx.aggregateMsg(() => "character-predicate")
    res
  }
  def CharIn(s: String*)(implicit ctx: ParsingRun[_]): ParsingRun[Unit] = macro MacroImpls.charInMacro

  def CharsWhileIn(s: String)
                  (implicit ctx: ParsingRun[_]): ParsingRun[Unit] = macro MacroImpls.charsWhileInMacro1
  def CharsWhileIn(s: String, min: Int)
                  (implicit ctx: ParsingRun[_]): ParsingRun[Unit] = macro MacroImpls.charsWhileInMacro

  def CharsWhile(p: Char => Boolean, min: Int = 1)(implicit ctx: ParsingRun[_]): ParsingRun[Unit] = {
    var index = ctx.index
    val input = ctx.input


    val start = index
    while(input.isReachable(index) && p(input(index))) index += 1
    val res =
      if (index - start >= min) ctx.freshSuccess((), index = index)
      else ctx.freshFailure()
    if (ctx.tracingEnabled) ctx.aggregateMsg(() => s"chars-while($min)")
    res
  }

  def NoCut[T](parse: => ParsingRun[T])(implicit ctx: ParsingRun[_]): ParsingRun[T] = {
    val cut = ctx.cut
    val oldNoCut = ctx.noDropBuffer
    ctx.noDropBuffer = true
    val res = parse
    ctx.noDropBuffer = oldNoCut

    res.cut = cut
    res
  }


  def StringIn(s: String*)(implicit ctx: ParsingRun[_]): ParsingRun[Unit] = macro MacroImpls.stringInMacro
  def StringInIgnoreCase(s: String*)(implicit ctx: ParsingRun[_]): ParsingRun[Unit] = macro MacroImpls.stringInIgnoreCaseMacro

  def parseInput[T](input: ParserInput,
                    parser: ParsingRun[_] => ParsingRun[T],
                    startIndex: Int = 0,
                    traceIndex: Int = -1,
                    instrument: ParsingRun.Instrument = null): Parsed[T] = parser(new ParsingRun(
    input = input,
    failureAggregate = List.empty,
    shortFailureMsg = () => "",
    failureStack = List.empty,
    isSuccess = true,
    logDepth = 0,
    startIndex,
    startIndex,
    true,
    (),
    traceIndex,
    parser,
    false,
    instrument
  )).result
  def parseIterator[T](input: Iterator[String],
                       parser: ParsingRun[_] => ParsingRun[T],
                       startIndex: Int = 0,
                       traceIndex: Int = -1,
                       instrument: ParsingRun.Instrument = null): Parsed[T] = parseInput(
    input = IteratorParserInput(input),
    parser = parser,
    startIndex = startIndex,
    traceIndex = traceIndex,
    instrument = instrument
  )
  def parse[T](input: String,
               parser: ParsingRun[_] => ParsingRun[T],
               startIndex: Int = 0,
               traceIndex: Int = -1,
               instrument: ParsingRun.Instrument = null): Parsed[T] = parseInput(
    input = IndexedParserInput(input),
    parser = parser,
    startIndex = startIndex,
    traceIndex = traceIndex,
    instrument = instrument
  )
}
