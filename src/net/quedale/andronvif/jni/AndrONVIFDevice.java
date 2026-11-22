package net.quedale.andronvif.jni;

import android.util.Log;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import org.jspecify.annotations.NonNull;

import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;

public class AndrONVIFDevice extends JNIObject {
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
    private static native long fetchProfiles(long pointer);
    //Not FastNative because of slow IO calls
    private static native long fetchSnapshot(long pointer, int profile_index);

    private transient final MutableLiveData<Boolean> authenticated = new MutableLiveData<>();
    private final MutableLiveData<AndrONVIFDeviceDetails> details = new MutableLiveData<>();
    private final MutableLiveData<AndrONVIFSnapshot> snapshot = new MutableLiveData<>();
    private transient final MutableLiveData<JNISoapObject.SoapException> scope_exception = new MutableLiveData<>();
    private transient final MutableLiveData<JNISoapObject.SoapException> snap_exception = new MutableLiveData<>();
    private transient final MutableLiveData<JNISoapObject.SoapException> auth_exception = new MutableLiveData<>();
    private final String url;
    private AndrONVIFMediaProfiles profiles = null;
    private final MutableLiveData<AndrONVIFMediaProfiles.AndrONVIFProfile> selected_profile = new MutableLiveData<>();

    public AndrONVIFDevice(String url) {
        super(nativeNew(url));
        this.url = url;
    }

    public AndrONVIFDevice(DiscoveryMatch match) {
        super(nativeNew(match.getAddressAt(0))); //TODO support multiple address
        this.url = match.getAddressAt(0);
        this.details.postValue(new AndrONVIFDeviceDetails(match.getScopeValue("name"), match.getScopeValue("location"), match.getScopeValue("hardware")));
    }

    public void authenticate() throws JNISoapObject.SoapInvalidDeviceException, JNISoapObject.SoapConnectionException, JNISoapObject.SoapActionNotSupportedException, JNISoapObject.SoapUnauthorizedException, JNISoapObject.SoapUnexpectedException {
        Boolean isAuth = authenticated.getValue();
        if(isAuth != null && isAuth){
            Log.d("AndrONVIFDevice","Device already authenticated.");
            return;
        }
        try {
            JNISoapObject.handleSoapException(AndrONVIFDevice.authenticate(this.pointer_address));
            auth_exception.postValue(null);
            authenticated.postValue(true);
        } catch (JNISoapObject.SoapException e){
            auth_exception.postValue(e);
            throw e;
        }
    }

    public boolean isAuthenticated(){
        Boolean isAuth = authenticated.getValue();
        return isAuth != null && isAuth;
    }

    public void setCredentials(String username, String password){
        setCredentials(this.pointer_address,username,password);
    }

    public AndrONVIFSnapshot getSnapshot(){
        return this.snapshot.getValue();
    }

    public AndrONVIFMediaProfiles fetchProfiles() throws JNISoapObject.SoapException {
        if(profiles != null){
            profiles = null;
        }

        profiles = new AndrONVIFMediaProfiles(fetchProfiles(this.pointer_address));
        return profiles;
    }

    public void fetchSnapshot() {
        //TODO Handling multiple profiles
        AndrONVIFSnapshot oldsnap = this.snapshot.getValue();
        if(oldsnap != null){
            oldsnap.unref();
        }
        try {
            AndrONVIFSnapshot snap = new AndrONVIFSnapshot(fetchSnapshot(this.pointer_address, 0));
            this.snap_exception.postValue(null);
            this.snapshot.postValue(snap);
        } catch (JNISoapObject.SoapException e){
            this.snap_exception.postValue(e);
        }
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

    public AndrONVIFDeviceDetails getDetails(){
        //TODO fetch name if null
        return this.details.getValue();
    }

    public void fetchScopes() {
        try{
            AndrONVIFScopes scopes = new AndrONVIFScopes(fetchScopes(this.pointer_address));
            AndrONVIFDeviceDetails details = new AndrONVIFDeviceDetails(scopes.extractScope("name"), scopes.extractScope("location"), scopes.extractScope("hardware"));
            this.scope_exception.postValue(null);
            this.details.postValue(details);
        } catch (JNISoapObject.SoapException e){
            this.scope_exception.postValue(e);
        }
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
        AndrONVIFSnapshot oldsnap = this.snapshot.getValue();
        if(oldsnap != null){
            this.snapshot.postValue(null);
            oldsnap.unref();
        }
    }

    public void setSelectedProfile(AndrONVIFMediaProfiles.AndrONVIFProfile profile) {
        this.selected_profile.postValue(profile);
    }

    public AndrONVIFMediaProfiles.AndrONVIFProfile getSelectedProfile() {
        return this.selected_profile.getValue();
    }

    public void observeSelectedProfile(LifecycleOwner owner, Observer<AndrONVIFMediaProfiles.AndrONVIFProfile> observer) {
        selected_profile.observe(owner, observer);
    }

    public void observeAuthentication(@org.jspecify.annotations.NonNull LifecycleOwner owner, @NonNull Observer<Boolean> observer){
        authenticated.observe(owner, observer);
    }

    public void observeDetails(LifecycleOwner owner, Observer<AndrONVIFDeviceDetails> observer) {
        details.observe(owner, observer);
    }

    public void observeSnapshot(LifecycleOwner owner, Observer<AndrONVIFSnapshot> observer) {
        snapshot.observe(owner, observer);
    }

    public void observeSnapshotException(@org.jspecify.annotations.NonNull LifecycleOwner owner, @NonNull Observer<JNISoapObject.SoapException> observer){
        snap_exception.observe(owner, observer);
    }

    public void observeScopeException(@org.jspecify.annotations.NonNull LifecycleOwner owner, @NonNull Observer<JNISoapObject.SoapException> observer){
        scope_exception.observe(owner, observer);
    }

    public void observeAuthException(@org.jspecify.annotations.NonNull LifecycleOwner owner, @NonNull Observer<JNISoapObject.SoapException> observer){
        auth_exception.observe(owner, observer);
    }

    public static class AndrONVIFDeviceDetails {
        private final String location;
        private final String hardware;
        private final String name;
        private AndrONVIFDeviceDetails(String name, String location, String hardware){
            this.name = name;
            this.hardware = hardware;
            this.location = location;
        }

        public String getName(){
            return name;
        }

        public String getLocation(){
            return location;
        }

        public String getHardware(){
            return hardware;
        }
    }
}
