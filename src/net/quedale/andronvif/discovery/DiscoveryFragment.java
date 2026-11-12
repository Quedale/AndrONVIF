package net.quedale.andronvif.discovery;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.R;
import net.quedale.andronvif.databinding.DeviceDiscoveryFragmentBinding;
import net.quedale.andronvif.list.DeviceListViewModel;
import net.quedale.andronvif.utils.DeviceListAdapter;
import net.quedale.andronvif.utils.ProgressBarTimer;

import java.util.List;

public class DiscoveryFragment extends Fragment {

    private DeviceDiscoveryFragmentBinding binding;
    private ProgressBarTimer countDownTimer;
    private DeviceListViewModel activity_model;
    private DiscoveryModel model;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = DeviceDiscoveryFragmentBinding.inflate(inflater, container, false);

        //Uses the systembar space to display loading indicator
        ViewCompat.setOnApplyWindowInsetsListener(binding.listlinelayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.LayoutParams params = v.getLayoutParams();
            params.height = insets.top;
            v.setLayoutParams(params);
            return WindowInsetsCompat.CONSUMED;
        });

        DeviceListAdapter<DiscoveryDeviceRow> adapter = getDevicePreviewAbstractDeviceListAdapter();
        binding.discoveryRecyclerView.setAdapter(adapter);

        //TODO use udp retrycoount * timeout
        int totalDurationMillis = 2 * 2000;
        int intervalMillis = 10;
        countDownTimer = new ProgressBarTimer(this.binding.discoveryProgress,totalDurationMillis,intervalMillis);

        activity_model = new ViewModelProvider(this.requireActivity()).get(DeviceListViewModel.class);
        model = new ViewModelProvider(this).get(DiscoveryModel.class);
        //TODO oberse activity model to add discovered device that were deleted?

        model.observeState(getViewLifecycleOwner(), newstate -> {
            Log.d(DiscoveryFragment.class.getSimpleName()," New State " + newstate);
            switch(newstate){
                case INITIALIZING:
                    DiscoveryFragment.this.binding.discoveryProgress.setVisibility(View.VISIBLE);
                    break;
                case LOADING:
                    DiscoveryFragment.this.countDownTimer.startProgress();
                    break;
                case DONE:
                    if(model.getDevices() == null || model.getDevices().isEmpty()){
                        Log.d("DiscoveryFragment","No device found on network.");
                        TextView lblNotFound = this.binding.noDeviceFoundLbl;//this.findViewById(R.id.devpreview_loading_overlay);
                        lblNotFound.setVisibility(View.VISIBLE);
                    }
                    TextView lblNotFound = this.binding.searchingDevicesLbl;//this.findViewById(R.id.devpreview_loading_overlay);
                    lblNotFound.setVisibility(View.GONE);
                    DiscoveryFragment.this.binding.discoveryProgress.setVisibility(View.INVISIBLE);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + newstate);
            }
        });

        model.observeDiscovery(getViewLifecycleOwner(), newlist -> {
            Log.d("DeviceDiscoveryFragment", "modelChanged!! " + newlist.size());
            for(AndrONVIFDevice device : newlist){
                List<AndrONVIFDevice> activiyDevices = activity_model.getDevices();
                if(activiyDevices != null && activiyDevices.contains(device)){
                    Log.d(DiscoveryFragment.class.getSimpleName(),"Device already added : " + activiyDevices.contains(device));
                    continue;
                }
                adapter.addDevice(device);
            }
        });

        binding.manualAddBtn.setOnClickListener(view1 -> NavHostFragment.findNavController(DiscoveryFragment.this).navigate(R.id.action_DeviceDiscoveryFragment_to_AddDeviceFragment));
        binding.back.setOnClickListener(view1 -> NavHostFragment.findNavController(DiscoveryFragment.this).popBackStack());
        return binding.getRoot();
    }

    @NonNull
    private DeviceListAdapter<DiscoveryDeviceRow> getDevicePreviewAbstractDeviceListAdapter() {
        DeviceListAdapter.DeviceViewNavigator nav = device -> {
            DiscoveryFragmentDirections.ActionDeviceDiscoveryFragmentToAddDeviceFragment action = DiscoveryFragmentDirections.actionDeviceDiscoveryFragmentToAddDeviceFragment();
            action.setDevice(device);
            NavHostFragment.findNavController(DiscoveryFragment.this).navigate(action);
        };
        DeviceListAdapter.DeviceViewSetter<DiscoveryDeviceRow> setter = DiscoveryDeviceRow::setDevice;
        return new DeviceListAdapter<>(R.layout.discovered_device,R.id.devicerow, setter, nav);
    }

    @Override
    public void onDestroyView() {
        Log.d(DiscoveryFragment.class.getSimpleName(),"onDestroyView");
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
        countDownTimer.stopProgress();
        super.onStop();
    }
}