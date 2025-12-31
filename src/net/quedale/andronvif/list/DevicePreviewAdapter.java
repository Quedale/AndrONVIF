package net.quedale.andronvif.list;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.recyclerview.widget.RecyclerView;

import net.quedale.andronvif.R;
import net.quedale.andronvif.jni.AndrONVIFDevice;

import java.util.List;

public class DevicePreviewAdapter extends RecyclerView.Adapter<DevicePreviewAdapter.DevicePreviewHolder>  {
    private final NavController nav;
    private List<AndrONVIFDevice> devices = null;

    public DevicePreviewAdapter(NavController nav) {
        this.nav = nav;
    }

    public void setDevices(List<AndrONVIFDevice> devices){
        //TODO proper merge?

        if(devices == null){
            if(this.devices != null){
                this.devices.clear();
                this.devices = null;
                Log.d("DevicePreviewAdapter", "Clear devices");
                notifyDataSetChanged();
            }
            return;
        }

        if(this.devices == null){
            this.devices = devices;
            Log.d("DevicePreviewAdapter", "Set devices " + devices.size());
            notifyDataSetChanged();
            return;
        }

        //Add new devices
        for(int i=0;i<devices.size();i++) {
            AndrONVIFDevice device = devices.get(i);
            if (this.devices.contains(device)) {
                Log.d("DevicePreviewAdapter", "Device already rendered : " + i);
                continue;
            }

            Log.d("DevicePreviewAdapter", "notify inserted : " + i);
            this.devices.add(device);
            notifyItemInserted(i);
        }

        //Clean up device that got removed
        for (int i = this.devices.size() - 1; i >= 0; i--) {
            AndrONVIFDevice device = this.devices.get(i);
            if (devices.contains(device)) {
                Log.d("DevicePreviewAdapter", "Device still valid : " + i);
                continue;
            }

            Log.d("DevicePreviewAdapter", "device no longer valid notify remove : " + i);
            this.devices.remove(device);
            notifyItemRemoved(i);
        }
    }

    @NonNull
    @Override
    public DevicePreviewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d("DevicePreviewAdapter","onCreateViewHolder");
        View device_view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_preview, parent, false);
        return new DevicePreviewHolder(device_view,nav);
    }

    @Override
    public void onBindViewHolder(@NonNull DevicePreviewHolder holder, int position) {
        Log.d("DevicePreviewAdapter","onBindViewHolder");
        holder.setDevice(devices.get(position));
    }

    @Override
    public int getItemCount() {
        if(devices == null){
            return 0;
        }
        return devices.size();
    }

    public static class DevicePreviewHolder extends RecyclerView.ViewHolder {
        private final DevicePreview devicePreview;
        private final NavController nav;
        private AndrONVIFDevice device;

        public DevicePreviewHolder(@NonNull View itemView, NavController nav) {
            super(itemView);
            this.nav = nav;
            devicePreview = itemView.findViewById(R.id.devicepreview);

            itemView.setOnClickListener(v -> {
                if(device == null){
                    Log.e("DevicePreviewAdapter", "Device is unexpectedly null");
                    return;
                }
                Log.d("DevicePreviewAdapter","Clicked " + device.getUrl());
                DeviceListFragmentDirections.ActionDeviceListFragmentToPlayerFragment action = DeviceListFragmentDirections.actionDeviceListFragmentToPlayerFragment(device);
                this.nav.navigate(action);
            });
        }

        public void setDevice(AndrONVIFDevice device){
            this.device = device;
            devicePreview.setDevice(device);
        }
    }
}
