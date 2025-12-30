package net.quedale.andronvif.adddevice;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.databinding.AddDeviceFragmentBinding;
import net.quedale.andronvif.list.DeviceListViewModel;

import java.util.Objects;

public class AddDeviceFragment extends Fragment {
    private AddDeviceFragmentBinding binding;
    private DeviceListViewModel activity_model;
    private AddDeviceModel model;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = AddDeviceFragmentBinding.inflate(inflater, container, false);
        activity_model = new ViewModelProvider(this.requireActivity()).get(DeviceListViewModel.class);
        model = new ViewModelProvider(this).get(AddDeviceModel.class);
        return binding.getRoot();
    }

    private void populate(AndrONVIFDevice device){
        if (device != null) {
            model.setDevice(device);

            String hostname = device.getHostname();
            String protocol = device.getProtocol();
            int port = device.getPort();
            if(hostname != null) {
                binding.txthostname.setText(hostname);
                binding.txthostname.setEnabled(false);
            }
            if(port > 0) {
                binding.txtport.setText(String.valueOf(port));
                binding.txtport.setEnabled(false);
            }
            if(protocol != null) {
                if(protocol.equalsIgnoreCase("https")){
                    binding.chckproto.setChecked(true);
                    binding.chckproto.setEnabled(false);
                } else if(protocol.equalsIgnoreCase("http")){
                    binding.chckproto.setChecked(false);
                    binding.chckproto.setEnabled(false);
                }
            }
        } else {
            binding.txthostname.setEnabled(true);
            binding.txtport.setEnabled(true);
            binding.chckproto.setEnabled(true);
        }
    }

    private void showErrorAlert(String message){
        AlertDialog alertDialog = new AlertDialog.Builder(this.requireContext()).create();
        alertDialog.setTitle("Error Adding Device");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        AndrONVIFDevice device = null;
        if(getArguments() != null)
            device = AddDeviceFragmentArgs.fromBundle(getArguments()).getDevice();
        else
            Log.e(AddDeviceFragment.class.getSimpleName(),"No argument bundle.");
        this.populate(device);

        model.observeState(getViewLifecycleOwner(), newstate -> {
            Log.d(AddDeviceFragment.class.getSimpleName()," New State " + newstate);
            switch(newstate){
                case AUTHENTICATING:
                    binding.loadingOverlay.setVisibility(VISIBLE);
                    break;
                case DONE:
                    binding.loadingOverlay.setVisibility(INVISIBLE);
                    switch(model.getFault()){
                        case SOAP_FAULT_NONE:
                            activity_model.addDevice(Objects.requireNonNull(model.getDevice()));
                            AddDeviceFragmentDirections.ActionAddDeviceFragmentToPlayerFragment action = AddDeviceFragmentDirections.actionAddDeviceFragmentToPlayerFragment(model.getDevice());
                            NavHostFragment.findNavController(AddDeviceFragment.this).navigate(action);
                            break;
                        case SOAP_FAULT_CONNECTION_ERROR:
                            AddDeviceFragment.this.showErrorAlert("Unable to connect.");
                            break;
                        case SOAP_FAULT_UNAUTHORIZED:
                            AddDeviceFragment.this.showErrorAlert("Unauthorized.");
                            break;
                        case SOAP_FAULT_NOT_VALID:
                        case SOAP_FAULT_ACTION_NOT_SUPPORTED:
                        case SOAP_FAULT_UNEXPECTED:
                            AddDeviceFragment.this.showErrorAlert("An unexpected error occured.");
                            break;
                    }
                    break;
            }
        });

        binding.txtport.addTextChangedListener(new PortNumberTextWatcher(binding.txtport));

        binding.saveBtn.setOnClickListener(view1 -> {
            if(binding.txthostname.getText() != null)
                model.setHostname(binding.txthostname.getText().toString());
            if(binding.txtport.getText() != null)
                model.setPort(Integer.parseInt(binding.txtport.getText().toString()));
            if(binding.chckproto.isChecked())
                model.setHTTPS(true);
            if(binding.txtuser.getText() != null)
                model.setUsername(binding.txtuser.getText().toString());
            if(binding.txtpassword.getText() != null)
                model.setPassword(binding.txtpassword.getText().toString());
            model.save();
        });

        binding.cancelbtn.setOnClickListener(view1 -> NavHostFragment.findNavController(AddDeviceFragment.this).popBackStack());
    }

    @Override
    public void onDestroyView() {
        Log.d(AddDeviceFragment.class.getSimpleName(),"onDestroyView");
        model.cleanup();
        super.onDestroyView();
    }

    @Override
    public void onStop() {
        Log.d(AddDeviceFragment.class.getSimpleName(),"onStop");
        super.onStop();
    }
}
