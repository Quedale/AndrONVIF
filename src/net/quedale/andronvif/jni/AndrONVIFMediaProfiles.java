package net.quedale.andronvif.jni;

import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;
public class AndrONVIFMediaProfiles extends JNIObject {
    @CriticalNative
    private static native int nativeGetSize(long pointer);

    @CriticalNative
    private static native long nativeGetProfileAt(long pointer, int index);

    public int getSize(){
        return nativeGetSize(this.pointer_address);
    }

    public AndrONVIFProfile getProfileAt(int index){
        return new AndrONVIFProfile(nativeGetProfileAt(this.pointer_address,index));
    }

    protected AndrONVIFMediaProfiles(long pointer) {
        super(pointer);
    }

    public static class AndrONVIFProfile extends JNIObject {

        @CriticalNative
        private static native int nativeGetIndex(long pointer);

        @FastNative
        private static native String nativeGetName(long pointer);

        @FastNative
        private static native String nativeGetToken(long pointer);

        @CriticalNative
        private static native boolean nativeIsFixed(long pointer);

        private String name = null;
        private String token = null;

        protected AndrONVIFProfile(long pointer) {
            super(pointer);
        }

        public int getIndex() {
            return nativeGetIndex(this.pointer_address);
        }

        public String getName() {
            if (name == null) {
                name = nativeGetName(this.pointer_address);
            }
            return name;
        }

        public String getToken() {
            if (token == null) {
                token = nativeGetToken(this.pointer_address);
            }
            return token;
        }

        public boolean isFixed() {
            return nativeIsFixed(this.pointer_address);
        }
    }
}