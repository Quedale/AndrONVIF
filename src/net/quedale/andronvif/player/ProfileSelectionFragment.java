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
import androidx.lifecycle.ViewModelProvider;

import net.quedale.andronvif.R;
import net.quedale.andronvif.databinding.ProfileSelectionFragmentBinding;

public class ProfileSelectionFragment extends DialogFragment {
        private ProfileSelectionFragmentBinding binding = null;
        private ProfileSelectionModel model = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater,container,savedInstanceState);
        binding = ProfileSelectionFragmentBinding.inflate(inflater, container, false);
        model = new ViewModelProvider(this.requireActivity()).get(ProfileSelectionModel.class);

        model.observeState(getViewLifecycleOwner(), newstate -> {
                Log.d("ProfileSelectionFragment", "New profile list state !! : " + newstate);
                switch(newstate){
                    case INITIALIZING:
                    case LOADING:
                        ProfileSelectionFragment.this.binding.profileLoading.setVisibility(View.VISIBLE);
                        break;
                    case DONE:
                        ProfileSelectionFragment.this.binding.profileLoading.setVisibility(View.INVISIBLE);
                        ProfileSelectionFragment.this.binding.tempText.setVisibility(View.VISIBLE);
                        break;
                }
            });

        binding.profileDialogLayout.setOnClickListener(v -> ProfileSelectionFragment.this.dismiss());
        binding.profileDialogContainer.setOnClickListener(v -> {
            //Placeholder to swallow click to prevent passing the click even below
        });
        return binding.getRoot();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState){
        final Dialog dialog = new Dialog(ProfileSelectionFragment.this.requireContext());

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
