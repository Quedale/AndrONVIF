package net.quedale.andronvif.list;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import net.quedale.andronvif.R;
import net.quedale.andronvif.databinding.DeviceListFragmentBinding;

public class DeviceListFragment extends Fragment {

private DeviceListFragmentBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        binding = DeviceListFragmentBinding.inflate(inflater, container, false);
        Log.d("DeviceListFragment", "onCreateView");
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("DeviceListFragment", "onViewCreated");


        binding.fab.setOnClickListener(view1 ->
            NavHostFragment.findNavController(DeviceListFragment.this).navigate(R.id.action_DeviceListFragment_to_DeviceDiscoveryFragment)
        );
    }
}