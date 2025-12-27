package net.quedale.andronvif.list;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

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

    public void setDevice(AndrONVIFDevice device){
        TextView device_row = this.findViewById(R.id.device_url_view);
        device_row.setText(device.getUrl());

        TextView device_name = this.findViewById(R.id.device_name);
        device_name.setText(device.getName());

        TextView device_location = this.findViewById(R.id.device_location);
        device_location.setText(device.getLocation());

        TextView device_hardware = this.findViewById(R.id.device_hw);
        device_hardware.setText(device.getHardware());
    }
}
