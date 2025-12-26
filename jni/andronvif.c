#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <gst/gst.h>
#include <glib.h>
#include "clogger.h"
#include "onvif_device.h"
#include <android/log.h>

#include "andronvif_player.h"
#include "jniobject.h"
#include "andronvif_device.h"

static JavaVM *java_vm;
/* Library initializer */
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;

    java_vm = vm;

    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        __android_log_print (ANDROID_LOG_ERROR, "tutorial-3", "Could not retrieve JNIEnv");
        return 0;
    }

    AndrONVIFPlayerNew__register_jni_class(env);
    JNIObject__register_jni_class(env);
    AndrONVIFDevice__register_jni_class(env);

    return JNI_VERSION_1_4;
}