#include <jni.h>
#include "clogger.h"
#include "onvif_discoverer.h"

JNIEXPORT jint JNICALL
Java_net_quedale_andronvif_jni_DiscoveryMatch_getAddressCount(jlong pointer){
    ProbMatch * match = (ProbMatch *) pointer;
    return match->addrs_count;
}

JNIEXPORT jstring JNICALL
Java_net_quedale_andronvif_jni_DiscoveryMatch_getAddressAt(JNIEnv * env, jobject thiz, jlong pointer, jint index){
    ProbMatch * match = (ProbMatch *) pointer;
    if(index > match->addrs_count || index < 0){
        C_ERROR("Invalid match adddress index. %d is lower than 0 or greater than %d.",index,match->addrs_count);
        return NULL;
    }

    jstring *java_str = (*env)->NewStringUTF (env, match->addrs[index]);
    return java_str;
}

JNIEXPORT jstring JNICALL
Java_net_quedale_andronvif_jni_DiscoveryMatch_getScopeValue(JNIEnv * env, jobject thiz, jlong pointer, jstring key){
    ProbMatch * match = (ProbMatch *) pointer;
    const char * native_key = (*env)->GetStringUTFChars(env, key, 0);
    jstring *java_str = (*env)->NewStringUTF (env, ProbMatch__extract_scope((char * )native_key,match));
    (*env)->ReleaseStringUTFChars(env, key, native_key);
    return java_str;
}