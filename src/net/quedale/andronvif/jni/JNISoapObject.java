package net.quedale.andronvif.jni;

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

    protected JNISoapObject(long pointer, boolean selfClean) throws SoapConnectionException, SoapUnauthorizedException, SoapInvalidDeviceException, SoapActionNotSupportedException, SoapUnexpectedException {
        super(pointer);
        try {
            handleSoapException(getFault(this.pointer_address));
        } catch (Exception e){
            if(selfClean) this.unref();
            throw e;
        }
    }

    protected static void handleSoapException(int error) throws SoapConnectionException, SoapInvalidDeviceException, SoapUnauthorizedException, SoapActionNotSupportedException, SoapUnexpectedException {
        SoapFault[] values = SoapFault.values();
        if(error < 0 || error > values.length) throw new SoapUnexpectedException("Unexpected soap error code received : " + error);
        switch(values[error]){
            case SOAP_FAULT_NONE -> {}
            case SOAP_FAULT_CONNECTION_ERROR -> throw new SoapConnectionException();
            case SOAP_FAULT_NOT_VALID -> throw new SoapInvalidDeviceException();
            case SOAP_FAULT_UNAUTHORIZED -> throw new SoapUnauthorizedException();
            case SOAP_FAULT_ACTION_NOT_SUPPORTED -> throw new SoapActionNotSupportedException();
            case SOAP_FAULT_UNEXPECTED -> throw new SoapUnexpectedException();
        }
    }

    public static class SoapConnectionException extends SoapException {
        public SoapConnectionException() {
            super();
        }
    }

    public static class SoapInvalidDeviceException extends SoapException {
        public SoapInvalidDeviceException() {
            super();
        }
    }

    public static class SoapUnexpectedException extends SoapException {
        public SoapUnexpectedException() {
            super();
        }
        public SoapUnexpectedException(String msg) {
            super(msg);
        }
    }

    public static class SoapActionNotSupportedException extends SoapException {
        public SoapActionNotSupportedException() {
            super();
        }
    }

    public static class SoapUnauthorizedException extends SoapException {
        public SoapUnauthorizedException() {
            super();
        }
    }

    public abstract static class SoapException extends Exception {
        public SoapException(){}
        public SoapException(String msg){super(msg);}
    }
}
