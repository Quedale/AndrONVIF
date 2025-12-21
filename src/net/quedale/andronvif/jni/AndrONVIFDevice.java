package net.quedale.andronvif.jni;

import java.io.Serializable;

import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;

public class AndrONVIFDevice extends JNIObject implements Serializable/*implements Parcelable*/ {
    public enum SoapFault {
        SOAP_FAULT_NONE,
        SOAP_FAULT_CONNECTION_ERROR,
        SOAP_FAULT_NOT_VALID,
        SOAP_FAULT_UNAUTHORIZED,
        SOAP_FAULT_ACTION_NOT_SUPPORTED,
        SOAP_FAULT_UNEXPECTED
    }

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
    @CriticalNative
    private static native int authenticate(long pointer);

    private final String url;
    private String location = null;
    private String hardware = null;
    private String name = null;

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

    public SoapFault authenticate(){
        return SoapFault.values()[AndrONVIFDevice.authenticate(this.pointer_address)];
    }

    public void setCredentials(String username, String password){
        AndrONVIFDevice.setCredentials(this.pointer_address,username,password);
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
        //TODO fetch hardware if null
        return this.hardware;
    }

    @Override
    public boolean equals(Object obj) {
        if(this.getClass().isInstance(obj)){
            AndrONVIFDevice devobj = (AndrONVIFDevice) obj;
            //TODO handle this.url null
            return this.url.equals(devobj.url);
        }
        return false;
    }
}
