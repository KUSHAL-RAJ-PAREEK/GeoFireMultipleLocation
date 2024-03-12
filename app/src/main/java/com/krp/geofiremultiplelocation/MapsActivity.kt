package com.krp.geofiremultiplelocation


import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.media.MediaParser
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryDataEventListener
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.krp.geofiremultiplelocation.databinding.ActivityMapsBinding
import kotlin.random.Random

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, IOnLoadLocationListener,
    GeoQueryDataEventListener {

    private  var mMap: GoogleMap? = null
    private lateinit var binding: ActivityMapsBinding
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private  var currentMarker: Marker?= null
    private lateinit var myLocationRef: DatabaseReference
    private lateinit var dangerousArea: MutableList<LatLng>
    private lateinit var listener : IOnLoadLocationListener

    private lateinit var myCity : DatabaseReference
    private lateinit var lastLocation: Location
    private  var geoQuery:GeoQuery? = null
    private lateinit var geoFire : GeoFire

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //request runtime
        Dexter.withActivity(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object :PermissionListener{
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {

                    buildLocationRequest()
                    buildLocationCallBack()
                    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this@MapsActivity)
                    initArea()
                    settingGeoFire()

                    //Add dangerous Area to firebase
//                    addDangerousAreaToFirebase()
                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                 Toast.makeText(this@MapsActivity,"You must enable this permission",Toast.LENGTH_SHORT).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {

                }

            }).check()


    }

    private fun addDangerousAreaToFirebase() {
        dangerousArea = ArrayList()
        dangerousArea.add(LatLng(26.7678, 75.8543))

        //submit all list to firebase

        FirebaseDatabase.getInstance()
            .getReference("DangerousArea")
            .child("MyCity")
            .setValue(dangerousArea)
            .addOnCompleteListener{
                Toast.makeText(this@MapsActivity,"update",Toast.LENGTH_SHORT).show()
            }.addOnFailureListener{ ex ->

                Toast.makeText(this@MapsActivity,"" + ex.message,Toast.LENGTH_SHORT).show()
            }

    }

    private fun settingGeoFire() {
        myLocationRef = FirebaseDatabase.getInstance().getReference("MyLocation")
        geoFire = GeoFire(myLocationRef)
    }

    private fun initArea() {
        myCity = FirebaseDatabase.getInstance()
            .getReference("DangerousArea")
            .child("MyCity")

        listener = this

        //Add realtime change update
        myCity!!.addValueEventListener(object:ValueEventListener{
            override fun onDataChange(datasnapshot: DataSnapshot) {
                //update dangerousArea
                val latLngList = ArrayList<MyLatLng>()
                for(locationSnapShot in datasnapshot.children){
                    val latLng = locationSnapShot.getValue(MyLatLng::class.java)
                    latLngList.add(latLng!!)
                }
                listener!!.onLocationLoadSuccess(latLngList)
            }

            override fun onCancelled(error: DatabaseError) {

            }

        })
    }

    private fun buildLocationCallBack() {
        locationCallback = object : LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if(mMap != null){
                    lastLocation = locationResult.lastLocation!!
                    addUserMarker()
                }
            }
        }
    }

    private fun addUserMarker() {
        geoFire!!.setLocation("you", GeoLocation(lastLocation!!.latitude,
            lastLocation!!.longitude)){ _,_ ->
            if (currentMarker != null) {
                currentMarker!!.remove()
            }
            currentMarker = mMap!!.addMarker(MarkerOptions().position(LatLng(lastLocation!!.latitude,
                lastLocation!!.longitude))
                .title("you"))!!

            mMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(currentMarker!!.position,12.0f))

        }
    }

    private fun buildLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest!!.interval = 5000
        locationRequest!!.fastestInterval = 3000
        locationRequest!!.smallestDisplacement = 10f
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap!!.uiSettings.isZoomControlsEnabled = true

     if(fusedLocationProviderClient != null){
       if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
           if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
               return
       }
         fusedLocationProviderClient!!.requestLocationUpdates(locationRequest,locationCallback!!,Looper.myLooper())

         addCircleArea()
     }
    }

    override fun onLocationLoadSuccess(latLngs: List<MyLatLng>) {

        dangerousArea = ArrayList()
        for(myLatLng in latLngs){
            val convert = LatLng(myLatLng.latitude,myLatLng.longitude)
            dangerousArea!!.add(convert)
        }
        //now, after dangerous area is have data , we will call Map Display
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //clear map and data again
        if(mMap != null){
            mMap!!.clear()
            //Add again user Marker
            addUserMarker()
            //Add circle of Dangerous area
            addCircleArea()
        }

    }

    private fun addCircleArea() {
        if(geoQuery != null){
            //remove old listener, image if you remove an location in firebase
            //it must be remove listener in GeoFire too
            geoQuery!!.removeGeoQueryEventListener(this@MapsActivity)
            geoQuery!!.removeAllListeners()
        }
        //Add again
        for(laLng in dangerousArea!!){
            mMap?.addCircle(CircleOptions().center(laLng)
                .radius(500.0)
                .strokeColor(Color.BLUE)
                .fillColor(0x220000FF)
                .strokeWidth(5.0f)
            )

            //creatr GeoQuery when user in dangerous location
            geoQuery = geoFire!!.queryAtLocation(GeoLocation(laLng.latitude,laLng.longitude),0.5) // 0.5 == 500m
            geoQuery!!.addGeoQueryDataEventListener(this@MapsActivity)
        }
    }

    override fun onLocationLoadFailed(message: String) {
       Toast.makeText(this,""+message,Toast.LENGTH_SHORT).show()
    }

    override fun onDataEntered(p0: DataSnapshot?, p1: GeoLocation?) {
       sendNotification("KRP",String.format("%s entered the dangerous area",p0))
    }

    private fun sendNotification(title: String, content: String) {
        Toast.makeText(this,""+content,Toast.LENGTH_SHORT).show()

        val NOTIFICATION_CHANNEL_ID = "krp_multiple_location"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MyNotification", NotificationManager.IMPORTANCE_DEFAULT
            )

            //config
            notificationChannel.description = "Channel Description"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.RED
            notificationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            notificationChannel.enableVibration(true)

            notificationManager.createNotificationChannel(notificationChannel)
        }

            val builder = NotificationCompat.Builder(this,NOTIFICATION_CHANNEL_ID)
            builder.setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(false)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(resources,R.mipmap.ic_launcher))

            val notification = builder.build()
            notificationManager.notify(java.util.Random().nextInt(),notification)

    }

    override fun onDataExited(p0: DataSnapshot?) {
        sendNotification("KRP",String.format("%s leave the dangerous area",p0))
    }

    override fun onDataMoved(p0: DataSnapshot?, p1: GeoLocation?) {
        sendNotification("KRP",String.format("%s move within the dangerous area",p0))
    }

    override fun onDataChanged(p0: DataSnapshot?, p1: GeoLocation?) {

    }

    override fun onGeoQueryReady() {

    }

    override fun onGeoQueryError(error: DatabaseError?) {
        Toast.makeText(this, ""+error!!.message, Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        fusedLocationProviderClient!!.removeLocationUpdates(locationCallback!!)
        super.onStop()
    }
}