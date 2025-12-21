package net.quedale.andronvif.jni;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.freedesktop.gstreamer.GStreamer;

import dalvik.annotation.optimization.FastNative;

public class AndrONVIFPlayer extends View implements SurfaceHolder.Callback {
    @FastNative
    private native String nativeGetGStreamerInfo();

    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay();     // Set pipeline to PLAYING
    private native void nativePause();    // Set pipeline to PAUSED
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface);
    private native void nativeSurfaceFinalize();

    @SuppressWarnings("unused")
    private long native_custom_data;      // Native code will use this to keep private data

    public AndrONVIFPlayer(Context context) {
        super(context);
    }

    public void init(){
        // Initialize GStreamer and warn if it fails
        try {
            Log.d(AndrONVIFPlayer.class.getSimpleName(),nativeGetGStreamerInfo());
            GStreamer.init(this.getContext());
        } catch (Exception e) {
            Toast.makeText(this.getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            //TODO Fatal handling
        }
        Log.d(AndrONVIFPlayer.class.getSimpleName(),"nativeInit");
        nativeInit();
    }

    public void deinit() {
        nativeFinalize();
    }

    // Called from native code. This sets the content of the TextView from the UI thread.
    @SuppressWarnings("unused")
    private void setMessage(final String message) {
        Log.w(AndrONVIFPlayer.class.getSimpleName(),"GstMessage : message");
//        final TextView tv = (TextView) this.findViewById(R.id.textview_message);
//        runOnUiThread (new Runnable() {
//            public void run() {
//                tv.setText(message);
//            }
//        });
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    @SuppressWarnings("unused")
    private void onGStreamerInitialized () {
        Log.i (AndrONVIFPlayer.class.getSimpleName(), "Gst initialized. Restoring state");
        nativePlay();
    }


    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        Log.d(AndrONVIFPlayer.class.getSimpleName(), "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit (holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(AndrONVIFPlayer.class.getSimpleName(), "Surface created: " + holder.getSurface());
    }

    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(AndrONVIFPlayer.class.getSimpleName(), "Surface destroyed");
        nativeSurfaceFinalize ();
    }

    static {
        nativeClassInit();
    }
}
