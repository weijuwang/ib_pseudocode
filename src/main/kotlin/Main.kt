fun main(args: Array<String>) {
    val code = java.io.File(args[0]).readText()
    val t = Tokenizer(code)
    println(t.tokens)
}