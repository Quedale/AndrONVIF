#include "andronvif_player.h"
#include <string.h>
#include <stdint.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <gst/gst.h>
#include <gst/video/video.h>
#include <pthread.h>
#include "clogger.h"
#include "onvif_device.h"

pthread_t main_thread;
GMainContext *context = NULL;  /* GLib context used to run the main loop */
GMainLoop *main_loop = NULL;   /* GLib main loop */

typedef struct {
    GstElement *pipeline;   /* The running pipeline */
    GstElement *video_sink; /* The video sink element which receives VideoOverlay commands */
    ANativeWindow *native_window; /* The Android native window where video will be rendered */
    gboolean initialized;   /* To avoid informing the UI multiple times about the initialization */

    OnvifDevice * device;
} AndrONVIFPlayerPrivate;

G_DEFINE_TYPE_WITH_PRIVATE (AndrONVIFPlayer, AndrONVIFPlayer_, G_TYPE_OBJECT)

/* Check if all conditions are met to report GStreamer as initialized.
 * These conditions will change depending on the application */
static void check_initialization_complete (AndrONVIFPlayer * self) {
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    if (!priv->initialized && priv->native_window && priv->video_sink && main_loop) {
        C_DEBUG ("Initialization complete, notifying application. native_window:%p main_loop:%p", priv->native_window, main_loop);

        /* The main loop is running and we received a native window, inform the sink about it */
        gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY (priv->video_sink), (guintptr)priv->native_window);

        priv->initialized = TRUE;
    }
}

/* Retrieve errors from the bus and show them on the UI */
static void error_cb (GstBus *bus, GstMessage *msg, AndrONVIFPlayer* self) {
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    GError *err;
    gchar *debug_info;
    gchar *message_string;

    gst_message_parse_error (msg, &err, &debug_info);
    message_string = g_strdup_printf ("Error received from element %s: %s", GST_OBJECT_NAME (msg->src), err->message);
    g_clear_error (&err);
    g_free (debug_info);

    //TODO Draw error on surface
    C_FIXME("Set error message on UI : '%s'",message_string);
    g_free (message_string);
    //TODO Auto-retry for certain errors
    gst_element_set_state (priv->pipeline, GST_STATE_NULL);
}

static void
AndrONVIFPlayer__create_pipeline(AndrONVIFPlayer* self){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    GError *error = NULL;

    /* Build pipeline */
    OnvifMediaService * mservice = OnvifDevice__get_media_service(priv->device);
    OnvifUri * muri = OnvifMediaService__getStreamUri(mservice,0);//TODO Support profile index
    char*  struri = OnvifUri__get_uri(muri);

    C_DEBUG("Playing url : %s",struri);
    //rtsp://192.168.1.17:554/cam/realmonitor?channel=1&subtype=0&unicast=true&proto=Onvif
    g_object_unref(muri);
    OnvifCredentials * creds = OnvifDevice__get_credentials(priv->device);
    char * username = OnvifCredentials__get_username(creds);
    char * password = OnvifCredentials__get_password(creds);

    //TODO Programmaitcally construct pipeline instead of launch string
    char launchstr[256];
    strcpy(launchstr,"rtspsrc location=\"");
    strcat(launchstr,struri);
    strcat(launchstr,"\" user-id=\"");
    strcat(launchstr, username);
    strcat(launchstr,"\" user-pw=\"");
    strcat(launchstr, password);
    strcat(launchstr, "\" protocols=tcp ! decodebin3 ! videoconvert ! autovideosink");

    free(username);
    free(password);

    C_DEBUG("Launch string : '%s'",launchstr);
    priv->pipeline = gst_parse_launch(launchstr, &error);
    if (error) {
        gchar *message = g_strdup_printf("Unable to build pipeline: %s", error->message);
        g_clear_error (&error);
        //TODO Draw error on surface
        C_FIXME("Set error message on UI : Could not retrieve video sink : '%s'",message);
        g_free (message);
        return;
    }

    /* Set the pipeline to READY, so it can already accept a window handle, if we have one */
    gst_element_set_state(priv->pipeline, GST_STATE_READY);

    priv->video_sink = gst_bin_get_by_interface(GST_BIN(priv->pipeline), GST_TYPE_VIDEO_OVERLAY);
    if (!priv->video_sink) {
        //TODO Draw error on surface
        C_ERROR ("Set error message on UI : Could not retrieve video sink");
        //TODO Clean up resource
        return;
    }

    /* Instruct the bus to emit signals for each received message, and connect to the interesting signals */
    GstBus * bus = gst_element_get_bus (priv->pipeline);
    GSource * bus_source = gst_bus_create_watch (bus);
    g_source_set_callback (bus_source, (GSourceFunc) gst_bus_async_signal_func, NULL, NULL);
    g_source_attach (bus_source, context);
    g_source_unref (bus_source);
    g_signal_connect (G_OBJECT (bus), "message::error", (GCallback)error_cb, self);
//    g_signal_connect (G_OBJECT (bus), "message::state-changed", (GCallback)state_changed_cb, data);
    gst_object_unref (bus);

    check_initialization_complete (self);
}

