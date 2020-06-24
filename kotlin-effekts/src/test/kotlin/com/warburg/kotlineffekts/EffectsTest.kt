package com.warburg.kotlineffekts

import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.DebugProbes.withDebugProbes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*

val defaultTimeout = Duration.ofSeconds(1)

/**
 * @author ewarburg
 */
class EffectsTest {
    @Test
    fun `effects returning simple result`() {
        withTimeout {
            val result = withEffects<Int> {
                run {
                    42
                }
            }
            assertEquals(42, result)
        }
    }

    @Test
    fun `effects returning simple result with NoOp returning handler never invoked`() {
        withTimeout {
            val result = withEffects<Int> {
                run {
                    42
                }
                handler(NoOp) {
                    resume(Unit)
                }
            }
            assertEquals(42, result)
        }
    }

    @Test
    fun `effects returning simple result with NoOp returning handler invoked once`() {
        withTimeout {
            val result = withEffects<Int> {
                run {
                    perform(NoOp, Unit)
                    42
                }
                handler(NoOp) {
                    resume(Unit)
                }
            }
            assertEquals(42, result)
        }
    }

    @Test
    fun `effect which does not resume`() {
        withTimeout {
            val result = withEffects<Int> {
                run {
                    perform(NoOp, Unit)
                    throw UnsupportedOperationException("unreachable")
                }
                handler(NoOp) {
                    fallthrough(1)
                }
            }
            assertEquals(1, result)
        }
    }

    @Test
    fun `effect which resumes with value`() {
        withTimeout {
            val result = withEffects<Int> {
                run {
                    perform(Add1, 41)
                }
                handler(Add1) { n ->
                    resume(n + 1)
                }
            }
            assertEquals(42, result)
        }
    }
    @Test
    fun `effect which resumes multiple times`() {
        withTimeout {
            val list = mutableListOf<Int>()
            val result = withEffects<Int> {
                run {
                    var result = 0
                    while (result < 42) {
                        result = perform(Add1, result)
                    }
                    result
                }
                handler(Add1) { n ->
                    if (list.isEmpty()) {
                        list.add(n)
                    }
                    val n1 = n + 1
                    list.add(n1)
                    resume(n1)
                }
            }
            assertEquals(42, result)
            assertEquals((0..42).toList(), list)
        }
    }
    
    @Test
    fun `finally in run() body never runs when fallthrough is called`() {
        withTimeout {
            withEffects<Unit> {
                run {
                    try {
                        perform(NoOp, Unit)
                    } finally {
                        fail("ran finally")
                    }
                }
                handler(NoOp) {
                    fallthrough(Unit)
                }
            }
        }
    }

    @Test
    fun `user exceptions bubble up`() {
        withTimeout {
            val caught: Boolean
            try {
                withEffects<Unit> {
                    handler(NoOp) {
                        resume(Unit)
                    }
                    run {
                        throw UserException()
                    }
                }
                fail("did not throw exception")
            } catch (e: UserException) {
                caught = true
            }

            assertTrue(caught, "user exception was not caught")
        }
    }

    @Test
    fun `handler exceptions bubble up when uncaught by user`() {
        withTimeout {
            val caught: Boolean
            try {
                withEffects<Unit> {
                    handler(NoOp) {
                        throw HandlerException()
                    }
                    run {
                        perform(NoOp, Unit)
                    }
                }
                fail("did not throw exception")
            } catch (e: HandlerException) {
                caught = true
            }

            assertTrue(caught, "handler exception was not caught")
        }
    }

    @Test
    fun `exceptions thrown in handler cannot be caught by user code`() {
        withTimeout {
            val caught: Boolean
            try {
                withEffects<Unit> {
                    handler(NoOp) {
                        throw HandlerException()
                    }
                    run {
                        try {
                            perform(NoOp, Unit)
                        } catch (e: HandlerException) {
                            fail("user code caught handler exception")
                        }
                    }
                }
                fail("did not throw exception")
            } catch (e: HandlerException) {
                caught = true
            }

            assertTrue(caught, "handler exception was not caught")
        } 
    }

    @Test
    fun `handler can cause user code to fail with exception`() {
        withTimeout {
            var caught = false
            try {
                withEffects<Unit> {
                    handler(NoOp) {
                        resumeWithException(HandlerException())
                    }
                    run {
                        try {
                            perform(NoOp, Unit)
                        } catch (e: HandlerException) {
                            caught = true
                        }
                    }
                }
            } catch (e: HandlerException) {
                fail("Exception bubbled up out of `withEffects`")
            }

            assertTrue(caught, "handler exception was not caught in user code")
        }
    }

    @Test
    fun `nested user code can perform effects`() {
        withTimeout {
            val result = withEffects<Int> {
                handler(GiveMeTheAnswer) {
                    resume(42)
                }
                run {
                    giveMeTheAnswer()
                }
            }

            assertEquals(42, result)
        }
    }

