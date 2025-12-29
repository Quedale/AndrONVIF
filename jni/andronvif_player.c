#include "andronvif_player.h"
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <gst/gst.h>
#include <gst/video/video.h>
#include "gst/rtsp/gstrtsptransport.h"
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
    OnvifDevice * device;
} AndrONVIFPlayerPrivate;

G_DEFINE_TYPE_WITH_PRIVATE (AndrONVIFPlayer, AndrONVIFPlayer_, G_TYPE_OBJECT)

static void
AndrONVIFPlayer__on_decoder_pad_added (GstElement *element, GstPad *new_pad, GstElement * converter){
    GstPad *sinkpad;
    GstPadLinkReturn ret;
    GstCaps * caps;

    /* Debug: Print the caps of the new pad */
    caps = gst_pad_get_current_caps(new_pad);
    gchar *caps_str = gst_caps_to_string(caps);
    C_DEBUG("Decoder pad added with caps: %s", caps_str);
    g_free(caps_str);

    /* We can now link this pad with the rtsp-decoder sink pad */
    sinkpad = gst_element_get_static_pad (converter, "sink");
    ret = gst_pad_link (new_pad, sinkpad);

    if (GST_PAD_LINK_FAILED (ret)) {
        C_ERROR ("failed to link decoder pad dynamically '%s' to '%s'",GST_ELEMENT_NAME(element),GST_ELEMENT_NAME(converter));
    }

    gst_object_unref (sinkpad);
}

static GstElement *
AndrONVIFPlayer__create_video_pad(){
    GstElement *vdecoder, *videoconvert, *video_bin, *sink;
    GstPad *pad, *ghostpad;

    video_bin = gst_bin_new("video_bin");
    vdecoder = gst_element_factory_make ("decodebin3", "video_decodebin");
    if (!vdecoder) {
        C_WARN("decodebin3 not available, trying decodebin");
        vdecoder = gst_element_factory_make ("decodebin", "video_decodebin");
    }
    //TODO Fallback into manual decoding bin if vdecoder failed
    videoconvert = gst_element_factory_make ("videoconvert", "videoconverter");

    sink = gst_element_factory_make ("autovideosink", NULL);

    // TODO Add Setting page allowing to configure Gstreamer
    // gst_base_sink_set_qos_enabled(GST_BASE_SINK_CAST(sink),FALSE);
    gst_base_sink_set_sync(GST_BASE_SINK_CAST(sink),FALSE);
    // gst_base_sink_set_async_enabled (GST_BASE_SINK_CAST(sink),TRUE);
    // gst_base_sink_set_max_lateness(GST_BASE_SINK_CAST(sink),10000);

    if (!video_bin ||
        !vdecoder ||
        !videoconvert ||
        !sink) {
        C_ERROR ("One of the video elements wasn't created... Exiting\n");
        return NULL;
    }

    // Add Elements to the Bin
    gst_bin_add_many (GST_BIN (video_bin),
                      vdecoder,
                      videoconvert,
                      sink, NULL);

    // Link confirmation
    if (!gst_element_link_many (videoconvert,
                                sink, NULL)){
        C_WARN ("Linking video part (A)-2 Fail...");
        return NULL;
    }

    // Dynamic Pad Creation
    if(! g_signal_connect (vdecoder, "pad-added", G_CALLBACK (AndrONVIFPlayer__on_decoder_pad_added),videoconvert)){
        C_ERROR ("Unabled to connect \"pad-added\" signal to video decoder.");
        return NULL;
    }

    pad = gst_element_get_static_pad (vdecoder, "sink");
    if (!pad) {
        // TODO gst_object_unref
        C_ERROR("unable to get decoder static sink pad");
        return NULL;
    }

    ghostpad = gst_ghost_pad_new ("bin_sink", pad);
    gst_element_add_pad (video_bin, ghostpad);
    gst_object_unref (pad);
    return video_bin;
}

