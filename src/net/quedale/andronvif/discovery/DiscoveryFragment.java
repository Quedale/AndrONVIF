package net.quedale.andronvif.discovery;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.R;
import net.quedale.andronvif.databinding.DeviceDiscoveryFragmentBinding;
import net.quedale.andronvif.list.DeviceListViewModel;

import java.util.List;

public class DiscoveryFragment extends Fragment {

    private DeviceDiscoveryFragmentBinding binding;
    private CountDownTimer countDownTimer;
    private DeviceListViewModel activity_model;
    private DiscoveryModel model;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = DeviceDiscoveryFragmentBinding.inflate(inflater, container, false);
        //TODO use udp retrycoount * timeout
        int totalDurationMillis = 2 * 2000;
        int intervalMillis = 10;
        countDownTimer = new CountDownTimer(totalDurationMillis, intervalMillis) {
            @Override
            public void onTick(long millisUntilFinished) {
                int max = DiscoveryFragment.this.binding.discoveryProgress.getMax();
                long progress = totalDurationMillis - millisUntilFinished;
                int percentage = (int) ((progress * max) / totalDurationMillis);
                DiscoveryFragment.this.binding.discoveryProgress.setProgress(percentage);
            }

            @Override
            public void onFinish() {
                DiscoveryFragment.this.binding.discoveryProgress.setProgress(DiscoveryFragment.this.binding.discoveryProgress.getMax());
            }
        };

        activity_model = new ViewModelProvider(this.requireActivity()).get(DeviceListViewModel.class);
        model = new ViewModelProvider(this).get(DiscoveryModel.class);
        //TODO oberse activity model to add discovered device that were deleted?

        model.observeState(getViewLifecycleOwner(), newstate -> {
            Log.d(DiscoveryFragment.class.getSimpleName()," New State " + newstate);
            switch(newstate){
                case INITIALIZING:
                    DiscoveryFragment.this.binding.discoveryProgress.setVisibility(View.VISIBLE);
                    DiscoveryFragment.this.binding.discoveryProgress.setIndeterminate(true);
                    break;
                case LOADING:
                    DiscoveryFragment.this.binding.discoveryProgress.setVisibility(View.VISIBLE);
                    DiscoveryFragment.this.binding.discoveryProgress.setIndeterminate(false);
                    DiscoveryFragment.this.countDownTimer.start();
                    break;
                case DONE:
                    DiscoveryFragment.this.binding.discoveryProgress.setVisibility(View.INVISIBLE);
                    break;
            }
        });

        model.observeDiscovery(getViewLifecycleOwner(), newlist -> {
            Log.d("DeviceDiscoveryFragment", "modelChanged!! " + newlist.size());
            for(AndrONVIFDevice device : newlist){
                List<AndrONVIFDevice> activiyDevices = activity_model.getDevices();
                if(model.isDeviceRendered(device)){
                    Log.d(DiscoveryFragment.class.getSimpleName(),"Device already rendered : " + model.isDeviceRendered(device));
                    continue;
                }

                if(activiyDevices != null && activiyDevices.contains(device)){
                    Log.d(DiscoveryFragment.class.getSimpleName(),"Device already added : " + activiyDevices.contains(device));
                    continue;
                }

                Log.d("DeviceDiscoveryFragment", "Adding device to UI : " + device.getUrl());
                View device_view = inflater.inflate(R.layout.discovered_device, binding.deviceList, false);
                DiscoveryDeviceRow device_row = device_view.findViewById(R.id.devicerow);
                device_row.setDevice(device);
                device_view.setOnClickListener(v -> {
                    Log.d(DiscoveryDeviceRow.class.getSimpleName(),"Clicked " + device.getUrl());
                    DiscoveryFragmentDirections.ActionDeviceDiscoveryFragmentToAddDeviceFragment action = DiscoveryFragmentDirections.actionDeviceDiscoveryFragmentToAddDeviceFragment();
                    action.setDevice(device);
                    NavHostFragment.findNavController(DiscoveryFragment.this).navigate(action);
                });
                binding.deviceList.addView(device_view);
                model.deviceRendered(device);
            }
        });

        binding.manualAddBtn.setOnClickListener(view1 -> NavHostFragment.findNavController(DiscoveryFragment.this).navigate(R.id.action_DeviceDiscoveryFragment_to_AddDeviceFragment));
        binding.back.setOnClickListener(view1 -> NavHostFragment.findNavController(DiscoveryFragment.this).popBackStack());
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        Log.d(DiscoveryFragment.class.getSimpleName(),"onDestroyView");
        model.resetRendered();
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d("DeviceDiscoveryFragment", "onStart");
        model.start();
    }

    @Override
    public void onStop() {
        Log.d(DiscoveryFragment.class.getSimpleName(),"onStop");
        countDownTimer.cancel();
        super.onStop();
    }
}