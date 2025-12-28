#include "clogger.h"
#include "andronvif_player.h"
#include "jniobject.h"
#include "andronvif_device.h"
#include <unistd.h>
#include <android/log.h>

static int pfd[2];
static pthread_t thr;
static const char *tag = "myapp";

static void *thread_func(void* userdata)
{
    ssize_t rdsz;
    char buf[128];
    while((rdsz = read(pfd[0], buf, sizeof buf - 1)) > 0) {
        if(buf[rdsz - 1] == '\n') --rdsz;
        buf[rdsz] = 0;  /* add null-terminator */
        __android_log_write(ANDROID_LOG_DEBUG, tag, buf);
    }
    return 0;
}

int start_logger(const char *app_name)
{
    tag = app_name;

    /* make stdout line-buffered and stderr unbuffered */
    setvbuf(stdout, 0, _IOLBF, 0);
    setvbuf(stderr, 0, _IONBF, 0);

    /* create the pipe and redirect stdout and stderr */
    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);

    /* spawn the logging thread */
    if(pthread_create(&thr, 0, thread_func, 0) == -1)
        return -1;
    pthread_detach(thr);
    return 0;
}

/* Library initializer */
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;

    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        C_ERROR ("Could not retrieve JNIEnv");
        return 0;
    }

    AndrONVIFPlayerNew__register_jni_class(env);
    JNIObject__register_jni_class(env);
    AndrONVIFDevice__register_jni_class(env);

    //    start_logger("mylogger");

    return JNI_VERSION_1_4;
}

void
JNI_OnUnload (JavaVM * vm, void *reversed)
{
    JNIEnv *env = NULL;

    if ((*vm)->GetEnv (vm, (void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        C_ERROR ("Could not retrieve JNIEnv");
        return;
    }

}