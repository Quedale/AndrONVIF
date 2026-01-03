#include "andronvif_player.h"
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <gst/gst.h>
#include <gst/video/video.h>
#include "gst/rtsp/gstrtsptransport.h"
#include <pthread.h>
#include "clogger.h"
#include "onvif_device.h"

static pthread_key_t current_jni_env;
static JavaVM *java_vm;
static jmethodID set_message_method_id;
static jmethodID on_media_size_changed_method_id;

//Data structure used to dispose data asynchronously
typedef struct {
    AndrONVIFPlayer * player;
    GstElement * pipeline;
    GMainLoop *main_loop;
    jobject java_object;
    OnvifDevice * device;
} DisposeData;

typedef struct {
    //Each player instance has its own loop
    pthread_t main_thread;
    GMainContext *context;  /* GLib context used to run the main loop */
    GMainLoop *main_loop;   /* GLib main loop */

    jobject java_object;    /* Parent java object */
    OnvifDevice * device;   /* ONVIF device to playe */

    //Gstreamer related elements
    GstElement *pipeline;   /* The running pipeline */
    GstElement *video_sink; /* The video sink element which receives VideoOverlay commands */
    GstState state;               /* Current pipeline state */
    GstState target_state;        /* Desired pipeline state, to be set once buffering is complete */
    gboolean is_live;             /* Live streams do not use buffering */
    ANativeWindow *native_window; /* The Android native window where video will be rendered */
} AndrONVIFPlayerPrivate;

G_DEFINE_TYPE_WITH_PRIVATE (AndrONVIFPlayer, AndrONVIFPlayer_, G_TYPE_OBJECT)

/* Register this thread with the VM */
static JNIEnv *
attach_current_thread (void){
    JNIEnv *env;
    JavaVMAttachArgs args;

    C_DEBUG ("Attaching thread %p", g_thread_self ());
    args.version = JNI_VERSION_1_4;
    args.name = NULL;
    args.group = NULL;

    if ((*java_vm)->AttachCurrentThread (java_vm, &env, &args) < 0) {
        C_ERROR ("Failed to attach current thread");
        return NULL;
    }

    return env;
}

/* Unregister this thread from the VM */
static void
detach_current_thread (void *env){
    C_DEBUG ("Detaching thread %p", g_thread_self ());
    (*java_vm)->DetachCurrentThread (java_vm);
}

/* Retrieve the JNI environment for this thread */
static JNIEnv *
get_jni_env (void){
    JNIEnv *env;
    if ((env = pthread_getspecific (current_jni_env)) == NULL) {
        env = attach_current_thread ();
        pthread_setspecific (current_jni_env, env);
    }
    return env;
}

static void
set_ui_message (const gchar * message, AndrONVIFPlayer * player){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (player);
    JNIEnv *env = get_jni_env ();
    jstring jmessage = (*env)->NewStringUTF (env, message);
    (*env)->CallVoidMethod (env, priv->java_object, set_message_method_id, jmessage);
    if ((*env)->ExceptionCheck (env)) {
        C_ERROR ("Failed to call Java method");
        (*env)->ExceptionClear (env);
    }
    (*env)->DeleteLocalRef (env, jmessage);
}

/* Retrieve errors from the bus and show them on the UI */
static void
error_cb (GstBus * bus, GstMessage * msg, AndrONVIFPlayer * player){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (player);
    GError *err;
    gchar *debug_info;
    gchar *message_string;

    gst_message_parse_error (msg, &err, &debug_info);
    message_string = g_strdup_printf ("Error received from element %s: %s", GST_OBJECT_NAME (msg->src), err->message);
    g_clear_error (&err);
    g_free (debug_info);
    set_ui_message (message_string, player);
    g_free (message_string);
    priv->target_state = GST_STATE_NULL;
    gst_element_set_state (priv->pipeline, GST_STATE_NULL);
}

/* Called when the End Of the Stream is reached. Just move to the beginning of the media and pause. */
static void
eos_cb (GstBus * bus, GstMessage * msg, AndrONVIFPlayer * player){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (player);
    priv->target_state = GST_STATE_PAUSED;
    priv->is_live |= (gst_element_set_state (priv->pipeline, GST_STATE_PAUSED) == GST_STATE_CHANGE_NO_PREROLL);
}