static GstElement *
AndrONVIFPlayer__create_audio_pad(){
    GstPad *pad, *ghostpad;
    GstElement *decoder, *convert, *level, *sink, *audio_bin;

    audio_bin = gst_bin_new("audiobin");
    decoder = gst_element_factory_make ("decodebin3", "audio_decodebin");
    if (!decoder) {
        C_WARN("decodebin3 not available, trying decodebin");
        decoder = gst_element_factory_make ("decodebin", "audio_decodebin");
    }
    convert = gst_element_factory_make ("audioconvert", "audioconverter");
    level = gst_element_factory_make("level",NULL);
    sink = gst_element_factory_make ("autoaudiosink", NULL);
    g_object_set (G_OBJECT (sink), "sync", FALSE, NULL);
    if (!audio_bin ||
        !decoder ||
        !convert ||
        !level ||
        !sink) {
        C_ERROR ("One of the audio elements wasn't created. audiobin[%d] decoder[%d] convert[%d] level[%d] sink[%d]",!audio_bin, !decoder, !convert, !level, !sink);
        return NULL;
    }

    // Add Elements to the Bin
    gst_bin_add_many (GST_BIN (audio_bin),
                      decoder,
                      convert,
                      level,
                      sink, NULL);


    // Link confirmation
    if (!gst_element_link_many (convert,
                                level,
                                sink, NULL)){
        C_WARN ("Linking audio part (A)-2 Fail...");
        return NULL;
    }

    // Dynamic Pad Creation
    if(! g_signal_connect (decoder, "pad-added", G_CALLBACK (AndrONVIFPlayer__on_decoder_pad_added),convert)){
        C_ERROR ("Unabled to connect \"pad-added\" signal to audio decoder.");
        return NULL;
    }

    pad = gst_element_get_static_pad (decoder, "sink");
    if (!pad) {
        // TODO gst_object_unref
        C_ERROR("unable to get decoder static sink pad");
        return NULL;
    }

    ghostpad = gst_ghost_pad_new ("bin_sink", pad);
    gst_element_add_pad (audio_bin, ghostpad);
    gst_object_unref (pad);

    return audio_bin;
}

static void
AndrONVIFPlayer__on_rtsp_pad_added(GstElement *element, GstPad *new_pad, AndrONVIFPlayer * self){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    GstElement * decoder, * converter, * new_bin = NULL, * sink = NULL;

    GstCaps *caps = gst_pad_get_current_caps(new_pad);

    /* Check the new pad's type */
    gchar * caps_str = gst_caps_to_string(caps);
    C_DEBUG("RTSP pad added with caps: %s", caps_str);
    g_free(caps_str);

    if(gst_caps_get_size(caps) < 1){
        C_ERROR("Unexpected caps structure received");
        goto exit;
    }

    GstStructure *str = gst_caps_get_structure(caps, 0);
    const gchar * media_type = gst_structure_get_string(str,"media");
    const gchar *name = gst_structure_get_name(str);

    if (!strcmp(media_type,"video")) {
        new_bin = AndrONVIFPlayer__create_video_pad();
    } else if (!strcmp(media_type,"audio")) {
        new_bin = AndrONVIFPlayer__create_audio_pad();
    } else {
        C_ERROR("Unexpected caps received %s",name);
    }

    if(new_bin){
        gst_bin_add (GST_BIN (priv->pipeline), new_bin);
        GstPad * sink_pad = gst_element_get_static_pad (new_bin, "bin_sink");
        GstPadLinkReturn pad_ret = gst_pad_link (new_pad, sink_pad);
        if (GST_PAD_LINK_FAILED (pad_ret)) {
            C_ERROR ("failed to link video bin dynamically '%s' to '%s'",GST_ELEMENT_NAME(element),GST_ELEMENT_NAME(new_bin));
            goto exit;
        }
        gst_element_sync_state_with_parent(new_bin);
    }
exit:
    gst_caps_unref(caps);
}


