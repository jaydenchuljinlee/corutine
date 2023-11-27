package com.example.corutine

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ChannelTest {
    @Test
    fun `RENDEZVOUS 하나씩 교차하면서 생산과 소비를 한다`() {
        runBlocking {
            val channel = Channel<Int>()

            CoroutineScope(Dispatchers.Default).launch {
                repeat(10) {
                    channel.send(it)
                    println("$channel send $it")
                    delay(10)
                }

                channel.close()
            }
            delay(500)
            consumeAll(channel)
        }
    }

    @Test
    fun `UNLIMITED 버퍼가 무한한 채널`() {

        runBlocking {
            val channel = Channel<Int>(Channel.UNLIMITED)

            CoroutineScope(Dispatchers.Default).launch {
                repeat(10) {
                    channel.send(it)
                    println("$channel send $it")
                    delay(10)
                }
                channel.close()
            }
            delay(500)
            consumeAll(channel)
        }
    }

    @Test
    fun `BUFFERED 크기가 고정된 채널`() {

        runBlocking {
            val channel = Channel<Int>(5)

            CoroutineScope(Dispatchers.Default).launch {
                repeat(10) {
                    channel.send(it)
                    println("$channel send $it")
                    delay(10)
                }
                channel.close()
            }
            delay(500)
            consumeAll(channel)
        }
    }

    @Test
    fun `CONFLATED 크기는 하나 생산된 데이터를 덮어쓰는 채널`() {

        runBlocking {
            val channel = Channel<Int>(Channel.CONFLATED)

            CoroutineScope(Dispatchers.Default).launch {
                repeat(10) {
                    channel.send(it)
                    println("$channel send $it")
                    delay(10)
                }
                channel.close()
            }
            delay(500)
            consumeAll(channel)
        }
    }

    private suspend fun <E> consumeAll(channel: Channel<E>) {
        channel.consumeEach { data -> println("[$channel] consume data: $data") }
    }
}