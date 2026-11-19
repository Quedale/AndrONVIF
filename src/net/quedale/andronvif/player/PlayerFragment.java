package net.quedale.andronvif.player;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
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

    private boolean nav_bar_visible = false;
    private boolean originalTheme = false;


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
        originalTheme = windowInsetsController.isAppearanceLightStatusBars();
        windowInsetsController.setAppearanceLightStatusBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(window.getDecorView(), new OnApplyWindowInsetsListener() {
            @Override
            public @org.jspecify.annotations.NonNull WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v, @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                nav_bar_visible = insets.isVisible(WindowInsetsCompat.Type.navigationBars());
                binding.deviceOnvifUrl.setPadding(0, 0, 0, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            }
        });

        binding.profileBtn.setOnClickListener(v -> new ProfileSelectionFragment().showNow(PlayerFragment.this.requireActivity().getSupportFragmentManager(), "tag"));
        binding.muteBtn.setOnClickListener(v -> {
            boolean muted = player.toggleMute();
            if(muted){
                binding.muteBtn.setImageResource(android.R.drawable.ic_lock_silent_mode);
            } else {
                binding.muteBtn.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            }
        });
        binding.surfaceVideo.setOnClickListener(v -> {
            View decorView = requireActivity().getWindow().getDecorView();
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(requireActivity().getWindow(), decorView);
            if(nav_bar_visible) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars());
            }
        });
        //TODO Disable this if player stop/fails
        binding.getRoot().setKeepScreenOn(true);
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
                player.stop();
                player.setDevice(device);
                player.play();
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
        windowInsetsController.setAppearanceLightStatusBars(originalTheme);
        player.unref();
        super.onDestroyView();
    }
}