/* Main method for the native code. This is executed on its own thread. */
static void *
mainloop_function (void *userdata) {
    /* Create a GLib Main Loop and set it to run */
    C_DEBUG ("Entering main loop...");
    main_loop = g_main_loop_new (context, FALSE);

    g_main_loop_run (main_loop);
    C_DEBUG ("Exited main loop");
    g_main_loop_unref (main_loop);
    main_loop = NULL;

    /* Free resources */
    g_main_context_pop_thread_default(context);
    g_main_context_unref (context);

    return NULL;
}

static void
AndrONVIFPlayer__init(AndrONVIFPlayer* self) {
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    priv->pipeline = NULL;
    priv->video_sink = NULL;
    priv->native_window = NULL;
    priv->device = NULL;
}

static JNIEXPORT void JNICALL
AndrONVIFPlayerNew_stop(jlong pointer) {
    g_return_if_fail(pointer > 0);
    g_return_if_fail(ANDRONVIF_IS_PLAYER((void*)pointer));
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (ANDRONVIF_PLAYER((void*)pointer));
    if(priv->pipeline){
        gst_element_set_state (priv->pipeline, GST_STATE_NULL);
        gst_object_unref (priv->video_sink);
        gst_object_unref (priv->pipeline);
        priv->pipeline = NULL;
        priv->video_sink = NULL;
        priv->initialized = FALSE;
    }
}

static gboolean
AndrONVIFPlayerNew_idle_play(AndrONVIFPlayer * self){
    //TODO aquire lock in case user change camera too fast
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    AndrONVIFPlayer__create_pipeline(self);
    gst_element_set_state (priv->pipeline, GST_STATE_PLAYING);
    return FALSE;
}


static JNIEXPORT void JNICALL
AndrONVIFPlayerNew_play(jlong pointer) {
    g_return_if_fail(pointer > 0);
    g_return_if_fail(ANDRONVIF_IS_PLAYER((void*)pointer));

    AndrONVIFPlayer * self = ANDRONVIF_PLAYER((void*)pointer);
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    if(priv->pipeline){
        C_WARN("Pipeline already created");
        return;
    }

    g_main_context_invoke(context,G_SOURCE_FUNC(AndrONVIFPlayerNew_idle_play),self);
}

JNIEXPORT void JNICALL
AndrONVIFPlayerNew__set_device(jlong self_pointer, jlong device_pointer){
    C_DEBUG("AndrONVIFPlayerNew__set_device");
    g_return_if_fail(self_pointer > 0);
    g_return_if_fail(ANDRONVIF_IS_PLAYER((void*)self_pointer));
    g_return_if_fail(device_pointer > 0);
    g_return_if_fail(ONVIF_IS_DEVICE((void*)device_pointer));

    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (ANDRONVIF_PLAYER((void*)self_pointer));
    priv->device = ONVIF_DEVICE((void*)device_pointer);
}

