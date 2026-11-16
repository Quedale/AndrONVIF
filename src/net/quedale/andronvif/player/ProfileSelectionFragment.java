package net.quedale.andronvif.player;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import net.quedale.andronvif.R;
import net.quedale.andronvif.databinding.ProfileSelectionFragmentBinding;
import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.list.DeviceListViewModel;

public class ProfileSelectionFragment extends DialogFragment {
        private ProfileSelectionFragmentBinding binding = null;
        private ProfileSelectionModel model = null;
        private DeviceListViewModel activity_model = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater,container,savedInstanceState);
        binding = ProfileSelectionFragmentBinding.inflate(inflater, container, false);

        ProfileListAdapter<ProfilePreview> adapter = getProfilePreviewAbstractDeviceListAdapter();
        binding.profileRecyclerView.setAdapter(adapter);

        LifecycleOwner owner = getViewLifecycleOwner();
        model.observeState(owner, newstate -> {
                Log.d("ProfileSelectionFragment", "New profile list state !! : " + newstate);
                switch(newstate){
                    case INITIALIZING:
                    case LOADING:
                        ProfileSelectionFragment.this.binding.profileLoading.setVisibility(View.VISIBLE);
                        break;
                    case DONE:
                        ProfileSelectionFragment.this.binding.profileLoading.setVisibility(View.GONE);
                        ProfileSelectionFragment.this.binding.profileRecyclerView.setVisibility(View.VISIBLE);
                        break;
                }
            });

        model.observeProfiles(owner, profiles -> adapter.setProfiles(profiles, model.getDevice()));

        binding.profileDialogLayout.setOnClickListener(v -> ProfileSelectionFragment.this.dismiss());
        binding.profileDialogContainer.setOnClickListener(v -> {
            //Placeholder to swallow click to prevent passing the click even below
        });
        return binding.getRoot();
    }

    @NonNull
    private ProfileListAdapter<ProfilePreview> getProfilePreviewAbstractDeviceListAdapter() {
        ProfileListAdapter.ProfileViewSetter<ProfilePreview> setter = ProfilePreview::setProfile;
        View.OnClickListener listener = v -> ProfileSelectionFragment.this.dismiss();
        return new ProfileListAdapter<>(setter,listener);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState){
        final Dialog dialog = new Dialog(ProfileSelectionFragment.this.requireContext());

        if(model == null)
            model = new ViewModelProvider(this.requireActivity()).get(ProfileSelectionModel.class);
        if(activity_model == null)
            activity_model = new ViewModelProvider(this.requireActivity()).get(DeviceListViewModel.class);

        AndrONVIFDevice device = activity_model.getSelectedDevice();
        if(device != null)
            Log.d(ProfileSelectionFragment.class.getSimpleName(),"Fetch profile for : " + device.getUrl());
        else
            Log.e(ProfileSelectionFragment.class.getSimpleName(),"No device set");
        
        model.setDevice(device);

        Window window = dialog.getWindow();
        if(window != null)
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.profile_selection_fragment);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        if(window != null)
            dialog.getWindow().setAttributes(lp);

        return dialog;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d("ProfileSelectionFragment", "onStart");
        model.start();
    }

    @Override
    public void onStop() {
        Log.d("ProfileSelectionFragment","onStop");
        super.onStop();
    }
}
