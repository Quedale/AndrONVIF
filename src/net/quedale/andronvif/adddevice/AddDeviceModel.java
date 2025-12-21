package net.quedale.andronvif.adddevice;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.utils.AbstractModel;

public class AddDeviceModel extends AbstractModel<AddDeviceModel.State> {
    public enum State {
        INITIALIZING,
        AUTHENTICATING,
        DONE
    }

    private AndrONVIFDevice.SoapFault fault = AndrONVIFDevice.SoapFault.SOAP_FAULT_NONE;
    private AndrONVIFDevice device;
    private String hostname;
    private int port;
    private boolean isHTTPS;
    private String username;
    private String password;

    public AndrONVIFDevice.SoapFault getFault() {
        return fault;
    }

    public void setFault(AndrONVIFDevice.SoapFault fault) {
        this.fault = fault;
    }

    public AndrONVIFDevice getDevice(){
        return device;
    }

    public void setDevice(AndrONVIFDevice device){
        this.device = device;
        this.device.ref();
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isHTTPS() {
        return isHTTPS;
    }

    public void setHTTPS(boolean HTTPS) {
        isHTTPS = HTTPS;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void cleanup(){
        if(this.device != null){
            this.device.unref();
            this.device = null;
        }
    }
}
