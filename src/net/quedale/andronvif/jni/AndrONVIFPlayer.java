package net.quedale.andronvif.jni;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.freedesktop.gstreamer.GStreamer;

import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;

public class AndrONVIFPlayer extends JNIObject implements SurfaceHolder.Callback {
    @CriticalNative
    private static native long nativeNew();
    public static native void nativeInit();     // Initialize native code, build pipeline, etc
    @CriticalNative
    public static native void nativeFinalize(); // Shutdown glib mainloop
    @FastNative  //Can't be CriticalNative because of Object parameter
    private static native void surfaceInit(long pointer, Object surface);
    @CriticalNative
    private static native void surfaceFinalize(long pointer);
    @CriticalNative
    private static native void nativePlay(long pointer);
    @CriticalNative
    private static native void nativeStop(long pointer);
    @CriticalNative
    private static native void setDevice(long self_pointer,long device_pointer);
    @FastNative //Can't be CriticalNative because it returns a String
    private static native String getGstreamerInfo();

    private static  boolean gst_initialized = false;

    public AndrONVIFPlayer(Context context) {
        super(nativeNew());
        // Initialize GStreamer and warn if it fails
        if(!gst_initialized){
            gst_initialized = true;
            try {
                Log.d(AndrONVIFPlayer.class.getSimpleName(),getGstreamerInfo());
                GStreamer.init(context);
            } catch (Exception e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                //TODO Fatal handling
            }
        }
        Log.d(AndrONVIFPlayer.class.getSimpleName(),"nativeInit");
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
        surfaceInit (this.pointer_address,holder.getSurface());
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.d(AndrONVIFPlayer.class.getSimpleName(), "Surface created: " + holder.getSurface());
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(AndrONVIFPlayer.class.getSimpleName(), "Surface destroyed");
        surfaceFinalize (this.pointer_address);
    }
}
