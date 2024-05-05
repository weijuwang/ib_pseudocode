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
    /**
     * The list of tokens obtained from the lexer without their positions in the original file.
     */
    private val tokens = lexicalAnalysis.tokens.values.toList()

    /**
     * The index in the token list that the parser is at.
     */
    private var index = 0

    /**
     * The parsed abstract syntax trees of each statement in the code in order.
     */
    val result: List<Ast>?

    /**
     * A list of statements or blocks that always begin with a certain keyword.
     */
    private val commands: Map<DefinedToken, () -> Ast.Statement> = mapOf(
        OUTPUT to {
            Ast.Output(parseExpressionList())
        },
        INPUT to {
            Ast.Input(nextTokenIf<VariableName>())
        },
        IF to {
            Ast.If(buildMap {
                // if [condition] then \n [statements \n]
                fun ifConditionThen() {
                    val condition = parseExpression()
                    nextDefinedTokenIf(THEN)
                    skipNewlines()
                    put(condition, parseStatements())
                }

                ifConditionThen()

                while (true) {
                    // else
                    if (optional { nextDefinedTokenIf(ELSE) } == null) {
                        break
                    }

                    if (optional { nextDefinedTokenIf(IF) } == null) {
                        // \n
                        skipNewlines()
                        // [statements \n]
                        put(Ast.Value(Bool(true)), parseStatements())
                        break
                    } else {
                        // if

                        // [condition] then \n [statements \n]
                        ifConditionThen()
                    }
                }

                // end
                nextDefinedTokenIf(END)
                optional { nextDefinedTokenIf(IF) }
            })
        },
        LOOP to {
            fun codeInsideBlock(): List<Ast.Statement> {
                skipNewlines()
                val loopedCode = parseStatements()
                nextDefinedTokenIf(END)
                optional { nextDefinedTokenIf(LOOP) }
                return loopedCode
            }

            if (optional { nextDefinedTokenIf(WHILE) } != null) {
                val condition = parseExpression()
                val code = codeInsideBlock()

                Ast.LoopWhile(
                    condition = condition,
                    code = code
                )
            } else if (optional { nextDefinedTokenIf(UNTIL) } != null) {
                val condition = parseExpression()
                val code = codeInsideBlock()

                Ast.LoopUntil(
                    condition = condition,
                    code = code
                )
            } else {
                val iteratorVariable = nextTokenIf<VariableName>()
                nextDefinedTokenIf(FROM)
                val start = parseExpression()
                nextDefinedTokenIf(TO)
                val end = parseExpression()
                val code = codeInsideBlock()

                Ast.LoopRange(
                    variableName = iteratorVariable,
                    start = start,
                    end = end,
                    code = code
                )
            }
        }
    )

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
     * Returns the value of the first lambda that does not throw [NullPointerException].
     */
    private fun firstValid(vararg parsers: () -> Ast): Ast = resetIfNull {
        for (parser in parsers) {
            try {
                return@resetIfNull parser()
            } catch (_: NullPointerException) {}
        }
        throw NullPointerException()
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
    private fun nextDefinedTokenIf(definedToken: DefinedToken) =
        nextTokenIf<DefinedToken> { it == definedToken }

    /**
     * Obtains the next token if it's a [DefinedToken] and [condition] is true. Equivalent to
     * ```
     * nextTokenIf<DefinedToken>(condition)
     * ```
     * See [nextTokenIf].
     */
    private fun nextDefinedTokenIf(condition: (DefinedToken) -> Boolean = { true }) =
        nextTokenIf<DefinedToken>(condition)

    /**
     * Parses a list of zero or more [Expression]s separated by commas.
     */
    private fun parseExpressionList() = buildList {
        while (true) {
            try {
                add(parseExpression())
                nextDefinedTokenIf(COMMA)
            } catch (_: NullPointerException) {
                break
            }
        }
    }

    /**
     * Skips all the next newlines. If [atLeastOne] is true, there must be at least one newline or
     * [NullPointerException] is thrown.
     */
    private fun skipNewlines(atLeastOne: Boolean = true) {
        if (atLeastOne) {
            nextDefinedTokenIf(NEWLINE)
        }

        while (true) {
            try {
                nextDefinedTokenIf(NEWLINE)
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
    private fun parseExpression(): Ast.Expression = resetIfNull {
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
                    Ast.BinaryOperation(
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
                nextDefinedTokenIf { it.isBinaryOperator }
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
    private fun parseSubExpression(): Ast.Expression = resetIfNull {
        try {
            firstValid(
                ::parseFunctionCall,
                ::parseArrayAccess,
                ::parseUnary,
                ::parseArrayLiteral,
                ::parseValue
            ) as Ast.Expression
        } catch (_: NullPointerException) {
            nextDefinedTokenIf(LEFT_PAREN)
            val result = parseExpression()
            nextDefinedTokenIf(RIGHT_PAREN)

            result
        }
    }

    private fun parseValue() = resetIfNull {
        val value = nextTokenIf<Value>()

        Ast.Value(value)
    }

    private fun parseArrayLiteral() = resetIfNull {
        nextDefinedTokenIf(LEFT_BRACKET)
        val values = parseExpressionList()
        nextDefinedTokenIf(RIGHT_BRACKET)

        Ast.ArrayLiteral(values)
    }

    private fun parseUnary() = resetIfNull {
        val operator = nextDefinedTokenIf { it.isUnaryOperator }
        val operand = parseSubExpression()

        Ast.UnaryOperation(operator, operand)
    }

    private fun parseArrayAccess() = resetIfNull {
        val arrayName = nextTokenIf<VariableName>()
        nextDefinedTokenIf(LEFT_BRACKET)
        val index = parseSubExpression()
        nextDefinedTokenIf(RIGHT_BRACKET)

        Ast.ArrayAccess(arrayName, index)
    }

    private fun parseFunctionCall() = resetIfNull {
        val functionName = nextTokenIf<MethodName>()
        nextDefinedTokenIf(LEFT_PAREN)
        val params = parseExpressionList()
        nextDefinedTokenIf(RIGHT_PAREN)

        Ast.FunctionCall(functionName, params)
    }

    private fun parseCommand(): Ast.Statement = resetIfNull {
        commands[nextDefinedTokenIf { it.type == Type.COMMAND_START }]!!()
    }

    private fun parseAssignment() = resetIfNull {
        // Left side can be a variable name or array access
        val leftSide = firstValid(::parseArrayAccess, { Ast.Value(nextTokenIf<VariableName>()) })
        nextDefinedTokenIf(EQUAL)
        val rightSide = parseExpression()

        Ast.Assignment(
            variableName = leftSide as Ast.LeftSide,
            value = rightSide
        )
    }

    private fun parseStatementOrBlock(): Ast.Statement = resetIfNull {
        firstValid(
            ::parseFunctionCall,
            ::parseCommand,
            ::parseAssignment
        ) as Ast.Statement
    }

    private fun parseStatements(): List<Ast.Statement> = resetIfNull {
        buildList {
            skipNewlines(false)
            try {
                while (true) {
                    add(parseStatementOrBlock())
                    skipNewlines()
                }
            } catch (_: NullPointerException) {}
        }
    }

    init {
        result = try {
            parseStatements()
        } catch (_: NullPointerException) {
            null
        }
    }

    companion object {
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
    }
}