static void
AndrONVIFPlayer__create_pipeline(AndrONVIFPlayer* self, char * url, char * user, char * pass){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);

    GstElement * rtspsrc = NULL;
    priv->pipeline = gst_pipeline_new ("onvif-pipeline");
    rtspsrc = gst_element_factory_make ("rtspsrc", NULL);

    if (!priv->pipeline){
        C_ERROR("%s Failed to created pipeline. Check your gstreamer installation...", url);
        //TODO Set error
        return;
    }

    if (!rtspsrc){
        C_ERROR ("%s Failed to created rtspsrc. Check your gstreamer installation...", url);
        //TODO Set error
        return;
    }

    // Add Elements to the Bin
    gst_bin_add_many (GST_BIN (priv->pipeline), rtspsrc, NULL);

    if(! g_signal_connect (G_OBJECT(rtspsrc), "pad-added", G_CALLBACK (AndrONVIFPlayer__on_rtsp_pad_added),self)){
        C_ERROR ("%s Failed to register rtspsrc pad-added signal", url);
        return;
    }

    if(user)
        g_object_set (G_OBJECT (rtspsrc), "user-id", user, NULL);
    if(pass)
        g_object_set (G_OBJECT (rtspsrc), "user-pw", pass, NULL);
    if(url)
        g_object_set (G_OBJECT (rtspsrc), "location", url, NULL);

    g_object_set (G_OBJECT (rtspsrc), "protocols", GST_RTSP_LOWER_TRANS_TCP, NULL); //TODO Allow changing this via settings

}

static GstBusSyncReply sync_bus_call(GstBus *bus, GstMessage *msg, AndrONVIFPlayer* self) {
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    if (gst_is_video_overlay_prepare_window_handle_message(msg)) {
        C_DEBUG("Received prepare window handle message");
        // This is where you get the overlay from the source of the message
        priv->video_sink = GST_ELEMENT(GST_MESSAGE_SRC(msg));

        gst_util_set_object_arg(G_OBJECT(priv->video_sink), "force-aspect-ratio", "true");

        // Set the window handle (e.g., to a Gtk widget or XID)
        gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(priv->video_sink), (guintptr)priv->native_window);
        C_DEBUG("Received prepare window handle message - done");
    }
    return GST_BUS_PASS;
}

static void
AndrONVIFPlayer__play_pipeline(AndrONVIFPlayer* self){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    GError *error = NULL;

    if(!OnvifDevice__is_authenticated(priv->device)){
        C_ERROR("Device not authenticated.");
        return;
    }

    /* Build pipeline */
    OnvifMediaService * mservice = OnvifDevice__get_media_service(priv->device);
    OnvifUri * muri = OnvifMediaService__getStreamUri(mservice,0);//TODO Support profile index
    char*  struri = OnvifUri__get_uri(muri);

    if(!struri){
        g_object_unref(muri);
        C_ERROR("Unable to retrieve StreamURI.");
        return;
    }

    C_DEBUG("Playing url : %s",struri);
    OnvifCredentials * creds = OnvifDevice__get_credentials(priv->device);
    char * username = OnvifCredentials__get_username(creds);
    char * password = OnvifCredentials__get_password(creds);

    AndrONVIFPlayer__create_pipeline(self,struri,username,password);
    /* Set the pipeline to READY, so it can already accept a window handle, if we have one */
    gst_element_set_state(priv->pipeline, GST_STATE_READY);

    char launchstr[512];
    strcpy(launchstr,"rtspsrc location=\"");
    strcat(launchstr,struri);
    strcat(launchstr,"\" user-id=\"");
    strcat(launchstr, username);
    strcat(launchstr,"\" user-pw=\"");
    strcat(launchstr, password);
    strcat(launchstr, "\" protocols=tcp ! decodebin3 ! videoconvert ! video/x-raw ! textoverlay text=test valignment=center halignment=center font-desc=\"Sans, 72\" draw-outline=true ! videoconvert ! autovideosink");

    free(username);
    free(password);
    g_object_unref(muri);


    /* Instruct the bus to emit signals for each received message, and connect to the interesting signals */
    GstBus *bus = gst_pipeline_get_bus(GST_PIPELINE(priv->pipeline));
    gst_bus_set_sync_handler(bus, (GstBusSyncHandler)sync_bus_call, self, NULL);
    gst_object_unref(bus);

    gst_element_set_state (priv->pipeline, GST_STATE_PLAYING);
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


static gboolean
AndrONVIFPlayer__idle_pipeline_cleanup(GstElement * pipeline){
    if(pipeline){
        gst_object_unref (pipeline);
    }
    return FALSE;
}

static void
AndrONVIFPlayer__stop(AndrONVIFPlayer * self) {
    C_DEBUG("AndrONVIFPlayer__stop");
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);

    GstElement * pipeline = priv->pipeline;
    priv->pipeline = NULL;
    priv->video_sink = NULL;

    if(pipeline) {
        gst_element_set_state(pipeline, GST_STATE_NULL);
        /*
         * Clean up invoke on the mainloop because disposing the pipeline needs
         * to stop first and changing the state occurs on the mainloop
         */
        g_main_context_invoke(context,G_SOURCE_FUNC(AndrONVIFPlayer__idle_pipeline_cleanup),pipeline);
    }

}

