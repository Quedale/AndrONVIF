#include "andronvif_soapobject.h"
#include "SoapObject.h"
#include <glib.h>

JNIEXPORT jint JNICALL
SoapObject__getFault(jlong pointer){
    g_return_val_if_fail(pointer != 0, SOAP_FAULT_UNEXPECTED);
    g_return_val_if_fail(SOAP_IS_OBJECT((void*)pointer), SOAP_FAULT_UNEXPECTED);

    SoapFault * faultptr = SoapObject__get_fault(SOAP_OBJECT((void*)pointer));
    return (jint) *faultptr;
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = {
        { "getFault", "(J)I",(void*)SoapObject__getFault}
};

void SoapObject__register_jni_class(JNIEnv *env){
    jclass klass = (*env)->FindClass (env,"net/quedale/andronvif/jni/JNISoapObject");
    (*env)->RegisterNatives (env, klass, native_methods, G_N_ELEMENTS (native_methods));
}