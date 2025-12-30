package net.quedale.andronvif.list;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import net.quedale.andronvif.R;
import net.quedale.andronvif.jni.AndrONVIFDevice;

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
        TextView device_row = this.findViewById(R.id.device_url_view);
        device_row.setText(device.getUrl());

        TextView device_name = this.findViewById(R.id.device_name);
        device_name.setText(device.getName());

        TextView device_location = this.findViewById(R.id.device_location);
        device_location.setText(device.getLocation());

        TextView device_hardware = this.findViewById(R.id.device_hw);
        device_hardware.setText(device.getHardware());
    }

    public void setDevice(AndrONVIFDevice device){
        updateScopes(device);
        device.observeStateFlags(getLifecycleOwner(), newstate -> {
            if((newstate & AndrONVIFDevice.STATE_AUTHENTICATED_MASK) != 0){
                Log.d("DevicePreview", "Device authenticated : " + device.getUrl());
            }

            if((newstate & AndrONVIFDevice.STATE_SCOPE_MASK) != 0){
                Log.d("DevicePreview", "Device scopes fetched : " + device.getUrl());
                updateScopes(device);
            }
        });
    }
}
