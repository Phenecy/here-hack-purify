package dev.bonch.herehackpurify.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.here.android.mpa.common.*
import com.here.android.mpa.common.PositioningManager.*
import com.here.android.mpa.guidance.TrafficUpdater
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.mapping.Map.OnTransformListener
import com.here.android.mpa.routing.*
import com.here.android.mpa.search.ErrorCode
import com.here.android.mpa.search.ReverseGeocodeRequest
import com.here.android.positioning.StatusListener
import com.here.android.positioning.StatusListener.ServiceError
import dev.bonch.herehackpurify.Main
import dev.bonch.herehackpurify.R
import dev.bonch.herehackpurify.model.pojo.Bin
import dev.bonch.herehackpurify.model.pojo.Point
import kotlinx.android.synthetic.main.fragment_map_bin_create.*
import java.io.File
import java.lang.ref.WeakReference
import java.util.*


class LocationActivity : AppCompatActivity(), OnPositionChangedListener,
    OnTransformListener {
    // map embedded in the map fragment
    private var map: Map? = null
    // map fragment embedded in this activity
    private var mapFragment: AndroidXMapFragment? = null
    // positioning manager instance
    private var mPositioningManager: PositioningManager? = null
    // HERE location data source instance
    private var mHereLocation: LocationDataSourceHERE? = null
    // flag that indicates whether maps is being transformed
    private var mTransforming = false
    // callback that is called when transforming ends
    private var mPendingUpdate: Runnable? = null
    // text view instance for showing location information
    private var mLocationInfo: TextView? = null

    private var m_tap_marker: MapScreenMarker? = null

    private var m_marker_image: Image? = null

    private var isFirst = true

    private lateinit var location: GeoCoordinate;

    lateinit var marker1: MapMarker
    lateinit var marker: MapMarker
    lateinit var marker2: MapMarker


    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasPermissions(
                this,
                *RUNTIME_PERMISSIONS
            )
        ) {
            initializeMapsAndPositioning()
        } else {
            ActivityCompat
                .requestPermissions(
                    this,
                    RUNTIME_PERMISSIONS,
                    REQUEST_CODE_ASK_PERMISSIONS
                )
        }

        initActivity()

    }

    private fun initActivity(){
        if (Main.isSecond) {
            titleCreate.text = "Выберите мусорку"

            createPointButton.setOnClickListener {
                Main.bin = Bin(false, location.latitude, location.longitude)
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("data", location.toString())
                Main.isSetPoint = true
                startActivity(intent)
            }
        } else {
            titleCreate.text = "Выберите точку сбора"
            createPointButton.setOnClickListener {
                Main.point = Point(location.latitude, location.longitude, 1, 1)
                val intent = Intent(this, LocationActivity::class.java)
                Main.isSecond = true
                startActivity(intent)
            }
        }
    }

    private fun triggerRevGeocodeRequest(coordinate: GeoCoordinate) {
        val revGecodeRequest = ReverseGeocodeRequest(coordinate)
        revGecodeRequest.locale = Locale("ru", "RU")
        revGecodeRequest.execute { p0, p1 ->
            if (p1 === ErrorCode.NONE) { /*
                             * From the location object, we retrieve the address and display to the screen.
                             * Please refer to HERE Android SDK doc for other supported APIs.
                             */
                // (location.getAddress().toString())
                location = p0.coordinate
                Main.pointAddressText = p0.address.street + " " + p0.address.houseNumber
                adress_tv.text = p0.address.street + " " + p0.address.houseNumber
            } else {
                adress_tv.text = "ERROR:RevGeocode Request returned error code:$p1"
            }
        }
    }

    /**
     * Initializes HERE Maps and HERE Positioning. Called after permission check.
     */
    private fun initializeMapsAndPositioning() {
        setContentView(R.layout.fragment_map_bin_create)
        mapFragment = getMapFragment()
        mapFragment!!.retainInstance = false
        // Set path of disk cache
        val diskCacheRoot = (this.filesDir.path
                + File.separator + ".isolated-here-maps")
        // Retrieve intent name from manifest
        var intentName = ""
        try {
            val ai = packageManager.getApplicationInfo(
                this.packageName,
                PackageManager.GET_META_DATA
            )
            val bundle = ai.metaData
            intentName = bundle.getString("INTENT_NAME")!!
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(
                this.javaClass.toString(),
                "Failed to find intent name, NameNotFound: " + e.message
            )
        }
        val success =
            MapSettings.setIsolatedDiskCacheRootPath(
                diskCacheRoot,
                intentName
            )
        if (!success) { // Setting the isolated disk cache was not successful, please check if the path is valid and
// ensure that it does not match the default location
// (getExternalStorageDirectory()/.here-maps).
// Also, ensure the provided intent name does not match the default intent name.
        } else {
            mapFragment!!.init { error ->
                if (error == OnEngineInitListener.Error.NONE) {
                    initMap()
                    (map as Map).setZoomLevel((map as Map).getMaxZoomLevel() - 1)
                    (map as Map).addTransformListener(this@LocationActivity)
                    mPositioningManager = getInstance()
                    mHereLocation = LocationDataSourceHERE.getInstance(
                        object : StatusListener {
                            override fun onOfflineModeChanged(offline: Boolean) { // called when offline mode changes
                            }

                            override fun onAirplaneModeEnabled() { // called when airplane mode is enabled
                            }

                            override fun onWifiScansDisabled() { // called when Wi-Fi scans are disabled
                            }

                            override fun onBluetoothDisabled() { // called when Bluetooth is disabled
                            }

                            override fun onCellDisabled() { // called when Cell radios are switch off
                            }

                            override fun onGnssLocationDisabled() { // called when GPS positioning is disabled
                            }

                            override fun onNetworkLocationDisabled() { // called when network positioning is disabled
                            }

                            override fun onServiceError(serviceError: ServiceError) { // called on HERE service error
                            }

                            override fun onPositioningError(positioningError: StatusListener.PositioningError) { // called when positioning fails
                            }

                            override fun onWifiIndoorPositioningNotAvailable() { // called when running on Android 9.0 (Pie) or newer
                            }

                            override fun onWifiIndoorPositioningDegraded() { // called when running on Android 9.0 (Pie) or newer
                            }
                        })
                    if (mHereLocation == null) {
                        Toast.makeText(
                            this@LocationActivity,
                            "LocationDataSourceHERE.getInstance(): failed, exiting",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    (mPositioningManager as PositioningManager).setDataSource(mHereLocation)
                    (mPositioningManager as PositioningManager).addListener(
                        WeakReference(
                            this@LocationActivity
                        )
                    )

                    // start position updates, accepting GPS, network or indoor positions
                    if ((mPositioningManager as PositioningManager).start(LocationMethod.GPS_NETWORK_INDOOR)) {
                        mapFragment!!.positionIndicator.isVisible = true
                    } else {
                        Toast.makeText(
                            this@LocationActivity,
                            "PositioningManager.start: failed, exiting",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    (mapFragment as AndroidXMapFragment).mapGesture
                        .addOnGestureListener(object : MapGesture.OnGestureListener {
                            override fun onPanStart() {
                            }

                            override fun onPanEnd() { /* show toast message for onPanEnd gesture callback */
                            }

                            override fun onMultiFingerManipulationStart() {}
                            override fun onMultiFingerManipulationEnd() {}
                            override fun onMapObjectsSelected(list: List<ViewObject>): Boolean {
                                list.forEach {
                                    if (it.getBaseType() == ViewObject.Type.USER_OBJECT) {
                                        if ((it as MapObject).getType() == MapObject.Type.MARKER) {
                                            // At this point we have the originally added
                                            // map marker, so we can do something with it
                                            // (like change the visibility, or more
                                            // marker-specific actions)
                                            if(list.indexOf(it) == 0){
                                                marker.icon.setImageResource(R.drawable.ic_curr_point)
                                                marker1.icon.setImageResource(R.drawable.ic_point)
                                                marker2.icon.setImageResource(R.drawable.ic_point)
                                                Main.bin = Bin(false, marker.coordinate.latitude, marker.coordinate.longitude)
                                                binCoordinateToText(marker)
                                            }
                                            if(list.indexOf(it) == 1){
                                                marker.icon.setImageResource(R.drawable.ic_point)
                                                marker1.icon.setImageResource(R.drawable.ic_curr_point)
                                                marker2.icon.setImageResource(R.drawable.ic_point)
                                                Main.bin = Bin(false, marker1.coordinate.latitude, marker1.coordinate.longitude)
                                                binCoordinateToText(marker1)
                                            }
                                            if(list.indexOf(it) == 2){
                                                marker.icon.setImageResource(R.drawable.ic_point)
                                                marker1.icon.setImageResource(R.drawable.ic_point)
                                                marker2.icon.setImageResource(R.drawable.ic_curr_point)
                                                Main.bin = Bin(false, marker2.coordinate.latitude, marker2.coordinate.longitude)
                                                binCoordinateToText(marker2)
                                            }

                                        }
                                    }
                                }
                                return false
                            }

                            override fun onTapEvent(pointF: PointF): Boolean { /* show toast message for onPanEnd gesture callback */
                                return false
                            }

                            override fun onDoubleTapEvent(pointF: PointF): Boolean {
                                return false
                            }

                            override fun onPinchLocked() {}
                            override fun onPinchZoomEvent(
                                v: Float,
                                pointF: PointF
                            ): Boolean {
                                return false
                            }

                            override fun onRotateLocked() {}
                            override fun onRotateEvent(v: Float): Boolean { /* show toast message for onRotateEvent gesture callback */
                                return false
                            }

                            override fun onTiltEvent(v: Float): Boolean {
                                return false
                            }

                            override fun onLongPressEvent(pointF: PointF): Boolean {
                                return false
                            }

                            override fun onLongPressRelease() {}
                            override fun onTwoFingerTapEvent(pointF: PointF): Boolean {
                                return false
                            }
                        }, 0, false)
                }
            }
        }
    }

    private fun initMap(){
        map = mapFragment!!.map
        (map as Map).setCenter(
                GeoCoordinate(
                        59.938537,
                        30.295882,
                        0.0
                ), Map.Animation.NONE
        )
        m_marker_image = Image()
        (m_marker_image as Image).setImageResource(R.drawable.ic_point)
        if (Main.isSecond) {
           marker = MapMarker(GeoCoordinate(59.88024,30.47563), m_marker_image)
           marker1 = MapMarker(GeoCoordinate(59.8787, 30.47689), m_marker_image)
           marker2 = MapMarker(GeoCoordinate(59.88149, 30.47865), m_marker_image)
            createBinMarkers()
        } else {
            markerToCenter()
        }
    }

    private fun binCoordinateToText(mark: MapMarker) {
        val revGecodeRequest = ReverseGeocodeRequest(mark.coordinate)
        revGecodeRequest.locale = Locale("ru", "RU")
        revGecodeRequest.execute { p0, p1 ->
            if (p1 === ErrorCode.NONE) {
                location = p0.coordinate
                Main.binAddressText = p0.address.street + " " + p0.address.houseNumber
                adress_tv.text = p0.address.street + " " + p0.address.houseNumber                                                    }
        }
    }

    private fun createBinMarkers() {
        map!!.addMapObject(marker)
        map!!.addMapObject(marker1)
        map!!.addMapObject(marker2)

    }

    private fun markerToCenter() {
        val myViewRect = Rect()
        cardView3.getDrawingRect(myViewRect)// getGlobalVisibleRect(myViewRect);
        if (m_tap_marker == null) {

            m_tap_marker = MapScreenMarker(
                PointF(myViewRect.exactCenterX(), myViewRect.exactCenterY()),
                m_marker_image
            )
            map!!.addMapObject(m_tap_marker)
        }
    }

    override fun onPositionUpdated(
            locationMethod: LocationMethod,
            geoPosition: GeoPosition,
            mapMatched: Boolean
    ) {
        if(!Main.isSecond) triggerRevGeocodeRequest((map as Map).center)

        val coordinate = geoPosition.coordinate
        if (mTransforming) {
            mPendingUpdate =
                    Runnable { onPositionUpdated(locationMethod, geoPosition, mapMatched) }
        } else {
            if (isFirst) {
                map!!.setCenter(coordinate, Map.Animation.BOW)
                updateLocationInfo(locationMethod, geoPosition)
                isFirst = false
            }

        }
    }

    override fun onPause() {
        super.onPause()
        if (mPositioningManager != null) {
            mPositioningManager!!.stop()
        }
    }

    override fun onResume() {
        super.onResume()
        if (mPositioningManager != null) {
            mPositioningManager!!.start(LocationMethod.GPS_NETWORK_INDOOR)
        }
    }


    override fun onPositionFixChanged(
            locationMethod: LocationMethod,
            locationStatus: LocationStatus
    ) { // ignored
    }

    override fun onMapTransformStart() {
        mTransforming = true
    }

    override fun onMapTransformEnd(mapState: MapState) {
        mTransforming = false
        if (mPendingUpdate != null) {
            mPendingUpdate!!.run()
            mPendingUpdate = null
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>,
            grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_ASK_PERMISSIONS -> {
                var index = 0
                while (index < permissions.size) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) { /*
                         * If the user turned down the permission request in the past and chose the
                         * Don't ask again option in the permission request system dialog.
                         */
                        if (!ActivityCompat
                                        .shouldShowRequestPermissionRationale(this, permissions[index])
                        ) {
                            Toast.makeText(
                                    this, "Required permission " + permissions[index]
                                    + " not granted. "
                                    + "Please go to settings and turn on for sample app",
                                    Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                    this, "Required permission " + permissions[index]
                                    + " not granted", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    index++
                }
                initializeMapsAndPositioning()
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun getMapFragment(): AndroidXMapFragment? {
        return supportFragmentManager.findFragmentById(R.id.mapfragment) as AndroidXMapFragment?
    }


    /**
     * Update location information.
     * @param geoPosition Latest geo position update.
     */
    private fun updateLocationInfo(
        locationMethod: LocationMethod,
        geoPosition: GeoPosition
    ) {
//        if (mLocationInfo == null) {
//            return
//        }

        val sb = StringBuffer()
        val coord = geoPosition.coordinate
        sb.append("Type: ")
            .append(String.format(Locale("ru", "RU"), "%s\n", locationMethod.name))
        sb.append("Coordinate:").append(
            String.format(
                Locale("ru", "RU"),
                "%.6f, %.6f\n",
                coord.latitude,
                coord.longitude
            )
        )
        if (coord.altitude != GeoCoordinate.UNKNOWN_ALTITUDE.toDouble()) {
            sb.append("Altitude:")
                .append(String.format(Locale.US, "%.2fm\n", coord.altitude))
        }
        if (geoPosition.heading != GeoPosition.UNKNOWN.toDouble()) {
            sb.append("Heading:").append(
                String.format(
                    Locale.US,
                    "%.2f\n",
                    geoPosition.heading
                )
            )
        }
        if (geoPosition.speed != GeoPosition.UNKNOWN.toDouble()) {
            sb.append("Speed:").append(
                String.format(
                    Locale.US,
                    "%.2fm/s\n",
                    geoPosition.speed
                )
            )
        }
        if (geoPosition.buildingName != null) {
            sb.append("Building: ").append(geoPosition.buildingName)
            if (geoPosition.buildingId != null) {
                sb.append(" (").append(geoPosition.buildingId).append(")\n")
            } else {
                sb.append("\n")
            }
        }
        if (geoPosition.floorId != null) {
            sb.append("Floor: ").append(geoPosition.floorId).append("\n")
        }
        sb.deleteCharAt(sb.length - 1)
        //mLocationInfo!!.text = sb.toString()
    }

    companion object {
        // permissions request code
        private const val REQUEST_CODE_ASK_PERMISSIONS = 1
        private val RUNTIME_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        /**
         * Only when the app's target SDK is 23 or higher, it requests each dangerous permissions it
         * needs when the app is running.
         */
        private fun hasPermissions(
            context: Context,
            vararg permissions: String
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && permissions != null) {
                for (permission in permissions) {
                    if (ActivityCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        return false
                    }
                }
            }
            return true
        }
    }

    private lateinit var m_route: Route
    private lateinit var m_requestInfo: TrafficUpdater.RequestInfo
    private lateinit var m_coreRouter: CoreRouter
    private lateinit var m_mapRoute: MapRoute



//    private fun calculateTta() { /*
//         * Receive arrival time for the whole m_route, if you want to get time only for part of
//         * m_route pass parameter in bounds 0 <= m_route.getSublegCount()
//         */
//        val ttaExcluding: RouteTta = m_route.getTtaExcludingTraffic(Route.WHOLE_ROUTE)
//        val ttaIncluding: RouteTta = m_route.getTtaIncludingTraffic(Route.WHOLE_ROUTE)
//        val tvInclude: TextView = findViewById(android.R.id.tvTtaInclude)
//        tvInclude.text = "Tta included: " + ttaIncluding.duration.toString()
//        val tvExclude: TextView = findViewById(android.R.id.tvTtaExclude)
//        tvExclude.text = "Tta excluded: " + ttaExcluding.duration.toString()
//    }
//
//    private fun calculateTtaUsingDownloadedTraffic() { /* Turn on traffic updates */
//        TrafficUpdater.getInstance().enableUpdate(true)
//        m_requestInfo = TrafficUpdater.getInstance().request(
//                m_route, TrafficUpdater.Listener {
//            val ttaDownloaded: RouteTta = m_route.getTtaUsingDownloadedTraffic(
//                    Route.WHOLE_ROUTE
//            )
//            UiThreadStatement.runOnUiThread(Runnable {
//                val tvDownload: TextView = findViewById(android.R.id.tvTtaDowload)
//                if (tvDownload != null) {
//                    tvDownload.text = "Tta downloaded: " + ttaDownloaded.duration.toString()
//                }
//            })
//        })
//    }
//
//    private fun calculateRoute() { /* Initialize a CoreRouter */
//        m_coreRouter = CoreRouter()
//        /* For calculating traffic on the m_route */
//        val dynamicPenalty = DynamicPenalty()
//        dynamicPenalty.trafficPenaltyMode = Route.TrafficPenaltyMode.OPTIMAL
//        m_coreRouter.setDynamicPenalty(dynamicPenalty)
//        val routePlan: RoutePlan = RouteUtil.createRoute()
//        m_coreRouter.calculateRoute(routePlan,
//                object : RouteUtil.RouteListener<List<RouteResult?>?, RoutingError?>() {
//                    override fun onCalculateRouteFinished(
//                            routeResults: List<RouteResult?>?,
//                            routingError: RoutingError?
//                    ) { /* Calculation is done. Let's handle the result */
//                        if (routingError == RoutingError.NONE) { /* Get route fro results */
//                            m_route = routeResults!![0]!!.route
//                            /* check if map route is already on map and if it is,
//                                        delete it.
//                                     */if (m_mapRoute != null) {
//                                map!!.removeMapObject(m_mapRoute)
//                            }
//                            /* Create a MapRoute so that it can be placed on the map */m_mapRoute =
//                                    MapRoute(routeResults[0].route)
//                            /* Add the MapRoute to the map */map!!.addMapObject(m_mapRoute)
//                            /*
//                                     * We may also want to make sure the map view is orientated properly so
//                                     * the entire route can be easily seen.
//                                     */map!!.zoomTo(
//                                    m_route.getBoundingBox(),
//                                    Map.Animation.NONE,
//                                    15f
//                            )
//                            /* Get TTA */calculateTta()
//                            calculateTtaUsingDownloadedTraffic()
//                        }
//                    }
//
//                })
//    }
}