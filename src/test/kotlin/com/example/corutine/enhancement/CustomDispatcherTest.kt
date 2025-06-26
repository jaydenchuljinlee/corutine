package com.example.corutine.enhancement

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class CustomDispatcherTest {
    fun calculateSomethingHeavy(): Long {
        var sum = 0L
        repeat(10_000_000) { sum += it }
        return sum
    }

    fun quickWork(id: Int) {
        println("[QuickWork-$id] on ${Thread.currentThread().name}")
    }

    @Test
    fun main() = runBlocking {
        val customDispatcher = Executors.newFixedThreadPool(16).asCoroutineDispatcher()

        // 1. 무거운 작업 100개 (커스텀 Dispatcher에서 실행)
        repeat(100) {
            launch(customDispatcher) {
                val result = calculateSomethingHeavy()
                println("[Heavy-$it] result=$result on ${Thread.currentThread().name}")
            }
        }

        // 2. Default Dispatcher 작업 10개
        repeat(10) {
            launch(Dispatchers.Default) {
                quickWork(it)
            }
        }

        delay(5000)  // wait for all
        customDispatcher.close()  // 반드시 close!
    }

}