package net.quedale.andronvif.jni;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import org.jspecify.annotations.NonNull;

import java.io.Serializable;

import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;

public class AndrONVIFDevice extends JNIObject implements Serializable/*implements Parcelable*/ {
    public static final int STATE_AUTHENTICATED_MASK = 1;
    public static final int STATE_SCOPE_MASK = 2;
    public static final int STATE_SNAP_MASK = 4;

    private transient final MutableLiveData<Integer> stateflags = new MutableLiveData<>();

    @FastNative //Can't be CriticalNative because of String parameter
    private static native long nativeNew(String url);
    @FastNative //Can't be CriticalNative because of String return value
    private static native String getHostname(long pointer);
    @FastNative //Can't be CriticalNative because of String return value
    private static native String getProtocol(long pointer);
    @CriticalNative
    private static native int getPort(long pointer);
    @FastNative
    private static native void setCredentials(long pointer, String username, String password);
    //Not FastNative because of multiple slow IO calls
    private static native int authenticate(long pointer);
    //Not FastNative because of slow IO calls
    private static native long fetchScopes(long pointer);
    //Not FastNative because of slow IO calls
    private static native long fetchSnapshot(long pointer, int profile_index);

    private final String url;
    private String location = null;
    private String hardware = null;
    private String name = null;
    private AndrONVIFSnapshot snapshot = null;

    public AndrONVIFDevice(String url) {
        super(nativeNew(url));
        this.url = url;
    }

    public AndrONVIFDevice(DiscoveryMatch match) {
        super(nativeNew(match.getAddressAt(0))); //TODO support multiple address
        this.url = match.getAddressAt(0);
        this.name = match.getScopeValue("name");
        this.location = match.getScopeValue("location");
        this.hardware = match.getScopeValue("hardware");
    }

    public void observeStateFlags(@org.jspecify.annotations.NonNull LifecycleOwner owner, @NonNull Observer<? super Integer> observer){
        stateflags.observe(owner, observer);
    }

    public JNISoapObject.SoapFault authenticate(){
        Integer val = stateflags.getValue();
        if(val != null && (val & AndrONVIFDevice.STATE_AUTHENTICATED_MASK) != 0){
            Log.d("AndrONVIFDevice","Device already authenticated.");
            return JNISoapObject.SoapFault.SOAP_FAULT_NONE;
        }
        return JNISoapObject.SoapFault.values()[AndrONVIFDevice.authenticate(this.pointer_address)];
    }

    public void setCredentials(String username, String password){
        setCredentials(this.pointer_address,username,password);
    }

    public AndrONVIFSnapshot getSnapshot(){
        return this.snapshot;
    }

    public AndrONVIFSnapshot fetchSnapshot(){
        //TODO Handling multiple profiles
        if(snapshot != null){
            snapshot.unref();
            this.snapshot = null;
        }
        this.snapshot = new AndrONVIFSnapshot(fetchSnapshot(this.pointer_address,0));
        return this.snapshot;
    }

    public int getPort(){
        return AndrONVIFDevice.getPort(this.pointer_address);
    }

    public String getHostname(){
        return AndrONVIFDevice.getHostname(this.pointer_address);
    }

    public String getProtocol(){
        return AndrONVIFDevice.getProtocol(this.pointer_address);
    }

    public String getUrl(){
        return this.url;
    }

    public String getName(){
        //TODO fetch name if null
        return this.name;
    }

    public String getLocation(){
        //TODO fetch location if null
        return this.location;
    }

    public String getHardware(){
        return this.hardware;
    }

    @MainThread
    public void setState(int state){
        if(stateflags.getValue() != null){
            state = state + stateflags.getValue();
        }
        stateflags.setValue(state);
    }

    public JNISoapObject.SoapFault fetchScopes(){
        AndrONVIFScopes scopes = new AndrONVIFScopes(fetchScopes(this.pointer_address));
        JNISoapObject.SoapFault fault = scopes.getFault();
        if(scopes.getFault() == JNISoapObject.SoapFault.SOAP_FAULT_NONE){
            this.name = scopes.extractScope("name");
            this.hardware = scopes.extractScope("hardware");
            this.location = scopes.extractScope("location");
        }
        return fault;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) return false;
        if(this == obj) return true;

        if(this.getClass().isInstance(obj)){
            AndrONVIFDevice devobj = (AndrONVIFDevice) obj;
            //TODO handle this.url null
            return this.url.equals(devobj.url);
        }
        return false;
    }

    @Override
    public void destroyed(){
        if(this.snapshot != null){
            this.snapshot.unref();
            this.snapshot = null;
        }
    }
}
