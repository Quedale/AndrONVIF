//
// Created by quedale on 11/13/26.
//

#include <jni.h>
#include "clogger.h"
#include "onvif_device.h"
#include "media/onvif_media_profile.h"

JNIEXPORT jint JNICALL
AndrONVIFProfiles__nativeGetSize(jlong profile_pointer) {
    g_return_val_if_fail(profile_pointer != 0, 0);
    g_return_val_if_fail(ONVIF_IS_MEDIA_PROFILES((void*)profile_pointer), 0);
    OnvifMediaProfiles * profiles = ONVIF_MEDIA_PROFILES((void*)profile_pointer);
    return OnvifMediaProfiles__get_size(profiles);
}

JNIEXPORT jlong JNICALL
AndrONVIFProfiles__nativeGetProfileAt(jlong profile_pointer, int index) {
    g_return_val_if_fail(profile_pointer != 0, 0);
    g_return_val_if_fail(index >= 0, 0);
    g_return_val_if_fail(ONVIF_IS_MEDIA_PROFILES((void*)profile_pointer), 0);
    OnvifMediaProfiles * profiles = ONVIF_MEDIA_PROFILES((void*)profile_pointer);
    return (long) OnvifMediaProfiles__get_profile(profiles, index);
}

JNIEXPORT jint JNICALL
AndrONVIFProfile__nativeGetIndex(jlong profile_pointer) {
    g_return_val_if_fail(profile_pointer != 0, 0);
    g_return_val_if_fail(ONVIF_IS_MEDIA_PROFILE((void*)profile_pointer), 0);
    OnvifMediaProfile * profile = ONVIF_MEDIA_PROFILE((void*)profile_pointer);
    return OnvifMediaProfile__get_index(profile);
}

JNIEXPORT jstring JNICALL
AndrONVIFProfile__nativeGetName(JNIEnv * env, jclass klass, jlong profile_pointer) {
    g_return_val_if_fail(profile_pointer != 0, 0);
    g_return_val_if_fail(ONVIF_IS_MEDIA_PROFILE((void*)profile_pointer), 0);
    OnvifMediaProfile * profile = ONVIF_MEDIA_PROFILE((void*)profile_pointer);
    jstring *java_str = (*env)->NewStringUTF (env, OnvifMediaProfile__get_name(profile));
    return java_str;
}

JNIEXPORT jstring JNICALL
AndrONVIFProfile__nativeGetToken(JNIEnv * env, jclass klass, jlong profile_pointer) {
    g_return_val_if_fail(profile_pointer != 0, 0);
    g_return_val_if_fail(ONVIF_IS_MEDIA_PROFILE((void*)profile_pointer), 0);
    OnvifMediaProfile * profile = ONVIF_MEDIA_PROFILE((void*)profile_pointer);
    jstring *java_str = (*env)->NewStringUTF (env, OnvifMediaProfile__get_token(profile));
    return java_str;
}

JNIEXPORT jboolean JNICALL
AndrONVIFProfile__nativeIsFixed(jlong profile_pointer) {
    g_return_val_if_fail(profile_pointer != 0, 0);
    g_return_val_if_fail(ONVIF_IS_MEDIA_PROFILE((void*)profile_pointer), 0);
    OnvifMediaProfile * profile = ONVIF_MEDIA_PROFILE((void*)profile_pointer);
    return OnvifMediaProfile__is_fixed(profile);
}

static JNINativeMethod profiles_native_methods[] = {
        { "nativeGetSize", "(J)I",(void*)AndrONVIFProfiles__nativeGetSize},
        { "nativeGetProfileAt", "(JI)J",(void*)AndrONVIFProfiles__nativeGetProfileAt}
};

static JNINativeMethod profile_native_methods[] = {
        { "nativeGetIndex", "(J)I",(void*)AndrONVIFProfile__nativeGetIndex},
        { "nativeGetName", "(J)Ljava/lang/String;",(void*)AndrONVIFProfile__nativeGetName},
        { "nativeGetToken", "(J)Ljava/lang/String;", (void*) AndrONVIFProfile__nativeGetToken},
        { "nativeIsFixed", "(J)Z", (void *) AndrONVIFProfile__nativeIsFixed},
};

void AndrONVIFProfile__register_jni_class(JNIEnv *env){
    jclass klass = (*env)->FindClass (env, "net/quedale/andronvif/jni/AndrONVIFMediaProfiles");
    (*env)->RegisterNatives (env, klass, profiles_native_methods, G_N_ELEMENTS (profiles_native_methods));

    klass = (*env)->FindClass (env, "net/quedale/andronvif/jni/AndrONVIFMediaProfiles$AndrONVIFProfile");
    (*env)->RegisterNatives (env, klass, profile_native_methods, G_N_ELEMENTS (profile_native_methods));
}