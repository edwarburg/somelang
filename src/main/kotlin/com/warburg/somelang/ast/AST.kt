package com.warburg.somelang.ast

import com.autodsl.annotation.AutoDsl
import com.warburg.somelang.attributable.AttrDef
import com.warburg.somelang.attributable.AttrVal
import com.warburg.somelang.attributable.Attributable
import com.warburg.somelang.id.Name


/**
 * @author ewarburg
 */
sealed class Node : Attributable {
    private val attributesByDef: MutableMap<AttrDef<*>, AttrVal<*>> = mutableMapOf()
    override val attributes: Collection<AttrVal<*>>
        get() = this.attributesByDef.values

    override fun <A> getAttributeValue(def: AttrDef<A>): AttrVal<A>? = this.attributesByDef[def] as? AttrVal<A>?

    override fun <A> putAttributeValue(value: AttrVal<A>) {
        this.attributesByDef[value.def] = value
    }
}

abstract class TypeExpressionNode : Node()
@AutoDsl(dslName = "typeConstructorInvoke")
class TypeConstructorInvocationNode(val target: IdentifierNode, val arguments: List<TypeExpressionNode>) : TypeExpressionNode()
@AutoDsl(dslName = "typeName")
class TypeNameNode(val nameNode: IdentifierNode) : TypeExpressionNode()
@AutoDsl(dslName = "functionType")
class FunctionTypeExpressionNode(val argumentTypes: List<TypeExpressionNode>, val returnType: TypeExpressionNode) : TypeExpressionNode()


@AutoDsl(dslName = "file")
data class FileNode(val nodes: List<Node>) : Node()

@AutoDsl(dslName = "functionDeclaration")
data class FunctionDeclarationNode(val nameNode: IdentifierNode, val body: Node, val parameters: List<ParameterDeclarationNode> = emptyList(), val returnType: TypeExpressionNode? = null) : Node()
sealed class ParameterDeclarationNode : Node()
data class NormalParameterDeclarationNode(val nameNode: IdentifierNode, val type: TypeExpressionNode? = null) : ParameterDeclarationNode()

@AutoDsl(dslName = "block")
data class BlockNode(val statements: List<Node>) : Node()

@AutoDsl(dslName = "ret")
data class ReturnNode(val value: Node) : Node()

@AutoDsl(dslName = "localVarDeclaration")
data class LocalVarDeclarationNode(val nameNode: IdentifierNode, val rhs: Node, val type: TypeExpressionNode? = null) : Node()

@AutoDsl(dslName = "readLocalVar")
data class ReadLocalVarNode(val target: IdentifierNode) : Node()

@AutoDsl(dslName = "identifier")
data class IdentifierNode(val name: Name) : Node()

@AutoDsl(dslName = "intLiteral")
data class IntLiteralNode(val value: Int) : Node()
@AutoDsl(dslName = "stringLiteral")
data class StringLiteralNode(val value: String) : Node()

@AutoDsl(dslName = "binaryOperator")
data class BinaryOperationNode(val operator: BinaryOperator, val lhs: Node, val rhs: Node) : Node()

enum class BinaryOperator {
    ADD
}

@AutoDsl(dslName = "invoke")
data class InvokeNode(val target: IdentifierNode, val arguments: List<ArgumentNode> = emptyList()) : Node()

@AutoDsl(dslName = "getStaticValue")
data class GetStaticValueNode(val target: IdentifierNode) : Node()

@AutoDsl(dslName = "readField")
data class ReadFieldValueNode(val receiver: Node, val field: IdentifierNode) : Node()

sealed class ArgumentNode : Node()
data class PositionalArgumentNode(val value: Node) : ArgumentNode()

object NoOpNode : Node()