static JNIEXPORT void JNICALL
AndrONVIFPlayer__nativeStop(jlong pointer) {
    C_DEBUG("AndrONVIFPlayer__nativeStop");
    g_return_if_fail(pointer > 0);
    g_return_if_fail(ANDRONVIF_IS_PLAYER((void*)pointer));
    AndrONVIFPlayer__stop(ANDRONVIF_PLAYER((void*)pointer));
}

static gboolean
AndrONVIFPlayer__idle_play(AndrONVIFPlayer * self){
    //TODO aquire lock in case user change camera too fast
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    AndrONVIFPlayer__play_pipeline(self);
    return FALSE;
}


static JNIEXPORT void JNICALL
AndrONVIFPlayer__play(jlong pointer) {
    g_return_if_fail(pointer > 0);
    g_return_if_fail(ANDRONVIF_IS_PLAYER((void*)pointer));

    AndrONVIFPlayer * self = ANDRONVIF_PLAYER((void*)pointer);
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    if(priv->pipeline){
        C_WARN("Pipeline already created");
        return;
    }

    g_main_context_invoke(context,G_SOURCE_FUNC(AndrONVIFPlayer__idle_play),self);
}

JNIEXPORT void JNICALL
AndrONVIFPlayer__set_device(jlong self_pointer, jlong device_pointer){
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
    if(priv->pipeline) {
        C_FIXME("Pipeline is still valid. Should be stopped before dispose is called.");
        AndrONVIFPlayer__stop(self);
    }

    G_OBJECT_CLASS (AndrONVIFPlayer__parent_class)->dispose (object);
}

static void
AndrONVIFPlayer__class_init (AndrONVIFPlayerClass *klass){
    GObjectClass *object_class = G_OBJECT_CLASS (klass);
    object_class->dispose = AndrONVIFPlayer__dispose;
}

JNIEXPORT jlong JNICALL
AndrONVIFPlayer__nativeNew() {
    return (jlong) g_object_new (ANDRONVIF_TYPE_PLAYER, NULL);
}

static JNIEXPORT void JNICALL
AndrONVIFPlayer__surfaceChanged (JNIEnv *env, jobject thiz, jlong pointer, jobject surface, jint format, jint width, jint height) {
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
        }
    }
    priv->native_window = new_native_window;
}

static JNIEXPORT void JNICALL
AndrONVIFPlayer__nativeSurfaceDestroyed (jlong pointer) {
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
}

