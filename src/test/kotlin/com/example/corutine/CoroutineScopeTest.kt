package com.example.corutine

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CoroutineScopeTest {
    @Test
    fun `Non-Blocking한 GlobalScope는 메시지를 못 찍는다`() {

        GlobalScope.launch {
            delay(1000L)
            println("This is not accessible") // cannot not access
        }

    }

    @Test
    fun `Non-Blocking한 GlobalScope에 지연이 생기면 메시지를 찍는다`() {
        GlobalScope.launch {
            delay(1000L)
            println("This is accessible")
        }

        Thread.sleep(1000L)
    }

    @Test
    fun `join을 이용하여 coroutine scope를 지정한다`() {
        runBlocking {
            val job = GlobalScope.launch {
                delay(1000L)
                println("This is accessible")
            }
            println("This is accessible, too")
            job.join()
        }
    }

    @Test
    fun `coroutine 블록 안에서 빌더를 통해 명시적 join을 사용한다`() {
        runBlocking {
            launch {
                delay(1000L)
                println("This is accessible")
            }
            println("This is accessible, too")
        }
    }

    @Test
    fun `runBlocking Scope는 현재 Thread를 멈춘다`() {
        runBlocking {
            launch {
                delay(500L)
                println("This Thread is waiting from the child node to finish ")
            }

            runBlocking {
                launch {
                    delay(200L)
                    println("This Child Node is Second Job")
                }

                delay(100L)
                println("This Child Node is First Job")
            }
        }
    }

    @Test
    fun `coroutine Scope는 Thread를 멈추지 않는다`() {
        runBlocking {
            launch {
                delay(200L)
                println("This Thread isn't waiting from the child node to finish")
            }

            coroutineScope {
                launch {
                    delay(500L)
                    println("This Child Node is Second Job")
                }

                delay(100L)
                println("This Child Node is First Job")
            }
        }
    }
}