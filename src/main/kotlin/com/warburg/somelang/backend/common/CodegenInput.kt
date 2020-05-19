package com.warburg.somelang.backend.common

import com.warburg.somelang.ast.FileNode
import com.warburg.somelang.backend.jvm.CodegenPrereqPhase
import com.warburg.somelang.middleend.TypeContext
import java.nio.file.Path

/**
 * @author ewarburg
 */
class CodegenInput(
    val filesToCompile: List<FileNode<CodegenPrereqPhase>>,
    val typeContext: TypeContext,
    val outPath: Path
)