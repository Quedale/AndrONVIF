package net.quedale.andronvif.jni;

import java.net.ConnectException;

import dalvik.annotation.optimization.CriticalNative;

public class JNISoapObject extends JNIObject {

    private enum SoapFault {
        SOAP_FAULT_NONE,
        SOAP_FAULT_CONNECTION_ERROR,
        SOAP_FAULT_NOT_VALID,
        SOAP_FAULT_UNAUTHORIZED,
        SOAP_FAULT_ACTION_NOT_SUPPORTED,
        SOAP_FAULT_UNEXPECTED
    }

    @CriticalNative
    private static native int getFault(long pointer);

    protected JNISoapObject(long pointer) throws ConnectException, SoapUnauthorizedException, SoapInvalidDeviceException, SoapActionNotSupportedException, SoapUnexpectedException {
        super(pointer);
        handleSoapException(getFault(this.pointer_address));
    }

    protected static void handleSoapException(int error) throws ConnectException, SoapInvalidDeviceException, SoapUnauthorizedException, SoapActionNotSupportedException, SoapUnexpectedException {
        SoapFault[] values = SoapFault.values();
        if(error < 0 || error > values.length) throw new SoapUnexpectedException("Unexpected soap error code received : " + error);
        switch(values[error]){
            case SOAP_FAULT_NONE -> {}
            case SOAP_FAULT_CONNECTION_ERROR -> throw new ConnectException("Failed to connect");
            case SOAP_FAULT_NOT_VALID -> throw new SoapInvalidDeviceException();
            case SOAP_FAULT_UNAUTHORIZED -> throw new SoapUnauthorizedException();
            case SOAP_FAULT_ACTION_NOT_SUPPORTED -> throw new SoapActionNotSupportedException();
            case SOAP_FAULT_UNEXPECTED -> throw new SoapUnexpectedException();
        }
    }
}
