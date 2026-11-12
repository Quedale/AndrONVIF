package net.quedale.andronvif;

import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PermissionHandler {


    private static PermissionHandler INSTANCE;
    private Runnable runnable = null;
    private final Lock lock = new ReentrantLock(); //To protect runnable pointer against race condition until it ran

    public PermissionHandler(){
        requestLocalNetworkLauncher =
                AndrONVIFActivity.ACTIVITY.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    try {
                        if (isGranted) {
                            // Permission granted: proceed with local network discovery or socket connections
                            Log.d("PermissionHandler", "Access prompt granted by user");
                            runnable.run();
                        } else {
                            // Permission denied: inform the user that LAN features are unavailable
                            Toast.makeText(AndrONVIFActivity.ACTIVITY, "Failed to get permission.", Toast.LENGTH_LONG).show();
                        }
                    } finally {
                        lock.unlock();
                    }
                });
    }
    public static PermissionHandler getInstance(){
        if(INSTANCE == null){
            INSTANCE = new PermissionHandler();
        }
        return INSTANCE;
    }

    // Register the permissions callback
    private final ActivityResultLauncher<String> requestLocalNetworkLauncher;

    public void checkAndRequestPermission(String permission,Runnable callback) {
        // ACCESS_LOCAL_NETWORK is only required on Android 17 (API 37) and higher
        if (Build.VERSION.SDK_INT >= 37) {
                if (ContextCompat.checkSelfPermission(AndrONVIFActivity.ACTIVITY, permission)
                        == PackageManager.PERMISSION_GRANTED) {
                    Log.d("PermissionHandler", "Already granted");
                    callback.run();
                } else if (AndrONVIFActivity.ACTIVITY.shouldShowRequestPermissionRationale(permission)) {
                    // Optional: Show an educational UI explaining why your app needs LAN access,
                    // then call requestLocalNetworkLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    lock.lock();
                    runnable = callback;
                    requestLocalNetworkLauncher.launch(permission);
                } else {
                    lock.lock();
                    runnable = callback;
                    requestLocalNetworkLauncher.launch(permission);
                }
        } else {
            Log.d("PermissionHandler", "SDK " + Build.VERSION.SDK_INT);
            // Automatically granted via INTERNET permission on SDK 36 and below
            callback.run();
        }
    }
}
