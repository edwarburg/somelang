package com.warburg.somelang.frontend

import com.warburg.somelang.ast.FileNode
import com.warburg.somelang.ast.withAttribute
import com.warburg.somelang.attributable.AttrDef
import com.warburg.somelang.attributable.Attributable
import com.warburg.somelang.common.LexingPhase
import java.nio.file.Path

/**
 * @author ewarburg
 */
private object FilePathAttrDef : AttrDef<Path>
fun <P : LexingPhase> FileNode<P>.getFilePath(): Path = getAttribute(FilePathAttrDef)!!
fun <K : Attributable> K.withFilePath(path: Path): K = withAttribute(FilePathAttrDef, path)
