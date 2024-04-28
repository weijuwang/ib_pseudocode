import Tokenizer.TokenType.*
import Tokenizer.OperatorType.*
import Tokenizer.DefinedToken.*
import kotlin.math.pow

/**
 * Tokenizes IB pseudocode. The tokenizer stops as soon as it finds any invalid tokens; it does not attempt to parse the
 * rest of the string. [successful] is set to `true` only if there are no such errors and the entire string can be
 * tokenized.
 *
 * # Basic syntax
 * The tokenizer will not understand even slight deviations from IB syntax.
 * Pay attention to the below in particular:
 * - Variable names must begin with an uppercase letter or underscore; the rest can be uppercase letters, numbers, or
 * underscores.
 * - Method names can have letters of either case, numbers, or underscores but cannot begin with a number.
 * - Identifiers beginning with an underscore and then having zero or more uppercase letters, underscores, or numbers
 * are interpreted as variable names by default.
 * This means, for example, that the tokenizer will understand `MY_METHOD` to be a variable name even if it is only ever
 * used in the code as a method, leading to a syntax error.
 *
 * # Deviations from IB pseudocode
 * This tokenizer implements some syntax that deviates from standard IB pseudocode. However, it should be able to
 * understand any IB pseudocode that strictly follows the syntax and does not contain human-readable language except in
 * comments. Specific deviations of note are:
 * - Strings can be multiline; there is no special multiline string syntax.
 * - The three defined string escape sequences are `\n`, `\t`, and `\\` for newlines, tabs, and backslashes
 * respectively. If an escape is not in this list, it is interpreted as though the escape backslash were not there; e.g.
 * `\a` and `a` are the same.
 * - The boolean operators `XOR`, `NAND`, and `NOR` are available.
 * - Class names begin with an uppercase letter and then must have one or more letters of either case, underscores, or
 * numbers. The class name cannot be all uppercase; otherwise, it is misinterpreted as a variable name.
 */
class Tokenizer (val code: String) {
    /**
     * The list of tokens in [code], including comments and newlines.
     */
    val tokens: List<Token>

    /**
     * The index of the char in [code] that could not be tokenized. If this is equal to [code].length then the entire
     * code was successfully tokenized.
     */
    val firstInvalidCharIndex: Int

    /**
     * `true` if the entire [code] was tokenized. Otherwise, [firstInvalidCharIndex] gives the index of the first char
     * that could not be tokenized.
     */
    val successful: Boolean

