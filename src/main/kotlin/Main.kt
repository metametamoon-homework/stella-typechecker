import ast.toAst
import com.strumenta.antlrkotlin.parsers.generated.StellaLexer
import com.strumenta.antlrkotlin.parsers.generated.StellaParser
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import type.TypeCheckVisitor
import type.TypeInferenceCtx
import type.TypeInferenceVisitor
import type.emptyEnv

fun main() {
  val sample =
    """
    language core;

    fn increment_twice(n : Nat) -> Nat {
      return succ(succ(n))
    }

    fn main(n : Nat) -> Nat {
      return increment_twice(n)
    }
    """
      .trimIndent()
  val lexer = StellaLexer(CharStreams.fromString(sample))
  val program = StellaParser(CommonTokenStream(lexer)).program().toAst()
  val typeChecks =
    program.accept(TypeInferenceVisitor(), TypeInferenceCtx(emptyEnv, TypeCheckVisitor()))
  println(typeChecks)
}
