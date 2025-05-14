package com.example.msdksample

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

class ServerThread : Thread() {
    @Volatile private var running = true
    private var serverSocket: ServerSocket? = null
    private val latestBitmap = AtomicReference<Bitmap?>(null)
    private val latestMetadata = AtomicReference<String?>(null)

    override fun run() {
        try {
            serverSocket = ServerSocket(5000)
            val client = serverSocket!!.accept()
            DataOutputStream(client.getOutputStream()).use { output ->
                while (running) {
                    latestMetadata.getAndSet(null)?.let {
                        output.writeInt(1)
                        output.writeUTF(it)
                    }
                    latestBitmap.getAndSet(null)?.let { bmp ->
                        output.writeInt(2)
                        ByteArrayOutputStream().use { baos ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                            val bytes = baos.toByteArray()
                            output.writeInt(bytes.size)
                            output.write(bytes)
                        }
                    }
                    output.flush()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            serverSocket?.close()
        }
    }

    fun setFrameMetaData(metadata: String) = latestMetadata.set(metadata)
    fun setBitmap(bitmap: Bitmap) = latestBitmap.set(bitmap)

    fun stopServer() {
        running = false
        serverSocket?.close()
    }
}
