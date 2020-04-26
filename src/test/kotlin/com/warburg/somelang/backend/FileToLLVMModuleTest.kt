package com.warburg.somelang.backend

import com.warburg.somelang.ast.*
import io.kotlintest.specs.FunSpec

/**
 * @author ewarburg
 */
class FileToLLVMModuleTest : FunSpec({
    context("functions") {
        test("return int from main") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        name = IdentifierNode("main")
                        body = ReturnNode(IntLiteralNode(123))
                    }
                }
            }
            val result = convertToLLVM(listOf(node))
            println(result)
        }
        test("return int from main with variable") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        name = IdentifierNode("main")
                        body = block {
                            statements {
                                +LocalVarDeclarationNode(IdentifierNode("a"), IntLiteralNode(123))
                                +ReturnNode(IdentifierNode("a"))
                            }
                        }
                    }
                }
            }
            val result = convertToLLVM(listOf(node))
            println(result)
        }
        test("return int from main with math") {
            val node = file {
                nodes {
                    +functionDeclaration {
                        name = IdentifierNode("main")
                        body = block {
                            statements {
                                +LocalVarDeclarationNode(IdentifierNode("a"), IntLiteralNode(123))
                                +LocalVarDeclarationNode(IdentifierNode("b"), IntLiteralNode(456))
                                +LocalVarDeclarationNode(IdentifierNode("c"), IntLiteralNode(789))
                                +ReturnNode(BinaryOperationNode(BinaryOperator.ADD, IdentifierNode("a"), BinaryOperationNode(BinaryOperator.ADD, IdentifierNode("b"), IdentifierNode("c"))))
                            }
                        }
                    }
                }
            }
            val result = convertToLLVM(listOf(node))
            println(result)
        }
    }
})


