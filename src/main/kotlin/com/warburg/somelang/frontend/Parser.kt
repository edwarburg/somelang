package com.warburg.somelang.frontend

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.parser.Parser
import com.warburg.somelang.ast.*

/**
 * @author ewarburg
 */
private val grammar = object : Grammar<File>() {
    val fun_kw by token("fun")
    val let_kw by token("let")
    val return_kw by token("return")
    val ident_tok by token("[_a-zA-Z][_a-zA-Z0-9]*")
    val lParen by token("\\(")
    val rParen by token("\\)")
    val lCurly by token("\\{")
    val rCurly by token("\\}")
    val equals by token("=")
    val plus by token("\\+")
    val intLit_tok by token("[1-9][0-9]*")
    val ws by token("\\s+", ignore = true)

    val ident by ident_tok map { Identifier(it.text) }
    val intLit by intLit_tok map { IntLiteral(it.text.toInt()) }

    val let_stmt by -let_kw * ident * -equals * parser { expr } map { (lhs, rhs) -> LocalVarDeclaration(lhs, rhs) }
    val return_stmt by -return_kw * parser<Node> { expr } map { Return(it) }
    val statement: Parser<Node> by let_stmt or return_stmt

    val block: Parser<Block> by oneOrMore(statement) map { Block(it) }

    val operator: Parser<BinaryOperator> by plus map { BinaryOperator.ADD }
    val binaryOp: Parser<BinaryOperation> by operator * parser { expr } * parser { expr } map { (operator, lhs, rhs) -> BinaryOperation(operator, lhs, rhs) }

    val expr: Parser<Node> by intLit or ident or binaryOp or block

    val funcDecl: Parser<FunctionDeclaration> by (-fun_kw * ident * -lParen * -rParen * -lCurly * expr * -rCurly) map { (ident, expr) -> FunctionDeclaration(ident, expr) }

    override val rootParser: Parser<File> = oneOrMore(funcDecl) map { File(it) }
}

fun String.parse(): File = grammar.parseToEnd(this)