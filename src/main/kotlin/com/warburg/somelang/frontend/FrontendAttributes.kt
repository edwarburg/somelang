package com.warburg.somelang.frontend

import com.warburg.somelang.ast.FileNode
import com.warburg.somelang.attributable.AttrDef
import java.nio.file.Path

/**
 * @author ewarburg
 */
object FilePathAttrDef : AttrDef<Path>
fun FileNode.getFilePath(): Path = getAttribute(FilePathAttrDef)!!

