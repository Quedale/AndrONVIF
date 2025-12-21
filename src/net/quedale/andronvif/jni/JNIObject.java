package net.quedale.andronvif.jni;

import android.util.Log;

import dalvik.annotation.optimization.CriticalNative;

public abstract class JNIObject {
    @CriticalNative
    protected static native void object_unref(long pointer);
    @CriticalNative
    protected static native void object_ref(long pointer);
    @CriticalNative
    protected static native void native_free(long pointer);

    protected long pointer_address;
    private int inner_ref = 1;

    protected JNIObject(long pointer){
        pointer_address = pointer;
    }

    public void ref(){
        if(this.pointer_address == 0){
            Log.e(this.getClass().getSimpleName(),"ref - Invalid Null native pointer");
            return;
        }
        Log.d(this.getClass().getSimpleName(),"ref");
        inner_ref++;
        object_ref(this.pointer_address);
    }

    public void unref(){
        if(this.pointer_address == 0){
            Log.e(this.getClass().getSimpleName(),"unref - Invalid Null native pointer");
            return;
        }
        Log.d(this.getClass().getSimpleName(),"unref");
        object_unref(this.pointer_address);
        if(--inner_ref == 0) this.pointer_address = 0;
    }

    public void free(){
        if(this.pointer_address == 0){
            Log.e(this.getClass().getSimpleName(),"free - Invalid Null native pointer");
            return;
        }
        Log.d(this.getClass().getSimpleName(),"free");
        native_free(this.pointer_address);
    }
}
