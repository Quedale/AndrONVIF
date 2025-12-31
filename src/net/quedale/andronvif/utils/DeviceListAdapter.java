package net.quedale.andronvif.utils;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.quedale.andronvif.jni.AndrONVIFDevice;

import java.util.ArrayList;
import java.util.List;

public class DeviceListAdapter<T extends View> extends RecyclerView.Adapter<DeviceListAdapter.DeviceListViewHolder<T>> {
    private final DeviceViewNavigator nav;
    private final int viewid;
    private final int layoutid;
    private List<AndrONVIFDevice> devices = null;
    private final DeviceViewSetter<T> setter;

    public DeviceListAdapter(int layoutid, int viewid, DeviceViewSetter<T> setter, DeviceViewNavigator nav){
        this.nav = nav;
        this.viewid = viewid;
        this.layoutid = layoutid;
        this.setter = setter;
    }

    @NonNull
    @Override
    public DeviceListViewHolder<T> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d("DeviceListAdapter","onCreateViewHolder");
        View device_view = LayoutInflater.from(parent.getContext()).inflate(layoutid, parent, false);
        return new DeviceListAdapter.DeviceListViewHolder<>(device_view,nav,viewid,setter);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceListViewHolder holder, int position) {
        Log.d("DeviceListAdapter","onBindViewHolder");
        holder.setDevice(devices.get(position));
    }

    @Override
    public int getItemCount() {
        if(devices == null){
            return 0;
        }
        return devices.size();
    }

    public void setDevices(List<AndrONVIFDevice> devices){
        if(devices == null){
            if(this.devices != null){
                this.devices.clear();
                this.devices = null;
                Log.d("DeviceListAdapter", "Clear devices");
                notifyDataSetChanged();
            }
            return;
        }

        if(this.devices == null){
            this.devices = devices;
            Log.d("DeviceListAdapter", "Set devices " + devices.size());
            notifyDataSetChanged();
            return;
        }

        //Add new devices
        for(int i=0;i<devices.size();i++) {
            AndrONVIFDevice device = devices.get(i);
            if (this.devices.contains(device)) {
                Log.d("DeviceListAdapter", "Device already rendered : " + i);
                continue;
            }

            Log.d("DeviceListAdapter", "notify inserted : " + i);
            this.devices.add(device);
            notifyItemInserted(this.devices.indexOf(device));
        }

        //Clean up device that got removed
        for (int i = this.devices.size() - 1; i >= 0; i--) {
            AndrONVIFDevice device = this.devices.get(i);
            if (devices.contains(device)) {
                Log.d("DeviceListAdapter", "Device still valid : " + i);
                continue;
            }

            Log.d("DeviceListAdapter", "device no longer valid notify remove : " + i);
            this.devices.remove(device);
            notifyItemRemoved(i);
        }
    }

    public void addDevice(AndrONVIFDevice device){
        if(this.devices == null){
            this.devices = new ArrayList<>();
        }

        if(!this.devices.contains(device)) {
            this.devices.add(device);
            notifyItemInserted(this.devices.indexOf(device));
        }
    }

    public interface DeviceViewSetter<T> {
        void setDevice(T view, AndrONVIFDevice device);
    }

    public interface DeviceViewNavigator {
        void navigate(AndrONVIFDevice device);
    }

    public static class DeviceListViewHolder<T extends View> extends RecyclerView.ViewHolder {
        private final T view;
        private AndrONVIFDevice device;
        private final DeviceViewSetter<T> setter;

        public DeviceListViewHolder(@NonNull View itemView, DeviceViewNavigator nav, int viewid, DeviceViewSetter<T> setter) {
            super(itemView);
            view = itemView.findViewById(viewid);
            this.setter = setter;
            itemView.setOnClickListener(v -> {
                if(device == null){
                    Log.e("DeviceListAdapter", "Device is unexpectedly null");
                    return;
                }
                Log.d("DeviceListAdapter","Clicked " + device.getUrl());
                nav.navigate(this.device);
            });
        }
        public T getView(){
            return view;
        }

        public void setDevice(AndrONVIFDevice device){
            this.device = device;
            setter.setDevice(view,device);
        }
    }
}
