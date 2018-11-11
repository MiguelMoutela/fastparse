package test.fastparse
import fastparse._
import NoWhitespace._
import fastparse.internal.Util
import utest._

object FailureTests extends TestSuite{
  def tests = Tests{
    def checkSimple(parser: P[_] => P[_]) = {
      val f @ Parsed.Failure(failureString, index, extra) = parse("d", parser(_))
      val trace = f.trace()

      assert(
        trace.terminalAggregateString == """("a" | "b" | "c")""",
        trace.groupAggregateString == """(parseB | "c")"""
      )
    }

    'simple - {
      'either - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( (parseB | "") ~ "c" )
        parseA(_)
      }
      'option - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( parseB.? ~ "c" )
        parseA(_)
      }
      'rep - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( parseB.rep ~ "c" )
        parseA(_)
      }
      'repApply - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( parseB.rep() ~ "c" )
        parseA(_)
      }
      'repX - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( parseB.repX ~ "c" )
        parseA(_)
      }
      'repXApply - checkSimple{
        def parseB[_: P] = P( "a" | "b" )
        def parseA[_: P] = P( parseB.repX() ~ "c" )
        parseA(_)
      }
    }

    'deep - {
      'option - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.rep(1) )
        def parseA[_: P] = P( parseB.? ~ "c" )
        parseA(_)
      }
      'either - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.rep(1) )
        def parseA[_: P] = P( (parseB | "") ~ "c" )
        parseA(_)
      }
      'rep - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.rep(1) )
        def parseA[_: P] = P( parseB.rep ~ "c" )
        parseA(_)
      }
      'repApply - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.rep(1) )
        def parseA[_: P] = P( parseB.rep() ~ "c" )
        parseA(_)
      }
      'repX - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.repX(1) )
        def parseA[_: P] = P( parseB.repX ~ "c" )
        parseA(_)
      }
      'repXApply - checkSimple{
        def parseC[_: P] = P( "a" | "b" )
        def parseB[_: P] = P( parseC.repX(1) )
        def parseA[_: P] = P( parseB.repX() ~ "c" )
        parseA(_)
      }
    }

    'sep - {
      def parseB[_: P] = P( "a" | "b" )
      def parseA[_: P] = P( parseB.rep(sep = ",") ~ "c" )
      val f1 @ Parsed.Failure(_, _, _) = parse("ad", parseA(_))
      val trace = f1.trace()

      assert(trace.groupAggregateString == """("," | "c")""")

      val f2 @ Parsed.Failure(_, _, _) = parse("a,d", parseA(_))
      val trace2 = f2.trace()

      // Make sure if the parse fails after the separator and has to backtrack,
      // we list both the separator and the post-separator parse that failed
      // since showing the separator alone (which passed) is misleading
      assert(trace2.groupAggregateString == """("," ~ parseB | "c")""")
      f2.index
    }

    'sepCut - {
      def parseB[_: P] = P( "a" | "b" | "c" )
      def parseA[_: P] = P( parseB.rep(sep = ","./) ~ "d" )
      val f1 @ Parsed.Failure(_, _, _) = parse("ax", parseA(_))
      val trace = f1.trace()

      trace.groupAggregateString ==> """("," | "d")"""
      f1.index ==> 1

      val f2 @ Parsed.Failure(_, _, _) = parse("a,x", parseA(_))
      val trace2 = f2.trace()

      trace2.groupAggregateString ==> """("a" | "b" | "c")"""
      f2.index ==> 2
    }

    'aggregateInNamedParser - {
      def parseB[_: P] = P( "a" ~ "b".? )
      def parseA[_: P] = P( parseB ~ "c" )
      val f1 @ Parsed.Failure(_, _, _) = parse("ad", parseA(_))
      val trace = f1.trace()

      assert(trace.groupAggregateString == """("b" | "c")""")
    }

    'manualSep - {
      def parseA[_: P] = P( "a" ~ (("." | ","./) ~ "c").rep ~ "x" )
      val f1 @ Parsed.Failure(_, _, _) = parse("a,d", parseA(_))

      val trace = f1.trace()

      trace.groupAggregateString ==> """"c""""
    }

    'sequentialEithers - {
      def parseD[_: P] = P( (("m" | "n") | "o").rep )
      def parseC[_: P] = P( (("g" | "h") | "i").? )
      def parseB[_: P] = P( ("a" | "b") | "c" | "" )
      def parseA[_: P] = P(
         parseB ~ ("d" | ("e" | "f") | "") ~
         parseC ~ ("j" | ("k" | "l")).? ~
         parseD ~ ("p" | ("q" | "r")).rep ~
         "x"
      )
      val f1 @ Parsed.Failure(_, _, _) = parse("z", parseA(_))

      val trace = f1.trace()

      trace.groupAggregateString ==>
      """("a" | "b" | "c" | "d" | "e" | "f" | "g" | "h" | "i" | "j" | "k" | "l" | "m" | "n" | "o" | "p" | "q" | "r" | "x")"""
    }

    'passingNamedParsersAggregateIsShallow - {
      def parseB[_: P] = P( "a" ~ "b".? )
      def parseA[_: P] = P( (parseB ~ Fail).? ~ "c" )
      val f1 @ Parsed.Failure(_, _, _) = parse("ad", parseA(_))
      val trace = f1.trace()
      assert(trace.groupAggregateString == """(parseB ~ fail | "c")""")
    }

    'passingNamedParsersEitherAggregateIsShallow - {
      def parseD[_: P] = P( "d" )
      def parseC[_: P] = P( "c" )
      def parseX[_: P] = P( "x" )
      def parseY[_: P] = P( "y" )
      def parseB[_: P] = P( parseC | parseD )
      def parseZ[_: P] = P( parseX | parseY )
      def parseA[_: P] = P( parseB | parseZ )
      val f1 @ Parsed.Failure(_, _, _) = parse("_", parseA(_))
      val trace = f1.trace()
      assert(trace.groupAggregateString == """(parseB | parseZ)""")
    }

    'offset - {
      'either1 - {
        def parseA[_: P] = P("a" ~ "b" | "a" ~/ "c")
        val f1@Parsed.Failure(_, _, _) = parse("ax", parseA(_))
        val trace = f1.trace()
        assert(trace.groupAggregateString == """("b" | "c")""")
      }
      'either2 - {
        def parseA[_: P] = P(("a" ~ "b" | "a" ~ "c" | "") ~ "a" ~ "d")
        val f1@Parsed.Failure(_, _, _) = parse("ax", parseA(_))
        val trace = f1.trace()
        assert(trace.groupAggregateString == """("b" | "c" | "d")""")
      }
      'opt - {
        def parseA[_: P] = P(("a" ~ "b").? ~ "a" ~ "d")
        val f1@Parsed.Failure(_, _, _) = parse("ax", parseA(_))
        val trace = f1.trace()
        assert(trace.groupAggregateString == """("b" | "d")""")
      }
      'rep - {
        def parseA[_: P] = P(("a" ~ "b").rep ~ "a" ~ "d")
        val f1@Parsed.Failure(_, _, _) = parse("ax", parseA(_))
        val trace = f1.trace()
        assert(trace.groupAggregateString == """("b" | "d")""")
      }
    }
  }
}