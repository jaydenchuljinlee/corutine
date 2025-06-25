package com.example.corutine.enhancement

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class SharedPoolDispatcherSaturationTest {
    fun calculateSomethingHeavy(): Long {
        var sum = 0L
        repeat(50_000_000) { sum += it }
        return sum
    }

    fun quickWork(id: Int) {
        println("[QuickWork-$id] on ${Thread.currentThread().name}")
    }

    @Test
    fun main() = runBlocking {
        // 1. Dispatchers.IO에서 무거운 작업 1000개
        repeat(1000) {
            launch(Dispatchers.IO) {
                val result = calculateSomethingHeavy()
                println("[Heavy-$it] result=$result on ${Thread.currentThread().name}")
            }
        }

        // 2. Dispatchers.Default에서 빠른 작업 10개
        repeat(10) {
            launch(Dispatchers.Default) {
                quickWork(it)
            }
        }
    }

}