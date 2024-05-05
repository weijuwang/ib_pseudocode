import LexicalAnalysis.DefinedToken.Type.*
import kotlin.math.pow

/**
 * Tokenizes a string of IB pseudocode. This is the first step in the parsing process.
 *
 * The lexer stops as soon as it finds any invalid tokens; it does not attempt to parse the rest of the string.
 * [successful] is set to `true` only if there are no such errors and the entire string can be tokenized.
 *
 * # Basic syntax
 * The lexer will not understand even slight deviations from IB syntax.
 * Pay attention to the below in particular:
 * - Variable names must begin with an uppercase letter or underscore; the rest can be uppercase letters, numbers, or
 * underscores.
 * - Method names can have letters of either case, numbers, or underscores but cannot begin with a number.
 * - Identifiers beginning with an underscore and then having zero or more uppercase letters, underscores, or numbers
 * are interpreted as variable names by default.
 * This means, for example, that the lexer will understand `MY_METHOD` to be a variable name even if it is only ever
 * used in the code as a method, leading to a syntax error.
 *
 * # Deviations from IB pseudocode
 * This lexer implements some syntax that deviates from standard IB pseudocode. However, it should be able to
 * understand any IB pseudocode that strictly follows the syntax and does not contain human-readable language except in
 * comments. Specific deviations of note are:
 * - Strings can be multiline; there is no special multiline string syntax.
 * - The three defined string escape sequences are `\n`, `\t`, and `\\` for newlines, tabs, and backslashes
 * respectively. If an escape is not in this list, it is interpreted as though the escape backslash were not there; e.g.
 * `\a` and `a` are the same.
 * - Class names begin with an uppercase letter and then must have one or more letters of either case, underscores, or
 * numbers. The class name cannot be all uppercase; otherwise, it is misinterpreted as a variable name.
 */
class LexicalAnalysis (val code: String) {
    /**
     * The index of the char in [code] that could not be tokenized. If this is equal to [code].length then the entire
     * code was successfully tokenized.
     */
    val firstInvalidCharIndex: Int

    /**
     * `true` if the entire [code] was tokenized. Otherwise, [firstInvalidCharIndex] gives the index of the first char
     * that could not be tokenized.
     */
    val successful: Boolean get() = firstInvalidCharIndex == code.length

