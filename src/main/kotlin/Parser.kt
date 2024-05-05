import LexicalAnalysis.*
import LexicalAnalysis.DefinedToken.*

/**
 * Attempts to parse a list of tokens from [lexicalAnalysis] into an [Ast].
 *
 * The parsed AST is stored in [result]. All classes representing ASTs inherit from `Ast`.
 *
 * If the given tokens aren't syntactically valid, `result` is `null`.
 *
 * TODO Class members/methods
 */
class Parser (val lexicalAnalysis: LexicalAnalysis) {
    interface Ast
    interface Statement: Ast
    interface Expression: Ast

    data class Value (
        val value: LexicalAnalysis.Value
    ) : Expression

    data class ArrayLiteral (
        val values: List<Expression>
    ) : Expression

    data class UnaryOperation (
        val operator: DefinedToken,
        val operand: Expression
    ) : Expression

    data class BinaryOperation (
        val operator: DefinedToken,
        val left: Expression,
        val right: Expression
    ) : Expression

    data class ArrayAccess (
        val arrayName: VariableName,
        val index: Expression
    ) : Expression

    data class FunctionCall (
        val functionName: MethodName,
        val params: List<Expression>
    ) : Expression, Statement

    data class Output (
        val expressions: List<Expression>
    ) : Statement

    data class Input (
        val variableName: String
    ) : Statement

    data class If (
        val branches: Map<Expression, List<Statement>>
    ) : Statement

    data class LoopWhile (
        val condition: Expression
    ) : Statement

    data class LoopUntil (
        val condition: Expression
    ) : Statement

    data class LoopRange (
        val variableName: VariableName,
        val start: Expression,
        val end: Expression
    ) : Statement

    data class Assignment (
        val variableName: VariableName,
        val value: Expression
    ) : Statement

    /**
     * The list of tokens obtained from the lexer without their positions in the original file.
     */
    private val tokens = lexicalAnalysis.tokens.values.toList()

    /**
     * The index in the token list that the parser is at.
     */
    private var index = 0

    /**
     * The parsed abstract syntax tree.
     */
    val result: Ast? = null

    /**
     * Whether the parser has reached the end of the token list.
     */
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
     * Evaluates [function], resetting [index] to where it was before it was evaluated if it throws
     * [NullPointerException]. [NullPointerException] is then deliberately rethrown.
     */
    private fun<T> resetIfNull(function: () -> T): T {
        val returnPoint = index

        try {
            return function()
        } catch (e: NullPointerException) {
            index = returnPoint
            throw e
        }
    }

    /**
     * Evaluates [function], returning null if it throws [NullPointerException].
     */
    private fun<T> optional(function: () -> T): T? =
        try {
            function()
        } catch (_: NullPointerException) {
            null
        }

    /**
     * Obtains the next token if it's an instance of [T] and [condition] is true. Otherwise, [index] is rewinded to
     * where it was before and a [NullPointerException] is thrown.
     */
    private inline fun<reified T: Token> nextTokenIf(crossinline condition: (T) -> Boolean = { true }): T =
        resetIfNull { (nextToken() as? T)!!.takeIf(condition)!! }

    /**
     * Obtains the next token if it's a [DefinedToken] and is [definedToken]. Equivalent to
     * ```
     * nextTokenIf<DefinedToken> { it == definedToken }
     * ```
     * See [nextTokenIf].
     */
    private fun nextIfDefinedToken(definedToken: DefinedToken) =
        nextTokenIf<DefinedToken> { it == definedToken }

