package net.quedale.andronvif.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.quedale.andronvif.jni.AndrONVIFDevice
import net.quedale.andronvif.jni.AndrONVIFMediaProfiles
import net.quedale.andronvif.jni.JNISoapObject
import net.quedale.andronvif.utils.AbstractModel
import java.net.ConnectException

class ProfileSelectionModel(application: Application) : AbstractModel<ProfileSelectionModel.State>(
    application
) {
    private val profiles = MutableLiveData<AndrONVIFMediaProfiles?>()
    private var device: AndrONVIFDevice? = null

    enum class State {
        INITIALIZING,
        LOADING,
        DONE
    }

    fun setDevice(device: AndrONVIFDevice?){
        this.device = device
    }

    fun getDevice(): AndrONVIFDevice? {
        return device
    }

    fun observeProfiles(owner: LifecycleOwner, observer: Observer<AndrONVIFMediaProfiles?>) {
        profiles.observe(owner, observer)
    }

    fun start(){
        if(device == null){
            Log.e("ProfileSelectionModel","No device set")
            return
        }

        //Clear previous value to prevent using a release pointer.
        //Alternatively, reference count could be increased and decreased as needed
        profiles.postValue(null)
        setState(State.INITIALIZING)
        kotlinx.coroutines.MainScope().launch(Dispatchers.IO) { //Launching with ApplicationScope since this list is the primary model
            coroutineScope {
                setState(State.LOADING)
                try{
                    profiles.postValue(device?.fetchProfiles())

                //TODO Display error message on UI
                } catch (_: ConnectException) {
                    Log.e("ProfileSelectionModel", "Failed to fetch media profiles from device. Failed to connect. : " + device!!.url)
                } catch (_: JNISoapObject.SoapUnexpectedException) {
                    Log.e("ProfileSelectionModel", "Failed to fetch media profiles from device. An unexcepted error occured. : " + device!!.url)
                } catch (_: JNISoapObject.SoapActionNotSupportedException) {
                    Log.e("ProfileSelectionModel", "Failed to fetch media profiles from device. Soap action not supported. : " + device!!.url)
                } catch (_: JNISoapObject.SoapInvalidDeviceException) {
                    Log.e("ProfileSelectionModel", "Failed to fetch media profiles from device. Not a valid ONVIF device. : " + device!!.url)
                } catch (_: JNISoapObject.SoapUnauthorizedException) {
                    Log.e("ProfileSelectionModel", "Failed to fetch media profiles from device. Unauthorized. : " + device!!.url)
                }
            }
            setState(State.DONE)
        }
    }
}