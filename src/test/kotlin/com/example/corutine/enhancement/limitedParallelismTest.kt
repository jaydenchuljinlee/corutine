package com.example.corutine.enhancement

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class limitedParallelismTest {
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
        // Dispatchers.IO를 8개 동시 작업만 허용하도록 제한
        val limitedIO = Dispatchers.IO.limitedParallelism(8)

        repeat(1000) {
            launch(limitedIO) {
                val result = calculateSomethingHeavy()
                println("[Heavy-$it] result=$result on ${Thread.currentThread().name}")
            }
        }

        repeat(10) {
            launch(Dispatchers.Default) {
                quickWork(it)
            }
        }
    }



}