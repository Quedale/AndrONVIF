package net.quedale.andronvif.jni;

import android.util.Log;

import dalvik.annotation.optimization.CriticalNative;

public class DiscoveryService extends JNIObject {
    @CriticalNative
    private static native long nativeNew(int repeat, int timeout);
    //Not CriticalNative because it needs callback capabilities
    //Not FastNative because this is long running
    private native void nativeDiscover(long pointer);
    @CriticalNative
    private static native void nativeStop(long pointer);

    private DiscoveryMatchFoundCallback match_found_cb;

    public DiscoveryService(){
        super(nativeNew(2,2));
        Log.d(this.getClass().getSimpleName(), "pointer_address : " + pointer_address);
    }

    public void discover(DiscoveryMatchFoundCallback match_found_cb){
        this.match_found_cb = match_found_cb;
        Log.d(this.getClass().getSimpleName(), "discover");
        nativeDiscover(this.pointer_address);
    }

    public void stop(){
        //Stop will get called from another thread.
        //Pointer check in case it was disposed before stop was called
        if(this.pointer_address > 0)
            nativeStop(this.pointer_address);
    }

    //This function is the callback from native JNI C code
    @SuppressWarnings("unused")
    private void nativeMatchCallback(Long pointer) {
        Log.d(this.getClass().getSimpleName(), "nativeMatchCallback" + pointer);
        DiscoveryMatch match = new DiscoveryMatch(pointer);
        this.match_found_cb.matchFound(match);
    }
}
