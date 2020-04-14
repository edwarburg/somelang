package com.warburg.somelang.ast

import com.autodsl.annotation.AutoDsl


/**
 * @author ewarburg
 */
sealed class Node

@AutoDsl
data class File(val nodes: List<Node>) : Node()

@AutoDsl
data class FunctionDeclaration(val name: Identifier, val body: Node) : Node()

@AutoDsl
data class Block(val statements: List<Node>) : Node()

@AutoDsl
data class Return(val value: Node) : Node()

@AutoDsl
data class LocalVarDeclaration(val name: Identifier, val rhs: Node) : Node()

@AutoDsl
data class Identifier(val id: String) : Node()

@AutoDsl
data class IntLiteral(val value: Int) : Node()

@AutoDsl
data class BinaryOperation(val operator: BinaryOperator, val lhs: Node, val rhs: Node) : Node()

enum class BinaryOperator {
    ADD
}