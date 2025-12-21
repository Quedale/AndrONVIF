#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <gst/gst.h>
#include <glib.h>
#include "clogger.h"
#include "onvif_device.h"
#include <android/log.h>

/*
 * Java Bindings
 */
static jstring
gst_native_get_gstreamer_info (JNIEnv * env, jobject thiz)
{
  char *version_utf8 = gst_version_string ();
  jstring *version_jstring = (*env)->NewStringUTF (env, version_utf8);
  g_free (version_utf8);
  return version_jstring;
}

static JNINativeMethod native_methods[] = {
  {"nativeGetGStreamerInfo", "()Ljava/lang/String;",
      (void *) gst_native_get_gstreamer_info}
};

JNIEXPORT jint
JNI_OnLoad (JavaVM * vm, void *reserved)
{
  JNIEnv *env = NULL;

  if ((*vm)->GetEnv (vm, (void **) &env, JNI_VERSION_1_4) != JNI_OK) {
    __android_log_print (ANDROID_LOG_ERROR, "AndrONVIF",
        "Could not retrieve JNIEnv");
    return 0;
  }
  jclass klass = (*env)->FindClass (env,
      "net/quedale/andronvif/AndrONVIFActivity");
  (*env)->RegisterNatives (env, klass, native_methods,
      G_N_ELEMENTS (native_methods));

  return JNI_VERSION_1_4;
}

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