package net.quedale.andronvif.discovery;

import android.util.Log;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.utils.AbstractModel;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;


public class DiscoveryModel extends AbstractModel<DiscoveryModel.State> {
    public enum State {
        INITIALIZING,
        LOADING,
        DONE
    }

    private final List<AndrONVIFDevice> rendered_devices = new ArrayList<>();
    private final List<AndrONVIFDevice> prepost_devices = new ArrayList<>();
    private final MutableLiveData<List<AndrONVIFDevice>> devices = new MutableLiveData<>();

    public void observeDiscovery(@org.jspecify.annotations.NonNull LifecycleOwner owner, @NonNull Observer<? super List<AndrONVIFDevice>> observer){
        devices.observe(owner, observer);
    }

    void deviceRendered(AndrONVIFDevice device) {
        rendered_devices.add(device);
    }

    boolean isDeviceRendered(AndrONVIFDevice device){
        return rendered_devices.contains(device);
    }

    public void addDevice(AndrONVIFDevice device) {
        if(prepost_devices.contains(device)){
            Log.d("DeviceDiscoveryModel", "Device already found");
            device.unref(); //TODO Compare URL instead of JNI AndrONVIFDevice object
            return;
        }
        prepost_devices.add(device);
        devices.postValue(new ArrayList<>(prepost_devices));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        for(AndrONVIFDevice device : prepost_devices){
            device.unref();
        }
        prepost_devices.clear();
        rendered_devices.clear();
        devices.setValue(prepost_devices);
    }

    public void resetRendered(){
        rendered_devices.clear();
    }
}