/* Retrieve the video sink's Caps and tell the application about the media size */
static void
check_media_size (AndrONVIFPlayer * player){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (player);
    JNIEnv *env = get_jni_env ();
    GstPad *video_sink_pad;
    GstCaps *caps;
    GstVideoInfo info;

    /* Retrieve the Caps at the entrance of the video sink */
    video_sink_pad = gst_element_get_static_pad (priv->video_sink, "sink");
    caps = gst_pad_get_current_caps (video_sink_pad);

    if (gst_video_info_from_caps (&info, caps)) {
        info.width = info.width * info.par_n / info.par_d;
        C_DEBUG ("Media size is %dx%d, notifying application", info.width, info.height);

        //TODO Handle size change?
        (*env)->CallVoidMethod (env, priv->java_object, on_media_size_changed_method_id, (jint) info.width, (jint) info.height);
        if ((*env)->ExceptionCheck (env)) {
            C_ERROR ("Failed to call Java method");
            (*env)->ExceptionClear (env);
        }
    }

    gst_caps_unref (caps);
    gst_object_unref (video_sink_pad);
}

/* Notify UI about pipeline state changes */
static void
state_changed_cb (GstBus * bus, GstMessage * msg, AndrONVIFPlayer * player){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (player);
    GstState old_state, new_state, pending_state;
    gst_message_parse_state_changed (msg, &old_state, &new_state, &pending_state);

    /* Only pay attention to messages coming from the pipeline, not its children */
    if (GST_MESSAGE_SRC (msg) == GST_OBJECT (priv->pipeline) 
            && priv->video_sink != NULL) { //TODO Check for ANY sink to support audio-only stream
        C_DEBUG ("State set to %s for %s", gst_element_state_get_name (new_state), GST_OBJECT_NAME (msg->src));
        priv->state = new_state;
        gchar *message = g_strdup_printf ("State changed to %s",
                                          gst_state_get_name (new_state));
//        set_ui_message (message, player);
        g_free (message);

        if (new_state == GST_STATE_NULL || new_state == GST_STATE_READY)
            priv->is_live = FALSE;

       /* The Ready to Paused state change is particularly interesting: */
       if (old_state == GST_STATE_READY && new_state == GST_STATE_PAUSED && priv->video_sink) {
           /* By now the sink already knows the media size */
           check_media_size (player);
       }
    }
}

/* Called when the clock is lost */
static void
clock_lost_cb (GstBus * bus, GstMessage * msg, AndrONVIFPlayer * player){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (player);
    if (priv->target_state >= GST_STATE_PLAYING) {
        gst_element_set_state (priv->pipeline, GST_STATE_PAUSED);
        gst_element_set_state (priv->pipeline, GST_STATE_PLAYING);
    }
}

static void
window_handle_cb(GstBus * bus, GstMessage * msg, AndrONVIFPlayer * player){
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (player);
    C_DEBUG("Received prepare window handle message");
    // This is where you get the overlay from the source of the message
    priv->video_sink = GST_ELEMENT(GST_MESSAGE_SRC(msg));

    gst_util_set_object_arg(G_OBJECT(priv->video_sink), "force-aspect-ratio", "true");

    // Set the window handle (e.g., to a Gtk widget or XID)
    gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(priv->video_sink), (guintptr)priv->native_window);
}

/* Called when buffering messages are received. We inform the UI about the current buffering level and
 * keep the pipeline paused until 100% buffering is reached. At that point, set the desired state. */
static void
buffering_cb (GstBus * bus, GstMessage * msg, AndrONVIFPlayer * player){
    gint percent;
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (player);

    if (priv->is_live)
        return;

    gst_message_parse_buffering (msg, &percent);
    if (percent < 100 && priv->target_state >= GST_STATE_PAUSED) {
        gchar *message_string = g_strdup_printf ("Buffering %d%%", percent);
        gst_element_set_state (priv->pipeline, GST_STATE_PAUSED);
        set_ui_message (message_string, player);
        g_free (message_string);
    } else if (priv->target_state >= GST_STATE_PLAYING) {
        gst_element_set_state (priv->pipeline, GST_STATE_PLAYING);
    } else if (priv->target_state >= GST_STATE_PAUSED) {
        set_ui_message ("Buffering complete", player);
    }
}

