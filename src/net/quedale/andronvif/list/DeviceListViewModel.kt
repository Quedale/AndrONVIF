package net.quedale.andronvif.list

import android.app.Application
import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
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
        kotlinx.coroutines.MainScope().launch {
            initDevice(device)
        }
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
    fun CoroutineScope.initDevice(device: AndrONVIFDevice){
        launch(Dispatchers.IO){
            Log.d("DeviceListViewModel", "Fetching scopes : " + device.url)
            when (val scopefault = device.fetchScopes()) {
                JNISoapObject.SoapFault.SOAP_FAULT_NONE -> {
                    withContext(Dispatchers.Main) {
                        device.setState(AndrONVIFDevice.STATE_SCOPE_MASK)
                    }
                }
                else -> { // TODO Error handling
                    Log.d("DeviceListViewModel", "Failed to fetch scopes " + scopefault + " : " + device.url)
                }
            }
        }
        launch(Dispatchers.IO){
            Log.d("DeviceListViewModel", "Fetching snapshot : " + device.url)
            val snapshot = device.fetchSnapshot()
            when (val snapfault = snapshot.fault) {
                JNISoapObject.SoapFault.SOAP_FAULT_NONE -> {
                    withContext(Dispatchers.Main) {
                        device.setState(AndrONVIFDevice.STATE_SNAP_MASK)
                    }
                }
                else -> { // TODO Error handling
                    Log.d("DeviceListViewModel", "Failed to fetch snapshot " + snapfault + " : " + device.url)
                }
            }
        }
    }

    fun CoroutineScope.authDevice(device: AndrONVIFDevice){
        launch {
            when (val fault = device.authenticate()) {
                JNISoapObject.SoapFault.SOAP_FAULT_NONE -> {
                    withContext(Dispatchers.Main) {
                        device.setState(AndrONVIFDevice.STATE_AUTHENTICATED_MASK)
                    }
                    initDevice(device)
                }

                else -> { // TODO Error handling
                    Log.d(
                        "DeviceListViewModel",
                        "Failed to authenticate device " + fault + " : " + device.url
                    )
                }
            }
        }
    }

    fun extractDevicesFromDB():MutableList<AndrONVIFDevice>{
        //Extract data from application DB
        val db = Room.databaseBuilder(
            getApplication(),
            AndrONVIFDatabase::class.java,
            "devices"
        ).build()
        val dao = db.deviceDao()
        val dbdevices = dao.getAll()

        var foundDevice: AndrONVIFDevice? = null
        val androdevices: MutableList<AndrONVIFDevice> = ArrayList()
        for (device in dbdevices) {
            val dbdevUrl = convertDBDeviceToString(device)
            for (existingDev in prepostDevices) {
                if (existingDev.url.equals(dbdevUrl)) {
                    foundDevice = existingDev
                    break
                }
            }

            if (foundDevice == null) {
                val newdev = AndrONVIFDevice(dbdevUrl)
                newdev.setCredentials(device.username, device.password)
                androdevices.add(newdev)
            } else {
                Log.d("DeviceListViewModel", "Device already added $dbdevUrl")
            }
        }
        db.close()

        return androdevices
    }

    fun start(){
        setState(State.INITIALIZING)
        kotlinx.coroutines.MainScope().launch(Dispatchers.IO) { //Launching with ApplicationScope since this list is the primary model
            coroutineScope {
                setState(State.LOADING)

                val androdevices = extractDevicesFromDB()

                //Initialize native devices
                for (androdev in androdevices) {
                    authDevice(androdev)
                }

                //The loop above should be fast enough that adding device individually would be irrelevant
                if (androdevices.isNotEmpty()) {
                    setDevices(androdevices)
                }
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

    override fun onCleared() {
        super.onCleared()
        for (device in prepostDevices) {
            device.unref()
        }
        prepostDevices.clear()
        devices.value = prepostDevices
    }
}