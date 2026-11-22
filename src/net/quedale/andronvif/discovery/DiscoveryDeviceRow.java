package net.quedale.andronvif.discovery;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.R;

public class DiscoveryDeviceRow extends LinearLayout {
    public DiscoveryDeviceRow(Context context) {
        super(context);
    }

    public DiscoveryDeviceRow(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DiscoveryDeviceRow(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    //TODO Create generic shared function
    private LifecycleOwner getLifecycleOwner() {
        Context context = getContext();
        while (!(context instanceof LifecycleOwner)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return (LifecycleOwner) context;
    }

    public void setDevice(AndrONVIFDevice device){
        TextView device_name = this.findViewById(R.id.device_name);
        TextView device_location = this.findViewById(R.id.device_location);
        TextView device_hardware = this.findViewById(R.id.device_hw);
        AndrONVIFDevice.AndrONVIFDeviceDetails details = device.getDetails();
        device.observeDetails(getLifecycleOwner(), andrONVIFDeviceDetails -> {
            if(details != null){
                device_name.setText(details.getName());
                device_location.setText(details.getLocation());
                device_hardware.setText(details.getHardware());
            } else {
                device_name.setText(null);
                device_location.setText(null);
                device_hardware.setText(null);
            }
        });

        TextView device_row = this.findViewById(R.id.device_url_view);
        device_row.setText(device.getUrl());

        if(details != null){
            device_name.setText(details.getName());
            device_location.setText(details.getLocation());
            device_hardware.setText(details.getHardware());
        } else {
            device_name.setText(null);
            device_location.setText(null);
            device_hardware.setText(null);
        }

    }
}