static void
on_decoder_pad_added_cb (GstElement *element, GstPad *new_pad, GstElement * converter){
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
    //Using decodebin because decodebin3 pad caps are null on pad-added signal
    vdecoder = gst_element_factory_make ("decodebin", "video_decodebin");
    if (!vdecoder) {
        C_WARN("decodebin not available, trying decodebin3");
        vdecoder = gst_element_factory_make ("decodebin3", "video_decodebin3");
    }
    //TODO Fallback into manual decoding bin if vdecoder failed
    videoconvert = gst_element_factory_make ("videoconvert", "videoconverter");

    sink = gst_element_factory_make ("autovideosink", NULL);

    // TODO Add Setting page allowing to configure Gstreamer
    // gst_base_sink_set_qos_enabled(GST_BASE_SINK_CAST(sink),FALSE);
    gst_base_sink_set_sync(GST_BASE_SINK_CAST(sink),FALSE);
    // gst_base_sink_set_async_enabled (GST_BASE_SINK_CAST(sink),TRUE);
    // gst_base_sink_set_max_lateness(GST_BASE_SINK_CAST(sink),10000);
    // gst_base_sink_set_throttle_time(GST_BASE_SINK_CAST(sink),40000000);

    if (!video_bin ||
        !vdecoder ||
        !videoconvert ||
        !sink) {
        C_ERROR ("One of the video elements wasn't created... Exiting");
        return NULL;
    }

    // Add Elements to the Bin
    gst_bin_add_many (GST_BIN (video_bin),
                      vdecoder,
                      videoconvert,
                      sink, NULL);

    // Link confirmation
    if (!gst_element_link_many (videoconvert, sink, NULL)){
        C_WARN ("Failed to link video converter to sink");
        return NULL;
    }

    // Dynamic Pad Creation
    if(! g_signal_connect (vdecoder, "pad-added", G_CALLBACK (on_decoder_pad_added_cb),videoconvert)){
        C_ERROR ("Unabled to connect \"pad-added\" signal to video decoder.");
        return NULL;
    }

    pad = gst_element_get_static_pad (vdecoder, "sink");
    if (!pad) {
        // TODO gst_object_unref
        C_ERROR("Unable to get video decoder static sink pad");
        return NULL;
    }

    ghostpad = gst_ghost_pad_new ("sink", pad);
    gst_element_add_pad (video_bin, ghostpad);
    gst_object_unref (pad);
    return video_bin;
}

