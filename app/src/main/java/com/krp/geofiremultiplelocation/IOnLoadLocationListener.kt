package com.krp.geofiremultiplelocation

interface IOnLoadLocationListener {
    fun onLocationLoadSuccess(latLngs:List<MyLatLng>)
    fun onLocationLoadFailed(message : String)
}