    @Test
    fun `simple state effect`() {
        withTimeout {
            val result = withEffects<Int> {
                run {
                    countTo10()
                }

                var theState = 0
                handler(GetIntStateEffect) {
                    resume(theState)
                }
                handler(PutIntStateEffect) { newState ->
                    theState = newState
                    resume(Unit)
                }
            }
            assertEquals(10, result)
        }
    }

    fun countTo10(): Int {
        while (perform(GetIntStateEffect, Unit) < 10) {
            perform(PutIntStateEffect, perform(GetIntStateEffect, Unit) + 1)
        }

        return perform(GetIntStateEffect, Unit)
    }

    object GetIntStateEffect : Effect<Unit, Int>
    object PutIntStateEffect : Effect<Int, Unit>

    @Test
    fun `logging state effect`() {
        withTimeout {
            val states = ArrayDeque<Int>()
            val result = withEffects<Int> {
                run {
                    countTo10()
                }

                states.push(0)
                handler(GetIntStateEffect) {
                    resume(states.peek())
                }
                handler(PutIntStateEffect) { newState ->
                    states.push(newState)
                    resume(Unit)
                }
            }
            assertEquals(10, result)
            assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), states.toList().reversed())
        }
    }

    @Test
    fun `handle errors by resuming`() {
        withTimeout {
            val result = withEffects<Int> {
                run {
                    doSomethingThatFails()
                }

                handler(Error) { e ->
                    println("some error occurred with $e, oh well")
                    resume(e)
                }
            }

            assertEquals(10, result)
        }
    }

    object Error : Effect<Any, Any>

    fun doSomethingThatFails(): Int {
        var last = 0
        for (i in 0..10) {
            last = if (i > 7) {
                perform(Error, i) as Int
            } else {
                i
            }
        }

        return last
    }

    @Test
    fun `handle errors by resuming with modified value`() {
        withTimeout {
            val result = withEffects<Int> {
                run {
                    doSomethingThatFails()
                }

                handler(Error) { e ->
                    resume((e as Int) + 10)
                }
            }

            assertEquals(20, result)
        }
    }

    @Test
    fun `handle errors by aborting`() {
        withTimeout {
            val result = withEffects<Int> {
                run {
                    doSomethingThatFails()
                }

                handler(Error) { e ->
                    fallthrough(42)
                }
            }

            assertEquals(42, result)
        }
    }

    @Test
    fun pipeline() {
        withTimeout {
            val input = listOf(1, 2, 3)
            val output = mutableListOf<Int>()
            pipe(
                {
                    for (i in input) {
                        println("sending $i")
                        perform(Send, i)
                    }
                },
                {
                    val i = perform(Receive, Unit) as Int
                    println("receiving $i")
                    output.add(i)
                }
            )
            assertEquals(listOf(1, 2, 3), output)
        }
    }

    fun pipe(sender: () -> Unit, receiver: () -> Unit) {
        withEffects<Unit> {
            run {
                sender()
            }

            handler(Send) { message: Any ->
                resume(withEffects {
                    run {
                        receiver()
                    }

                    handler(Receive) {
                        resume(message)
                    }
                })
            }
        }
    }

    object Send : Effect<Any, Unit>
    object Receive : Effect<Unit, Any>

    fun giveMeTheAnswer(): Int {
        return perform(GiveMeTheAnswer, Unit)
    }

    object Get: Effect<Unit, Any> {}
    object Put: Effect<Any, Unit> {}

    @Test
    fun `state test`() {
        val actual = indexed(listOf("a", "b", "c"))
        assertEquals(listOf(0 to "a", 1 to "b", 2 to "c"), actual)
    }

    private fun <S : Any, T> state(initial: S, comp: () -> T): T {
        return withEffects {
            var cell = initial
            run {
                comp()
            }
            handler(Get) {
                resume(cell)
            }
            handler(Put) {
                cell = it as S
                resume(Unit)
            }
        }
    }

    private fun next(): Int {
        val last = perform(Get, Unit) as Int
        perform(Put, last + 1)
        return last
    }

    fun <A> indexed(list: List<A>): List<Pair<Int, A>> {
        return state(0) { list.map { next() to it } }
    }
    
    private fun withTimeout(duration: Duration = defaultTimeout, block: () -> Unit) {
        withDebugProbes {
            assertTimeoutPreemptively(duration, block) {
                val baos = ByteArrayOutputStream()
                val strStream = PrintStream(baos, true, StandardCharsets.UTF_8.name())
                DebugProbes.dumpCoroutines(strStream)
                baos.toString(StandardCharsets.UTF_8)
            }
        }
    }
}

class UserException : RuntimeException()
class HandlerException : RuntimeException()

object NoOp : Effect<Unit, Unit>
object Add1 : Effect<Int, Int>
object GiveMeTheAnswer : Effect<Unit, Int>