static GstElement *
AndrONVIFPlayer__create_audio_pad(){
    GstPad *pad, *ghostpad;
    GstElement *decoder, *convert, *level, *sink, *audio_bin;

    audio_bin = gst_bin_new("audiobin");
    //Using decodebin because decodebin3 pad caps are null on pad-added signal
    decoder = gst_element_factory_make ("decodebin", "audio_decodebin");
    if (!decoder) {
        C_WARN("decodebin not available, trying decodebin3");
        decoder = gst_element_factory_make ("decodebin3", "audio_decodebin3");
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
    if (!gst_element_link_many (convert,level, sink, NULL)){
        C_WARN ("Failed to link audio concverter, level and sink.");
        return NULL;
    }

    // Dynamic Pad Creation
    if(! g_signal_connect (decoder, "pad-added", G_CALLBACK (on_decoder_pad_added_cb),convert)){
        C_ERROR ("Unabled to connect \"pad-added\" signal to audio decoder.");
        return NULL;
    }

    pad = gst_element_get_static_pad (decoder, "sink");
    if (!pad) {
        // TODO gst_object_unref
        C_ERROR("Unable to get audio decoder static sink pad");
        return NULL;
    }

    ghostpad = gst_ghost_pad_new ("sink", pad);
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
        GstPad * sink_pad = gst_element_get_static_pad (new_bin, "sink");
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
    g_object_set (G_OBJECT(rtspsrc), "is-live", TRUE, NULL);
    // g_object_set (G_OBJECT(rtspsrc), "latency", 20000, NULL);
    // g_object_set (G_OBJECT(rtspsrc), "onvif-mode", TRUE, NULL); // causes "gst_event_new_segment: assertion 'segment->rate != 0.0' failed" on android
}

static void
warning_cb (GstBus *bus, GstMessage *msg,AndrONVIFPlayer * player) {
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (player);
    GError *err;
    gchar *debug_info;
    gst_message_parse_warning (msg, &err, &debug_info);
    if(strcmp(err->message,"Empty Payload.")){ //Ignoring rtp empty payload warnings
        C_WARN ("Warning received from element %s: %s", GST_OBJECT_NAME (msg->src), err->message);
        C_WARN ("Debugging information: %s", debug_info ? debug_info : "none");
        set_ui_message(err->message,player);
    }
    g_clear_error (&err);
    g_free (debug_info);
}

static const char * GST_STREAM_STATUS_TYPE_CREATE_STRING = "Create";
static const char * GST_STREAM_STATUS_TYPE_ENTER_STRING = "Enter";
static const char * GST_STREAM_STATUS_TYPE_LEAVE_STRING = "Leave";
static const char * GST_STREAM_STATUS_TYPE_DESTROY_STRING = "Destroy";
static const char * GST_STREAM_STATUS_TYPE_START_STRING = "Start";
static const char * GST_STREAM_STATUS_TYPE_PAUSE_STRING = "Pause";
static const char * GST_STREAM_STATUS_TYPE_STOP_STRING = "Stop";

static const char * GST_PROGRESS_TYPE_START_STRING = "Start";
static const char * GST_PROGRESS_TYPE_CONTINUE_STRING = "Continue";
static const char * GST_PROGRESS_TYPE_COMPLETE_STRING = "Complete";
static const char * GST_PROGRESS_TYPE_CANCELED_STRING = "Canceled";
static const char * GST_PROGRESS_TYPE_ERROR_STRING = "Error";


static const char * GST_FORMAT_UNDEFINED_STRING  =  "Undefined";
static const char * GST_FORMAT_DEFAULT_STRING =  "Default";
static const char * GST_FORMAT_BYTES_STRING =  "Bytes";
static const char * GST_FORMAT_TIME_STRING =  "Time";
static const char * GST_FORMAT_BUFFERS_STRING =  "Buffers";
static const char * GST_FORMAT_PERCENT_STRING =  "Percent";

static const char *
gst_progress_type_get_name (GstProgressType type){
    switch(type){
        case GST_PROGRESS_TYPE_START:
            return GST_PROGRESS_TYPE_START_STRING;
        case GST_PROGRESS_TYPE_CONTINUE:
            return GST_PROGRESS_TYPE_CONTINUE_STRING;
        case GST_PROGRESS_TYPE_COMPLETE:
            return GST_PROGRESS_TYPE_COMPLETE_STRING;
        case GST_PROGRESS_TYPE_CANCELED:
            return GST_PROGRESS_TYPE_CANCELED_STRING;
        case GST_PROGRESS_TYPE_ERROR:
            return GST_PROGRESS_TYPE_ERROR_STRING;
        default:
            return NULL;
    }
}

static const gchar *
gst_stream_status_get_name (GstStreamStatusType type) {
    switch (type) {
        case GST_STREAM_STATUS_TYPE_CREATE:
            return GST_STREAM_STATUS_TYPE_CREATE_STRING;
        case GST_STREAM_STATUS_TYPE_ENTER:
            return GST_STREAM_STATUS_TYPE_ENTER_STRING;
        case GST_STREAM_STATUS_TYPE_LEAVE:
            return GST_STREAM_STATUS_TYPE_LEAVE_STRING;
        case GST_STREAM_STATUS_TYPE_DESTROY:
            return GST_STREAM_STATUS_TYPE_DESTROY_STRING;
        case GST_STREAM_STATUS_TYPE_START:
            return GST_STREAM_STATUS_TYPE_START_STRING;
        case GST_STREAM_STATUS_TYPE_PAUSE:
            return GST_STREAM_STATUS_TYPE_PAUSE_STRING;
        case GST_STREAM_STATUS_TYPE_STOP:
            return GST_STREAM_STATUS_TYPE_STOP_STRING;
        default:
            return NULL;
    }
}

static const char *
gst_format_type_get_name(GstFormat format){
    switch(format){
        case GST_FORMAT_UNDEFINED:
            return GST_FORMAT_UNDEFINED_STRING;
        case GST_FORMAT_DEFAULT:
            return GST_FORMAT_DEFAULT_STRING;
        case GST_FORMAT_BYTES:
            return GST_FORMAT_BYTES_STRING;
        case GST_FORMAT_TIME:
            return GST_FORMAT_TIME_STRING;
        case GST_FORMAT_BUFFERS:
            return GST_FORMAT_BUFFERS_STRING;
        case GST_FORMAT_PERCENT:
            return GST_FORMAT_PERCENT_STRING;
        default:
            return NULL;
    }
}

static void
qos_cb(GstMessage * msg){
    C_TRACE ("QoS message from %s:", GST_OBJECT_NAME (msg->src));
    gboolean live;
    guint64 running_time, stream_time, timestamp, duration;
    gst_message_parse_qos(msg, &live, &running_time, &stream_time,
                          &timestamp, &duration);
    C_TRACE("\tlive[%s] runTime[%" GST_TIME_FORMAT "] steamTime[%" GST_TIME_FORMAT "] duration[%" GST_TIME_FORMAT "] timestamp[%" GST_TIME_FORMAT "]",live ? "TRUE" : "FALSE", GST_TIME_ARGS(running_time), GST_TIME_ARGS(stream_time),GST_TIME_ARGS(duration),GST_TIME_ARGS(timestamp));
    GstFormat format;
    guint64 processed, dropped;
    gst_message_parse_qos_stats(msg, &format, &processed, &dropped);
    C_TRACE("\tprocessed[%lu] dropped[%lu] format[%s]", processed, dropped,gst_format_type_get_name(format));
    gint64 jitter;
    gdouble proportion;
    gint quality;
    gst_message_parse_qos_values(msg, &jitter, &proportion, &quality);
    C_TRACE("\tjitter[%" GST_TIME_FORMAT "] proportion[%.2f%%] quality[%d]", GST_TIME_ARGS (jitter), proportion * 100,quality);
}

static void
progress_cb(GstMessage * msg, AndrONVIFPlayer* self){
    GstProgressType type;
    gchar *code;
    gchar *text;
    gst_message_parse_progress(msg, &type, &code, &text);
    C_DEBUG("progress : type[%s] code[%s] text[%s]", gst_progress_type_get_name(type), code,text);
    set_ui_message (text, self);
}

static void
latency_cb(AndrONVIFPlayer* self) {
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    gboolean live;
    GstClockTime min_latency, max_latency;
    C_DEBUG("Recalculating latency...");
    if(!gst_bin_recalculate_latency(GST_BIN(priv->pipeline))){
        C_WARN("Latency recalculation failed!");
        return;
    };
    /* Create a new latency query */
    GstQuery *query = gst_query_new_latency();

    /* Query the element (or pipeline) */
    if (gst_element_query(priv->pipeline, query)) {
        /* Parse the latency query results */
        gst_query_parse_latency(query, &live, &min_latency, &max_latency);

        C_DEBUG("Latency Query Result:");
        C_DEBUG("\tIs live pipeline: %s", live ? "TRUE" : "FALSE");
        C_DEBUG("\tMinimum latency: %" GST_TIME_FORMAT, GST_TIME_ARGS(min_latency));
        C_DEBUG("\tMaximum latency: %" GST_TIME_FORMAT, GST_TIME_ARGS(max_latency));
    } else {
        C_DEBUG("Latency query failed\n");
    }
}

static gboolean
async_bus_call(GstBus *bus, GstMessage *msg, AndrONVIFPlayer* self) {
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    const GstStructure *s;
    const gchar *name;
    switch(GST_MESSAGE_TYPE(msg)){
        case GST_MESSAGE_ELEMENT:
            if (gst_is_video_overlay_prepare_window_handle_message(msg)) {
                window_handle_cb(bus,msg,self);
            } else {
                s = gst_message_get_structure (msg);
                name = gst_structure_get_name (s);
                if (strcmp (name, "level") == 0) {
//                    OverlayState__level_handler(bus,message,priv->overlay_state,s);
                } else {
                    C_WARN("element message %s", name);
                }
            }
            break;
        case GST_MESSAGE_ERROR:
            error_cb(bus,msg,self);
            break;
        case GST_MESSAGE_EOS:
            eos_cb(bus,msg,self);
            break;
        case GST_MESSAGE_BUFFERING:
            buffering_cb(bus,msg,self);
            break;
        case GST_MESSAGE_STATE_CHANGED:
            state_changed_cb(bus,msg,self);
            break;
        case GST_MESSAGE_CLOCK_LOST:
            clock_lost_cb(bus,msg,self);
            break;
        case GST_MESSAGE_WARNING:
            warning_cb(bus,msg,self);
            break;
        case GST_MESSAGE_QOS:
            qos_cb(msg);
            break;
        case GST_MESSAGE_PROGRESS:
            progress_cb(msg,self);
            break;
        case GST_MESSAGE_STREAM_STATUS: {
            GstStreamStatusType type;
            GstElement *owner;
            gst_message_parse_stream_status(msg, &type, &owner);
            C_TRACE("New stream status : type[%s] element[%s]", gst_stream_status_get_name(type), GST_ELEMENT_NAME(owner));
            break;
        }
        case GST_MESSAGE_LATENCY:
            latency_cb(self);
            break;
        case GST_MESSAGE_TAG:
            C_TRACE("new tags");
            break;
        case GST_MESSAGE_STREAM_START:
            set_ui_message ("Playing...", self);
            break;
        case GST_MESSAGE_NEED_CONTEXT: {
            const gchar *context_type;
            gst_message_parse_context_type(msg,&context_type);
            C_DEBUG("Need context type : %s", context_type ? context_type : "NULL");
            break;
        }
        case GST_MESSAGE_UNKNOWN:
        case GST_MESSAGE_INFO:
        case GST_MESSAGE_STATE_DIRTY:
        case GST_MESSAGE_STEP_DONE:
        case GST_MESSAGE_CLOCK_PROVIDE:
        case GST_MESSAGE_NEW_CLOCK:
        case GST_MESSAGE_STRUCTURE_CHANGE:
        case GST_MESSAGE_APPLICATION:
        case GST_MESSAGE_SEGMENT_START:
        case GST_MESSAGE_SEGMENT_DONE:
        case GST_MESSAGE_DURATION_CHANGED:
        case GST_MESSAGE_ASYNC_START:
        case GST_MESSAGE_ASYNC_DONE:
        case GST_MESSAGE_REQUEST_STATE:
        case GST_MESSAGE_STEP_START:
        case GST_MESSAGE_TOC:
        case GST_MESSAGE_RESET_TIME:
        case GST_MESSAGE_HAVE_CONTEXT:
        case GST_MESSAGE_EXTENDED:
        case GST_MESSAGE_DEVICE_ADDED:
        case GST_MESSAGE_DEVICE_REMOVED:
        case GST_MESSAGE_PROPERTY_NOTIFY:
        case GST_MESSAGE_STREAM_COLLECTION:
        case GST_MESSAGE_STREAMS_SELECTED:
        case GST_MESSAGE_REDIRECT:
        case GST_MESSAGE_DEVICE_CHANGED:
        case GST_MESSAGE_INSTANT_RATE_REQUEST:
        case GST_MESSAGE_ANY:
        default:
            C_ERROR("unhandled message gst message %s",GST_MESSAGE_TYPE_NAME(msg));
            break;
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

    set_ui_message ("Fetching ONVIF StreamURI", self);

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

    set_ui_message ("Creating pipeline...", self);
    AndrONVIFPlayer__create_pipeline(self,struri,username,password);

    free(username);
    free(password);
    g_object_unref(muri);

    /* Instruct the bus to emit signals for each received message*/
    GstBus *bus = gst_element_get_bus (priv->pipeline);
    GSource *source = gst_bus_create_watch (bus);
    if (!source) {
        C_ERROR ("Creating bus watch failed");
        gst_object_unref(bus);
        return;
    }
    // g_source_set_priority (source, priority);
    g_source_set_callback (source, G_SOURCE_FUNC(async_bus_call), self, NULL);
    g_source_attach (source, priv->context);
    g_source_unref (source);
    gst_object_unref (bus);

    priv->is_live |=
            (gst_element_set_state (priv->pipeline,GST_STATE_PLAYING) == GST_STATE_CHANGE_NO_PREROLL);
}

/* Main method for the native code. This is executed on its own thread. */
static void *
mainloop_function (void *userdata) {
    AndrONVIFPlayer * self = ANDRONVIF_PLAYER(userdata);
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);

    GMainContext *context = g_main_context_new ();
    priv->context = context;
    // g_main_context_push_thread_default (priv->context);

    /* Create a GLib Main Loop and set it to run */
    C_DEBUG ("Entering main loop...");
    GMainLoop *main_loop = g_main_loop_new (context, FALSE);
    priv->main_loop = main_loop;

    g_main_loop_run (main_loop);
    C_DEBUG ("Exited main loop");
    g_main_loop_unref (main_loop);

    /* Free resources */
    // g_main_context_pop_thread_default(priv->context);
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
    priv->context = NULL;
    priv->main_loop = NULL;
    priv->java_object = NULL;
    pthread_create (&priv->main_thread, NULL, &mainloop_function, self);
}


static gboolean
AndrONVIFPlayer__idle_pipeline_cleanup(GstElement * pipeline){
    if(pipeline){
        C_DEBUG("AndrONVIFPlayer__idle_pipeline_cleanup");
        gst_object_unref (pipeline);
    }
    return FALSE;
}

static gboolean
AndrONVIFPlayer__idle_stop(AndrONVIFPlayer * self) {
    C_DEBUG("AndrONVIFPlayer__idle_stop");
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);

    GstElement * pipeline = priv->pipeline;
    priv->pipeline = NULL;
    priv->video_sink = NULL;
    if(pipeline) {
        priv->target_state = GST_STATE_NULL;
        C_DEBUG("Setting pipeline state to null.");
        priv->is_live |= (gst_element_set_state (pipeline, GST_STATE_NULL) == GST_STATE_CHANGE_NO_PREROLL);
        /*
         * Clean up invoke on the mainloop because disposing the pipeline needs
         * to stop first and changing the state occurs on the mainloop
         */
        C_DEBUG("Pipeline clean up after stop.");
        gst_object_unref (pipeline);
    }

    return FALSE;
}

