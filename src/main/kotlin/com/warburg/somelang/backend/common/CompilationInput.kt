package com.warburg.somelang.backend.common

import com.warburg.somelang.ast.FileNode
import com.warburg.somelang.middleend.TypeContext
import java.nio.file.Path

/**
 * @author ewarburg
 */
class CompilationInput(
    val filesToCompile: List<FileNode>,
    val typeContext: TypeContext,
    val outPath: Path
)