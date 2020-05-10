package com.warburg.somelang.ast

import com.warburg.somelang.id.FullyQualifiedName
import com.warburg.somelang.id.Name
import com.warburg.somelang.id.UnresolvedName

fun id(name: Name): IdentifierNode = IdentifierNode(name)
fun id(name: String): IdentifierNode = id(UnresolvedName(name))
fun unresolved(name: String): UnresolvedName = UnresolvedName(name)
fun fqn(name: String): FullyQualifiedName = FullyQualifiedName(name)
fun lit(value: Int): IntLiteralNode = IntLiteralNode(value)
fun lit(value: String): StringLiteralNode = StringLiteralNode(value)

fun ret(node: Node): ReturnNode = ReturnNode(node)

fun readLocal(name: String): ReadLocalVarNode = ReadLocalVarNode(id(name))
fun getStatic(name: String): GetStaticValueNode = GetStaticValueNode(id(name))
fun readField(receiver: Node, field: String): ReadFieldValueNode = ReadFieldValueNode(receiver, id(field))

fun binOp(operator: BinaryOperator, lhs: Node, rhs: Node): BinaryOperationNode = BinaryOperationNode(operator, lhs, rhs)
fun add(lhs: Node, rhs: Node): BinaryOperationNode = binOp(BinaryOperator.ADD, lhs, rhs)

fun pos(node: Node): PositionalArgumentNode = PositionalArgumentNode(node)

val noop: NoOpNode = NoOpNode

fun funcType(arguments: List<TypeExpressionNode>, returnType: TypeExpressionNode): FunctionTypeExpressionNode = FunctionTypeExpressionNode(arguments, returnType)
fun args(vararg types: TypeExpressionNode): List<TypeExpressionNode> = types.toList()