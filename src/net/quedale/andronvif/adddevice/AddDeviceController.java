package net.quedale.andronvif.adddevice;


import android.util.Log;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.utils.AbstractController;

public class AddDeviceController extends AbstractController<AddDeviceModel, AddDeviceFragment> {
    public AddDeviceController(AddDeviceFragment fragment) {
        super(fragment);
    }

    public void save(){
        //TODO Create runnable task
        AddDeviceModel model  = getModel();
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
        model.setState(AddDeviceModel.State.DONE);


        //TODO Save to app settings
    }
}
