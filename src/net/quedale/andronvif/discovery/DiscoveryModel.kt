package net.quedale.andronvif.discovery

import android.app.Application
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.quedale.andronvif.jni.AndrONVIFDevice
import net.quedale.andronvif.jni.DiscoveryMatch
import net.quedale.andronvif.jni.DiscoveryService
import net.quedale.andronvif.utils.AbstractModel

class DiscoveryModel(application: Application) : AbstractModel<DiscoveryModel.State>(
    application
) {
    enum class State {
        INITIALIZING,
        LOADING,
        DONE
    }

    private val renderedDevices: MutableList<AndrONVIFDevice> = ArrayList()
    private val prepostDevices: MutableList<AndrONVIFDevice> = ArrayList()
    private val devices = MutableLiveData<MutableList<AndrONVIFDevice>>()

    fun observeDiscovery(
        owner: LifecycleOwner,
        observer: Observer<in MutableList<AndrONVIFDevice>>
    ) {
        devices.observe(owner, observer)
    }

    fun deviceRendered(device: AndrONVIFDevice) {
        renderedDevices.add(device)
    }

    fun isDeviceRendered(device: AndrONVIFDevice?): Boolean {
        return renderedDevices.contains(device)
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
        super.onCleared()
        for (device in prepostDevices) {
            device.unref()
        }
        prepostDevices.clear()
        renderedDevices.clear()
        devices.value = prepostDevices
    }

    fun resetRendered() {
        renderedDevices.clear()
    }

    fun start() {
        setState(State.INITIALIZING)
        viewModelScope.launch(Dispatchers.IO){
            setState(State.LOADING)
            val service = DiscoveryService()
            service.discover { match: DiscoveryMatch? ->
                Log.d(
                    "DiscoveryModel",
                    "Device Found length " + match!!.addressCount + " : " + match.getAddressAt(0)
                )
                Log.d("DiscoveryModel", "Name " + match.getScopeValue("name"))
                Log.d("DiscoveryModel", "Hardware " + match.getScopeValue("hardware"))
                Log.d("DiscoveryModel", "Location " + match.getScopeValue("location"))
                if (match.addressCount > 0) addDevice(AndrONVIFDevice(match))
            }

            service.unref()
            setState(State.DONE)
        }
    }
}