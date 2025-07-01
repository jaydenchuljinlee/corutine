package com.example.corutine.enhancement

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class SharedPoolDispatcherSaturationTest {
    private fun blockingIO(index: Int): Long {
        Thread.sleep(500) // 블로킹 작업
        println("[Heavy-$index] IO on ${Thread.currentThread().name}")
        return 42
    }

    private fun quickDefault(index: Int) {
        println("[Quick-$index] START on ${Thread.currentThread().name}")
        Thread.sleep(50)
        println("[Quick-$index] END on ${Thread.currentThread().name}")
    }


    @Test
    fun main() = runBlocking {
        // 🔸 1. IO 작업 2000개: sharedPool 포화 유도
        repeat(2000) { index ->
            launch(Dispatchers.IO) {
                blockingIO(index)
            }
        }

        delay(100) // IO 먼저 시작되도록 살짝 지연

        // 🔸 2. Default Dispatcher에서 빠른 작업 20개
        val time = measureTimeMillis {
            repeat(20) { index ->
                launch(Dispatchers.Default) {
                    quickDefault(index)
                }
            }
        }

        println("⏱️ Total Quick Work Duration: ${time}ms")
    }

}