void gst_print_elements_by_type(char * type){
    GList * factories, *filtered, *tmp;

    C_INFO("* %s decoders ***",type);
    int found =0;
    GstCaps * caps = gst_caps_new_empty_simple(type);
    factories = gst_element_factory_list_get_elements (GST_ELEMENT_FACTORY_TYPE_DECODER | GST_ELEMENT_FACTORY_TYPE_MEDIA_VIDEO | GST_ELEMENT_FACTORY_TYPE_MEDIA_AUDIO, GST_RANK_MARGINAL);
    filtered = gst_element_factory_list_filter (factories, caps, GST_PAD_SINK, FALSE);
    for (tmp = filtered; tmp; tmp = tmp->next) {
        GstElementFactory *fact = (GstElementFactory *) tmp->data;
        guint rank = gst_plugin_feature_get_rank(GST_PLUGIN_FEATURE_CAST(fact));
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
AndrONVIFPlayer__getGstreamerInfo (JNIEnv * env, jobject thiz){
    char *version_utf8 = gst_version_string ();
    jstring *version_jstring = (*env)->NewStringUTF (env, version_utf8);
    g_free (version_utf8);
    return version_jstring;
}

/* Quit the main loop, remove the native thread and free resources */
static JNIEXPORT void JNICALL
AndrONVIFPlayer__finalize () {
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
    guint rank = gst_plugin_feature_get_rank(plugfeat);
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
static void AndrONVIFPlayer__nativeInit (JNIEnv* env, jobject thiz) {

    gst_debug_set_default_threshold(GST_LEVEL_WARNING);

    gst_plugin_feature_set_rank_by_name("amcviddec-c2goldfishhevcdecoder",GST_RANK_PRIMARY); //This one apparently doesn't support 4k
    gst_plugin_feature_set_rank_by_name("amcviddec-c2androidhevcdecoder",GST_RANK_PRIMARY+1);

    C_INFO("**** Video decoders *******");
    gst_print_elements_by_type("video/x-h264");
    gst_print_elements_by_type("video/x-h265");
    gst_print_elements_by_type("image/jpeg");
    gst_print_elements_by_type("video/x-av1");
    C_INFO("****************************");
    C_INFO("**** Audio decoders *******");
    gst_print_elements_by_type("audio/x-mulaw");
    gst_print_elements_by_type("audio/x-alaw");
    gst_print_elements_by_type("audio/mpeg");
    C_INFO("****************************");
    pthread_create (&main_thread, NULL, &mainloop_function, NULL);

}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = {
        { "nativeInit", "()V", (void *) AndrONVIFPlayer__nativeInit},
        {"nativeNew", "()J", (void *) AndrONVIFPlayer__nativeNew},
        {"nativePlay", "(J)V", (void *) AndrONVIFPlayer__play},
        {"nativeStop", "(J)V", (void *) AndrONVIFPlayer__nativeStop},
        {"setDevice", "(JJ)V", (void *) AndrONVIFPlayer__set_device},
        {"getGstreamerInfo", "()Ljava/lang/String;", (void *) AndrONVIFPlayer__getGstreamerInfo},
        {"nativeSurfaceDestroyed", "(J)V", (void *) AndrONVIFPlayer__nativeSurfaceDestroyed},
        {"nativeSurfaceChanged", "(JLjava/lang/Object;III)V", (void *) AndrONVIFPlayer__surfaceChanged},
        {"nativeFinalize", "()V", (void *) AndrONVIFPlayer__finalize}
};

/* Library initializer */
void AndrONVIFPlayerNew__register_jni_class(JNIEnv *env){
    jclass klass = (*env)->FindClass (env,"net/quedale/andronvif/jni/AndrONVIFPlayer");
    (*env)->RegisterNatives (env, klass, native_methods, G_N_ELEMENTS (native_methods));

}