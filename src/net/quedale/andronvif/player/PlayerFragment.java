package net.quedale.andronvif.player;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import net.quedale.andronvif.R;
import net.quedale.andronvif.discovery.DiscoveryFragment;
import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.databinding.PlayerFragmentBinding;
import net.quedale.andronvif.jni.AndrONVIFPlayer;
import net.quedale.andronvif.list.DeviceListViewModel;

public class PlayerFragment  extends Fragment {
    private PlayerFragmentBinding binding;
    private AndrONVIFPlayer player;
    private DeviceListViewModel activity_model;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = PlayerFragmentBinding.inflate(inflater, container, false);

        player = new AndrONVIFPlayer(binding.surfaceVideo,binding.deviceOnvifUrl);
        
        //Set immersive mode
        Window window = this.requireActivity().getWindow();
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(window, window.getDecorView());
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

        binding.profileBtn.setOnClickListener(v -> new ProfileSelectionFragment().showNow(PlayerFragment.this.requireActivity().getSupportFragmentManager(), "tag"));
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        if(activity_model == null)
            activity_model = new ViewModelProvider(this.requireActivity()).get(DeviceListViewModel.class);
        
        AndrONVIFDevice device = activity_model.getSelectedDevice();
        if (device != null) {
            Log.d(PlayerFragment.class.getSimpleName(),"Playing : " + device.getUrl());

            player.setDevice(device);

            String text = getString(R.string.player_playing_device_message) + device.getUrl();
            binding.deviceOnvifUrl.setText(text);

            SurfaceHolder sh = binding.surfaceVideo.getHolder();
            sh.addCallback(player);

            device.observeSelectedProfile(getViewLifecycleOwner(), profile -> {
                Log.d("PlayerFragment","MediaProfile changed : " + profile.getIndex() + " : " + profile.getName());
                player.setDevice(device);
                player.stop();
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> player.play());
            });
        } else {
            Log.e(PlayerFragment.class.getSimpleName(), "No device selected.");
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
        //Unset immersive mode
        Window window = this.requireActivity().getWindow();
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(window, window.getDecorView());
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
        player.unref();
        super.onDestroyView();
    }
}