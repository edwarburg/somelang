package com.warburg.somelang

import com.warburg.somelang.effects.Effect
import com.warburg.somelang.effects.perform
import com.warburg.somelang.effects.withEffects
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.DebugProbes.withDebugProbes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.time.Duration

lateinit var theScope: CoroutineScope
val defaultTimeout = Duration.ofSeconds(1)

/**
 * @author ewarburg
 */
class EffectsTest {
    @Test
    fun fancyEffectsManually() {
        val up = Channel<Int>()
        val down = Channel<Unit>()
        val channels = Channels(up, down)
        runBlocking {
            theScope = this
            doTheSendManually(channels)
            println("waiting to receive from ${Thread.currentThread()}")
            for (result in up) {
                println("got result: $result")
                if (result == 2) {
                    break
                }
                down.send(Unit)
            }
            down.close()
        }
    }

    private fun doTheSendManually(channels: Channels<Int, Unit>) {
        theScope.launch {
            for (i in 0..3) {
                println("doing something $i")
                println("sending something: $i")
                channels.up.send(i)
                val resumedWith = channels.down.receiveOrClosed()
                if (resumedWith.isClosed) {
//                    yield()
                    cancel()
                    yield()
                }
                println("resumed with: $resumedWith")
            }
            channels.up.close()
        }
    }

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
                handler(NoOp) { resume(Unit) }
                run {
                    42
                }
            }
            assertEquals(42, result)
        }
    }

    @Test
    fun `effects returning simple result with NoOp returning handler invoked once`() {
        withTimeout {
            val result = withEffects<Int> {
                handler(NoOp) { resume(Unit) }
                run {
                    perform(NoOp, Unit)
                    42
                }
            }
            assertEquals(42, result)
        }
    }

    @Test
    fun `effect which does not resume`() {
        withTimeout {
            val result = withEffects<Int> {
                handler(NoOp) {
                    fallthrough(1)
                }
                run {
                    perform(NoOp, Unit)
                    throw UnsupportedOperationException("unreachable")
                }
            }
            assertEquals(1, result)
        }
    }

    @Test
    fun `effect which resumes with value`() {
        withTimeout {
            val result = withEffects<Int> {
                handler(Add1) { n ->
                    resume(n + 1)
                }
                run {
                    perform(Add1, 41)
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
                handler(Add1) { n ->
                    if (list.isEmpty()) {
                        list.add(n)
                    }
                    val n1 = n + 1
                    list.add(n1)
                    resume(n1)
                }
                run {
                    var result = 0
                    while (result < 42) {
                        result = perform(Add1, result)
                    }
                    result
                }
            }
            assertEquals(42, result)
            assertEquals((0..42).toList(), list)
        }
    }
    
    @Test
    fun `finally in run() body never runs when fallthrough is called`() {
        withTimeout {
            val result = withEffects<Unit> {
                handler(NoOp) {
                    fallthrough(Unit)
                }
                run {
                    try {
                        perform(NoOp, Unit)
                    } finally {
                        fail("ran finally")
                    }
                }
            }
        }
    }

    @Test
    fun `user exceptions bubble up`() {
        withTimeout {
            var caught = false
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
            var caught = false
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
            var caught = false
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

    fun giveMeTheAnswer(): Int {
        return perform(GiveMeTheAnswer, Unit)
    }
    
    fun withTimeout(duration: Duration = defaultTimeout, block: () -> Unit) {
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

internal data class Channels<A, R>(val up: Channel<A>, val down: Channel<R>)