static void
AndrONVIFPlayer__dispose (GObject *object){
    C_DEBUG("AndrONVIFPlayer__dispose");
    AndrONVIFPlayer * self = ANDRONVIF_PLAYER(object);
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    AndrONVIFPlayerNew_stop((jlong)self);

    G_OBJECT_CLASS (AndrONVIFPlayer__parent_class)->dispose (object);
}

static void
AndrONVIFPlayer__class_init (AndrONVIFPlayerClass *klass){
    GObjectClass *object_class = G_OBJECT_CLASS (klass);
    object_class->dispose = AndrONVIFPlayer__dispose;

//    if(!main_loop)
//        pthread_create (&main_thread, NULL, &mainloop_function, NULL);
}

JNIEXPORT jlong JNICALL
AndrONVIFPlayerNew_nativeNew() {
    return (jlong) g_object_new (ANDRONVIF_TYPE_PLAYER, NULL);
}


static JNIEXPORT void JNICALL
AndrONVIFPlayerNew_surfaceInit (JNIEnv *env, jobject thiz, jlong pointer, jobject surface) {
    g_return_if_fail(pointer > 0);
    g_return_if_fail(ANDRONVIF_IS_PLAYER((void*)pointer));

    AndrONVIFPlayer * self = ANDRONVIF_PLAYER((void*)pointer);
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);

    ANativeWindow *new_native_window = ANativeWindow_fromSurface(env, surface);
    C_DEBUG ("Received surface %p (native window %p)", (void*)surface, (void*)new_native_window);

    if (priv->native_window) {
        ANativeWindow_release (priv->native_window);
        if (priv->native_window == new_native_window) {
            C_DEBUG ("New native window is the same as the previous one");
            if (priv->video_sink) {
                gst_video_overlay_expose(GST_VIDEO_OVERLAY (priv->video_sink));
            }
            return;
        } else {
            C_DEBUG ("Released previous native window %p", (void*)priv->native_window);
            priv->initialized = FALSE;
        }
    }
    priv->native_window = new_native_window;

    check_initialization_complete (self);
}

static JNIEXPORT void JNICALL
AndrONVIFPlayerNew_surfaceFinalize (jlong pointer) {
    g_return_if_fail(pointer > 0);
    g_return_if_fail(ANDRONVIF_IS_PLAYER((void*)pointer));

    AndrONVIFPlayer * self = ANDRONVIF_PLAYER((void*)pointer);
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    C_DEBUG ("Releasing Native Window %p", priv->native_window);

    if (priv->video_sink) {
        gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY (priv->video_sink), (guintptr)NULL);
        gst_element_set_state (priv->pipeline, GST_STATE_READY);
    }

    ANativeWindow_release (priv->native_window);
    priv->native_window = NULL;
    priv->initialized = FALSE;
}

void gst_print_elements_by_type(char * type){
    GList * factories, *filtered, *tmp;

    C_INFO("* %s decoders ***",type);
    int found =0;
    GstCaps * caps = gst_caps_new_empty_simple(type);
    factories = gst_element_factory_list_get_elements (GST_ELEMENT_FACTORY_TYPE_DECODER | GST_ELEMENT_FACTORY_TYPE_MEDIA_VIDEO, GST_RANK_MARGINAL);
    filtered = gst_element_factory_list_filter (factories, caps, GST_PAD_SINK, FALSE);
    for (tmp = filtered; tmp; tmp = tmp->next) {
        GstElementFactory *fact = (GstElementFactory *) tmp->data;
        int rank = gst_plugin_feature_get_rank(GST_PLUGIN_FEATURE_CAST(fact));
        char * name = gst_plugin_feature_get_name(GST_PLUGIN_FEATURE_CAST(fact));
        C_INFO("*    %s[%d]",name,rank);
        found = 1;
    }

    if(!found){
        C_INFO("*    None");
    }
    gst_caps_unref (caps);
    gst_plugin_feature_list_free(filtered);
    gst_plugin_feature_list_free (factories);
}


