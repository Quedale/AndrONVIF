#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <gst/gst.h>
#include <glib.h>
#include "clogger.h"
#include "onvif_device.h"
#include <android/log.h>

JNIEXPORT void JNICALL
Java_net_quedale_andronvif_jni_JNIObject_object_1ref(jlong pointer) {
  C_TRACE("ref %p",(void*) pointer);
  g_object_ref(G_OBJECT((void*) pointer));
}

JNIEXPORT void JNICALL
Java_net_quedale_andronvif_jni_JNIObject_object_1unref(jlong pointer) {
  C_TRACE("unref %p",(void*) pointer);
  g_object_unref(G_OBJECT((void*) pointer));
}

JNIEXPORT void JNICALL
Java_net_quedale_andronvif_jni_JNIObject_native_1free(jlong pointer) {
  C_TRACE("free %p",(void*) pointer);  
  free((void*) pointer);
}