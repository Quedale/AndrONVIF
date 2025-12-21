package net.quedale.andronvif;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

import org.freedesktop.gstreamer.GStreamer;

public class AndrONVIFApplication extends Application {

    // Global variables or methods can be defined here
    @Override
    public void onCreate() {
        super.onCreate();
        // Perform application-level initialization here
        try {
            GStreamer.init(this);
        } catch (Exception e) {

            Log.d(AndrONVIFApplication.class.getSimpleName(),"onCreate");
            Toast.makeText(this, "etoat", Toast.LENGTH_LONG).show();
            System.exit(0);
        }
    }

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("andronvif");
    }
}