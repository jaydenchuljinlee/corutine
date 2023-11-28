package com.example.corutine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

// @SpringBootTest
class ConcurrencyTest {
    @Test
    fun `Thread의 활성화 갯수 테스트`() {
        runBlocking {
            val time = measureTimeMillis {
                val jobs = mutableListOf<Thread>()
                repeat(100_000) {
                    jobs += Thread {
                        Thread.sleep(1000)
                    }.also { it.start() }
                }
                println("활성화된 Thread Count: ${Thread.activeCount()}")
                jobs.forEach { it.join() }
            }
            println("걸린 시간 $time")
        }
    }

    @Test
    fun `Coroutine 활성화 갯수 테스트`() {
        runBlocking {
            val time = measureTimeMillis {
                val jobs = mutableListOf<Job>()
                repeat(100_000) {
                    jobs += launch{ delay(1000) }
                }
                println("활성화된 Thread Count: ${Thread.activeCount()}")
                jobs.forEach { it.join() }
            }
            println("걸린 시간 $time")
        }

    }

    @Test
    fun `먼저 끝난 b coroutine이 변수에 담긴다`() {
        var a_or_b = ""
        runBlocking {
            launch {
                delay(100)
                if (a_or_b == "") a_or_b = "a"
            }

            launch {
                delay(50)
                if (a_or_b == "") a_or_b = "b"
            }
        }

        assertEquals(a_or_b, "b")
    }

    @Test
    fun `Flow의 Producer와 Consumer는 순차적으로 동작한다`() {
        runBlocking(Dispatchers.Default) {
            var produceNo = 0
            var consumerNo = 0
            flow { // producer
                repeat(times = 3) { data ->
                    delay(300L)
                    produceNo = data
                    emit(data)
                }

            }.collect { data -> // consumer
                delay(700L)
                consumerNo = data

                assertEquals(produceNo, consumerNo)
            }
        }

    }

    @Test
    fun `딜레이 시간만큼의 consume이 누락된다`() {
        val list = mutableListOf<Int>()
        runBlocking(Dispatchers.Default) {
            val flowStartTime = AtomicLong(0L)

            flow {
                repeat(times = 7) { data ->
                    delay(100L)
                    emit(data)
                }
            }.onStart {
                flowStartTime.set(System.currentTimeMillis())
            }.conflate().collect() { data ->
                delay(700L)
                list.add(data)
            }
        }
        assertTrue(list.size == 2)
    }
}