class Iterator (private val string: String) {
    var index: Int = 0
        private set

    /**
     * Returns whether the iterator is at the end of the string.
     */
    fun reachedEnd() = index >= string.length

    /**
     * Returns the next character in the string and moves the iterator forward.
     *
     * If [reachedEnd] is true, `null` is returned.
     */
    fun next(): Char? {
        if (reachedEnd())
            return null

        val currIndex = index
        index++
        return string[currIndex]
    }

    /**
     * Moves the iterator back 1 character.
     */
    fun goBackOne() {
        index--
    }

    /**
     * Returns whether [literal] exists in the string.
     */
    fun nextIsLiteral(literal: String): Boolean {
        val returnPoint = index

        for (c in literal) {
            if (next() != c) {
                index = returnPoint
                return false
            }
        }
        return true
    }

    /**
     * Get the sequence of digits in the code starting at [index], returning an empty list if there are none. This is
     * useful for parsing numbers.
     */
    fun nextDigitSequence(base: Int) = buildList {
        doUntilEnd findSequence@ {
            getSampleChar {
                if (it!!.isDigit()) {
                    add(it.digitToInt(base))
                    true
                } else false
            }
        }
    }

    /**
     * Run [function] repeatedly until either the end of the string is reached or [function] returns false.
     */
    fun doUntilEnd(function: () -> Boolean) {
        while (!reachedEnd()) {
            if (!function()) {
                break
            }
        }
    }

    /**
     * Get the next character from the iterator, or null if [reachedEnd] is true. The character is passed to [function];
     * if it returns `false`, the iterator moves one char back.
     *
     * If you know [reachedEnd] is false (and therefore there is a next character), use [getSampleCharNotAtEnd], which
     * guarantees the character to be non-null.
     *
     * This allows the caller to get a character from the iterator, decide whether they want to use it, and retreat if
     * not.
     */
    fun getSampleChar(function: (Char?) -> Boolean): Boolean {
        val result = function(next())
        if (!result)
            goBackOne()
        return result
    }

    /**
     * Gets the next character from the iterator. The character is passed to [function]; if it returns `false`, the
     * iterator moves one char back. If [reachedEnd] is true when this is called, you will get a [NullPointerException].
     *
     * Use [getSampleChar] if [reachedEnd] can be true; it will pass `null` to [function] in that case instead of
     * throwing an error.
     */
    fun getSampleCharNotAtEnd(function: (Char) -> Boolean): Boolean =
        getSampleChar { function(it!!) }
}