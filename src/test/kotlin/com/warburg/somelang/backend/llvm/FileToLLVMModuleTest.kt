package com.warburg.somelang.backend.llvm

import com.warburg.somelang.ast.*
import com.warburg.somelang.backend.llvm.convertToLLVM
import org.junit.jupiter.api.Test

/**
 * @author ewarburg
 */
class FileToLLVMModuleTest {
    @Test
    fun `return int from main`() {
        val node = file {
            nodes {
                +functionDeclaration {
                    nameNode = id(fqn("main"))
                    body = ReturnNode(IntLiteralNode(123))
                }
            }
        }
        val result = convertToLLVM(listOf(node))
        println(result)
    }

    @Test
    fun `return int from main with variable`() {
        val node = file {
            nodes {
                +functionDeclaration {
                    nameNode = id(fqn("main"))
                    body = block {
                        statements {
                            +LocalVarDeclarationNode(id(fqn("a")), IntLiteralNode(123))
                            +ReturnNode(id(fqn("a")))
                        }
                    }
                }
            }
        }
        val result = convertToLLVM(listOf(node))
        println(result)
    }

    @Test
    fun `return int from main with math`() {
        val node = file {
            nodes {
                +functionDeclaration {
                    nameNode = id(fqn("main"))
                    body = block {
                        statements {
                            +LocalVarDeclarationNode(id(fqn("a")), IntLiteralNode(123))
                            +LocalVarDeclarationNode(id(fqn("b")), IntLiteralNode(456))
                            +LocalVarDeclarationNode(id(fqn("c")), IntLiteralNode(789))
                            +ReturnNode(
                                BinaryOperationNode(
                                    BinaryOperator.ADD,
                                    id(fqn("a")),
                                    BinaryOperationNode(BinaryOperator.ADD, readLocal("b"), readLocal("c"))
                                )
                            )
                        }
                    }
                }
            }
        }
        val result = convertToLLVM(listOf(node))
        println(result)
    }

    @Test
    fun `no args data constructor`() {
        val node = file {
            nodes {
                +dataDecl {
                    typeConstDecl {
                        nameNode = id("Foo")
                    }
                    valueConstructorDeclarations {
                        +valueConstDecl {
                            nameNode = id("Bar")
                        }
                    }
                }
            }
        }
    }
}


