package com.atakmap.android.plugintemplate
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.atakmap.android.dropdown.DropDown.OnStateListener
import com.atakmap.android.dropdown.DropDownReceiver
import com.atakmap.android.maps.MapView
import com.atakmap.android.plugintemplate.plugin.R
import java.io.DataInputStream
import java.net.Socket
import kotlin.concurrent.Volatile


class PluginTemplateDropDownReceiver(mapView: MapView, private val pluginContext: Context) : DropDownReceiver(mapView), OnStateListener {
    private lateinit var tabView: TabViewDropDown
    @Volatile
    private var isListening = false
    private var socketReceiverThread: Thread? = null

    private fun showToast(text: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            Toast.makeText(pluginContext, text, Toast.LENGTH_LONG).show()
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startListening(capture: Boolean) {
        if (socketReceiverThread != null)
        {
            return
        }
        isListening = capture
        showToast("Waiting for the connection")
        if (isListening) {
            socketReceiverThread = Thread {
                while (isListening) {
                    try {
                        val socket = Socket("localhost", 5000)
                        val inputStream = DataInputStream(socket.getInputStream())
                        showToast("Connected")
                        while (isListening) {
                            val messageType = inputStream.readInt()
                            if (messageType == 1) {
                                tabView.telemetryUpdate(0, inputStream)
                            } else if (messageType == 2) {
                                tabView.cameraUpdate(0, inputStream)
                            }
                        }
                        socket.close()
                    } catch (e: java.net.ConnectException) {
                        showToast("Waiting for the connection")
                        Thread.sleep(3000)
                    } catch (e: java.io.IOException) {
                        Thread.sleep(3000)
                    } catch (e: Exception) {
                        break
                    }
                }
            }.apply { isDaemon = true }
            socketReceiverThread?.start()
        } else {
            socketReceiverThread?.interrupt()
            try {
                socketReceiverThread?.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            socketReceiverThread = null
            isListening = false
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        if ((intent.action ?: return) == SHOW_PLUGIN) {
            if (!::tabView.isInitialized) {
                tabView = TabViewDropDown(mapView, pluginContext)
            }
            tabView.show()
            startListening(true)
        }
    }
    override fun onDropDownSelectionRemoved() {}
    override fun onDropDownVisible(v: Boolean) {}
    override fun onDropDownSizeChanged(width: Double, height: Double) {}
    override fun onDropDownClose() {}
    override fun disposeImpl() {
        if (::tabView.isInitialized) {
            tabView.endFlight()
            startListening(false)
            tabView.dispose()
        }
    }
    companion object {
        const val SHOW_PLUGIN: String = "com.atakmap.android.plugintemplate.SHOW_PLUGIN"
    }
}
