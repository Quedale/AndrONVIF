package net.quedale.andronvif.adddevice;

import android.util.Log;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.utils.CancellableRunnable;

public class AddDeviceBackgroundTask extends CancellableRunnable {
    private final AddDeviceModel model;
    public AddDeviceBackgroundTask(AddDeviceModel model){
        this.model = model;
    }

    @Override
    public void done() {
        model.setState(AddDeviceModel.State.DONE);
    }

    @Override
    public void execute() {
        model.setState(AddDeviceModel.State.AUTHENTICATING);
        AndrONVIFDevice device = model.getDevice();
        if(device == null){
            String port = null;
            StringBuilder urlbuilder;
            if(model.isHTTPS()){
                urlbuilder = new StringBuilder("https://");
                if(model.getPort() > 0 && model.getPort() != 443){
                    port = String.valueOf(model.getPort());
                }
            } else {
                urlbuilder = new StringBuilder("http://");
                if(model.getPort() > 0 && model.getPort() != 80){
                    port = String.valueOf(model.getPort());
                }
            }
            urlbuilder.append(model.getHostname());
            if(port != null) {
                urlbuilder.append(":");
                urlbuilder.append(port);
            }
            urlbuilder.append("/onvif/device_service");
            device = new AndrONVIFDevice(urlbuilder.toString());
        }

        Log.d(AddDeviceController.class.getSimpleName(), "Authenticating : " + model.getUsername() + "@" + device.getUrl());
        device.setCredentials(model.getUsername(), model.getPassword());
        AndrONVIFDevice.SoapFault fault = device.authenticate();

        //Set device to model only if valid
        if(fault == AndrONVIFDevice.SoapFault.SOAP_FAULT_NONE) model.setDevice(device);
        model.setFault(fault);

        //TODO Save to app settings
    }
}
