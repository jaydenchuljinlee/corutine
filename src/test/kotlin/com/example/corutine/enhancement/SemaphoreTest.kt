package com.example.corutine.enhancement

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.Semaphore

class SemaphoreTest {
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
        val semaphore = Semaphore(8)  // 한 번에 8개만 실행

        // 1. 무거운 작업 100개
        repeat(100) { index ->
            launch(Dispatchers.IO) {
                semaphore.acquire()  // 수동 acquire
                try {
                    val result = calculateSomethingHeavy()
                    println("[Heavy-$index] result=$result on ${Thread.currentThread().name}")
                } finally {
                    semaphore.release()  // 반드시 release 호출
                }
            }

        }


        // 2. Default Dispatcher 작업 10개
        repeat(10) {
            launch(Dispatchers.Default) {
                quickWork(it)
            }
        }
    }

}