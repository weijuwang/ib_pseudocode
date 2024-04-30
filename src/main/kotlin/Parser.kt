import LexicalAnalysis.*
import LexicalAnalysis.DefinedToken.*
import LexicalAnalysis.TokenType.*
import LexicalAnalysis.OperatorType.*

/**
 *
 */
class Parser (val lexicalAnalysis: LexicalAnalysis) {
    interface Ast

    interface Statement: Ast
    interface Expression: Ast

    data class Value (val value: LexicalAnalysis.Value) : Expression
    data class UnaryOperation (val operator: DefinedToken, val operand: Expression) : Expression {
        init {
            assert(UNARY in operator.operatorTypes)
        }
    }
    data class BinaryOperation (val operator: DefinedToken, val left: Expression, val right: Expression) : Expression {
        init {
            assert(operator.types == OPERATOR)
        }
    }
    data class FunctionCall (val functionName: String, val params: List<Expression>) : Expression, Statement

    data class Output (val expressions: List<Expression>) : Statement
    data class Input (val variableName: String) : Statement
    data class If (val condition: Expression) : Statement
    data class ElseIf (val condition: Expression) : Statement
    class Else : Statement
    data class LoopWhile (val condition: Expression) : Statement
    data class LoopUntil (val condition: Expression) : Statement
    data class LoopRange (val start: Expression, val end: Expression) : Statement
    data class Assignment (val variableName: String, val value: Expression) : Statement
    class EndIf : Statement
    class EndLoop : Statement

    var index = 0
        private set



    companion object {
        /**
         * A list of commands that always begin with a certain keyword.
         */
        val commands: Map<DefinedToken, (List<Token>) -> Unit> = mapOf(
            OUTPUT to {},
            INPUT to {},
            IF to {},
            ELSE to {},
            LOOP to {},
            END to {}
        )
    }
}