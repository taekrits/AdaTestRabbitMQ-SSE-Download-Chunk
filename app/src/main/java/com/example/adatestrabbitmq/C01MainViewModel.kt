package com.example.adatestrabbitmq

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.math.min

class C01MainViewModel : ViewModel() {

    var atC_MessageLiveDataRabbitMQ by mutableStateOf("0")
    var atC_MessageLiveDataSSE by mutableStateOf(" ")
    var tC_MessageDownload by mutableStateOf("")
    lateinit var oC_eventSource: EventSource

    fun C_GETxRabbitMQConnect() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val oFactory = ConnectionFactory().apply {
                    this.host = "10.0.2.2"
//                        this.host = "192.168.1.161"
//                        this.port = 5672
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

                val tUrl = "https://postman-echo.com/server-events/5"
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

    fun C_SETxCancleSSE() {
        try {

            oC_eventSource.cancel()
            atC_MessageLiveDataSSE = "STOP"

        } catch (e: Exception) {

        }
    }

    fun C_GETxDownloadAndSaveVideo(oContext: Context) {
        viewModelScope.launch() {
            try {
                withContext(Dispatchers.IO) {

                    Log.d("DEV1", "full")
                    val oUrl =
                        "https://drive.usercontent.google.com/download?id=1mVnr6p1KMSUYJqe0AygqvSHrJh0pfuIn&export=download&authuser=1&confirm=t&uuid=9e0f54c5-30f3-4018-b833-b9c710b375d4&at=APZUnTVy_a4-gDm6b0Y7koyD6s8R:1692352691233"
//                        "https://dev.ada-soft.com/AdaFileServer/AdaPos5Dev/Adasoft/AdaFile/00001/admessage/2308171630554022d84619f2f46.mp4"
//                        "https://drive.usercontent.google.com/download?id=1VyOUl5hUbGguaAaGriFiuhOb0loAB3Mc&export=download&authuser=1&confirm=t&uuid=51663261-9231-4b1e-ba57-72d2ca355c4c&at=APZUnTWK0P_kBtXkBt4dHpWUQNOF:1692264155857"
                    val tFolderName = "Video Download"
                    val iFileName = "Full${UUID.randomUUID()}.mp4"


                    val oClient = OkHttpClient()
                    val oRequest = Request.Builder()
                        .url(oUrl)
                        .build()

                    oClient.newCall(oRequest).execute().use { oResponse ->
                        if (oResponse.isSuccessful) {
                            val oVideoStream: InputStream = oResponse.body!!.byteStream()
                            val nTotalSize = oResponse.header("Content-Length")?.toLong() ?: 0
                            var nDownloadedSize = 0L

                            val oFolder =
                                File(
                                    oContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                                    tFolderName
                                )
                            if (!oFolder.exists()) {
                                oFolder.mkdirs()
                            }

                            val oVideoFile = File(oFolder, iFileName)

                            val oFileOutputStream = FileOutputStream(oVideoFile)
                            val oBuffer = ByteArray(2048)
                            var nBytesRead: Int

                            while (oVideoStream.read(oBuffer).also { nBytesRead = it } != -1) {
                                oFileOutputStream.write(oBuffer, 0, nBytesRead)
                                nDownloadedSize += nBytesRead

                                val nPercentage = ((nDownloadedSize.toDouble() / nTotalSize.toDouble()) * 100).toInt()
                                Log.d("DEV1", "Downloaded: $nPercentage% $nDownloadedSize $nTotalSize")
                                tC_MessageDownload = "$nPercentage %"
                            }

                            Log.d("DEV1", "full SUCCESS")

                            oFileOutputStream.flush()
                            oFileOutputStream.close()
                            tC_MessageDownload = "SUCCESS FULL"
                        } else {
                            throw IOException("Failed to download video")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun C_GETxDownloadAndSaveVideoChunkedProgress(oContext: Context) {
        viewModelScope.launch() {
            withContext(Dispatchers.IO) {
                val tFolderName = "Video Download"
                val tRandomFileName = "Chunk${UUID.randomUUID()}.mp4"
                val oUrl =
                    "https://drive.usercontent.google.com/download?id=1mVnr6p1KMSUYJqe0AygqvSHrJh0pfuIn&export=download&authuser=1&confirm=t&uuid=9e0f54c5-30f3-4018-b833-b9c710b375d4&at=APZUnTVy_a4-gDm6b0Y7koyD6s8R:1692352691233"
//                    "https://drive.usercontent.google.com/download?id=1VyOUl5hUbGguaAaGriFiuhOb0loAB3Mc&export=download&authuser=1&confirm=t&uuid=51663261-9231-4b1e-ba57-72d2ca355c4c&at=APZUnTWK0P_kBtXkBt4dHpWUQNOF:1692264155857"
//                    "https://dev.ada-soft.com/AdaFileServer/AdaPos5Dev/Adasoft/AdaFile/00001/admessage/2308171630554022d84619f2f46.mp4"
                val oFolder = File(oContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES), tFolderName)
                if (!oFolder.exists()) {
                    oFolder.mkdirs()
                }
                val oVideoFile = File(oFolder, tRandomFileName)
                val oRandomAccessFile = RandomAccessFile(oVideoFile, "rw")

                val oClient = OkHttpClient()

                val oResponse = oClient.newCall(Request.Builder().url(oUrl).head().build()).execute()
                val oTotalSize = oResponse.header("Content-Length")?.toLong() ?: 0


                val nMaxChunkCount = 10
                val nChunkSize = (oTotalSize.toInt() + nMaxChunkCount - 1) / nMaxChunkCount
                val nChunkCount = min(nMaxChunkCount, (oTotalSize / nChunkSize).toInt() + if (oTotalSize % nChunkSize > 0) 1 else 0)

                var nDownloadedSize = 0L

                (0 until nChunkCount).map { nIndex ->
                    async(Dispatchers.IO) {
                        val nStartByte = nIndex * nChunkSize
                        val nEndByte = min((nStartByte + nChunkSize) - 1, oTotalSize.toInt() - 1)

                        val oRequest = Request.Builder()
                            .url(oUrl)
                            .header("Range", "bytes=$nStartByte-$nEndByte")
                            .build()

                        oClient.newCall(oRequest).execute().use { oResponse ->
                            if (oResponse.isSuccessful) {
                                val oBuffer = oResponse.body!!.bytes()

                                synchronized(oRandomAccessFile) {
                                    oRandomAccessFile.seek(nStartByte.toLong())
                                    oRandomAccessFile.write(oBuffer)
                                }

                                nDownloadedSize += oBuffer.size
                                val nPercentage = (nDownloadedSize * 100 / oTotalSize).toInt()
                                withContext(Dispatchers.Main) {
                                    Log.d("DEV2", "downloadedSize = $nDownloadedSize ")
                                    tC_MessageDownload = "$nPercentage %"
                                }

                                Log.d("DEV1", "index = $nIndex  SUCCESS")
                            } else {
                                throw IOException("Failed to download video")
                            }
                        }
                    }
                }.awaitAll()

                oRandomAccessFile.close()
                tC_MessageDownload = "SUCCESS CHUNK"
            }
        }
    }

    fun C_GETxDownloadAndSaveVideoChunkedProgressMaxMemory(oContext: Context) {
        viewModelScope.launch() {
            withContext(Dispatchers.IO) {
                val tFolderName = "Video Download"
                val tRandomFileName = "Chunk${UUID.randomUUID()}.mp4"
                val oUrl =
                    "https://drive.usercontent.google.com/download?id=1VyOUl5hUbGguaAaGriFiuhOb0loAB3Mc&export=download&authuser=1&confirm=t&uuid=51663261-9231-4b1e-ba57-72d2ca355c4c&at=APZUnTWK0P_kBtXkBt4dHpWUQNOF:1692264155857"
//                    "https://drive.usercontent.google.com/download?id=1mVnr6p1KMSUYJqe0AygqvSHrJh0pfuIn&export=download&authuser=1&confirm=t&uuid=9e0f54c5-30f3-4018-b833-b9c710b375d4&at=APZUnTVy_a4-gDm6b0Y7koyD6s8R:1692352691233"
                val oFolder = File(oContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES), tFolderName)
                if (!oFolder.exists()) {
                    oFolder.mkdirs()
                }
                val oVideoFile = File(oFolder, tRandomFileName)
                val oRandomAccessFile = RandomAccessFile(oVideoFile, "rw")

                val oClient = OkHttpClient()

                val oResponse = oClient.newCall(Request.Builder().url(oUrl).head().build()).execute()
                val nTotalSize = oResponse.header("Content-Length")?.toLong() ?: 0


                val nMaxMemory = Runtime.getRuntime().maxMemory()
                val nMemoryUsageFraction = 0.7
                val nMaxChunkCount = ((nMaxMemory * nMemoryUsageFraction) / (1024 * 1024)).toInt()
                val nChunkSize = (nTotalSize.toInt() + nMaxChunkCount - 1) / nMaxChunkCount
                val nChunkCount = min(nMaxChunkCount, (nTotalSize / nChunkSize).toInt() + if (nTotalSize % nChunkSize > 0) 1 else 0)



                var nDownloadedSize = 0L

                (0 until nChunkCount).map { nIndex ->
                    async(Dispatchers.IO) {
                        val nStartByte = nIndex * nChunkSize
                        val nEndByte = min((nStartByte + nChunkSize) - 1, nTotalSize.toInt() - 1)

                        val oRequest = Request.Builder()
                            .url(oUrl)
                            .header("Range", "bytes=$nStartByte-$nEndByte")
                            .build()

                        oClient.newCall(oRequest).execute().use { oResponse ->
                            if (oResponse.isSuccessful) {
                                val oBuffer = oResponse.body!!.bytes()

                                synchronized(oRandomAccessFile) {
                                    oRandomAccessFile.seek(nStartByte.toLong())
                                    oRandomAccessFile.write(oBuffer)
                                }

                                nDownloadedSize += oBuffer.size
                                val nPercentage = (nDownloadedSize * 100 / nTotalSize).toInt()
                                withContext(Dispatchers.Main) {

                                    tC_MessageDownload = "$nPercentage %"
                                }

                                Log.d("DEV1", "index = $nIndex  SUCCESS")
                            } else {
                                throw IOException("Failed to download video")
                            }
                        }
                    }
                }.awaitAll()

                oRandomAccessFile.close()
                tC_MessageDownload = "SUCCESS CHUNK"
            }
        }
    }



}



