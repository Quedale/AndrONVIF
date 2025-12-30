package net.quedale.andronvif.list

import android.app.Application
import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.quedale.andronvif.db.AndrONVIFDatabase
import net.quedale.andronvif.db.DBDevice
import net.quedale.andronvif.jni.AndrONVIFDevice
import net.quedale.andronvif.jni.JNISoapObject
import net.quedale.andronvif.utils.AbstractModel

class DeviceListViewModel(application: Application) : AbstractModel<DeviceListViewModel.State>(
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

    fun observeDevices(owner: LifecycleOwner, observer: Observer<in MutableList<AndrONVIFDevice>>) {
        devices.observe(owner, observer)
    }

    fun getDevices(): MutableList<AndrONVIFDevice>? {
        return devices.getValue()
    }

    @MainThread
    fun addDevice(device: AndrONVIFDevice) {
        val oldlist: MutableList<AndrONVIFDevice>? = devices.getValue()
        if (oldlist != null && oldlist.contains(device)) {
            return
        }

        val newlist: MutableList<AndrONVIFDevice> = ArrayList()
        if (oldlist != null) newlist.addAll(oldlist)

        newlist.add(device)
        devices.value = newlist
    }

    fun setDevices(devices: MutableList<AndrONVIFDevice>?) {
        if (devices == null) {
            val iterator = prepostDevices.iterator()
            while (iterator.hasNext()) {
                val androdev = iterator.next()
                androdev.unref() //This may not be safe if a view doesn't add its own reference
                iterator.remove()
            }
            this.devices.postValue(ArrayList())
            return
        }

        //Clean up devices that are already rendered
        if (!devices.isEmpty()) {
            val iterator = devices.iterator()
            while (iterator.hasNext()) {
                val androdev = iterator.next()
                if (prepostDevices.contains(androdev)) {
                    androdev.unref()
                    iterator.remove()
                }
            }
        }

        //Clean up devices that no longer exists
        val iterator = prepostDevices.iterator()
        while (iterator.hasNext()) {
            val androdev = iterator.next()
            if (!devices.contains(androdev)) {
                androdev.unref() //This may not be safe if a view doesn't add its own reference
                iterator.remove()
            }
        }

        //Post new list
        prepostDevices.addAll(devices)
        this.devices.postValue(prepostDevices)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun start(){
        setState(State.INITIALIZING)

//        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
//        GlobalScope.launch {
//        viewModelScope.launch(Dispatchers.IO) {
//        applicationScope.launch {
        kotlinx.coroutines.MainScope().launch(Dispatchers.IO) { //Launching with ApplicationScope since this list is the primary model
            setState(State.LOADING)

            //Extract data from application DB
            val db = Room.databaseBuilder(
                getApplication(),
                AndrONVIFDatabase::class.java,
                "devices"
            ).build()
            val dao = db.deviceDao()
            val dbdevices = dao.getAll()

            var deviceAlreadyFound = false
            val androdevices: MutableList<AndrONVIFDevice> = ArrayList()
            for (device in dbdevices) {
                val dbdevUrl = convertDBDeviceToString(device)
                for(existingDev in prepostDevices){
                    if(existingDev.url.equals(dbdevUrl)){
                        deviceAlreadyFound = true
                        break
                    }
                }

                if(!deviceAlreadyFound){
                    val newdev = AndrONVIFDevice(dbdevUrl)
                    newdev.setCredentials(device.username, device.password)
                    androdevices.add(newdev)
                } else {
                    Log.d("NewDeviceListViewModel", "Device already added $dbdevUrl")
                }
            }
            db.close()

            //Initialize native devices
            for (androdev in androdevices) {
                launch { // Launching authentication on ApplicationScope so that devices fully setup for all views
                    when (val fault = androdev.authenticate()) {
                        JNISoapObject.SoapFault.SOAP_FAULT_NONE -> {
                            withContext(Dispatchers.Main) {
                                androdev.setState(AndrONVIFDevice.STATE_AUTHENTICATED_MASK)
                            }
                            viewModelScope.launch(Dispatchers.IO){ //Launching using ViewModelScope because the OnvifScopes are only necessary for the DevicePreview
                                when (val scopefault = androdev.fetchScopes()) {
                                    JNISoapObject.SoapFault.SOAP_FAULT_NONE -> {
                                        withContext(Dispatchers.Main) {
                                            androdev.setState(AndrONVIFDevice.STATE_SCOPE_MASK)
                                        }
                                    }
                                    else -> { // TODO Error handling
                                        Log.d("NewDeviceListViewModel", "Failed to fetch scopes " + scopefault + " : " + androdev.url)
                                    }
                                }
                            }
                        }
                        else -> { // TODO Error handling
                            Log.d("NewDeviceListViewModel", "Failed to authenticate device " + fault + " : " + androdev.url)
                        }
                    }
                }
            }

            //The loop above should be fast enough that adding device individually would be irrelevant
            if(androdevices.isNotEmpty()){
                setDevices(androdevices)
            }
            setState(State.DONE)
        }
    }

    private fun convertDBDeviceToString(device: DBDevice): String {
        val builder = StringBuilder()
        if (device.tls) builder.append("https://")
        else builder.append("http://")

        builder.append(device.address)
        if ((device.tls && device.port != 443) || (!device.tls && device.port != 80)) {
            builder.append(":")
            builder.append(device.port)
        }
        builder.append("/onvif/device_service")
        return builder.toString()
    }

    fun deviceRendered(device: AndrONVIFDevice?) {
        renderedDevices.add(device!!)
    }

    fun isDeviceRendered(device: AndrONVIFDevice?): Boolean {
        return renderedDevices.contains(device)
    }

    fun resetRendered() {
        renderedDevices.clear()
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
}