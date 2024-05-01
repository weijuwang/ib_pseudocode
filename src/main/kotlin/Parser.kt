import LexicalAnalysis.*
import LexicalAnalysis.DefinedToken.*

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
            assert(operator.isUnaryOperator)
        }
    }
    data class BinaryOperation (val operator: DefinedToken, val left: Expression, val right: Expression) : Expression {
        init {
            assert(operator.isBinaryOperator)
        }
    }
    data class FunctionCall (val functionName: String, val params: List<Expression>) : Expression, Statement
    data class Output (val expressions: List<Expression>) : Statement
    data class Input (val variableName: String) : Statement
    data class If (val branches: Map<Expression, List<Statement>>) : Statement
    data class LoopWhile (val condition: Expression) : Statement
    data class LoopUntil (val condition: Expression) : Statement
    data class LoopRange (val variableName: String, val start: Expression, val end: Expression) : Statement
    data class Assignment (val variableName: String, val value: Expression) : Statement

    /**
     * The list of tokens obtained from the lexer without their positions in the original file.
     */
    private val tokens = lexicalAnalysis.tokens.values.toList()

    /**
     * The index in the token list that the parser is at.
     */
    private var index = 0

    private fun reachedEnd() = index >= tokens.size

    /**
     * Obtains the next token and moves the parser forward. Returns `null` if [reachedEnd] is true.
     */
    private fun nextToken(): Token? =
        if (reachedEnd())
            null
        else {
            val currIndex = index
            index++
            tokens[currIndex]
        }

    /**
     * Evaluates [function]. If [function] throws a [NullPointerException], [index] is reset to where it was before
     * [function] was called and the exception is rethrown to be caught by any higher scopes.
     *
     * **This is not null-safe.** [NullPointerException]s are deliberately sent up the chain until caught. Whichever
     * scope first calls this needs to wrap it in a big try/catch for [NullPointerException].
     */
    private fun<T> resetIfNullExc(function: () -> T): T {
        val resetPoint = index
        try {
            return function()
        } catch (e: NullPointerException) {
            index = resetPoint
            throw e
        }
    }

    /**
     * Obtains the next token if it's an instance of [T] and [condition] is true. Otherwise, [index] is rewinded to
     * where it was before.
     */
    private inline fun<reified T: Token> nextTokenIf(crossinline condition: (T) -> Boolean = { true }): T =
        resetIfNullExc {
            (nextToken() as? T)!!.takeIf(condition)!!
        }

    ////////////////////////////////////////////////////////////////////////////

    private fun parseValue(): Value = resetIfNullExc {
        Value(nextTokenIf<LexicalAnalysis.Value>())
    }

    private fun parseUnary(): UnaryOperation = resetIfNullExc {
        val operator = nextTokenIf<DefinedToken> { it.isUnaryOperator }
        val operand = parseValue() // TODO expression

        UnaryOperation(operator, operand)
    }

    private fun parseBinary(): BinaryOperation = resetIfNullExc {
        val left = parseValue() // TODO expression
        val operator = nextTokenIf<DefinedToken> { it.isBinaryOperator }
        val right = parseValue() // TODO expression

        BinaryOperation(operator, left, right)
    }

    ////////////////////////////////////////////////////////////////////////////

    fun debug() {
        println(lexicalAnalysis.tokens)
        try {
            println(parseBinary())
        } catch (_: NullPointerException) {
            print("nope ")
            println(index)
        }
    }

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