static JNIEXPORT void JNICALL
AndrONVIFPlayer__nativeStop(jlong pointer) {
    C_DEBUG("AndrONVIFPlayer__nativeStop");
    g_return_if_fail(pointer > 0);
    g_return_if_fail(ANDRONVIF_IS_PLAYER((void*)pointer));
    AndrONVIFPlayer * self = ANDRONVIF_PLAYER((void*)pointer);
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);
    if(!priv->context) C_FIXME("Invalid context. Can't stop.");
    g_main_context_invoke(priv->context,G_SOURCE_FUNC(AndrONVIFPlayer__idle_stop),self);
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

    if(!priv->context) C_FIXME("Invalid context. Can't play.");
    priv->target_state = GST_STATE_PLAYING;
    g_main_context_invoke(priv->context,G_SOURCE_FUNC(AndrONVIFPlayer__idle_play),self);
}

JNIEXPORT void JNICALL
AndrONVIFPlayer__set_device(jlong self_pointer, jlong device_pointer){
    C_DEBUG("AndrONVIFPlayerNew__set_device");
    g_return_if_fail(self_pointer > 0);
    g_return_if_fail(ANDRONVIF_IS_PLAYER((void*)self_pointer));
    g_return_if_fail(device_pointer > 0);
    g_return_if_fail(ONVIF_IS_DEVICE((void*)device_pointer));

    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (ANDRONVIF_PLAYER((void*)self_pointer));
    OnvifDevice * new_device = ONVIF_DEVICE((void*)device_pointer);

    if(priv->device == new_device) return;

    //Removing reference if another device is added
    if(priv->device) g_object_unref(priv->device);
    
    priv->device = new_device;
    g_object_ref(priv->device); //Adding reference while using it
}

