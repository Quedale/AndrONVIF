package net.quedale.andronvif.list;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import net.quedale.andronvif.R;
import net.quedale.andronvif.databinding.DeviceListFragmentBinding;
import net.quedale.andronvif.utils.DeviceListAdapter;

public class DeviceListFragment extends Fragment {

    private DeviceListFragmentBinding binding;
    private DeviceListViewModel model;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        binding = DeviceListFragmentBinding.inflate(inflater, container, false);
        Log.d("DeviceListFragment", "onCreateView");

        DeviceListAdapter<DevicePreview> adapter = getDevicePreviewAbstractDeviceListAdapter();
        binding.myRecyclerView.setAdapter(adapter);

        model = new ViewModelProvider(this.requireActivity()).get(DeviceListViewModel.class);
        model.observeState(getViewLifecycleOwner(), newstate -> {
            Log.d("DeviceListFragment", "New Device list state !! : " + newstate);
            switch(newstate){
                case INITIALIZING:
                case LOADING:
                    DeviceListFragment.this.binding.dbfetchProgress.setVisibility(View.VISIBLE);
                    break;
                case DONE:
                    DeviceListFragment.this.binding.dbfetchProgress.setVisibility(View.GONE);
                    break;
            }
        });

        model.observeDevices(getViewLifecycleOwner(), newlist -> {
            Log.d("DeviceListFragment", "New Device list : " + newlist.size());
            adapter.setDevices(newlist);
        });

        return binding.getRoot();
    }

    @NonNull
    private DeviceListAdapter<DevicePreview> getDevicePreviewAbstractDeviceListAdapter() {
        DeviceListAdapter.DeviceViewNavigator nav = device -> {
            net.quedale.andronvif.list.DeviceListFragmentDirections.ActionDeviceListFragmentToPlayerFragment action = net.quedale.andronvif.list.DeviceListFragmentDirections.actionDeviceListFragmentToPlayerFragment(device);
            NavHostFragment.findNavController(DeviceListFragment.this).navigate(action);
        };
        DeviceListAdapter.DeviceViewSetter<DevicePreview> setter = DevicePreview::setDevice;
        return new DeviceListAdapter<>(R.layout.device_preview,R.id.devicepreview, setter, nav);
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
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d("DeviceListFragment", "onStart");
        model.start();
    }

    @Override
    public void onStop() {
        Log.d("DeviceListFragment","onStop");
        super.onStop();
    }
}