    /**
     * The list of tokens in [code], including newlines.
     *
     * The bulk of code that actually performs the tokenization is contained entirely in the implementation of this
     * property.
     */
    val tokens: Map<Int, Token> = buildMap {
        val iterator = Iterator(code)
        var tokenStartIndex = 0

        fun addToken(token: Token) {
            put(tokenStartIndex, token)
        }

        iterator.doUntilEndAnd nextToken@ {
            // Skip whitespace.
            if (iterator.getSampleChar { it in WHITESPACE_CHARS }) {
                return@nextToken true
            }

            tokenStartIndex = iterator.index

            //////////////////////////////////////////////////////////////////////////

            // Attempt to find a [VariableName] or [MethodName].
            val identifierName = buildString {
                iterator.getSampleCharNotAtEnd { firstChar ->
                    if (firstChar.isLetter() || firstChar == '_') {
                        append(firstChar)

                        iterator.doUntilEndAnd findVariableName@ {
                            iterator.getSampleCharNotAtEnd {
                                if (it.isLetterOrDigit() || it == '_') {
                                    append(it)
                                    true
                                } else false
                            }
                        }

                        true
                    } else false
                }
            }

            if (identifierName.isNotEmpty()) {
                /*
                Annoying thing: a lot of keywords and `true`/`false` look like identifier names. We can sort this out by
                checking if it's one of those.
                 */

                // Check keywords
                for (token in DefinedToken.entries) {
                    if (identifierName == token.value) {
                        addToken(token)
                        return@nextToken true
                    }
                }

                // Check boolean literals
                for ((translation, value) in Bool.values) {
                    if (identifierName == value) {
                        addToken(Bool(translation))
                        return@nextToken true
                    }
                }

                addToken(
                    if (VariableName.REGEX.matchEntire(identifierName) != null)
                        VariableName(identifierName)
                    else if (MethodName.REGEX.matchEntire(identifierName) != null)
                        MethodName(identifierName)
                    else
                        ClassName(identifierName)
                )

                return@nextToken true
            }

            //////////////////////////////////////////////////////////////////////////

            // Attempt to find an [Integer] or [Float].
            val integerPartDigits = iterator
                .nextDigitSequence(base=10)
                .asReversed()
                .mapIndexed { i, digit -> digit * 10.0.pow(i).toInt() }

            if (integerPartDigits.isNotEmpty()) {
                val integerPart = integerPartDigits.sum()

                /*
                If there's a decimal point following an integer then it's not really an integer, it's actually a
                decimal
                 */
                val alreadyAtEnd = iterator.reachedEnd()

                iterator.getSampleChar { nextChar ->
                    if (nextChar == Decimal.DECIMAL_POINT) {
                        val decimalPart = iterator
                            .nextDigitSequence(base=10)
                            .map { it.toDouble() }
                            .mapIndexed { i, digit -> digit * 10.0.pow(-i-1) }
                            .sum()

                        addToken(Decimal(integerPart + decimalPart))
                        true
                    } else {
                        addToken(Integer(integerPart))
                        alreadyAtEnd
                    }
                }

                return@nextToken true
            }

            //////////////////////////////////////////////////////////////////////////

            // Attempt to find a [DefinedToken].
            for (token in DefinedToken.entries) {
                if (iterator.nextIsLiteral(token.value)) {
                    addToken(token)
                    return@nextToken true
                }
            }

            //////////////////////////////////////////////////////////////////////////

            /*
            Tokens that always begin with a certain character or string but have more complicated structures after
            that are listed here. The key is the beginning "trigger" string and the value is a lambda that adds
            tokens based on what characters follow the trigger.
             */
            val complexTokenSearches = mapOf(
                // Comments
                COMMENT_BEGIN to {
                    iterator.doUntilEndAnd {
                        iterator.getSampleChar { it != NEWLINE_CHAR }
                    }

                    // Comments are always terminated by newlines or the end of the code
                    if (!iterator.reachedEnd())
                        addToken(DefinedToken.NEWLINE)
                },

                // String
                Str.BOUNDARY.toString() to {
                    addToken(Str(buildString {
                        var escaped = false

                        iterator.doUntilEndAnd findStringContent@ {
                            val nextContentChar = iterator.next()

                            if (nextContentChar == Str.ESCAPE) {
                                // Start of escape sequence
                                escaped = true
                            } else if (escaped) {
                                // Escape sequence
                                append(Str.ESCAPES[nextContentChar] ?: nextContentChar)
                                escaped = false
                            } else if (nextContentChar == Str.BOUNDARY) {
                                // End of string
                                return@findStringContent false
                            } else {
                                // Actual string content
                                append(nextContentChar)
                            }

                            return@findStringContent true
                        }
                    }))
                }
            )

            for ((beginning, action) in complexTokenSearches) {
                if (iterator.nextIsLiteral(beginning)) {
                    action()
                    return@nextToken true
                }
            }

            // If nothing has turned up at this point, then whatever token is next is invalid and we have to stop
            return@nextToken false
        }

        firstInvalidCharIndex = iterator.index
    }

    /**
     * All tokens inherit from here.
     */
    interface Token

    /**
     * [Literal] or [VariableName].
     */
    interface Value: Token

    /**
     * [Bool], [Integer], [Decimal], or [Str].
     */
    interface Literal : Value

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
    interface Number: Literal

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

    /**
     * The name of a variable, which must be all uppercase.
     */
    data class VariableName(val name: String) : Value {
        companion object {
            val REGEX = "[_A-Z][_\\dA-Z]*".toRegex()
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
        OUTPUT("output", COMMAND),
        INPUT("input", COMMAND),

        IF("if", COMMAND),
        ELSE("else", COMMAND),
        THEN("then", KEYWORD),

        LOOP("loop", COMMAND),
        WHILE("while", KEYWORD),
        UNTIL("until", KEYWORD),
        FROM("from", KEYWORD),
        TO("to", KEYWORD),

        END("end", COMMAND),

        NEWLINE(NEWLINE_CHAR.toString()),

        LEFT_PAREN("("),
        RIGHT_PAREN(")"),
        LEFT_BRACKET("["),
        RIGHT_BRACKET("]"),
        COMMA(","),
        MEMBER_INVOCATION("."),

        EQUAL("=", BINARY_OPERATOR),
        NOT_EQUAL("≠", BINARY_OPERATOR),
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
         * [COMMAND] is any statement that always begins with a certain keyword, like `if`, `else`, `loop`, `end`, `output`,
         * or `input`. Commands are technically also keywords.
         *
         * [MISC] is anything else, including parentheses, commas, etc.
         */
        enum class Type {
            KEYWORD, UNARY_OPERATOR, BINARY_OPERATOR, UNARY_AND_BINARY_OPERATOR, COMMAND, MISC
        }

        val isUnaryOperator get() = type in listOf(UNARY_OPERATOR, UNARY_AND_BINARY_OPERATOR)
        val isBinaryOperator get() = type in listOf(BINARY_OPERATOR, UNARY_AND_BINARY_OPERATOR)
    }

    companion object {
        val WHITESPACE_CHARS = listOf(' ', '\t')
        const val NEWLINE_CHAR = '\n'
        const val COMMENT_BEGIN = "//"
    }
}