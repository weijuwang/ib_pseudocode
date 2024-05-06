package parser

import parser.Token.DefinedToken.Type.*

/**
 * All tokens inherit from here.
 */
interface Token {
    /**
     * [Literal] or [VariableName].
     */
    interface Value: Token {
        /**
         * [Bool], [Number.Integer], [Number.Decimal], or [Str].
         */
        interface Literal : Value {
            /**
             * A Boolean literal.
             */
            data class Bool(val value: Boolean) : Literal {
                companion object {
                    val values = mapOf(
                        true to "true",
                        false to "false"
                    )
                }
            }

            /**
             * [Integer] or [Decimal].
             */
            interface Number: Literal {
                /**
                 * An integer literal.
                 */
                data class Integer(val value: Int) : Number

                /**
                 * A float/double literal.
                 */
                data class Decimal(val value: Double) : Number {
                    companion object {
                        const val DECIMAL_POINT = '.'
                    }
                }
            }

            /**
             * A string literal.
             */
            data class Str(val content: String) : Literal {
                companion object {
                    const val BOUNDARY = '"'
                    const val ESCAPE = '\\'

                    val ESCAPES = mapOf(
                        '\\' to '\\',
                        'n' to '\n',
                        't' to '\t'
                    )
                }
            }
        }

        /**
         * The name of a variable, which must be all uppercase.
         */
        data class VariableName(val name: String) : Value {
            companion object {
                val REGEX = "[_A-Z][_\\dA-Z]*".toRegex()
            }
        }
    }

    /**
     * The name of a method, which must be camelCase. Because one-word camelCase and lowercase names are
     * indistinguishable, any keyword that is not defined in [DefinedToken] will be erroneously parsed as a [MethodName]
     * and it's up to context to determine which one it is.
     */
    data class MethodName(val name: String) : Token {
        companion object {
            val REGEX = "[_a-z][_\\dA-Za-z]*".toRegex()
        }
    }

    /**
     * The name of a class, which must be PascalCase. Because one-word camelCase and lowercase names are
     * indistinguishable, any keyword that is not defined in [DefinedToken] will be erroneously parsed as a [MethodName]
     * and it's up to context to determine which one it is.
     */
    data class ClassName(val name: String) : Token

    /**
     * All predefined tokens, including keywords and operators.
     *
     * **The order of the tokens' declaration is significant. Be careful if you move them.** For example, <= must be
     * declared before < because otherwise the lexer would interpret it as two separate tokens < and =. It's not
     * worth adding logic in the lexer itself to deal with this because there are very few cases where this is
     * actually an issue.
     */
    enum class DefinedToken(
        val value: String,
        val type: Type = MISC
    ) : Token {
        OUTPUT("output", COMMAND_START),
        INPUT("input", COMMAND_START),

        IF("if", COMMAND_START),
        ELSE("else", CONTROL),
        THEN("then", KEYWORD),

        LOOP("loop", COMMAND_START),
        WHILE("while", KEYWORD),
        UNTIL("until", KEYWORD),
        FROM("from", KEYWORD),
        TO("to", KEYWORD),

        METHOD("method", COMMAND_START),

        END("end", CONTROL),

        NEWLINE(LexicalAnalysis.NEWLINE_CHAR.toString()),

        LEFT_PAREN("("),
        RIGHT_PAREN(")"),
        LEFT_BRACKET("["),
        RIGHT_BRACKET("]"),
        COMMA(","),
        MEMBER_INVOCATION("."),

        EQUAL("=", BINARY_OPERATOR),
        NOT_EQUAL("!=", BINARY_OPERATOR),
        GREATER_THAN_EQUAL(">=", BINARY_OPERATOR),
        LESS_THAN_EQUAL("<=", BINARY_OPERATOR),
        GREATER_THAN(">", BINARY_OPERATOR),
        LESS_THAN("<", BINARY_OPERATOR),

        LOGIC_NOT("NOT", UNARY_OPERATOR),
        LOGIC_AND("AND", BINARY_OPERATOR),
        LOGIC_OR("OR", BINARY_OPERATOR),

        PLUS("+", UNARY_AND_BINARY_OPERATOR),
        MINUS("-", UNARY_AND_BINARY_OPERATOR),
        MULTIPLY("*", BINARY_OPERATOR),
        DIVIDE("div", BINARY_OPERATOR),
        MODULUS("mod", BINARY_OPERATOR),
        ;

        /**
         * [KEYWORD], [UNARY_OPERATOR], [BINARY_OPERATOR], and [UNARY_AND_BINARY_OPERATOR] are what the
         * names suggest.
         *
         * [COMMAND_START] is a token that signals the start of a certain type of block, like `if`, `loop` `output`,
         * or `input`.
         *
         * [CONTROL] is any token that is used to signal something within a block, like `end` or `else`, but does not
         * start it.
         *
         * [MISC] is anything else, including parentheses, commas, etc.
         */
        enum class Type {
            KEYWORD, UNARY_OPERATOR, BINARY_OPERATOR, UNARY_AND_BINARY_OPERATOR, COMMAND_START, CONTROL, MISC
        }

        val isUnaryOperator get() = type in listOf(UNARY_OPERATOR, UNARY_AND_BINARY_OPERATOR)
        val isBinaryOperator get() = type in listOf(BINARY_OPERATOR, UNARY_AND_BINARY_OPERATOR)
    }
}