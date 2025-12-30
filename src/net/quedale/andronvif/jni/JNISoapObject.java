package net.quedale.andronvif.jni;

import dalvik.annotation.optimization.CriticalNative;

public class JNISoapObject extends JNIObject {

    public enum SoapFault {
        SOAP_FAULT_NONE,
        SOAP_FAULT_CONNECTION_ERROR,
        SOAP_FAULT_NOT_VALID,
        SOAP_FAULT_UNAUTHORIZED,
        SOAP_FAULT_ACTION_NOT_SUPPORTED,
        SOAP_FAULT_UNEXPECTED
    }

    @CriticalNative
    private static native int getFault(long pointer);

    protected JNISoapObject(long pointer) {
        super(pointer);
    }

    public SoapFault getFault(){
        return SoapFault.values()[getFault(this.pointer_address)];
    }
}
