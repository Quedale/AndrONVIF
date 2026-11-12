package net.quedale.andronvif.discovery

import android.Manifest
import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.quedale.andronvif.jni.AndrONVIFDevice
import net.quedale.andronvif.jni.DiscoveryService
import net.quedale.andronvif.utils.AbstractModel

import net.quedale.andronvif.PermissionHandler
import net.quedale.andronvif.jni.DiscoveryMatch

class DiscoveryModel(application: Application) : AbstractModel<DiscoveryModel.State>(
    application
) {
    enum class State {
        INITIALIZING,
        LOADING,
        DONE
    }

    private val prepostDevices: MutableList<AndrONVIFDevice> = ArrayList()
    private val devices = MutableLiveData<MutableList<AndrONVIFDevice>>()

    fun observeDiscovery(
        owner: LifecycleOwner,
        observer: Observer<in MutableList<AndrONVIFDevice>>
    ) {
        devices.observe(owner, observer)
    }

    fun addDevice(device: AndrONVIFDevice) {
        if (prepostDevices.contains(device)) {
            Log.d("DiscoveryModel", "Device already found")
            device.unref() //TODO Compare URL instead of JNI AndrONVIFDevice object
            return
        }
        prepostDevices.add(device)
        devices.postValue(ArrayList(prepostDevices))
    }

    override fun onCleared() {
        for (device in prepostDevices) {
            device.unref()
        }
        prepostDevices.clear()
        devices.value = prepostDevices
    }

    private fun launch(){
        viewModelScope.launch(Dispatchers.IO){
            setState(State.LOADING)
            val service = DiscoveryService()
            service.discover() { match: DiscoveryMatch? ->
                Log.d(
                    "DiscoveryModel",
                    "Device Found length " + match!!.addressCount + " : " + match.getAddressAt(0)
                )
                Log.d("DiscoveryModel", "Name " + match.getScopeValue("name"))
                Log.d("DiscoveryModel", "Hardware " + match.getScopeValue("hardware"))
                Log.d("DiscoveryModel", "Location " + match.getScopeValue("location"))
                if (match.addressCount > 0) addDevice(AndrONVIFDevice(match))
            }

            service.unref();
            setState(State.DONE)
        }
    }

    fun start() {
        setState(State.INITIALIZING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            PermissionHandler.getInstance().checkAndRequestPermission(
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) {
                launch();
            }
        } else {
            launch();
        }
    }
}