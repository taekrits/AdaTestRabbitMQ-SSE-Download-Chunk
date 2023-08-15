package com.example.adatestrabbitmq

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch

class C01MainViewModel : ViewModel() {

    var atC_MessageLiveDataRabbitMQ by mutableStateOf("0")
    var atC_MessageLiveDataSSE by mutableStateOf(" ")
    lateinit var oC_eventSource: EventSource


    fun C_GETxRabbitMQConnect() {
        viewModelScope.launch() {
            withContext(Dispatchers.IO) {
                try {
                    val oFactory = ConnectionFactory().apply {
                        this.host = "10.0.2.2"
                    }

                    val oConnection = oFactory.newConnection()
                    val nQueue = 500000

                    if (oConnection.isOpen) {
                        println("Connected successfully!")

                        val oChannel = oConnection.createChannel()
                        oChannel.queueDeclare("testQueue", false, false, false, null)

                        val oLatch = CountDownLatch(nQueue)

                        val oConsumer = object : DefaultConsumer(oChannel) {
                            override fun handleDelivery(
                                consumerTag: String?,
                                envelope: Envelope?,
                                properties: AMQP.BasicProperties?,
                                body: ByteArray?
                            ) {
                                val tMessage = body?.toString(Charset.defaultCharset())

                                if (tMessage != null) {
                                    atC_MessageLiveDataRabbitMQ = tMessage
                                }


                                oLatch.countDown() // ลด latch ที่ละ 1 เมื่อ consumer ทำงานเสร็จ
                            }
                        }
                        oChannel.basicConsume("testQueue", true, oConsumer)

                        for (i in 1..nQueue) {
                            val tMessage = "$i"
                            oChannel.basicPublish(
                                "",
                                "testQueue",
                                null,
                                tMessage.toByteArray(Charset.defaultCharset())
                            )
                        }

                        oLatch.await()

                        oChannel.close()
                        oConnection.close()

                    } else {
                        println("Failed to connect!")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun C_GETxRabbitMQConnectExchange() {
        viewModelScope.launch() {
            withContext(Dispatchers.IO) {
                try {
                    val oFactory = ConnectionFactory().apply {
                        this.host = "10.0.2.2"
                    }

                    val oConnection = oFactory.newConnection()

                    if (oConnection.isOpen) {
                        println("Connected successfully!")

                        val oChannel = oConnection.createChannel()


                        val tExchangeName = "multiQueueExchange"
                        oChannel.exchangeDeclare(tExchangeName, "direct")


                        val atQueueNames = listOf("queue1", "queue2", "queue3")
                        for (queueName in atQueueNames) {
                            oChannel.queueDeclare(queueName, false, false, false, null)
                            oChannel.queueBind(queueName, tExchangeName, "commonRouteKey")
                        }


                        val tMessage = "Hello from Android!"
                        oChannel.basicPublish(
                            tExchangeName,
                            "commonRouteKey",
                            null,
                            tMessage.toByteArray(Charset.defaultCharset())
                        )
                        val oLatch = CountDownLatch(atQueueNames.size)

                        val oConsumer = object : DefaultConsumer(oChannel) {
                            override fun handleDelivery(
                                consumerTag: String?,
                                envelope: Envelope?,
                                properties: AMQP.BasicProperties?,
                                body: ByteArray?
                            ) {
                                val tReceivedMessage = body?.toString(Charset.defaultCharset())
                                println("Received on ${envelope?.routingKey}: $tReceivedMessage")
                                oLatch.countDown()
                            }
                        }

                        for (queueName in atQueueNames) {
                            oChannel.basicConsume(queueName, true, oConsumer)
                        }

                        oLatch.await()

                        oChannel.close()
                        oConnection.close()
                        println("Close")

                    } else {
                        println("Failed to connect!")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun C_GetxConnectToSSE() {
        viewModelScope.launch() {
            withContext(Dispatchers.IO) {
                val tUrl = "https://postman-echo.com/server-events/10"
                val oClient = OkHttpClient()

                val oRequest = Request.Builder()
                    .url(tUrl)
                    .build()

                val oListener = object : EventSourceListener() {
                    override fun onOpen(eventSource: EventSource, response: Response) {
                        println("SSE Connection Opened!")
                    }

                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String
                    ) {

                        atC_MessageLiveDataSSE = """Received event: $type 
                            |with data: $data""".trimMargin()

                        println("Received event: $type with data: $data")
                    }

                    override fun onClosed(eventSource: EventSource) {
                        println("SSE Connection Closed!")
                        C_GetxConnectToSSE()
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?
                    ) {
                        println("SSE Connection Failed! Error: ${t?.message}")
                    }
                }

                oC_eventSource =
                    EventSources.createFactory(oClient).newEventSource(oRequest, oListener)


            }
        }
    }

    fun C_SETxCancle() {
        try {

            oC_eventSource.cancel()
            atC_MessageLiveDataSSE = "STOP"

        } catch (e: Exception) {
        }

    }


}