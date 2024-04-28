/**
 *
 */
class Parser (val lexer: LexicalAnalysis) {
    /**
     * The list of tokens produced by the lexer.
     */
    private val tokens get() = lexer.tokens.values

    /**
     * The indices within the token list where each line begins.
     *
     * The first index is always 0 and the last index is always the number of tokens plus one.
     */
    private val lineStartPositions =
        (
            listOf(0)
            + tokens
                .mapIndexedNotNull { i, token ->
                    (i + 1).takeIf { token == LexicalAnalysis.DefinedToken.NEWLINE }
                }
            + listOf(tokens.size + 1)
        )

    /**
     * A list of tokens in each non-empty line.
     */
    val lines = lineStartPositions
        .dropLast(1)
        .mapIndexed { lineNum, preLineStartIndex ->
            tokens.toList().subList(preLineStartIndex, lineStartPositions[lineNum+1] - 1)
        }
        .filter { it.isNotEmpty() }

    init {
        for (line in lines) {
            println(line)
        }
    }
}