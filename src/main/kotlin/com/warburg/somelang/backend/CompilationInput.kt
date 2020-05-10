package com.warburg.somelang.backend

import com.warburg.somelang.ast.FileNode
import com.warburg.somelang.middleend.IdResolver
import com.warburg.somelang.middleend.TypeContext
import java.nio.file.Path

/**
 * @author ewarburg
 */
class CompilationInput(val filesToCompile: List<FileNode>, val typeContext: TypeContext, val idResolver: IdResolver, val outPath: Path)