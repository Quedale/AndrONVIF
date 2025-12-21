package net.quedale.andronvif.player;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.databinding.PlayerFragmentBinding;

public class PlayerFragment  extends Fragment {
    private PlayerFragmentBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = PlayerFragmentBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        if (getArguments() != null) {
            AndrONVIFDevice device = PlayerFragmentArgs.fromBundle(getArguments()).getDevice();
            Log.d(PlayerFragment.class.getSimpleName(),"Playing : " + device.getUrl());

            TextView tv = new TextView(this.requireContext());
            tv.setText("Playing device : " + device.getUrl());
            binding.listlayout.addView(tv);

        } else {
            Log.e(PlayerFragment.class.getSimpleName(), "No argument bundle.");
        }
    }
}