    /**
     * Parses a list of zero or more [Expression]s separated by commas.
     */
    private fun parseExpressionList() = buildList {
        while (true) {
            try {
                add(parseExpression())
                nextIfDefinedToken(COMMA)
            } catch (_: NullPointerException) {
                break
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////

    /**
     * Parses any possible expression, turning it into a tree of [BinaryOperation]s based on the [orderOfOperations].
     * Parentheses are handled in [parseSubExpression].
     *
     * The return value might not be a [BinaryOperation] in the case that there is only one value and no binary
     * operators.
     */
    private fun parseExpression(): Expression = resetIfNull {
        val operators = ArrayDeque<DefinedToken>() // +
        val values = ArrayDeque(listOf(parseSubExpression())) // a*b c

        /**
         * Combines the last two values in `values` into a [BinaryOperation] where the operator is the last item in
         * `operators`.
         */
        fun combineLastTwo() {
            try {
                val right = values.removeLast()
                val left = values.removeLast()
                values.add(
                    BinaryOperation(
                        operator = operators.removeLast(),
                        left = left,
                        right = right
                    )
                )
            } catch (_: NoSuchElementException) {
                /*
                This means there somehow weren't enough values or operators, which means the expression as a
                whole is malformed, so we need to go back
                 */
                throw NullPointerException()
            }
        }

        while (true) {
            val nextOperator = try {
                nextTokenIf<DefinedToken> { it.isBinaryOperator }
            } catch (_: NullPointerException) {
                break
            }

            if(operators.isNotEmpty() && orderOfOperations[operators.last()]!! > orderOfOperations[nextOperator]!!) {
                combineLastTwo()
            }

            operators.add(nextOperator)
            values.add(parseSubExpression())
        }

        while (operators.isNotEmpty()) {
            combineLastTwo()
        }

        values.last()
    }

    /**
     * Parses any expression that could be treated as an operand somewhere in [parseExpression].
     */
    private fun parseSubExpression(): Expression = resetIfNull {
        try {
            (::parseFunctionCall or
                ::parseArrayAccess or
                ::parseUnary or
                ::parseArrayLiteral or
                ::parseValue
            )()
        } catch (_: NullPointerException) {
            nextIfDefinedToken(LEFT_PAREN)
            val result = parseExpression()
            nextIfDefinedToken(RIGHT_PAREN)

            result
        }
    }

    private fun parseValue() = resetIfNull {
        val value = nextTokenIf<LexicalAnalysis.Value>()

        Value(value)
    }

    private fun parseArrayLiteral() = resetIfNull {
        nextIfDefinedToken(LEFT_BRACKET)
        val values = parseExpressionList()
        nextIfDefinedToken(RIGHT_BRACKET)

        ArrayLiteral(values)
    }

    private fun parseUnary() = resetIfNull {
        val operator = nextTokenIf<DefinedToken> { it.isUnaryOperator }
        val operand = parseSubExpression()

        UnaryOperation(operator, operand)
    }

    private fun parseArrayAccess() = resetIfNull {
        val arrayName = nextTokenIf<VariableName>()
        nextIfDefinedToken(LEFT_BRACKET)
        val index = parseSubExpression()
        nextIfDefinedToken(RIGHT_BRACKET)

        ArrayAccess(arrayName, index)
    }

    private fun parseFunctionCall() = resetIfNull {
        val functionName = nextTokenIf<MethodName>()
        nextIfDefinedToken(LEFT_PAREN)
        val params = parseExpressionList()
        nextIfDefinedToken(RIGHT_PAREN)

        FunctionCall(functionName, params)
    }

    fun debug() {
        println(lexicalAnalysis.tokens)

        try {
            resetIfNull {
                println(parseExpression())
            }
        } catch (_: NullPointerException) {
            println("nope")
        }
        println("stopped at $index")
    }

    companion object {
        /**
         * A list of commands that always begin with a certain keyword.
         */
        private val commands: Map<DefinedToken, (List<Token>) -> Unit> = mapOf(
            OUTPUT to {},
            INPUT to {},
            IF to {},
            ELSE to {},
            LOOP to {},
            END to {}
        )

        /**
         * Order of operations for binary operators, listed from lowest to highest.
         */
        private val orderOfOperations = listOf(
            listOf(LOGIC_OR),
            listOf(LOGIC_AND),
            listOf(EQUAL, NOT_EQUAL),
            listOf(GREATER_THAN, LESS_THAN, GREATER_THAN_EQUAL, LESS_THAN_EQUAL),
            listOf(PLUS, MINUS),
            listOf(MULTIPLY, DIVIDE, MODULUS)
        )
            .mapIndexed { precedence, operators ->
                operators.map { it to precedence }
            }
            .flatten()
            .toMap()

        /**
         * Usage:
         * ```
         * ({ /* value */ }
         *     or { /* next value */ }
         *     or { /* next value */ })()
         * ```
         * Add as many `or`s as needed. After the last `or`, make sure to evaluate it to actually get the value, since
         * the raw return value of the whole thing is a lambda.
         *
         * Returns a lambda that returns the value of the first lambda that does not throw [NullPointerException].
         */
        private infix fun<T: Ast> (() -> T).or(nextOption: () -> T): () -> T =
            {
                try {
                    this()
                } catch (e: NullPointerException) {
                    nextOption()
                }
            }
    }
}