static JNIEXPORT jstring JNICALL
AndrONVIFPlayerNew_getGstreamerInfo (JNIEnv * env, jobject thiz){
    char *version_utf8 = gst_version_string ();
    jstring *version_jstring = (*env)->NewStringUTF (env, version_utf8);
    g_free (version_utf8);
    return version_jstring;
}

/* Quit the main loop, remove the native thread and free resources */
static JNIEXPORT void JNICALL
AndrONVIFPlayerNew_finalize () {
    if(main_loop){
        C_DEBUG ("Quitting main loop...");
        g_main_loop_quit (main_loop);
        C_DEBUG ("Waiting for thread to finish...");
        pthread_join (main_thread, NULL);
        main_loop = NULL;
    } else {
        C_DEBUG("No mainloop started.");
    }
}

static void gst_plugin_feature_set_rank_by_name(char * element_name, int priority){
    GstRegistry* plugins_register = gst_registry_get();
    GstPluginFeature* plugfeat = gst_registry_lookup_feature(plugins_register, element_name);
    if(plugfeat == NULL) {
        C_WARN("Element '%s' not found.",element_name);
        return;
    }
    int rank = gst_plugin_feature_get_rank(plugfeat);
    if(rank > priority){
        C_WARN("Found element '%s'. Lowering priority from %d to %d...",element_name,rank,priority);
        gst_plugin_feature_set_rank(plugfeat, priority);
    } else if(rank < priority){
        C_WARN("Found element '%s'. Increasing priority from %d to %d...",element_name,rank,priority);
        gst_plugin_feature_set_rank(plugfeat, priority);
    }

    gst_object_unref(plugfeat);
}

/* Instruct the native code to create its internal data structure, pipeline and thread */
static void AndrONVIFPlayerNew__init (JNIEnv* env, jobject thiz) {
    gst_plugin_feature_set_rank_by_name("amcviddec-c2goldfishhevcdecoder",GST_RANK_PRIMARY); //This one apparently doesn't support 4k
    gst_plugin_feature_set_rank_by_name("amcviddec-c2androidhevcdecoder",GST_RANK_PRIMARY+1);
    C_INFO("**** Video decoders *******");
    gst_print_elements_by_type("video/x-h264");
    gst_print_elements_by_type("video/x-h265");
    gst_print_elements_by_type("image/jpeg");
    gst_print_elements_by_type("video/x-av1");
    C_INFO("****************************");
    pthread_create (&main_thread, NULL, &mainloop_function, NULL);
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = {
        { "nativeInit", "()V", (void *) AndrONVIFPlayerNew__init},
        {"nativeNew", "()J", (void *) AndrONVIFPlayerNew_nativeNew},
        {"nativePlay", "(J)V", (void *) AndrONVIFPlayerNew_play},
        {"nativeStop", "(J)V", (void *) AndrONVIFPlayerNew_stop},
        {"setDevice", "(JJ)V", (void *) AndrONVIFPlayerNew__set_device},
        {"getGstreamerInfo", "()Ljava/lang/String;", (void *) AndrONVIFPlayerNew_getGstreamerInfo},
        {"surfaceFinalize", "(J)V", (void *) AndrONVIFPlayerNew_surfaceFinalize},
        {"surfaceInit", "(JLjava/lang/Object;)V", (void *) AndrONVIFPlayerNew_surfaceInit},
        {"nativeFinalize", "()V", (void *) AndrONVIFPlayerNew_finalize}
};

/* Library initializer */
void AndrONVIFPlayerNew__register_jni_class(JNIEnv *env){

    jclass klass = (*env)->FindClass (env,"net/quedale/andronvif/jni/AndrONVIFPlayer");
    (*env)->RegisterNatives (env, klass, native_methods, G_N_ELEMENTS (native_methods));

}