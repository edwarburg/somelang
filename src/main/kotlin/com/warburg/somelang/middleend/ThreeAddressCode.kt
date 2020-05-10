package com.warburg.somelang.middleend

import com.warburg.somelang.ast.*

/**
 * @author ewarburg
 */

inline class Variable(val id: String)

sealed class ValueReference
data class Immediate<T>(val value: T) : ValueReference()
abstract class VariableReference : ValueReference() {
    abstract val variable: Variable
}
data class LocalVarReference(override val variable: Variable) : VariableReference()
data class TempVarReference(override val variable: Variable) : VariableReference()

sealed class TacInstruction
data class DefineOp(val defines: Variable, val rhs: ValueReference) : TacInstruction()
data class BinOp(val defines: Variable, val lhs: ValueReference, val op: Op, val rhs: ValueReference) : TacInstruction()
data class ReturnOp(val value: ValueReference) : TacInstruction()


enum class Op {
    IntAdd
}

// mmm, tacops al pastor
fun BinaryOperator.asTacOp() : Op = when (this) {
    BinaryOperator.ADD -> Op.IntAdd
}

data class TacBlock(val instructions: List<TacInstruction>)

fun Node.asThreeAddressCode(): TacBlock {
    val context = TacContext()
    translate(this, context)
    val copy = ArrayList(context.instructions)
    return TacBlock(copy)
}

private fun translate(node: Node, context: TacContext): ValueReference {
    return when (node) {
        is BlockNode -> {
            var lastStatementResult: ValueReference? = null
            for (stmt in node.statements) {
                lastStatementResult = translate(stmt, context)
            }
            lastStatementResult ?: throw UnsupportedOperationException("no defined value for empty blocks")
        }
        is LocalVarDeclarationNode -> {
            val rhsResult = translate(node.rhs, context)
            val variable = Variable("${node.nameNode.name}_${context.nextVar().id}")
            context.putVarName(node.nameNode.name.text, variable)
            context.add(DefineOp(variable, rhsResult))
            LocalVarReference(variable)
        }
        is BinaryOperationNode -> {
            val resultVar = context.nextVar()
            context.add(BinOp(resultVar, translate(node.lhs, context), node.operator.asTacOp(), translate(node.rhs, context)))
            TempVarReference(resultVar)
        }
        is ReturnNode -> {
            val result = translate(node.value, context)
            context.add(ReturnOp(result))
            result
        }
        is IntLiteralNode -> Immediate(node.value)
        is IdentifierNode -> LocalVarReference(context.getVarName(node.name.text))
        else -> throw UnsupportedOperationException("Can only convert expressions")
    }
}

private class TacContext {
    private val _instructions = mutableListOf<TacInstruction>()
    val instructions: List<TacInstruction>
        get() = this._instructions
    private var variableNumber = 0
    private val namedVariables: MutableMap<String, Variable> = mutableMapOf()

    fun nextVar(): Variable {
        val variable = Variable("tac${this.variableNumber}")
        this.variableNumber++
        return variable
    }

    // TODO scopes
    fun putVarName(name: String, boundTo: Variable) {
        this.namedVariables[name] = boundTo
    }
    fun getVarName(name: String): Variable = this.namedVariables[name] ?: throw IllegalStateException("No variable bound for identifier '$name")


    fun add(instruction: TacInstruction) {
        this._instructions.add(instruction)
    }
}
