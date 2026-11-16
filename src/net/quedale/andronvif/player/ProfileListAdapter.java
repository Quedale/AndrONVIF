package net.quedale.andronvif.player;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.quedale.andronvif.R;
import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.jni.AndrONVIFMediaProfiles;

public class ProfileListAdapter <T extends View> extends RecyclerView.Adapter<ProfileListAdapter.ProfileListViewHolder<T>> {
    private AndrONVIFMediaProfiles profiles = null;
    private AndrONVIFDevice device;
    private final ProfileListAdapter.ProfileViewSetter<T> setter;
    private final View.OnClickListener listener;

    public ProfileListAdapter(ProfileViewSetter<T> setter, View.OnClickListener listener) {
        this.setter = setter;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProfileListViewHolder<T> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d("ProfileListAdapter","onCreateViewHolder");
        View device_view = LayoutInflater.from(parent.getContext()).inflate(R.layout.profile_preview, parent, false);
        return new ProfileListAdapter.ProfileListViewHolder<>(device_view,R.id.profilepreview,setter,listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileListViewHolder<T> holder, int position) {
        Log.d("ProfileListAdapter","onBindViewHolder");
        holder.setProfile(profiles.getProfileAt(position),device);
    }

    @Override
    public int getItemCount() {
        if(profiles == null){
            return 0;
        }
        return profiles.getSize();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setProfiles(AndrONVIFMediaProfiles profiles, AndrONVIFDevice device){

        if(profiles == null){
            if(this.profiles != null){
                Log.d("ProfileListAdapter", "Clear devices");
                this.profiles = null;
                notifyDataSetChanged();
            }
            return;
        }

        this.profiles = profiles;
        this.device = device;
        //TODO Compare new and old list and notify changes only
        notifyDataSetChanged();
    }

    public interface ProfileViewSetter<T> {
        void setProfile(T view, AndrONVIFMediaProfiles.AndrONVIFProfile profile);
    }

    public static class ProfileListViewHolder<T extends View> extends RecyclerView.ViewHolder {
        private final T view;
        private AndrONVIFMediaProfiles.AndrONVIFProfile profile;
        private AndrONVIFDevice device;
        private final ProfileViewSetter<T> setter;

        public ProfileListViewHolder(@NonNull View itemView, int viewid, ProfileViewSetter<T> setter, View.OnClickListener listener) {
            super(itemView);
            view = itemView.findViewById(viewid);
            this.setter = setter;

            itemView.setOnClickListener(v -> {
                device.setSelectedProfile(this.profile);
                listener.onClick(v);
            });
        }
        public T getView(){
            return view;
        }

        public void setProfile(AndrONVIFMediaProfiles.AndrONVIFProfile profile, AndrONVIFDevice device){
            this.profile = profile;
            this.device = device;
            setter.setProfile(view,profile);
        }
    }
}
