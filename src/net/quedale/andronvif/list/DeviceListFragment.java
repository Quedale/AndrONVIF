package net.quedale.andronvif.list;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import net.quedale.andronvif.R;
import net.quedale.andronvif.databinding.DeviceListFragmentBinding;
import net.quedale.andronvif.jni.AndrONVIFDevice;

public class DeviceListFragment extends Fragment {

    private DeviceListFragmentBinding binding;
    private final DeviceListController controller = new DeviceListController(this);

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        binding = DeviceListFragmentBinding.inflate(inflater, container, false);
        Log.d("DeviceListFragment", "onCreateView");

        controller.getModel().observeState(getViewLifecycleOwner(), newstate -> {
            Log.d("DeviceListFragment", "New Device list state !! : " + newstate);
            switch(newstate){
                case INITIALIZING:
                case LOADING:
                    DeviceListFragment.this.binding.dbfetchProgress.setVisibility(View.VISIBLE);
                    break;
                case DONE:
                    DeviceListFragment.this.binding.dbfetchProgress.setVisibility(View.INVISIBLE);
                    break;
            }
        });

        controller.getModel().observeDevices(getViewLifecycleOwner(), newlist -> {
            Log.d("DeviceListFragment", "New Device list !! : " + newlist.size());
            for(int i=0;i<newlist.size();i++) {
                AndrONVIFDevice device = newlist.get(i);
                if (controller.getModel().isDeviceRendered(device)) {
                    Log.d("DeviceListFragment", "Device already rendered or added : " + controller.getModel().isDeviceRendered(device));
                    continue;
                }

                Log.d("DeviceListFragment", "Adding device to UI : " + device.getUrl());
                View device_view = inflater.inflate(R.layout.device_preview, binding.gridlayout, false);
                DevicePreview device_preview = device_view.findViewById(R.id.devicepreview);
                device_preview.setDevice(device);

                device_view.setOnClickListener(v -> {
                    Log.d("DeviceListFragment","Clicked " + device.getUrl());
                    DeviceListFragmentDirections.ActionDeviceListFragmentToPlayerFragment action = DeviceListFragmentDirections.actionDeviceListFragmentToPlayerFragment(device);
                    NavHostFragment.findNavController(DeviceListFragment.this).navigate(action);
                });

                int rowindex = i / 2;
                int colindex = i % 2;
                GridLayout.Spec rowSpec = GridLayout.spec(rowindex);
                GridLayout.Spec colSpec = GridLayout.spec(colindex);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(rowSpec, colSpec);
                params.width = 0; // Or ViewGroup.LayoutParams.WRAP_CONTENT
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);

                int grid_margin = (int) getResources().getDimension(R.dimen.grid_margin);
                if(rowindex > 0)
                    params.topMargin = grid_margin;
                if(colindex == 0)
                    params.rightMargin = grid_margin/2;
                else
                    params.leftMargin = grid_margin/2;

                binding.gridlayout.addView(device_view,params);

                controller.getModel().deviceRendered(device);
            }
        });

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("DeviceListFragment", "onViewCreated");

        binding.fab.setOnClickListener(view1 ->
            NavHostFragment.findNavController(DeviceListFragment.this).navigate(R.id.action_DeviceListFragment_to_DeviceDiscoveryFragment)
        );
    }

    @Override
    public void onDestroyView() {
        Log.d("DeviceListFragment","onDestroyView");
        controller.getModel().resetRendered();
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d("DeviceListFragment", "onStart");
        controller.start();
    }

    @Override
    public void onStop() {
        Log.d("DeviceListFragment","onStop");
        controller.stop();
        super.onStop();
    }
}