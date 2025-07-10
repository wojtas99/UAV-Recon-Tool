package com.example.msdksample

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.KeyManager
import dji.v5.manager.SDKManager
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.common.Attitude
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.sdk.keyvalue.value.product.ProductType
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change"
        private val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        }.toTypedArray()
    }

    private val isRegistrationInProgress = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val serverThread = ServerThread()

    private lateinit var mTextConnectionStatus: TextView
    private lateinit var mTextProduct: TextView
    private lateinit var mTextModelAvailable: TextView
    private lateinit var mVersionTv: TextView
    private lateinit var atakButton: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            startSDKRegistration()
        } else {
            showToast("Missing Permissions!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        serverThread.start()

        initUI()
        setupAtakButton()

        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startSDKRegistration()
        }
    }

    private fun initUI() {
        mTextConnectionStatus = findViewById(R.id.text_connection_status)
        mTextModelAvailable = findViewById(R.id.text_model_available)
        mTextProduct = findViewById(R.id.text_product_info)
        mVersionTv = findViewById(R.id.textView2)

        mVersionTv.text = resources.getString(
            R.string.sdk_version,
            SDKManager.getInstance().sdkVersion
        )
    }

    private fun setupAtakButton() {
        atakButton = findViewById(R.id.button)
        atakButton.setOnClickListener { launchAtakCiv() }
    }

    private fun launchAtakCiv() {
        val pkg = "com.atakmap.app.civ"
        val probe = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = pkg
        }
        val resolved = packageManager.queryIntentActivities(probe, 0)
        if (resolved.isNotEmpty()) {
            val info = resolved[0].activityInfo
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(info.packageName, info.name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(intent)
        } else {
            showToast("Install ATAK first!")
        }
    }

    private fun startSDKRegistration() {
        if (!isRegistrationInProgress.compareAndSet(false, true)) return

        SDKManager.getInstance().init(this, object : SDKManagerCallback {
            override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    SDKManager.getInstance().registerApp()
                }
            }
            override fun onRegisterSuccess() {
                Log.d(TAG, "DJI SDK register success")
            }
            override fun onRegisterFailure(error: IDJIError?) {
                showToast("SDK registration failed")
                Log.e(TAG, error?.description() ?: "Unknown error")
            }
            override fun onProductDisconnect(productId: Int) {
                updateUI("Disconnected", "Unknown", "Unknown")
            }
            override fun onProductConnect(product: Int) {
                handleProductConnected()
            }
            override fun onProductChanged(product: Int) {
                handleProductConnected()
            }
            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                showToast("FlySafe DB: $current / $total")
            }
        })
    }

    private fun handleProductConnected() {
        getProductType { type ->
            runOnUiThread { mTextProduct.text = type?.name ?: "Unknown" }
        }
        getFirmwareVersion { ver ->
            runOnUiThread {
                if (ver != null) {
                    atakButton.visibility = View.VISIBLE
                    subscribeVideoStream()
                    mTextConnectionStatus.text = "Connected"
                }
                mTextModelAvailable.text = ver ?: "Unknown"
            }
        }
    }

    private fun subscribeVideoStream() {
        MediaDataCenter.getInstance().cameraStreamManager.addFrameListener(
            ComponentIndexType.LEFT_OR_MAIN,
            ICameraStreamManager.FrameFormat.RGBA_8888,
            object : ICameraStreamManager.CameraFrameListener {
                override fun onFrame(
                    frameData: ByteArray,
                    offset: Int,
                    length: Int,
                    width: Int,
                    height: Int,
                    format: ICameraStreamManager.FrameFormat
                ) {
                    val slice = frameData.copyOfRange(offset, offset + length)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(slice))
                    val loc = KeyManager.getInstance()
                        .getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D))
                    val att = KeyManager.getInstance()
                        .getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude))
                    val meta = JSONObject().apply {
                        put("timestamp", System.currentTimeMillis())
                        put("latitude", loc?.latitude ?: 0.0)
                        put("longitude", loc?.longitude ?: 0.0)
                        put("altitude", loc?.altitude ?: 0.0)
                        put("roll", att?.roll ?: 0.0)
                        put("pitch", att?.pitch ?: 0.0)
                        put("yaw", att?.yaw ?: 0.0)
                    }

                    serverThread.setFrameMetaData(meta.toString())
                    serverThread.setBitmap(bmp)
                }
            }
        )
    }

    private fun getProductType(callback: (ProductType?) -> Unit) {
        KeyManager.getInstance().getValue(
            KeyTools.createKey(ProductKey.KeyProductType),
            object : CommonCallbacks.CompletionCallbackWithParam<ProductType> {
                override fun onSuccess(p: ProductType?) = callback(p)
                override fun onFailure(error: IDJIError) {
                    Log.e(TAG, "Get type failed: ${error.description()}")
                    callback(null)
                }
            }
        )
    }

    private fun getFirmwareVersion(callback: (String?) -> Unit) {
        KeyManager.getInstance().getValue(
            KeyTools.createKey(ProductKey.KeyFirmwareVersion),
            object : CommonCallbacks.CompletionCallbackWithParam<String> {
                override fun onSuccess(p: String?) = callback(p)
                override fun onFailure(error: IDJIError) {
                    Log.e(TAG, "Get firmware failed: ${error.description()}")
                    callback(null)
                }
            }
        )
    }

    private val updateRunnable = Runnable {
        sendBroadcast(Intent(FLAG_CONNECTION_CHANGE))
    }

    private fun updateUI(conn: String, prod: String, ver: String) {
        handler.post {
            mTextConnectionStatus.text = conn
            mTextProduct.text = prod
            mTextModelAvailable.text = ver
        }
    }

    private fun showToast(msg: String) = handler.post {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        serverThread.stopServer()
    }
}