    init {
        this.tokens = buildList {
            val iterator = Iterator(code)

            iterator.doUntilEnd nextToken@ {
                // Skip whitespace.
                if (iterator.getSampleChar { it in WHITESPACE_CHARS }) {
                    return@nextToken true
                }

                //////////////////////////////////////////////////////////////////////////

                // Attempt to find a [VariableName] or [MethodName].
                val identifierName = buildString {
                    iterator.getSampleCharNotAtEnd { firstChar ->
                        if (firstChar.isLetter() || firstChar == '_') {
                            append(firstChar)

                            iterator.doUntilEnd findVariableName@ {
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
                    // If it's actually a keyword then add that instead of making it a variable or method
                    for (token in DefinedToken.entries) {
                        if (identifierName == token.value) {
                            add(token)
                            return@nextToken true
                        }
                    }

                    add(
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

                    iterator.getSampleChar { nextDigit ->
                        if (nextDigit == Decimal.DECIMAL_POINT) {
                            val decimalPart = iterator
                                .nextDigitSequence(base=10)
                                .map { it.toDouble() }
                                .mapIndexed { i, digit -> digit * 10.0.pow(-i-1) }
                                .sum()

                            add(Decimal(integerPart + decimalPart))
                        } else {
                            add(Integer(integerPart))
                        }

                        true
                    }

                    return@nextToken true
                }

                //////////////////////////////////////////////////////////////////////////

                // Attempt to find a [DefinedToken].
                for (token in DefinedToken.entries) {
                    if (iterator.nextIsLiteral(token.value)) {
                        add(token)
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
                    Comment.INDICATOR to {
                        add(Comment(buildString {
                            // Find the content of the comment
                            iterator.doUntilEnd findCommentContent@ {
                                when (val commentChar = iterator.next()) {
                                    NEWLINE_CHAR -> return@findCommentContent false
                                    else -> {
                                        append(commentChar)
                                        return@findCommentContent true
                                    }
                                }
                            }
                        }))

                        // Comments are always terminated by newlines or the end of the code
                        if (!iterator.reachedEnd())
                            add(NEWLINE)
                    },

                    // String
                    Str.BOUNDARY.toString() to {
                        add(Str(buildString {
                            var escaped = false

                            iterator.doUntilEnd findStringContent@ {
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
            successful = iterator.reachedEnd()
        }
    }

    /**
     * [KEYWORD] and [OPERATOR] are what the names suggest. [MISC] is anything else, including parentheses, commas, etc.
     */
    enum class TokenType {
        KEYWORD, BOOLEAN_LITERAL, OPERATOR, MISC
    }

    /**
     * Various types of operators.
     */
    enum class OperatorType {
        /**
         * Binary operator on two numbers returning a number, where a number is an integer or float.
         */
        NUMERICAL,

        /**
         * Binary operator on two strings returning a string.
         */
        STRING,

        /**
         * Binary operator on two Booleans returning a Boolean.
         */
        BOOLEAN,

        /**
         * Binary operator on two numbers returning a Boolean, where a number is an integer or float.
         */
        NUMERICAL_COMPARISON,

        /**
         * Binary operator on two strings returning a Boolean.
         */
        STRING_COMPARISON,

        /**
         * Unary operator.
         */
        UNARY
    }

    /**
     * All tokens inherit from here.
     */
    interface Token

    /**
     * A comment in the source code. These do not affect the semantics of the code in any way.
     */
    class Comment(val content: String) : Token {
        companion object {
            val INDICATOR = "//"
        }
    }

    /**
     * [Integer], [Decimal], or [Str].
     */
    interface Literal : Token

    /**
     * [Integer] or [Decimal].
     */
    interface Number: Literal

    /**
     * An integer literal.
     */
    class Integer(val value: Int) : Number

    /**
     * A float/double literal.
     */
    class Decimal(val value: Double) : Number {
        companion object {
            const val DECIMAL_POINT = '.'
        }
    }

    /**
     * A string literal.
     */
    class Str(val content: String) : Literal {
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
    class VariableName(val name: String) : Token {
        companion object {
            val REGEX = "[_A-Z][_\\dA-Z]*".toRegex()
        }
    }

    /**
     * The name of a method, which must be camelCase. Because one-word camelCase and lowercase names are
     * indistinguishable, any keyword that is not defined in [DefinedToken] will be erroneously parsed as a [MethodName]
     * and it's up to context to determine which one it is.
     */
    class MethodName(val name: String) : Token {
        companion object {
            val REGEX = "[_a-z][_\\dA-Za-z]*".toRegex()
        }
    }

    /**
     * The name of a class, which must be PascalCase. Because one-word camelCase and lowercase names are
     * indistinguishable, any keyword that is not defined in [DefinedToken] will be erroneously parsed as a [MethodName]
     * and it's up to context to determine which one it is.
     */
    class ClassName(val name: String) : Token

    /**
     * All predefined tokens, including keywords and operators.
     *
     * **The order of the tokens' declaration is significant. Be careful if you move them.** For example, <= must be
     * declared before < because otherwise the tokenizer would interpret it as two separate tokens < and =. It's not
     * worth adding logic in the tokenizer itself to deal with this because there are very few cases where this is
     * actually an issue.
     */
    enum class DefinedToken(
        val value: String,
        val types: TokenType = MISC,
        val operatorTypes: List<OperatorType> = listOf()
    ) : Token {
        TRUE("true", BOOLEAN_LITERAL),
        FALSE("false", BOOLEAN_LITERAL),

        OUTPUT("output", KEYWORD),
        INPUT("input", KEYWORD),

        IF("if", KEYWORD),
        THEN("then", KEYWORD),
        ELSE("else", KEYWORD),

        LOOP("loop", KEYWORD),
        WHILE("while", KEYWORD),
        UNTIL("until", KEYWORD),
        FROM("from", KEYWORD),
        TO("to", KEYWORD),

        END("end", KEYWORD),

        NEWLINE(NEWLINE_CHAR.toString()),

        LEFT_PAREN("("),
        RIGHT_PAREN(")"),
        LEFT_BRACKET("["),
        RIGHT_BRACKET("]"),
        COMMA(","),
        MEMBER_INVOCATION("."),

        EQUAL("=", OPERATOR, listOf(NUMERICAL_COMPARISON, STRING_COMPARISON, BOOLEAN)),
        NOT_EQUAL("≠", OPERATOR, listOf(NUMERICAL_COMPARISON, STRING_COMPARISON, BOOLEAN)),
        GREATER_THAN_EQUAL(">=", OPERATOR, listOf(NUMERICAL_COMPARISON)),
        LESS_THAN_EQUAL("<=", OPERATOR, listOf(NUMERICAL_COMPARISON)),
        GREATER_THAN(">", OPERATOR, listOf(NUMERICAL_COMPARISON)),
        LESS_THAN("<", OPERATOR, listOf(NUMERICAL_COMPARISON)),

        LOGIC_NOT("NOT", OPERATOR, listOf(UNARY)),
        LOGIC_AND("AND", OPERATOR, listOf(BOOLEAN)),
        LOGIC_OR("OR", OPERATOR, listOf(BOOLEAN)),
        LOGIC_XOR("XOR", OPERATOR, listOf(BOOLEAN)),
        LOGIC_NAND("NAND", OPERATOR, listOf(BOOLEAN)),
        LOGIC_NOR("NOR", OPERATOR, listOf(BOOLEAN)),

        PLUS("+", OPERATOR, listOf(UNARY, STRING, NUMERICAL)),
        MINUS("-", OPERATOR, listOf(UNARY, NUMERICAL)),
        MULTIPLY("*", OPERATOR, listOf(NUMERICAL)),
        DIVIDE("div", OPERATOR, listOf(NUMERICAL)),
        MODULUS("mod", OPERATOR, listOf(NUMERICAL))
    }

    companion object {
        val WHITESPACE_CHARS = listOf(' ', '\t')
        val NEWLINE_CHAR = '\n'
    }
}