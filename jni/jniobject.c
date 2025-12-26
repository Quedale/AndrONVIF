#include "jniobject.h"
#include <glib-object.h>
#include "clogger.h"

JNIEXPORT void JNICALL
JNIObject_object_ref(jlong pointer) {
    C_TRACE("ref %p",(void*) pointer);
    g_object_ref(G_OBJECT((void*) pointer));
}

JNIEXPORT void JNICALL
JNIObject_object_unref(jlong pointer) {
    C_TRACE("unref %p",(void*) pointer);
    g_object_unref(G_OBJECT((void*) pointer));
}

JNIEXPORT void JNICALL
JNIObject_native_free(jlong pointer) {
    C_TRACE("free %p",(void*) pointer);
    free((void*) pointer);
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = {
        { "object_ref", "(J)V", (void *) JNIObject_object_ref},
        {"object_unref", "(J)V", (void *) JNIObject_object_unref},
        {"native_free", "(J)V", (void *) JNIObject_native_free}
};

/* Library initializer */
void JNIObject__register_jni_class(JNIEnv *env){
    jclass klass = (*env)->FindClass (env,"net/quedale/andronvif/jni/JNIObject");
    (*env)->RegisterNatives (env, klass, native_methods, G_N_ELEMENTS (native_methods));
}