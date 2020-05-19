package com.warburg.somelang.middleend

import com.warburg.somelang.ast.Node
import com.warburg.somelang.ast.withAttribute
import com.warburg.somelang.attributable.AttrDef
import com.warburg.somelang.attributable.Attributable
import com.warburg.somelang.common.NameResolvingPhase
import com.warburg.somelang.common.TypecheckingPhase
import com.warburg.somelang.id.FullyQualifiedName

/**
 * @author ewarburg
 */
private object DeclarationFqnAttrNode : AttrDef<FullyQualifiedName>
fun <P : NameResolvingPhase> Node<P>.getDeclarationFqn(): FullyQualifiedName = getAttribute(DeclarationFqnAttrNode)!!
fun <K : Attributable> K.withDeclFqn(fqn: FullyQualifiedName): K = withAttribute(DeclarationFqnAttrNode, fqn)

private object ReferentFqnAttrDef : AttrDef<FullyQualifiedName>
fun <P : NameResolvingPhase> Node<P>.getReferentFqn(): FullyQualifiedName = getAttribute(ReferentFqnAttrDef)!!
fun <K : Attributable> K.withReferentFqn(fqn: FullyQualifiedName): K = withAttribute(ReferentFqnAttrDef, fqn)

private object ExpressionTypeAttrDef : AttrDef<SomelangType>
fun <P : TypecheckingPhase> Node<P>.getExpressionType(): SomelangType = getAttribute(ExpressionTypeAttrDef)!!
fun <K : Attributable> K.withExpressionType(type: SomelangType): K = withAttribute(ExpressionTypeAttrDef, type)


