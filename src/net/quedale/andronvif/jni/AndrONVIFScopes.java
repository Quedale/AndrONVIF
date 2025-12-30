package net.quedale.andronvif.jni;

import dalvik.annotation.optimization.FastNative;

public class AndrONVIFScopes extends JNISoapObject {
    @FastNative
    private static native String extractScope(long pointer, String key);

    protected AndrONVIFScopes(long pointer) {
        super(pointer);
    }

    public String extractScope(String key){
        return extractScope(this.pointer_address,key);
    }
}
