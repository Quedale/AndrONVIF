#include <jni.h>
#include "clogger.h"
#include "onvif_device.h"

JNIEXPORT jlong JNICALL
Java_net_quedale_andronvif_jni_AndrONVIFDevice_nativeNew(JNIEnv * env, jobject thiz, jstring url){
    const char * native_url = (*env)->GetStringUTFChars(env, url, 0);
    OnvifDevice * device = OnvifDevice__new((char *)native_url);
    (*env)->ReleaseStringUTFChars(env, url, native_url);
    return (jlong) device;
}

JNIEXPORT jstring JNICALL
Java_net_quedale_andronvif_jni_AndrONVIFDevice_getHostname(JNIEnv * env, jobject thiz, jlong pointer){
    OnvifDevice * device = ONVIF_DEVICE((void*)pointer);
    char * hostname = OnvifDevice__get_host(device);
    jstring *java_str = (*env)->NewStringUTF (env, hostname);
    free(hostname);

    return java_str;
}

JNIEXPORT jstring JNICALL
Java_net_quedale_andronvif_jni_AndrONVIFDevice_getProtocol(JNIEnv * env, jobject thiz, jlong pointer){
    OnvifDevice * device = ONVIF_DEVICE((void*)pointer);
    char * proto = OnvifDevice__get_protocol(device);
    jstring *java_str = (*env)->NewStringUTF (env, proto);
    free(proto);

    return java_str;
}

static int DEFAULT_HTTPS_PORT = 443;
static int DEFAULT_HTTP_PORT = 80;

JNIEXPORT jint JNICALL
Java_net_quedale_andronvif_jni_AndrONVIFDevice_getPort(jlong pointer){
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
Java_net_quedale_andronvif_jni_AndrONVIFDevice_setCredentials(JNIEnv * env, jobject thiz, jlong pointer, jstring username, jstring password){
    const char * native_username = (*env)->GetStringUTFChars(env, username, 0);
    const char * native_password = (*env)->GetStringUTFChars(env, password, 0);
    OnvifDevice__set_credentials(ONVIF_DEVICE((void*)pointer),native_username,native_password);
    (*env)->ReleaseStringUTFChars(env, username, native_username);
    (*env)->ReleaseStringUTFChars(env, password, native_password);
}

JNIEXPORT jint JNICALL
Java_net_quedale_andronvif_jni_AndrONVIFDevice_authenticate(jlong pointer){
    return (jint) OnvifDevice__authenticate(ONVIF_DEVICE((void*)pointer));
}