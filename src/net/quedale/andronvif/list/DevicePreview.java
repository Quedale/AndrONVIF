package net.quedale.andronvif.list;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import net.quedale.andronvif.R;
import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.jni.AndrONVIFSnapshot;
import net.quedale.andronvif.jni.JNISoapObject;

public class DevicePreview extends FrameLayout {
    public DevicePreview(Context context) {
        super(context);
    }

    public DevicePreview(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DevicePreview(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private LifecycleOwner getLifecycleOwner() {
        Context context = getContext();
        while (!(context instanceof LifecycleOwner)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return (LifecycleOwner) context;
    }

    private void updateScopes(AndrONVIFDevice device){
        Log.d("DevicePreview", "updating Scopes : " + device.getUrl());
        TextView device_name = this.findViewById(R.id.device_name);
        TextView device_location = this.findViewById(R.id.device_location);
        TextView device_hardware = this.findViewById(R.id.device_hw);
        AndrONVIFDevice.AndrONVIFDeviceDetails details = device.getDetails();
        if(details != null){
            device_name.setText(details.getName());
            device_location.setText(details.getLocation());
            device_hardware.setText(details.getHardware());

        } else {
            device_name.setText(null);
            device_location.setText(null);
            device_hardware.setText(null);
        }

        LinearLayout layout = DevicePreview.this.findViewById(R.id.devpreview_scope_loading_overlay);
        layout.setVisibility(GONE);
        device_name.setVisibility(VISIBLE);
        device_location.setVisibility(VISIBLE);
        device_hardware.setVisibility(VISIBLE);
    }

    private void updateBitmap(AndrONVIFDevice device){
        AndrONVIFSnapshot snapshot = device.getSnapshot();
        ImageView iv = this.findViewById(R.id.snapshotPreview);
        if(snapshot == null) {
            Log.d("DevicePreview", "Device no snapshot to update : " + device.getUrl());
            // iv.setImageBitmap(null);
            return;
        }

        Log.d("DevicePreview", "Device updating snapshot : " + device.getUrl());
        iv.setImageBitmap(snapshot.getBitmap());
        iv.setVisibility(VISIBLE);

        LinearLayout layout = DevicePreview.this.findViewById(R.id.devpreview_snap_loading_overlay);
        layout.setVisibility(GONE);
    }

    public void setDevice(AndrONVIFDevice device){
        device.observeDetails(getLifecycleOwner(), andrONVIFDeviceDetails -> updateScopes(device));
        device.observeScopeException(getLifecycleOwner(), e -> {
            if(e == null) return;
            //Clear previous values
            TextView device_name = this.findViewById(R.id.device_name);
            TextView device_location = this.findViewById(R.id.device_location);
            TextView device_hardware = this.findViewById(R.id.device_hw);
            device_name.setVisibility(GONE);
            device_location.setVisibility(GONE);
            device_hardware.setVisibility(GONE);

            //TODO Show error icon
        });

        device.observeSnapshot(getLifecycleOwner(), andrONVIFSnapshot -> updateBitmap(device));
        device.observeSnapshotException(getLifecycleOwner(), e -> {
            if(e == null) return;
            Log.d("DevicePreview", "Failed to retrieve device snapshot : " + device.getUrl());
            //Show error icon
            ImageView iv = this.findViewById(R.id.snapshotPreview);
            iv.setImageResource(android.R.drawable.stat_notify_error);
            iv.setVisibility(VISIBLE);

            LinearLayout layout = this.findViewById(R.id.devpreview_snap_loading_overlay);
            layout.setVisibility(GONE);
        });

        device.observeAuthentication(getLifecycleOwner(), aBoolean -> {
            if(aBoolean == null || !aBoolean) return;
            Log.d("DevicePreview", "Device authenticated : " + device.getUrl());
            LinearLayout scope_layout = this.findViewById(R.id.devpreview_scope_layout);

            TextView device_name = this.findViewById(R.id.device_name);
            TextView device_location = this.findViewById(R.id.device_location);
            TextView device_hardware = this.findViewById(R.id.device_hw);
            device_name.setVisibility(GONE);
            device_location.setVisibility(GONE);
            device_hardware.setVisibility(GONE);

            LinearLayout layout = this.findViewById(R.id.devpreview_loading_overlay);
            layout.setVisibility(GONE);

            layout = this.findViewById(R.id.devpreview_scope_loading_overlay);
            layout.setVisibility(VISIBLE);
            layout = this.findViewById(R.id.devpreview_snap_loading_overlay);
            layout.setVisibility(VISIBLE);
            scope_layout.setVisibility(VISIBLE);

            TextView errormsg = this.findViewById(R.id.deviceError);
            errormsg.setText(null);
            errormsg.setVisibility(GONE);
        });
        device.observeAuthException(getLifecycleOwner(), e -> {
            if(e == null) return;

            Log.d("DevicePreview","Authentication failed : " + device.getUrl());
            LinearLayout layout = this.findViewById(R.id.devpreview_loading_overlay);
            layout.setVisibility(GONE);

            ImageView iv = this.findViewById(R.id.snapshotPreview);
            iv.setImageResource(android.R.drawable.stat_notify_error);
            iv.setVisibility(VISIBLE);

            TextView errormsg = this.findViewById(R.id.deviceError);
            if (e instanceof JNISoapObject.SoapConnectionException){
                errormsg.setText(R.string.failed_to_connect_error_msg);
            } else if (e instanceof JNISoapObject.SoapUnauthorizedException){
                errormsg.setText(R.string.unauthorized_error_msg);
            } else if (e instanceof JNISoapObject.SoapUnexpectedException
                        || e instanceof JNISoapObject.SoapActionNotSupportedException //Action Not Supported shouldn't occur on auth
                        || e instanceof JNISoapObject.SoapInvalidDeviceException){ //Invalid device shouldn't occur if device is already added
                errormsg.setText(R.string.unexpected_error_occured_error_msg);
            }
            errormsg.setVisibility(VISIBLE);
        });

        updateScopes(device);
        updateBitmap(device);
    }
}
