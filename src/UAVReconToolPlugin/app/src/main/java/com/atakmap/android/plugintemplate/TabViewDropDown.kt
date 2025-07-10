package com.atakmap.android.plugintemplate

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.atakmap.android.dropdown.DropDownReceiver
import com.atakmap.android.ipc.AtakBroadcast
import com.atakmap.android.maps.CrumbTrail
import com.atakmap.android.maps.MapView
import com.atakmap.android.maps.MapView.getMapView
import com.atakmap.android.maps.Marker
import com.atakmap.android.maps.PointMapItem
import com.atakmap.android.plugintemplate.plugin.R
import com.atakmap.coremap.maps.coords.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

@RequiresApi(Build.VERSION_CODES.O)
class TabViewDropDown(mapView: MapView, plugin: Context) : DropDownReceiver(mapView),
    View.OnClickListener, ViewPager.OnPageChangeListener {
    private val view: View = LayoutInflater.from(plugin).inflate(R.layout.tab_swipe_view, mapView, false)
    private val viewPager: ViewPager
    private val fragments: List<TestFragment>
    private val tabDots: Array<View> = arrayOf(
        view.findViewById(R.id.tab_left_dot),
        view.findViewById(R.id.tab_middle_dot),
        view.findViewById(R.id.tab_right_dot)
    )
    @Volatile
    private var currentFlight: FlightSession
    init {
        val id = "Drone Flight - ${LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}"
        currentFlight = FlightSession(id, System.currentTimeMillis())
        FlightRepository.addSession(mapView.context, currentFlight)
        fragments = listOf(
            TestFragment().init(plugin, 1, this),
            TestFragment().init(plugin, 2, this),
            TestFragment().init(plugin, 3, this)
        )
        val adapter = TestPagerAdapter(
            (mapView.context as FragmentActivity).supportFragmentManager,
            fragments
        )
        viewPager = view.findViewById(R.id.tab_pager)
        viewPager.adapter = adapter
        viewPager.addOnPageChangeListener(this)
        viewPager.offscreenPageLimit = fragments.size
        tabDots[0].isSelected = true
    }
    fun test(tabIndex: Int, bitmap: Bitmap) {
        if (viewPager.currentItem == 0) {
            if (tabIndex in fragments.indices) {
                fragments[tabIndex].test(bitmap)
            }
        }
    }
    fun cameraUpdate(tabIndex: Int, inputStream: DataInputStream) {
        if (tabIndex in fragments.indices) {
            fragments[tabIndex].cameraUpdate(inputStream)
        }
    }
    fun telemetryUpdate(tabIndex: Int, inputStream: DataInputStream) {
        if (tabIndex in fragments.indices) {
            fragments[tabIndex].telemetryUpdate(inputStream)
        }
    }
    override fun onReceive(ctx: Context, intent: Intent) {}
    override fun disposeImpl() {
        hideDropDown()
    }
    fun endFlight() {
        currentFlight.endTime = System.currentTimeMillis()
        FlightRepository.updateSession(mapView.context, currentFlight)
    }
    fun recordDetection(event: DetectionEvent) {
        currentFlight.detections.add(event)
        FlightRepository.updateSession(mapView.context, currentFlight)
    }
    fun show() {
        showDropDown(view, FULL_WIDTH, FULL_HEIGHT, FULL_WIDTH, FULL_HEIGHT)
    }
    override fun onPageSelected(position: Int) {
        for (i in tabDots.indices) {
            tabDots[i].isSelected = position == i
        }
    }
    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
    override fun onPageScrollStateChanged(state: Int) {}
    override fun onClick(p0: View?) {}

    class TestFragment() : Fragment(), Detector.DetectorListener {
        private fun showToast(text: String) {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                Toast.makeText(this.plugin, text, Toast.LENGTH_LONG).show()
            }
        }

        // Variables
        private lateinit var mapContainer_frameLayout: LinearLayout
        @Volatile
        // Init
        private var plugin: Context? = null
        private var tabNum: Int = 0
        private var parentDropDown: TabViewDropDown? = null
        // Buttons
        private var map_Button: Button? = null
        private var crosshair_Button: Button? = null
        private var detector_Button: Button? = null
        private var back_Button: Button? = null
        private var lockMap_Button: Button? = null
        private var hideUI_Button: Button? = null

        // Boolean
        private var detectionStatus: Boolean = false
        private var textureReady: Boolean = false
        private var gpsReady: Boolean = false

        // Double
        private var droneLat: Double = 0.0
        private var droneLon: Double = 0.0
        private var droneAlt: Double = 0.0
        private var droneRoll: Double = 0.0
        private var droneYaw: Double = 0.0
        private var dronePitch: Double = 0.0
        private var gimbalRoll: Double = 0.0
        private var gimbalYaw: Double = 0.0
        private var gimbalPitch: Double = 0.0
        private var droneCompass: Double = 0.0

        // Image View
        private var crosshair_imageView: ImageView? = null

        // Text Views
        private var flightTitle_textView: TextView? = null

        // Views & Layouts
        private var droneCamera_textureView: TextureView? = null
        private var mCOP_lineCompassView: LineCompassView? = null
        private var mapHolder_relativeLayout: RelativeLayout? = null
        private var flightsData_recyclerView: RecyclerView? = null
        private var flightMetaData_constraintLayout: View? = null
        private var flightsMetaData_recyclerView: RecyclerView? = null
        private var userInterface_constraintLayout: ConstraintLayout? = null
        private lateinit var labels_listView: ListView
        private lateinit var avgHeight_listView: ListView

        // Others
        private var detector: Detector? = null
        private var models_Spinner: Spinner? = null
        private var pending_Bitmap: Bitmap? = null
        private var avgHeightData: ArrayList<AvgHeightData>? = null
        private var dataModel: ArrayList<DataModel>? = null
        private var selectedModel = ""
        private lateinit var droneCrumbTrail: CrumbTrail
        private val movementHistory = mutableListOf<GeoPoint>()
        private var lastUpdateTime = 0L
        private var frameCount: Int = 0

        private var mc: OffscreenMapCapture? = null
        private lateinit var uavMarker: Marker

        private val detectionExecutor = Executors.newSingleThreadExecutor()
        private var detectionFuture: Future<Bitmap>? = null


        // Data Classes
        data class AvgHeightData (var label: String?, var height: String?)
        data class DataModel (var name: String?, var checked: Boolean)
        data class FlightMetaData(
            val key: String,
            val value: String,
            val iconDrawable: Drawable? = null
        )

        // Functions
        // Inits
        fun init(plugin: Context, tabNum: Int, parent: TabViewDropDown): TestFragment {
            this.plugin = plugin
            this.tabNum = tabNum
            this.parentDropDown = parent
            return this
        }
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            var v = LayoutInflater.from(plugin).inflate(R.layout.fragment_foo, container, false)
            v = initObjects(v)
            initButtonListeners()
            initTextureViewListeners()
            initSeekBarListeners(v)
            return v
        }
        private fun initObjects(view: View) : View {
            // Layouts
            // Constraint Layouts
            val firstTab_constraintLayout: ConstraintLayout = view.findViewById(R.id.firstTab_constraintLayout)
            val secondTab_constraintLayout: ConstraintLayout = view.findViewById(R.id.secondTab_constraintLayout)
            val thirdTab_constraintLayout: ConstraintLayout = view.findViewById(R.id.thirdTab_constraintLayout)
            flightMetaData_constraintLayout = view.findViewById(R.id.flightMetaData_constraintLayout)
            userInterface_constraintLayout = view.findViewById(R.id.userInterface_constraintLayout)

            // Frame Layouts
            mapContainer_frameLayout = view.findViewById(R.id.mapContainer_linearLayout)

            // Relative Layouts
            mapHolder_relativeLayout = view.findViewById(R.id.mapHolder_relativeLayout)

            // Recycler Views
            flightsData_recyclerView = view.findViewById(R.id.flightsData_recyclerView)
            flightsMetaData_recyclerView = view.findViewById(R.id.flightsMetaData_recyclerView)

            // Buttons
            lockMap_Button = view.findViewById(R.id.lockMap_Button)
            detector_Button = view.findViewById(R.id.detector_Button)
            crosshair_Button = view.findViewById(R.id.crosshair_Button)
            map_Button = view.findViewById(R.id.map_Button)
            back_Button = view.findViewById(R.id.back_Button)
            hideUI_Button = view.findViewById(R.id.hideUI_Button)

            // Text Views
            flightTitle_textView = view.findViewById(R.id.flightTitle_textView)

            // Image Views
            crosshair_imageView = view.findViewById(R.id.crosshair_imageView)

            // List Views
            labels_listView = view.findViewById(R.id.labels_listView)
            avgHeight_listView = view.findViewById(R.id.avgHeight_listView)

            // Texture Views
            droneCamera_textureView = view.findViewById(R.id.droneCamera_textureView)

            // Compass Views
            mCOP_lineCompassView = view.findViewById(R.id.mCOP_lineCompassView)

            // Spinners
            models_Spinner = view.findViewById(R.id.models_Spinner)

            // Others
            mc = OffscreenMapCapture(mapContainer_frameLayout)

            val allLabels = plugin!!.assets.open("labels.txt")
                .bufferedReader()
                .useLines { it.toList() }

            val allLabels_avg_height = plugin!!.assets.open("labels_avg_height")
                .bufferedReader()
                .useLines { it.toList() }

            avgHeightData = ArrayList()
            allLabels.zip(allLabels_avg_height) { label, height ->
                avgHeightData!!.add(AvgHeightData(("$label : "), ("$height m")))
            }
            val avg_height_adapter = context?.let { AvgHeightAdapter(avgHeightData!!, it) }!!
            avgHeight_listView.adapter = avg_height_adapter

            avgHeight_listView.setOnItemClickListener { _, _, position, _ ->
                val item = avgHeightData!![position]
                val currentHeight = item.height?.removeSuffix(" m")
                val editText = EditText(requireContext()).apply {
                    setText(currentHeight)
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setSelection(text.length)
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Edit Height: ${item.label?.trimEnd(' ', ':')}")
                    .setView(editText)
                    .setPositiveButton("Save") { dialog, _ ->
                        val newVal = editText.text.toString().trim()
                        if (newVal.isNotEmpty()) {
                            item.height = "$newVal m"
                            avg_height_adapter.notifyDataSetChanged()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
            dataModel = ArrayList<DataModel>()
            dataModel!!.add(DataModel("Select All", false))
            allLabels.forEach { label ->
                dataModel!!.add(DataModel(label, false))
            }
            val detection_adapter = context?.let { DetectionAdapter(dataModel!!, it) }!!
            labels_listView.adapter = detection_adapter

            val allSessions = FlightRepository.loadAll(requireContext())
            flightsData_recyclerView?.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = FlightsDataAdapter(allSessions) { session ->
                    showFlightDetail(session)
                }
                visibility = View.VISIBLE
            }

            val assetManager = plugin!!.assets
            val allFiles = assetManager.list("") ?: arrayOf()
            val tfliteFiles = allFiles.filter { it.endsWith(".tflite") }

            models_Spinner?.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                tfliteFiles
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }


            thirdTab_constraintLayout.visibility = if (tabNum == 3) View.VISIBLE else View.GONE
            secondTab_constraintLayout.visibility = if (tabNum == 2) View.VISIBLE else View.GONE
            firstTab_constraintLayout.visibility = if (tabNum == 1) View.VISIBLE else View.GONE

            return view
        }
        @SuppressLint("ClickableViewAccessibility")
        private fun initButtonListeners() {
            // Buttons Listeners
            // Hide UI Button
            hideUI_Button?.setOnClickListener {
                hideUI_Button?.isSelected = !(hideUI_Button?.isSelected ?: false)
                if (hideUI_Button?.isSelected == true) {
                    hideUI_Button?.setBackgroundResource(R.drawable.vision_off)
                    userInterface_constraintLayout?.visibility = View.GONE
                } else {
                    hideUI_Button?.setBackgroundResource(R.drawable.vision)
                    userInterface_constraintLayout?.visibility = View.VISIBLE
                }
            }
            // LockMap Button
            lockMap_Button?.setOnClickListener {
                lockMap_Button?.isSelected = !(lockMap_Button?.isSelected ?: false)
                if (lockMap_Button?.isSelected == true) {
                    lockMap_Button?.setBackgroundResource(R.drawable.lock_on)
                }
                else {
                    lockMap_Button?.setBackgroundResource(R.drawable.lock_off)
                }
            }
            map_Button?.setOnClickListener {
                val show = !(map_Button?.isSelected ?: false)
                map_Button?.isSelected = show
                map_Button?.setBackgroundResource(if (show) R.drawable.map_on else R.drawable.map_off)
                mapHolder_relativeLayout?.visibility = if (show) View.VISIBLE else View.INVISIBLE
                if (show) {
                    mc!!.capture(true)
                }
                else {
                    mc!!.capture(false)
                    //mapContainer_frameLayout.removeView(mc!!.glView)
                }
            }
            // Detection Button
            detector_Button?.setOnClickListener {
                detectionStatus = !detectionStatus
                if (detectionStatus) {
                    detector_Button?.setBackgroundResource(R.drawable.ai_on)
                } else {
                    detector_Button?.setBackgroundResource(R.drawable.ai_off)
                }
            }
            // Crosshair Button
            crosshair_Button?.setOnClickListener {
                crosshair_Button?.isSelected = !(crosshair_Button?.isSelected ?: false)
                if (crosshair_Button?.isSelected == true)
                {
                    crosshair_Button?.setBackgroundResource(R.drawable.crosshair_on)
                    crosshair_imageView?.visibility = View.VISIBLE
                }
                else
                {
                    crosshair_Button?.setBackgroundResource(R.drawable.crosshair_off)
                    crosshair_imageView?.visibility = View.GONE
                }
            }
            // Back Button
            back_Button?.setOnClickListener {
                flightMetaData_constraintLayout?.visibility        = View.GONE
                flightsData_recyclerView?.visibility = View.VISIBLE
            }
        }
        private fun initTextureViewListeners() {
            // Texture View Listeners
            droneCamera_textureView?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    textureReady = true
                    pending_Bitmap?.let {
                        drawImageOnTexture(it)
                        pending_Bitmap = null
                    }
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    textureReady = false
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                }
            }
        }
        @SuppressLint("ClickableViewAccessibility")
        private fun initSeekBarListeners(view: View) {
            view.findViewById<SeekBar>(R.id.threshold_seekBar).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                @SuppressLint("SetTextI18n")
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    view.findViewById<TextView>(R.id.thresholdValue_textView).text = "$progress %"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            view.findViewById<SeekBar>(R.id.bbox_seekBar).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                @SuppressLint("SetTextI18n")
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    view.findViewById<TextView>(R.id.bboxValue_textView).text = ("$progress dp")
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            view.findViewById<SeekBar>(R.id.frame_seekBar).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                @SuppressLint("SetTextI18n")
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    view.findViewById<TextView>(R.id.frameValue_textView).text = ("$progress frame")
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        private fun initDroneTracking() {
            if (!::droneCrumbTrail.isInitialized && ::uavMarker.isInitialized) {
                val mapView = getMapView()
                val prefs = PreferenceManager.getDefaultSharedPreferences(mapView.context)
                val crumbTrailId = "drone-trail-" + UUID.randomUUID().toString()
                droneCrumbTrail = CrumbTrail(
                    mapView,           // MapView parameter
                    uavMarker,         // PointMapItem parameter (your drone marker)
                    prefs,             // SharedPreferences parameter
                    crumbTrailId       // String uid parameter
                )
                droneCrumbTrail.setMetaString("shapeName", "Drone Path")
                droneCrumbTrail.setMetaBoolean("addToObjList", true)
                mapView.rootGroup.addItem(droneCrumbTrail)
                droneCrumbTrail.setTracking(true)
            }
        }
        // Drone Functions
        private fun showFlightDetail(session: FlightSession) {
            val fresh = FlightRepository
                .loadAll(requireContext())
                .firstOrNull { it.id == session.id }
                ?: session

            flightTitle_textView?.text = fresh.id
            val baseMeta = mutableListOf<FlightMetaData>(
                FlightMetaData(
                    key = "Flight Time",
                    value = fresh.endTime?.let { (it - fresh.startTime) / 60000 }?.toString() + " min",
                    iconDrawable = plugin?.let { ContextCompat.getDrawable(it, R.drawable.drone) }
                ),
                FlightMetaData(
                    key = "Detected Objects",
                    value = fresh.detections.size.toString(),
                    iconDrawable = plugin?.let { ContextCompat.getDrawable(it, R.drawable.detective) }
                )
            )
            fresh.detections.forEach { ev ->
                baseMeta.add(
                    FlightMetaData(
                        key = "Object Name: ${ev.label}",
                        value = "Detected Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ev.timestamp))}, "
                                + "Confidence: ${(ev.confidence * 100).toInt()}%, Latitude: " + ev.latitude + " Longitude: " + ev.longitude,
                        iconDrawable = ev.iconDrawable
                    )
                )
            }
            flightsMetaData_recyclerView?.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = FlightsMetaDataAdapter(baseMeta)
                visibility = View.VISIBLE
            }
            flightsData_recyclerView?.visibility = View.GONE
            flightMetaData_constraintLayout?.visibility        = View.VISIBLE
        }
        private fun operatorDataUpdate() {
            val selfMarker = getMapView().selfMarker
            if (selfMarker?.iconVisibility == Marker.ICON_VISIBLE) {
                view?.findViewById<TextView>(R.id.latOperator_textView)?.text = "Lat: ${selfMarker.point.latitude}"
                view?.findViewById<TextView>(R.id.lonOperator_textView)?.text = "Lon: ${selfMarker.point.longitude}"
                view?.findViewById<TextView>(R.id.altOperator_textView)?.text = "Alt: ${selfMarker.point.altitude.toString().take(4)}m"
            } else {
                view?.findViewById<TextView>(R.id.latOperator_textView)?.text = "Lat: NoGPS"
                view?.findViewById<TextView>(R.id.lonOperator_textView)?.text = "Lon: NoGPS"
                view?.findViewById<TextView>(R.id.altOperator_textView)?.text = "Alt: NoGPS"
            }
            if (gpsReady) {
                view?.findViewById<TextView>(R.id.latDrone_textView)?.text = "Lat: ${droneLat}"
                view?.findViewById<TextView>(R.id.lonDrone_textView)?.text = "Lon: ${droneLon}"
                view?.findViewById<TextView>(R.id.altDrone_textView)?.text = "Alt: ${droneAlt.toString().take(4)}m"
            } else {
                view?.findViewById<TextView>(R.id.latDrone_textView)?.text = "Lat: NoGPS"
                view?.findViewById<TextView>(R.id.lonDrone_textView)?.text = "Lon: NoGPS"
                view?.findViewById<TextView>(R.id.altDrone_textView)?.text = "Alt: NoGPS"
            }
            CoroutineScope(Dispatchers.IO).launch {
                withContext(Dispatchers.Main) {
                    mCOP_lineCompassView?.setAngle(droneCompass)
                }
            }
            if (gpsReady) {
                val selfPt = getMapView().selfMarker?.point
                if (selfPt != null) {
                    val dronePt = GeoPoint(droneLat, droneLon)
                    val distanceMeters = dronePt.distanceTo(selfPt)
                    view?.findViewById<TextView>(R.id.distance_textView)?.text = "%.1f m".format(distanceMeters)
                }
                view?.findViewById<TextView>(R.id.altitude_textView)?.text = "${droneAlt.toString().take(4)} m"
            }
        }
        fun telemetryUpdate(inputStream: DataInputStream) {
            val metadataJson = inputStream.readUTF()
            val jsonObject = JSONObject(metadataJson)
            val timestamp = jsonObject.getLong("timestamp")
            droneLat = jsonObject.getDouble("latitude")
            droneLon = jsonObject.getDouble("longitude")
            droneAlt = jsonObject.getDouble("altitude")
            droneRoll = jsonObject.getDouble("roll")
            dronePitch = jsonObject.getDouble("pitch")
            droneYaw = jsonObject.getDouble("yaw")
            droneCompass = jsonObject.getDouble("compass")
            gimbalRoll = jsonObject.getDouble("gimbal_roll")
            gimbalYaw = jsonObject.getDouble("gimbal_yaw")
            gimbalPitch = jsonObject.getDouble("gimbal_pitch")

            gpsReady = droneLat != 0.0 && droneLon != 0.0

            view?.findViewById<TextView>(R.id.droneRoll_textView)?.text = "Drone Roll: ${droneRoll}"
            view?.findViewById<TextView>(R.id.droneYaw_textView)?.text = "Drone Pitch: ${dronePitch}"
            view?.findViewById<TextView>(R.id.dronePitch_textView)?.text = "Drone Yaw: ${droneYaw}"

            view?.findViewById<TextView>(R.id.gimbalRoll_textView)?.text = "Gimbal Roll: ${gimbalRoll}"
            view?.findViewById<TextView>(R.id.gimbalPitch_textView)?.text = "Gimbal Pitch: ${gimbalPitch}"
            view?.findViewById<TextView>(R.id.gimbalYaw_textView)?.text = "Gimbal Yaw: ${gimbalYaw}"


        }
        private fun setupTrackListeners() {
            uavMarker.addOnTrackChangedListener(object : Marker.OnTrackChangedListener {
                override fun onTrackChanged(marker: Marker) {
                    val heading = marker.trackHeading
                    Log.d("TESTS", "Drone heading changed to: $heading")
                }
            })

            uavMarker.addOnPointChangedListener(object : PointMapItem.OnPointChangedListener {
                override fun onPointChanged(item: PointMapItem) {
                    val newLocation = item.point
                    if (map_Button?.isSelected == true && lockMap_Button?.isSelected == true) {
                        parentDropDown!!.mapView.updateView(
                            item.point.latitude,
                            item.point.longitude,
                            parentDropDown!!.mapView.mapScale,
                            parentDropDown!!.mapView.mapRotation,
                            parentDropDown!!.mapView.mapTilt,
                            false
                            )
                    }
                    Log.d("TESTS", "Drone moved to: $newLocation")
                }
            })
        }
        private fun trackMovement(newLocation: GeoPoint) {
            val currentTime = SystemClock.elapsedRealtime()

            movementHistory.add(newLocation)

            if (movementHistory.size > 1 && lastUpdateTime > 0) {
                val previousPoint = movementHistory[movementHistory.size - 2]
                val distance = previousPoint.distanceTo(newLocation)
                val timeDelta = (currentTime - lastUpdateTime) / 1000.0

                if (timeDelta > 0) {
                    val speed = distance / timeDelta
                    uavMarker.setMetaDouble("Speed", speed)
                    uavMarker.setMetaDouble("Distance", getTotalDistance())
                }
            }

            lastUpdateTime = currentTime
        }
        private fun getTotalDistance(): Double {
            var totalDistance = 0.0
            for (i in 1 until movementHistory.size) {
                totalDistance += movementHistory[i-1].distanceTo(movementHistory[i])
            }
            return totalDistance
        }
        private fun droneMarker(localization: GeoPoint) {
            if (gpsReady) {
                if (!::uavMarker.isInitialized) {
                    uavMarker = Marker(localization, UUID.randomUUID().toString())
                    uavMarker.type = "a-f-A-M-R-D"
                    uavMarker.setMetaBoolean("readiness", true)
                    uavMarker.setMetaBoolean("archive", true)
                    uavMarker.setMetaString("how", "h-g-i-g-o")
                    uavMarker.setMetaBoolean("editable", true)
                    uavMarker.setMetaBoolean("movable", true)
                    uavMarker.setMetaBoolean("removable", true)
                    uavMarker.setMetaString("entry", "user")
                    uavMarker.setMetaString("callsign", "Drone")
                    uavMarker.title = "Drone"

                    getMapView().rootGroup.addItem(uavMarker)

                    //initDroneTracking()
                    //setupTrackListeners()

                    //trackMovement(localization)


                    uavMarker.persist(
                        getMapView().mapEventDispatcher,
                        null,
                        this.javaClass
                    )
                    Intent("com.atakmap.android.maps.COT_PLACED").also {
                        it.putExtra("uid", uavMarker.uid)
                        AtakBroadcast.getInstance().sendBroadcast(it)
                    }
                } else {
                    uavMarker.point = localization
                    if (map_Button?.isSelected == true && lockMap_Button?.isSelected == true) {
                        parentDropDown!!.mapView.updateView(
                            uavMarker.point.latitude,
                            uavMarker.point.longitude,
                            parentDropDown!!.mapView.mapScale,
                            parentDropDown!!.mapView.mapRotation,
                            parentDropDown!!.mapView.mapTilt,
                            false
                        )
                    }
                    uavMarker.persist(
                        getMapView().mapEventDispatcher,
                        null,
                        this.javaClass
                    )
                    Intent("com.atakmap.android.maps.COT_PLACED").also {
                        it.putExtra("uid", uavMarker.uid)
                        AtakBroadcast.getInstance().sendBroadcast(it)
                    }
                }
            }
        }
        private fun sendMarker(type: String, localization: GeoPoint, boundingBox: BoundingBox, threshold: Double) {
            if(gpsReady) {
                if (parentDropDown!!.mapView.rootGroup.findMapGroup("enemy") == null) {
                    parentDropDown!!.mapView.rootGroup.addGroup("enemy")
                }
                if (parentDropDown!!.mapView.rootGroup.findMapGroup("enemy")
                        .deepFindClosestItem(localization, threshold) == null
                ) {
                    val enemyMarker = Marker(localization, UUID.randomUUID().toString())
                    enemyMarker.type = type
                    enemyMarker.setMetaBoolean("readiness", true)
                    enemyMarker.setMetaBoolean("archive", true)
                    enemyMarker.setMetaString("how", "h-g-i-g-o")
                    enemyMarker.setMetaBoolean("editable", true)
                    enemyMarker.setMetaBoolean("movable", true)
                    enemyMarker.setMetaBoolean("removable", true)
                    enemyMarker.setMetaString("entry", "user")
                    enemyMarker.setMetaString("callsign", boundingBox.clsName)
                    enemyMarker.title = boundingBox.clsName
                    parentDropDown!!.mapView.rootGroup.findMapGroup("enemy").addItem(enemyMarker)
                    enemyMarker.persist(getMapView().mapEventDispatcher, null, this.javaClass)

                    Intent("com.atakmap.android.maps.COT_PLACED").also {
                        it.putExtra("uid", enemyMarker.uid)
                        AtakBroadcast.getInstance().sendBroadcast(it)
                    }

                    val event = DetectionEvent(
                        label = boundingBox.clsName,
                        confidence = boundingBox.cnf,
                        timestamp = System.currentTimeMillis(),
                        latitude = localization.latitude,
                        longitude = localization.longitude,
                        iconDrawable = enemyMarker.iconDrawable
                    )
                    parentDropDown?.recordDetection(event)
                }
            }
        }
        fun test(bitmap: Bitmap) {
            operatorDataUpdate()
            droneMarker(GeoPoint(52.24967778146404, 20.89645134494565))
            frameCount++
            if (detectionStatus) {
                if (selectedModel != models_Spinner?.selectedItem.toString()) {
                    selectedModel = models_Spinner?.selectedItem.toString()
                    detector = this.plugin?.let {
                        Detector(
                            it,
                            selectedModel,
                            "labels.txt",
                            this
                        )
                    }
                    detector?.setup()
                }
                if ((frameCount % parentDropDown!!.fragments[1].view?.findViewById<TextView>(R.id.frameValue_textView)?.text?.removeSuffix(" frame").toString().toInt() == 0)) {
                    if (detectionFuture == null || detectionFuture!!.isDone) {
                        val bmpToProcess = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        detectionFuture = detectionExecutor.submit<Bitmap> {
                            detector?.detect(bmpToProcess) ?: bmpToProcess
                        }
                        Thread {
                            val result = detectionFuture!!.get()
                            droneCamera_textureView?.post {
                                drawImageOnTexture(result)
                            }
                        }.start()
                    }
                }
                //val processedBitmap = detector?.detect(bitmap) ?: bitmap
                //drawImageOnTexture(processedBitmap)
            } else {
                drawImageOnTexture(bitmap)
            }
        }
        fun cameraUpdate(inputStream: DataInputStream) {
            operatorDataUpdate()
            droneMarker(GeoPoint(droneLat, droneLon))
            val imgLength = inputStream.readInt()
            val imgBytes = ByteArray(imgLength)
            inputStream.readFully(imgBytes)
            val bitmap = BitmapFactory.decodeByteArray(imgBytes, 0, imgLength)
            if (!detectionStatus) {
                drawImageOnTexture(bitmap)
                return
            }
            frameCount++
            if (selectedModel != models_Spinner?.selectedItem.toString()) {
                selectedModel = models_Spinner?.selectedItem.toString()
                detector = this.plugin?.let {
                    Detector(
                        it,
                        selectedModel,
                        "labels.txt",
                        this
                    )
                }
                detector?.setup()
            }
            if ((frameCount % parentDropDown!!.fragments[1].view?.findViewById<TextView>(R.id.frameValue_textView)?.text?.removeSuffix(" frame").toString().toInt() == 0)) {
                if (detectionFuture == null || detectionFuture!!.isDone) {
                    val bmpToProcess = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    detectionFuture = detectionExecutor.submit<Bitmap> {
                        detector?.detect(bmpToProcess) ?: bmpToProcess
                    }
                    Thread {
                        val result = detectionFuture!!.get()
                        if (parentDropDown!!.fragments[1].view?.findViewById<TextView>(R.id.frameValue_textView)?.text?.removeSuffix(" frame").toString().toInt() == 1) {
                            droneCamera_textureView?.post {
                                drawImageOnTexture(result)
                            }
                        }
                    }.start()
                }
            }
            else {
                drawImageOnTexture(bitmap)
            }
        }
        private fun drawImageOnTexture(bitmap: Bitmap) {
            if (!textureReady) {
                pending_Bitmap = bitmap
                return
            }
            droneCamera_textureView?.post {
                val canvas = droneCamera_textureView?.lockCanvas()
                if (canvas != null) {
                    try {
                        val destRect = Rect(0, 0, canvas.width, canvas.height)
                        canvas.drawBitmap(bitmap, null, destRect, null)
                    } finally {
                        droneCamera_textureView?.unlockCanvasAndPost(canvas)
                    }
                }
            }
            /*
            droneCamera?.let { textureView ->
                val canvas = textureView.lockCanvas()
                if (canvas != null) {
                    try {
                        val destRect = Rect(0, 0, canvas.width, canvas.height)
                        canvas.drawBitmap(bitmap, null, destRect, null)
                    } finally {
                        textureView.unlockCanvasAndPost(canvas)
                    }
                }
            }

             */
        }
        override fun onEmptyDetect(frame: Bitmap): Bitmap {
            return frame
        }
        override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long, frame: Bitmap): Bitmap {
            val selectedClasses: Set<String> = parentDropDown!!.fragments[1].dataModel!!
                .filter { it.checked }
                .mapNotNull { it.name }
                .toSet()
                ?: emptySet()
            val selectAllChecked = parentDropDown!!.fragments[1].dataModel!![0].checked
            val boxesToDraw = if (selectAllChecked) boundingBoxes
            else boundingBoxes.filter { it.clsName in selectedClasses}

            val mutableBitmap = frame.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val imageWidth = frame.width
            val imageHeight = frame.height
            for (boundingBox in boxesToDraw) {
                if(!(boundingBox.cnf >= (parentDropDown!!.fragments[1].view?.findViewById<TextView>(R.id.thresholdValue_textView)?.text.toString().removeSuffix(" %").toFloat()/100)))
                {
                    continue
                }
                val left = boundingBox.x1 * imageWidth
                val top = boundingBox.y1 * imageHeight
                val right = boundingBox.x2 * imageWidth
                val bottom = boundingBox.y2 * imageHeight
                val detectionPaint: Paint by lazy {
                    Paint().apply {
                        style = Paint.Style.STROKE
                        color = Color.RED
                        strokeWidth = parentDropDown!!.fragments[1].view?.findViewById<TextView>(R.id.bboxValue_textView)?.text?.removeSuffix("dp").toString().toFloat()
                        textSize = 40f
                    }
                }
                val heightMap: Map<String?, Double> = parentDropDown!!.fragments[1].avgHeightData
                        ?.associate { data ->
                        val lbl = data.label?.removeSuffix(" : ")?.trim()?.lowercase()
                        val h = data.height?.removeSuffix(" m")?.toDoubleOrNull() ?: 0.0
                        lbl to h
                    } ?: emptyMap()
                val clsKey = boundingBox.clsName.lowercase()
                val realHeight = heightMap[clsKey]
                val labelText = boundingBox.clsName
                val text: String
                if (realHeight != null && realHeight > 0.0) {
                    val distanceMeters = estimateDistanceToObject(
                        boundingBox = boundingBox,
                        imageHeight = imageHeight,
                        droneAltitudeMeters = droneAlt,
                        cameraPitchDegrees = gimbalPitch,
                        objectRealHeightMeters = realHeight
                    )
                    val distanceText = if (distanceMeters.isNaN()) "?" else String.format("%.2f m", distanceMeters)
                    text = "$labelText ${(boundingBox.cnf * 100).toInt()}% $distanceText"
                    val centerX = (boundingBox.x1 + boundingBox.x2) / 2.0
                    val deltaX = centerX - 0.5
                    val horizFov = 75.8
                    val angleOffsetH = deltaX * horizFov
                    val azimuth = (droneYaw + angleOffsetH + 360) % 360
                    val (objLat, objLon) = offsetLatLng(droneLat, droneLon, azimuth, distanceMeters)
                    if (parentDropDown!!.fragments[1].view?.findViewById<CheckBox>(R.id.drawMarker_checkBox)?.isChecked == true) {
                        sendMarker("a-h-G-U-C-I", GeoPoint(objLat, objLon), boundingBox, 18.0)
                    }
                    if (parentDropDown!!.fragments[1].view?.findViewById<CheckBox>(R.id.drawBbox_checkBox)?.isChecked == true) {
                        canvas.drawText(
                            "Lat: %.6f".format(objLat),
                            right + 10,
                            top + 25,
                            detectionPaint
                        )
                        canvas.drawText(
                            "Lon: %.6f".format(objLon),
                            right + 10,
                            top + 75,
                            detectionPaint
                        )
                    }
                } else {
                    text = "$labelText ${(boundingBox.cnf * 100).toInt()}%"
                }
                if (parentDropDown!!.fragments[1].view?.findViewById<CheckBox>(R.id.drawBbox_checkBox)?.isChecked == true) {
                    canvas.drawRect(left, top, right, bottom, detectionPaint)
                    canvas.drawText(text, left - 45, top - 10, detectionPaint)
                }
            }
            return mutableBitmap
        }
    }
        class TestPagerAdapter(fm: FragmentManager, private val fragments: List<Fragment>) :
        FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getCount(): Int = fragments.size

        override fun getItem(position: Int): Fragment = fragments[position]

        override fun getItemPosition(`object`: Any): Int = PagerAdapter.POSITION_NONE
    }
}