static gboolean
AndrONVIFPlayer__idle_dispose (DisposeData * data){
    C_DEBUG("AndrONVIFPlayer__idle_dispose");
    if(data->pipeline) {
        C_FIXME("Pipeline is still valid. Should be stopped before dispose is called.");
        gst_element_set_state (data->pipeline, GST_STATE_NULL);
        C_FIXME("Pipeline clean up from dispose.");
        gst_object_unref (data->pipeline);
    }

    C_DEBUG ("Quitting main loop...");
    //The mainloop will self-cleanup after quitting
    g_main_loop_quit (data->main_loop);

    C_DEBUG ("Releasing java object reference...");
    //The main_loop terminated, there shouldn't be any usage of the java_object
    if(data->java_object){
        JNIEnv *env = get_jni_env ();
        (*env)->DeleteGlobalRef (env, data->java_object);
        data->java_object = NULL;
    }
    
    C_DEBUG ("Releasing OnvifDevice reference...");
    if(data->device){
        g_object_unref(data->device);
    }
    free(data);
    return FALSE;
}

static void
AndrONVIFPlayer__dispose (GObject *object){
    C_DEBUG("AndrONVIFPlayer__dispose");
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (ANDRONVIF_PLAYER(object));
    if(priv->context){
        GMainContext *context = priv->context;
        priv->context = NULL;

        /*
         * Dispatching a mainloop self-destruct call
         * Construct data holder because "self" won't be available after this function returns
         */
        DisposeData * data = malloc(sizeof(DisposeData));
        data->pipeline = priv->pipeline;
        data->java_object = priv->java_object;
        data->main_loop = priv->main_loop;
        data->device = priv->device;
        g_main_context_invoke(context,G_SOURCE_FUNC(AndrONVIFPlayer__idle_dispose),data);
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
AndrONVIFPlayer__surfaceChanged (JNIEnv *env, jclass klass, jlong pointer, jobject surface, jint format, jint width, jint height) {
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
                gst_video_overlay_expose(GST_VIDEO_OVERLAY (priv->video_sink));
            }
            return;
        } else {
            C_DEBUG ("Released previous native window %p", (void*)priv->native_window);
        }
    }
    priv->native_window = new_native_window;
    if (priv->video_sink) {
        gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY (priv->video_sink),
                                            (guintptr) priv->native_window);
    }
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

