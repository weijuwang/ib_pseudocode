import parser.Parser
import java.io.File
import kotlin.time.*

fun main(args: Array<String>) {
    val code = File(args[0]).readText()
    val (parser, timeTaken) = measureTimedValue {
        Parser(code)
    }
    println(timeTaken)
    println(parser.result)
}