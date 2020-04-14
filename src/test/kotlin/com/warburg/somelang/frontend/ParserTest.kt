package com.warburg.somelang.frontend

import io.kotlintest.specs.FunSpec

/**
 * @author ewarburg
 */
class ParserTest : FunSpec({
    context("func decl") {
        test("can parse func decl") {
            val input = "fun hello() { 123 }"
            val result = input.parse()
            println(result)
        }
    }
})