static JNIEXPORT jboolean JNICALL
AndrONVIFPlayer__nativeClassInit (JNIEnv * env, jclass klass){
    set_message_method_id = (*env)->GetMethodID (env, klass, "setMessage", "(Ljava/lang/String;)V");
    on_media_size_changed_method_id = (*env)->GetMethodID (env, klass, "onMediaSizeChanged", "(II)V");

    if (!set_message_method_id || !on_media_size_changed_method_id) {
        /* We emit this message through the Android log instead of the GStreamer log because the later
         * has not been initialized yet.
         */
        C_ERROR("The calling class does not implement all necessary interface methods");
        return JNI_FALSE;
    }

    gst_debug_set_default_threshold(GST_LEVEL_ERROR);

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

    return JNI_TRUE;
}

/* Instruct the native code to create its internal data structure, pipeline and thread */
static void AndrONVIFPlayer__nativeInit (JNIEnv* env, jobject thiz, jlong pointer) {
    g_return_if_fail(pointer > 0);
    g_return_if_fail(ANDRONVIF_IS_PLAYER((void*)pointer));
    AndrONVIFPlayer * self = ANDRONVIF_PLAYER((void*)pointer);
    AndrONVIFPlayerPrivate *priv = AndrONVIFPlayer__get_instance_private (self);

    priv->java_object = (*env)->NewGlobalRef (env, thiz);
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = {
        { "nativeInit", "(J)V", (void *) AndrONVIFPlayer__nativeInit},
        {"nativeNew", "()J", (void *) AndrONVIFPlayer__nativeNew},
        {"nativePlay", "(J)V", (void *) AndrONVIFPlayer__play},
        {"nativeStop", "(J)V", (void *) AndrONVIFPlayer__nativeStop},
        {"setDevice", "(JJ)V", (void *) AndrONVIFPlayer__set_device},
        {"getGstreamerInfo", "()Ljava/lang/String;", (void *) AndrONVIFPlayer__getGstreamerInfo},
        {"nativeSurfaceDestroyed", "(J)V", (void *) AndrONVIFPlayer__nativeSurfaceDestroyed},
        {"nativeSurfaceChanged", "(JLjava/lang/Object;III)V", (void *) AndrONVIFPlayer__surfaceChanged},
        {"nativeClassInit", "()Z", (void *) AndrONVIFPlayer__nativeClassInit}
};

/* Library initializer */
void AndrONVIFPlayer__register_jni_class(JavaVM * vm, JNIEnv *env){
    java_vm = vm;

    jclass klass = (*env)->FindClass (env,"net/quedale/andronvif/jni/AndrONVIFPlayer");
    (*env)->RegisterNatives (env, klass, native_methods, G_N_ELEMENTS (native_methods));

    pthread_key_create (&current_jni_env, detach_current_thread);
}