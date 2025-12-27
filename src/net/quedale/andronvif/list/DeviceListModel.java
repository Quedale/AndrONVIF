package net.quedale.andronvif.list;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelStoreOwner;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.utils.AbstractModel;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DeviceListModel extends AbstractModel<DeviceListModel.State> {
    public enum State {
        INITIALIZING,
        LOADING,
        DONE
    }

    private final List<AndrONVIFDevice> rendered_devices = new ArrayList<>();
    private final List<AndrONVIFDevice> prepost_devices = new ArrayList<>();
    private final MutableLiveData<List<AndrONVIFDevice>> devices = new MutableLiveData<>();

    public void observeDevices(@org.jspecify.annotations.NonNull LifecycleOwner owner, @NonNull Observer<? super List<AndrONVIFDevice>> observer){
        devices.observe(owner, observer);
    }

    public void setDevices(List<AndrONVIFDevice> devices){
        //TODO support null devices?

        //Clean up devices that are already rendered
        boolean was_alraady_empty = devices.isEmpty();
        if(!was_alraady_empty){
            Iterator<AndrONVIFDevice> iterator = devices.iterator();
            while (iterator.hasNext()) {
                AndrONVIFDevice androdev = iterator.next();
                if(prepost_devices.contains(androdev)){
                    androdev.unref();
                    iterator.remove();
                }
            }
        }

        //Clean up devices that no longer exists
        Iterator<AndrONVIFDevice> iterator = prepost_devices.iterator();
        while (iterator.hasNext()) {
            AndrONVIFDevice androdev = iterator.next();
            if(!devices.contains(androdev)){
                androdev.unref(); //This may not be safe if a view doesn't add its own reference
                iterator.remove();
            }
        }

        //Post new list
        this.devices.postValue(devices);
    }

    public List<AndrONVIFDevice> getDevices(){
        return devices.getValue();
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
}
