#include <jni.h>
#include "clogger.h"
#include "onvif_device.h"

JNIEXPORT jlong JNICALL
AndrONVIFDevice__nativeNew(JNIEnv * env, jobject thiz, jstring url){
    const char * native_url = (*env)->GetStringUTFChars(env, url, 0);
    OnvifDevice * device = OnvifDevice__new((char *)native_url);
    (*env)->ReleaseStringUTFChars(env, url, native_url);
    return (jlong) device;
}

JNIEXPORT jstring JNICALL
AndrONVIFDevice__getHostname(JNIEnv * env, jobject thiz, jlong pointer){
    OnvifDevice * device = ONVIF_DEVICE((void*)pointer);
    char * hostname = OnvifDevice__get_host(device);
    jstring *java_str = (*env)->NewStringUTF (env, hostname);
    free(hostname);

    return java_str;
}

JNIEXPORT jstring JNICALL
AndrONVIFDevice__getProtocol(JNIEnv * env, jobject thiz, jlong pointer){
    OnvifDevice * device = ONVIF_DEVICE((void*)pointer);
    char * proto = OnvifDevice__get_protocol(device);
    jstring *java_str = (*env)->NewStringUTF (env, proto);
    free(proto);

    return java_str;
}

static int DEFAULT_HTTPS_PORT = 443;
static int DEFAULT_HTTP_PORT = 80;

JNIEXPORT jint JNICALL
AndrONVIFDevice__getPort(jlong pointer){
    OnvifDevice * device = ONVIF_DEVICE((void*)pointer);
    char * strport = OnvifDevice__get_port(device);
    jint ret = -1;
    if(!strport){ //Default
        char * protocol = OnvifDevice__get_protocol(device);
        if(!strcmp(protocol,"https")){
            ret = (jint) DEFAULT_HTTPS_PORT;
        } else if (!strcmp(protocol,"http")){
            ret = (jint) DEFAULT_HTTP_PORT;
        }
        free(protocol);
    } else {
        ret = (jint) atoi(strport);;
        free(strport);
    }

    return ret;
}

JNIEXPORT void JNICALL
AndrONVIFDevice__setCredentials(JNIEnv * env, jobject thiz, jlong pointer, jstring username, jstring password){
    const char * native_username = (*env)->GetStringUTFChars(env, username, 0);
    const char * native_password = (*env)->GetStringUTFChars(env, password, 0);
    OnvifDevice__set_credentials(ONVIF_DEVICE((void*)pointer),native_username,native_password);
    (*env)->ReleaseStringUTFChars(env, username, native_username);
    (*env)->ReleaseStringUTFChars(env, password, native_password);
}

JNIEXPORT jint JNICALL
AndrONVIFDevice__authenticate(JNIEnv * env, jobject thiz, jlong pointer){
    return (jint) OnvifDevice__authenticate(ONVIF_DEVICE((void*)pointer));
}

JNIEXPORT jlong JNICALL
AndrONVIFDevice__fetchScopes(JNIEnv * env, jobject thiz, jlong pointer) {
    g_return_val_if_fail(pointer > 0, 0);
    g_return_val_if_fail(ONVIF_IS_DEVICE((void*)pointer), 0);
    OnvifDevice * dev = ONVIF_DEVICE((void*)pointer);
    OnvifDeviceService * dev_service = OnvifDevice__get_device_service(dev);
    OnvifScopes * scopes = OnvifDeviceService__getScopes(dev_service);

    return (jlong)scopes;
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = {
        { "nativeNew", "(Ljava/lang/String;)J",(void*)AndrONVIFDevice__nativeNew},
        { "getHostname", "(J)Ljava/lang/String;",(void*)AndrONVIFDevice__getHostname},
        { "getProtocol", "(J)Ljava/lang/String;", (void*) AndrONVIFDevice__getProtocol},
        { "getPort", "(J)I", (void *) AndrONVIFDevice__getPort},
        {"setCredentials", "(JLjava/lang/String;Ljava/lang/String;)V", (void *) AndrONVIFDevice__setCredentials},
        {"authenticate", "(J)I", (void *) AndrONVIFDevice__authenticate},
        {"fetchScopes", "(J)J", (void *) AndrONVIFDevice__fetchScopes}
};

/* Library initializer */
void AndrONVIFDevice__register_jni_class(JNIEnv *env){
    jclass klass = (*env)->FindClass (env,"net/quedale/andronvif/jni/AndrONVIFDevice");
    (*env)->RegisterNatives (env, klass, native_methods, G_N_ELEMENTS (native_methods));
}