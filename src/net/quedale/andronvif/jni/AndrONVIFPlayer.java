package net.quedale.andronvif.jni;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.quedale.andronvif.R;

import org.freedesktop.gstreamer.GStreamer;
import org.freedesktop.gstreamer.GStreamerSurfaceView;

import java.util.concurrent.Executor;

import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;

public class AndrONVIFPlayer extends JNIObject implements SurfaceHolder.Callback {
    @CriticalNative
    private static native long nativeNew();                 // Create the native pointer
    @FastNative //Can't be CriticalNative because it needs JNIEnv
    private static native boolean nativeClassInit();        // Initialize native java method signatures
    @FastNative //Can't be CriticalNative because it needs JNIEnv
    public native void nativeInit(long pointer);            // Register java object natively TODO could be added to nativeNew
    @CriticalNative
    private static native void nativePlay(long pointer);    // Fetch StreamURI and construct/play pipeline
    @CriticalNative
    private static native void nativeStop(long pointer);    // Stop and destroy pipeline
    @CriticalNative
    private static  native void setDevice(long self_pointer,long device_pointer);   // Set device used to fetch StreamURI

    @FastNative  //Can't be CriticalNative because of Object parameter
    private static native void nativeSurfaceChanged(long pointer, Object surface, int format, int width, int height);
    @CriticalNative
    private static native void nativeSurfaceDestroyed(long pointer);
    @FastNative //Can't be CriticalNative because it returns a String
    private static native String getGstreamerInfo();

    private final TextView txtview;
    private final GStreamerSurfaceView view;
    private static  boolean gst_initialized = false;

    public AndrONVIFPlayer(GStreamerSurfaceView view, TextView txtview) {
        super(nativeNew());
        nativeInit(this.pointer_address);
        this.view = view;
        this.txtview = txtview;
        // Initialize GStreamer and warn if it fails
        if(!gst_initialized){
            gst_initialized = true;
            try {
                Log.d(AndrONVIFPlayer.class.getSimpleName(),getGstreamerInfo());
                GStreamer.init(view.getContext());
            } catch (Exception e) {
                Toast.makeText(view.getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                //TODO Fatal handling
            }
        }
        Log.d(AndrONVIFPlayer.class.getSimpleName(),"nativeInit");
    }

    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
        Log.d("AndrONVIFPlayer","message: " + message);
        view.post (new Runnable() {
            public void run() {
                txtview.setText(message);
            }
        });
    }

    // Called from native code when the size of the media changes or is first detected.
    // Inform the video surface about the new size and recalculate the layout.
    private void onMediaSizeChanged (int width, int height) {
        Log.i ("GStreamer", "Media size changed to " + width + "x" + height);
        view.media_width = width;
        view.media_height = height;
        view.post(new Runnable() {
            public void run() {
                view.requestLayout();
            }
        });
    }

    public void setDevice(AndrONVIFDevice device){
        setDevice(this.pointer_address,device.pointer_address);
    }

    public void play(){
        Log.d(AndrONVIFPlayer.class.getSimpleName(), "play");
        nativePlay(this.pointer_address);
    }

    public void stop(){
        Log.d(AndrONVIFPlayer.class.getSimpleName(), "stop");
        nativeStop(this.pointer_address);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        Log.d(AndrONVIFPlayer.class.getSimpleName(), "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceChanged (this.pointer_address,holder.getSurface(), format, width, height);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.d(AndrONVIFPlayer.class.getSimpleName(), "Surface created: " + holder.getSurface());
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(AndrONVIFPlayer.class.getSimpleName(), "Surface destroyed");
        nativeSurfaceDestroyed (this.pointer_address);
    }

    static {
        nativeClassInit();
    }
}
