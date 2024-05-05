import LexicalAnalysis.*

/**
 * An abstract syntax tree produced by [Parser]. ASTs are further subdivided into [Statement]s and [Expression]s; see
 * the documentation below those for more info.
 */
interface Ast {
    /**
     *
     */
    interface Statement: Ast

    /**
     *
     */
    interface Expression: Ast

    /**
     * The left side of an assignment; currently this can be [VariableName] or [ArrayAccess].
     */
    interface LeftSide: Ast

    data class Value (
        val value: LexicalAnalysis.Value
    ) : Expression, LeftSide

    data class ArrayLiteral (
        val values: List<Expression>
    ) : Expression

    data class UnaryOperation (
        val operator: DefinedToken,
        val operand: Expression
    ) : Expression

    data class BinaryOperation (
        val operator: DefinedToken,
        val left: Expression,
        val right: Expression
    ) : Expression

    data class ArrayAccess (
        val arrayName: VariableName,
        val index: Expression
    ) : Expression, LeftSide

    data class FunctionCall (
        val functionName: MethodName,
        val params: List<Expression>
    ) : Expression, Statement

    data class Output (
        val expressions: List<Expression>
    ) : Statement

    data class Input (
        val variableName: VariableName
    ) : Statement

    data class If (
        val branches: Map<Expression, List<Statement>>
    ) : Statement

    data class LoopWhile (
        val condition: Expression,
        val code: List<Statement>
    ) : Statement

    data class LoopUntil (
        val condition: Expression,
        val code: List<Statement>
    ) : Statement

    data class LoopRange (
        val variableName: VariableName,
        val start: Expression,
        val end: Expression,
        val code: List<Statement>
    ) : Statement

    data class Assignment (
        val variableName: LeftSide,
        val value: Expression
    ) : Statement
}
