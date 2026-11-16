package net.quedale.andronvif.adddevice

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.quedale.andronvif.db.AndrONVIFDatabase
import net.quedale.andronvif.db.DBDevice
import net.quedale.andronvif.jni.AndrONVIFDevice
import net.quedale.andronvif.jni.JNIObject
import net.quedale.andronvif.utils.AbstractModel
import java.net.ConnectException

class AddDeviceModel(application: Application) : AbstractModel<AddDeviceModel.State>(application) {
    enum class State {
        INITIALIZING,
        AUTHENTICATING,
        ERROR_CONNECTION_FAILED,
        ERROR_UNAUTHORIZED,
        ERROR_UNEXPECTED,
        DONE
    }

    private var device: AndrONVIFDevice? = null
    private var hostname: String? = null
    private var port = 0
    private var isHTTPS = false
    private var username: String? = null
    private var password: String? = null

    fun getDevice(): AndrONVIFDevice? {
        return device
    }

    fun setDevice(device: AndrONVIFDevice?) {
        this.device = device
        this.device!!.ref()
    }

    fun setHostname(hostname: String?) {
        this.hostname = hostname
    }

    fun setPort(port: Int) {
        this.port = port
    }

    fun setHTTPS(iHTTPS: Boolean) {
        isHTTPS = iHTTPS
    }

    fun setUsername(username: String?) {
        this.username = username
    }

    fun setPassword(password: String?) {
        this.password = password
    }

    fun cleanup() {
        if (this.device != null) {
            this.device!!.unref()
            this.device = null
        }
    }

    fun save(){
        setState(State.INITIALIZING)
        viewModelScope.launch(Dispatchers.IO) {
            setState(State.AUTHENTICATING)
            if (device == null) {
                var strport: String? = null
                val urlbuilder: StringBuilder?
                if (isHTTPS) {
                    urlbuilder = StringBuilder("https://")
                    if (port > 0 && port != 443) {
                        strport = port.toString()
                    }
                } else {
                    urlbuilder = StringBuilder("http://")
                    if (port > 0 && port != 80) {
                        strport = port.toString()
                    }
                }
                urlbuilder.append(hostname)
                if (strport != null) {
                    urlbuilder.append(":")
                    urlbuilder.append(strport)
                }
                urlbuilder.append("/onvif/device_service")
                device = AndrONVIFDevice(urlbuilder.toString())
            }

            Log.d(
                AddDeviceModel::class.java.getSimpleName(),
                "Authenticating : " + username + "@" + device!!.url
            )
            device!!.setCredentials(username, password)
            try {
                device!!.authenticate()
                setDevice(device)
                //Persisting new device
                val db = Room.databaseBuilder(
                    getApplication(),
                    AndrONVIFDatabase::class.java,
                    "devices"
                ).build()
                val dao = db.deviceDao()
                dao.insertAll(
                    DBDevice(
                        hostname,
                        port,
                        isHTTPS,
                        username,
                        password
                    )
                )
                db.close()

                setState(State.DONE)
            } catch (_: ConnectException) {
                setState(State.ERROR_CONNECTION_FAILED)
            } catch (_: JNIObject.SoapUnexpectedException) {
                setState(State.ERROR_UNEXPECTED)
            } catch (_: JNIObject.SoapActionNotSupportedException) {
                setState(State.ERROR_UNEXPECTED) //TODO Display relevant onvif feature not supported
            } catch (_: JNIObject.SoapInvalidDeviceException) {
                setState(State.ERROR_UNEXPECTED) //TODO Display relevant not onvif error
            } catch (_: JNIObject.SoapUnauthorizedException) {
                setState(State.ERROR_UNAUTHORIZED)
            }
        }
    }
}