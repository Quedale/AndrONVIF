#include <jni.h>
#include "clogger.h"
#include <glib.h>
#include "onvif_discoverer.h"
#include "onvif_device.h"

#define DISCOVERY_SREVICE_CLASSNAME "net/quedale/andronvif/jni/DiscoveryService"
#define DISCOVERY_SERVICE_METHODNAME "nativeMatchCallback"
#define LONG_CLASSNAME "java/lang/Long"

typedef struct {
    jclass long_class;
    jmethodID long_constructor;
    jmethodID discovery_service_method_id;
} DiscoveryServiceCallbackData;

typedef struct {
    JNIEnv* env;
    jobject obj;
    void * user_data;
} JNIObject;

void dicovery_probe_match_cb (OnvifDiscoverer * discoverer, OnvifProbeMatches * probematches, gpointer user_data) {
    JNIObject * jniobj = (JNIObject *) user_data;
    DiscoveryServiceCallbackData * discovery_service_data = (DiscoveryServiceCallbackData*) jniobj->user_data;
    ProbMatch * m;
    int match_count = OnvifProbeMatches__get_count(probematches);
    int i;
    for (i = 0 ; i < match_count ; ++i) {
        m = OnvifProbeMatches__get_matches(probematches)[i];

        if(m->addrs_count < 1)
            continue;

        jobject longObject = (*jniobj->env)->NewObject(jniobj->env, discovery_service_data->long_class, discovery_service_data->long_constructor, (void*)m);
        (*jniobj->env)->CallVoidMethod(jniobj->env, jniobj->obj, discovery_service_data->discovery_service_method_id,longObject);
    }
}

JNIEXPORT jlong JNICALL
Java_net_quedale_andronvif_jni_DiscoveryService_nativeNew(jint repeat, jint timeout){
    OnvifDiscoverer * discoverer = OnvifDiscoverer__new(repeat,timeout);
    C_DEBUG("New discovery %p", (void*) discoverer);
    return (jlong) discoverer;
}

JNIEXPORT void JNICALL
Java_net_quedale_andronvif_jni_DiscoveryService_nativeDiscover
(JNIEnv* env, jobject thiz, jlong pointer){
    C_DEBUG("nativeDiscover");
    jclass discovery_service_class = (*env)->FindClass(env, DISCOVERY_SREVICE_CLASSNAME);

    DiscoveryServiceCallbackData data;
    data.long_class = (*env)->FindClass(env, LONG_CLASSNAME);
    data.long_constructor = (*env)->GetMethodID(env, data.long_class, "<init>", "(J)V");
    data.discovery_service_method_id = (*env)->GetMethodID(env, discovery_service_class, DISCOVERY_SERVICE_METHODNAME, "(L"LONG_CLASSNAME";)V");

    JNIObject jobj;
    jobj.env = env;
    jobj.obj = thiz;
    jobj.user_data = &data;
    JNIObject * jobjptr = &jobj;
    OnvifDiscoverer * discoverer = ONVIF_DISCOVERER((void*)pointer); //UdpDiscoverer__create(dicovery_found_server_cb);
    long sigid = g_signal_connect (discoverer, "probe-match", G_CALLBACK (dicovery_probe_match_cb), jobjptr);
    OnvifDiscoverer__start(discoverer);
    g_signal_handler_disconnect(discoverer,sigid);
}

JNIEXPORT void JNICALL
Java_net_quedale_andronvif_jni_DiscoveryService_nativeStop(jlong pointer){
    C_DEBUG("Stopping discovery %p", (void*) pointer);
    OnvifDiscoverer__stop(ONVIF_DISCOVERER((void*)pointer));
}