package com.warburg.somelang.middleend

import com.warburg.somelang.ast.Node
import com.warburg.somelang.attributable.AttrDef
import com.warburg.somelang.id.FullyQualifiedName

/**
 * @author ewarburg
 */
object DeclarationFqnAttrNode : AttrDef<FullyQualifiedName>
fun Node.getDeclarationFqn(): FullyQualifiedName = getAttribute(DeclarationFqnAttrNode)!!

object ReferentFqnAttrDef : AttrDef<FullyQualifiedName>
fun Node.getReferentFqn(): FullyQualifiedName = getAttribute(ReferentFqnAttrDef)!!

object ExpressionTypeAttrDef : AttrDef<SomelangType>
fun Node.getExpressionType(): SomelangType = getAttribute(ExpressionTypeAttrDef)!!