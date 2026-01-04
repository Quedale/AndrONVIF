#include "andronvif_snapshot.h"
#include <glib.h>
#include "media/onvif_media_snapshot.h"
#include "clogger.h"

JNIEXPORT jobject JNICALL
AndrONVIFSnapshot__getNativeBuffer(JNIEnv *env, jclass klazz, jlong pointer) {
    g_return_val_if_fail(pointer != 0, NULL);
    g_return_val_if_fail(ONVIF_IS_MEDIA_SNAPSHOT((void*)pointer), NULL);

    OnvifMediaSnapshot * snapshot = ONVIF_MEDIA_SNAPSHOT((void*)pointer);

    // 2. Wrap the native memory in a Java DirectByteBuffer
    jobject directBuffer = (*env)->NewDirectByteBuffer(env,OnvifMediaSnapshot__get_buffer(snapshot), OnvifMediaSnapshot__get_size(snapshot));

    // Note: The JVM does not automatically free this memory.
    // You must manage its lifecycle (e.g., free it when Java is done).
    return directBuffer;
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = {
        { "getNativeBuffer", "(J)Ljava/nio/ByteBuffer;",(void*)AndrONVIFSnapshot__getNativeBuffer}
};

void AndrONVIFSnapshot__register_jni_class(JNIEnv *env){
    jclass klass = (*env)->FindClass (env,"net/quedale/andronvif/jni/AndrONVIFSnapshot");
    (*env)->RegisterNatives (env, klass, native_methods, G_N_ELEMENTS (native_methods));
}