package parser

import parser.Token.*
import parser.Token.Value.VariableName
import parser.Token.Value.Literal.*
import parser.Token.Value.Literal.Number.*
import kotlin.math.pow

/**
 * Tokenizes a string of IB pseudocode. This is the first step in the parsing process.
 *
 * The lexer stops as soon as it finds any invalid tokens; it does not attempt to parse the rest of the string.
 * [successful] is set to `true` only if there are no such errors and the entire string can be tokenized.
 */
class LexicalAnalysis (private val code: String) {
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

    companion object {
        val WHITESPACE_CHARS = listOf(' ', '\t')
        const val NEWLINE_CHAR = '\n'
        const val COMMENT_BEGIN = "//"
    }
}