fun estimateDistanceToObject(
    boundingBox: BoundingBox,
    imageHeight: Int,
    verticalFovDegrees: Double = 46.4,
    droneAltitudeMeters: Double = 0.0,
    cameraPitchDegrees: Double = 0.0,
    objectRealHeightMeters: Double = 1.75
): Double {
    val bboxHeightPixels = (boundingBox.y2 - boundingBox.y1) * imageHeight
    if (bboxHeightPixels <= 0) return Double.NaN

    val bboxCenterY = (boundingBox.y1 + boundingBox.y2) / 2.0
    val deltaY = bboxCenterY - 0.5
    val angleOffsetDegrees = deltaY * verticalFovDegrees
    val totalAngleDegrees = cameraPitchDegrees + angleOffsetDegrees
    if (droneAltitudeMeters > 0.5 && totalAngleDegrees < -5.0) {
        val totalAngleRadians = Math.toRadians(totalAngleDegrees)
        return droneAltitudeMeters / tan(-totalAngleRadians)
    }
    val verticalFovRadians = Math.toRadians(verticalFovDegrees)
    return (objectRealHeightMeters * imageHeight) /
            (2 * bboxHeightPixels * tan(verticalFovRadians / 2))
}

fun offsetLatLng(
    lat0: Double,
    lon0: Double,
    bearingDegrees: Double,
    distanceMeters: Double
): Pair<Double, Double> {
    val r_earth = 6_371_000.0
    val br = Math.toRadians(bearingDegrees)
    val lat = Math.toRadians(lat0)
    var lon = Math.toRadians(lon0)
    val tmp = distanceMeters / r_earth

    // formuła na nowe φ2 i λ2
    val lat_new = asin(sin(lat) * cos(tmp) + cos(lon) * sin(tmp) * cos(br))
    val lon_new = lon + atan2(
        sin(br) * sin(tmp) * cos(lat),
        cos(tmp) - sin(lat) * sin(lat_new)
    )

    return Pair(Math.toDegrees(lat_new), Math.toDegrees(lon_new))
}









