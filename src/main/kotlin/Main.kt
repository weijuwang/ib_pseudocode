import java.io.File

fun main(args: Array<String>) {
    val code = File(args[0]).readText()
    val t = Tokenizer(code)
    println(t.tokens)
}