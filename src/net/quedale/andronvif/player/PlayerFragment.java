package net.quedale.andronvif.player;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.quedale.andronvif.discovery.DiscoveryFragment;
import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.databinding.PlayerFragmentBinding;
import net.quedale.andronvif.jni.AndrONVIFPlayer;

public class PlayerFragment  extends Fragment {
    private PlayerFragmentBinding binding;
    private AndrONVIFPlayer player;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = PlayerFragmentBinding.inflate(inflater, container, false);

        player = new AndrONVIFPlayer(this.requireContext());

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        if (getArguments() != null) {
            AndrONVIFDevice device = PlayerFragmentArgs.fromBundle(getArguments()).getDevice();
            Log.d(PlayerFragment.class.getSimpleName(),"Playing : " + device.getUrl());

            player.setDevice(device);

            binding.deviceOnvifUrl.setText("Playing device : " + device.getUrl());

            SurfaceHolder sh = binding.surfaceVideo.getHolder();
            sh.addCallback(player);
        } else {
            Log.e(PlayerFragment.class.getSimpleName(), "No argument bundle.");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(PlayerFragment.class.getSimpleName(), "onStart");
        player.play();
    }

    @Override
    public void onStop() {
        Log.d(PlayerFragment.class.getSimpleName(),"onStop");
        player.stop();
        super.onStop();
    }

    @Override
    public void onDestroyView() { //TODO Check if detach is better
        Log.d(DiscoveryFragment.class.getSimpleName(),"onDestroyView");
        player.unref();
        super.onDestroyView();
    }
}