package net.quedale.andronvif.list;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import net.quedale.andronvif.R;
import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.jni.AndrONVIFSnapshot;

public class DevicePreview extends LinearLayout {
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
        device_name.setText(device.getName());

        TextView device_location = this.findViewById(R.id.device_location);
        device_location.setText(device.getLocation());

        TextView device_hardware = this.findViewById(R.id.device_hw);
        device_hardware.setText(device.getHardware());

        LinearLayout scope_layout = this.findViewById(R.id.devpreview_scope_layout);
        scope_layout.setVisibility(VISIBLE);
    }

    private void updateBitmap(AndrONVIFDevice device){
        AndrONVIFSnapshot snapshot = device.getSnapshot();
        if(snapshot == null) {
            Log.d("DevicePreview", "Device no snapshot to update : " + device.getUrl());
            return;
        }

        Log.d("DevicePreview", "Device updating snapshot : " + device.getUrl());
        ImageView iv = this.findViewById(R.id.snapshotPreview);
        iv.setImageBitmap(snapshot.getBitmap());
    }

    public void setDevice(AndrONVIFDevice device){
        device.observeStateFlags(getLifecycleOwner(), newstate -> {
            if((newstate & AndrONVIFDevice.STATE_AUTHENTICATED_MASK) != 0){
                Log.d("DevicePreview", "Device authenticated : " + device.getUrl());
                LinearLayout layout = this.findViewById(R.id.devpreview_loading_overlay);
                layout.setVisibility(VISIBLE);
            }

            if((newstate & AndrONVIFDevice.STATE_SCOPE_MASK) != 0){
                updateScopes(device);
                LinearLayout layout = this.findViewById(R.id.devpreview_loading_overlay);
                layout.setVisibility(INVISIBLE);
            }

            if((newstate & AndrONVIFDevice.STATE_SNAP_MASK) != 0){
                updateBitmap(device);
                LinearLayout layout = this.findViewById(R.id.devpreview_loading_overlay);
                layout.setVisibility(INVISIBLE);
            }
        });
    }
}
