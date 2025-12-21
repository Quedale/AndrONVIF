package net.quedale.andronvif.jni;

import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;

public class DiscoveryMatch extends JNIObject {
    @CriticalNative
    private static native int getAddressCount(long pointer);
    @FastNative //Can't be CriticalNative because it returns a String
    private static native String getAddressAt(long pointer, int index);
    @FastNative //Can't be CriticalNative because it returns a String
    private static native String getScopeValue(long pointer, String key);


    protected DiscoveryMatch(long pointer) {
        super(pointer);
    }

    public int getAddressCount(){
        return getAddressCount(this.pointer_address);
    }

    public String getAddressAt(int index){
        return getAddressAt(this.pointer_address,index);
    }

    public String getScopeValue(String key){
        return getScopeValue(this.pointer_address,key);
    }
}
