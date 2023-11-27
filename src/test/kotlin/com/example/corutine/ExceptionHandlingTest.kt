package com.example.corutine

import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.lang.IllegalStateException

@SpringBootTest
class ExceptionHandlingTest {
    var result = 0

    @Test
    fun `Exception이 발생하여 결과값 변경이 안 일어난다`() {

        runBlocking {
            val root = CoroutineScope(Dispatchers.Default + exceptionHandler()).launch {
                rootScope()
            }

            root.join()
            delay(1000)
        }

        assertTrue(result == 0)
    }

    @Test
    fun `Exception이 발생해도 결과 값은 변경 된다`() {
        runBlocking {
            val root = CoroutineScope(Dispatchers.Default).launch {
                supervisorScope()
            }

            root.join()
            delay(1000)

        }

        assertTrue(result == 1)
    }

    private fun exceptionHandler(): CoroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
        println("exception handled :  $exception")
    }

    private suspend fun rootScope() {
        coroutineScope {
            val failed = launch { childException() }
            val success = launch { childNormal() }

            joinAll(failed, success)
        }
    }

    private suspend fun supervisorScope() {
        supervisorScope {
            val childScope1 = launch(exceptionHandler()) { childException() }
            val childScope2 = launch { childNormal() }

            joinAll(childScope1, childScope2)
        }

    }

    private suspend fun childException() {
        delay(10)
        throw IllegalStateException("Not Supported yet")
    }

    private suspend fun childNormal() {
        delay(100)
        result = 1
    }
}