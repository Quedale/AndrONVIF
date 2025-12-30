#include "andronvif_scopes.h"
#include <glib.h>
#include "devicemgmt/onvif_device_scopes.h"

static JNIEXPORT jstring JNICALL
AndrONVIFScopes__extractScope(JNIEnv * env, jclass clazz, jlong pointer, jstring key) {
    g_return_val_if_fail(pointer > 0, NULL);
    g_return_val_if_fail(ONVIF_IS_DEVICE_SCOPES((void*)pointer), NULL);

    const char * native_key = (*env)->GetStringUTFChars(env, key, 0);

    OnvifScopes * scopes = ONVIF_DEVICE_SCOPES((void*)pointer);
    char * scope_val = OnvifScopes__extract_scope(scopes,(char *)native_key);

    (*env)->ReleaseStringUTFChars(env, key, native_key);

    jstring *java_str = (*env)->NewStringUTF (env, scope_val);

    free(scope_val);
    return java_str;
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = {
        { "extractScope", "(JLjava/lang/String;)Ljava/lang/String;",(void*)AndrONVIFScopes__extractScope}
};

/* Library initializer */
void AndrONVIFScopes__register_jni_class(JNIEnv *env){
    jclass klass = (*env)->FindClass (env,"net/quedale/andronvif/jni/AndrONVIFScopes");
    (*env)->RegisterNatives (env, klass, native_methods, G_N_ELEMENTS (native_methods));
}