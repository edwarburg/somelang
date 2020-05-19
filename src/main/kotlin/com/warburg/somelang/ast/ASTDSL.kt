package com.warburg.somelang.ast

import com.warburg.somelang.attributable.AttrDef
import com.warburg.somelang.attributable.AttrVal
import com.warburg.somelang.attributable.Attributable
import com.warburg.somelang.attributable.SimpleAttrVal
import com.warburg.somelang.common.Phase
import com.warburg.somelang.id.FullyQualifiedName
import com.warburg.somelang.id.Name
import com.warburg.somelang.id.UnresolvedName


fun <P : Phase> file(vararg nodes: Node<P>): FileNode<P> = FileNode(nodes.toList())
fun <P : Phase> block(vararg statements: Node<P>): BlockNode<P> = BlockNode(statements.toList())

fun <P : Phase> id(name: Name): IdentifierNode<P> = IdentifierNode(name)
fun <P : Phase> id(name: String): IdentifierNode<P> = id(UnresolvedName(name))
fun nresolved(name: String): UnresolvedName = UnresolvedName(name)
fun fqn(name: String): FullyQualifiedName = FullyQualifiedName(name)
fun <P : Phase> lit(value: Int): IntLiteralNode<P> = IntLiteralNode(value)
fun <P : Phase> lit(value: String): StringLiteralNode<P> = StringLiteralNode(value)

fun <P : Phase> ret(node: Node<P>): ReturnNode<P> = ReturnNode(node)

fun <P : Phase> readLocal(name: String): ReadLocalVarNode<P> = ReadLocalVarNode(id(name))
fun <P : Phase> getStatic(name: String): GetStaticValueNode<P> = GetStaticValueNode(id(name))
fun <P : Phase> readField(receiver: Node<P>, field: String): ReadFieldValueNode<P> = ReadFieldValueNode(receiver, id(field))

fun <P : Phase> binOp(operator: BinaryOperator, lhs: Node<P>, rhs: Node<P>): BinaryOperationNode<P> = BinaryOperationNode(operator, lhs, rhs)
fun <P : Phase> add(lhs: Node<P>, rhs: Node<P>): BinaryOperationNode<P> = binOp(BinaryOperator.ADD, lhs, rhs)

fun <P : Phase> pos(node: Node<P>): PositionalArgumentNode<P> = PositionalArgumentNode(node)

fun <P : Phase> funcType(arguments: List<TypeExpressionNode<P>>, returnType: TypeExpressionNode<P>): FunctionTypeExpressionNode<P> = FunctionTypeExpressionNode(arguments, returnType)
fun <P : Phase> args(vararg types: TypeExpressionNode<P>): List<TypeExpressionNode<P>> = types.toList()

fun <K : Attributable, A> K.withAttribute(value: AttrVal<A>): K = apply { putAttributeValue(value) }
fun <K : Attributable, A> K.withAttribute(def: AttrDef<A>, value: A): K = withAttribute(SimpleAttrVal(def, value))
