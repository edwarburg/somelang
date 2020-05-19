package com.warburg.somelang.ast

import com.warburg.somelang.attributable.AttrDef
import com.warburg.somelang.attributable.AttrVal
import com.warburg.somelang.attributable.Attributable
import com.warburg.somelang.common.Phase
import com.warburg.somelang.id.Name

/**
 * @param P denotes the phases that this Node has passed through. Passing through a phase usually adds some sort of attribute
 *          onto the node, and by making extension functions to read attributes off Node<Foo>, you can restrict those
 *          extension functions to only Nodes that have already passed through phase Foo.
 * @author ewarburg
 */
sealed class Node<P : Phase> : Attributable {
    private val attributesByDef: MutableMap<AttrDef<*>, AttrVal<*>> = mutableMapOf()
    override val attributes: Collection<AttrVal<*>>
        get() = this.attributesByDef.values

    override fun <A> getAttributeValue(def: AttrDef<A>): AttrVal<A>? = this.attributesByDef[def] as? AttrVal<A>?

    override fun <A> putAttributeValue(value: AttrVal<A>) {
        this.attributesByDef[value.def] = value
    }
}

abstract class TypeExpressionNode<P : Phase> : Node<P>()

class TypeConstructorInvocationNode<P : Phase>(val target: IdentifierNode<P>, val arguments: List<TypeExpressionNode<P>> = emptyList()) : TypeExpressionNode<P>()

class TypeNameNode<P : Phase>(val nameNode: IdentifierNode<P>) : TypeExpressionNode<P>()

class FunctionTypeExpressionNode<P : Phase>(val argumentTypes: List<TypeExpressionNode<P>> = emptyList(), val returnType: TypeExpressionNode<P>) : TypeExpressionNode<P>()

data class FileNode<P : Phase>(val nodes: List<Node<P>>) : Node<P>()

data class FunctionDeclarationNode<P : Phase>(val nameNode: IdentifierNode<P>, val body: Node<P>, val parameters: List<ParameterDeclarationNode<P>> = emptyList(), val returnType: TypeExpressionNode<P>? = null) : Node<P>()
sealed class ParameterDeclarationNode<P : Phase> : Node<P>()
data class NormalParameterDeclarationNode<P : Phase>(val nameNode: IdentifierNode<P>, val type: TypeExpressionNode<P>? = null) : ParameterDeclarationNode<P>()

data class BlockNode<P : Phase>(val statements: List<Node<P>> = emptyList()) : Node<P>()

data class ReturnNode<P : Phase>(val value: Node<P>) : Node<P>()

data class LocalVarDeclarationNode<P : Phase>(val nameNode: IdentifierNode<P>, val rhs: Node<P>, val type: TypeExpressionNode<P>? = null) : Node<P>()

data class ReadLocalVarNode<P : Phase>(val target: IdentifierNode<P>) : Node<P>()

data class IdentifierNode<P : Phase>(val name: Name) : Node<P>()

data class IntLiteralNode<P : Phase>(val value: Int) : Node<P>()

data class StringLiteralNode<P : Phase>(val value: String) : Node<P>()

data class BinaryOperationNode<P : Phase>(val operator: BinaryOperator, val lhs: Node<P>, val rhs: Node<P>) : Node<P>()

enum class BinaryOperator {
    ADD
}

data class InvokeNode<P : Phase>(val target: IdentifierNode<P>, val arguments: List<ArgumentNode<P>> = emptyList()) : Node<P>()

data class GetStaticValueNode<P : Phase>(val target: IdentifierNode<P>) : Node<P>()

data class ReadFieldValueNode<P : Phase>(val receiver: Node<P>, val field: IdentifierNode<P>) : Node<P>()

sealed class ArgumentNode<P : Phase> : Node<P>()
data class PositionalArgumentNode<P : Phase>(val value: Node<P>) : ArgumentNode<P>()

data class DataDeclarationNode<P : Phase>(val typeConstructorDeclaration: TypeConstructorDeclarationNode<P>, val valueConstructorDeclarations: List<ValueConstructorDeclarationNode<P>> = emptyList()) : Node<P>()

data class TypeConstructorDeclarationNode<P : Phase>(val nameNode: IdentifierNode<P>, val parameters: List<IdentifierNode<P>> = emptyList()) : Node<P>()

data class ValueConstructorDeclarationNode<P : Phase>(val nameNode: IdentifierNode<P>, val parameters: List<TypeExpressionNode<P>> = emptyList()) : Node<P>()

data class ValueConstructorInvocationNode<P : Phase>(val target: IdentifierNode<P>) : Node<P>()

// NoOp is not an object because each noop might carry its own set of attributes
class NoOpNode<P : Phase> : Node<P>()
