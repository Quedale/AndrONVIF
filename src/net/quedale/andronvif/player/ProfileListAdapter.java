package net.quedale.andronvif.player;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.quedale.andronvif.R;
import net.quedale.andronvif.jni.AndrONVIFMediaProfiles;

public class ProfileListAdapter <T extends View> extends RecyclerView.Adapter<ProfileListAdapter.ProfileListViewHolder<T>> {
    private AndrONVIFMediaProfiles profiles = null;
    private final ProfileListAdapter.ProfileViewSetter<T> setter;

    public ProfileListAdapter(ProfileViewSetter<T> setter) {
        this.setter = setter;
    }

    @NonNull
    @Override
    public ProfileListViewHolder<T> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d("ProfileListAdapter","onCreateViewHolder");
        View device_view = LayoutInflater.from(parent.getContext()).inflate(R.layout.profile_preview, parent, false);
        return new ProfileListAdapter.ProfileListViewHolder<>(device_view,R.id.profilepreview,setter);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileListViewHolder<T> holder, int position) {
        Log.d("ProfileListAdapter","onBindViewHolder");
        holder.setProfile(profiles.getProfileAt(position));
    }

    @Override
    public int getItemCount() {
        if(profiles == null){
            return 0;
        }
        return profiles.getSize();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setProfiles(AndrONVIFMediaProfiles profiles){

        if(profiles == null){
            if(this.profiles != null){
                Log.d("ProfileListAdapter", "Clear devices");
                this.profiles = null;
                notifyDataSetChanged();
            }
            return;
        }

        this.profiles = profiles;
        notifyDataSetChanged();
    }

    public interface ProfileViewSetter<T> {
        void setProfile(T view, AndrONVIFMediaProfiles.AndrONVIFProfile profile);
    }

    public static class ProfileListViewHolder<T extends View> extends RecyclerView.ViewHolder {
        private final T view;
        private AndrONVIFMediaProfiles.AndrONVIFProfile profile;
        private final ProfileViewSetter<T> setter;

        public ProfileListViewHolder(@NonNull View itemView, int viewid, ProfileViewSetter<T> setter) {
            super(itemView);
            view = itemView.findViewById(viewid);
            this.setter = setter;
            itemView.setOnClickListener(v -> {
                if(profile == null){
                    Log.e("ProfileListAdapter", "MediaProfile is unexpectedly null");
                    return;
                }
                Log.d("DeviceListAdapter","Clicked " + profile.getName());
                //TODO Select profile to view
            });
        }
        public T getView(){
            return view;
        }

        public void setProfile(AndrONVIFMediaProfiles.AndrONVIFProfile profile){
            this.profile = profile;
            setter.setProfile(view,profile);
        }
    }
}
