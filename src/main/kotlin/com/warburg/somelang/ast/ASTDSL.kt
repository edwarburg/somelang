package com.warburg.somelang.ast

fun id(name: String): IdentifierNode = IdentifierNode(name)
fun lit(value: Int): IntLiteralNode = IntLiteralNode(value)

fun ret(node: Node): ReturnNode = ReturnNode(node)

fun readLocal(name: String): ReadLocalVarNode = ReadLocalVarNode(id(name))
fun getStatic(name: String): GetStaticValueNode = GetStaticValueNode(id(name))
fun readField(receiver: Node, field: String): ReadFieldValueNode = ReadFieldValueNode(receiver, id(field))

fun binOp(operator: BinaryOperator, lhs: Node, rhs: Node): BinaryOperationNode = BinaryOperationNode(operator, lhs, rhs)
fun add(lhs: Node, rhs: Node): BinaryOperationNode = binOp(BinaryOperator.ADD, lhs, rhs)

fun pos(node: Node): PositionalArgumentNode = PositionalArgumentNode(node)

val noop: NoOpNode = NoOpNode