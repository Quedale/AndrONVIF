package net.quedale.andronvif.discovery;

import android.util.Log;

import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.jni.DiscoveryService;
import net.quedale.andronvif.utils.CancellableRunnable;

public class DiscoveryBackgroundTask extends CancellableRunnable {
    private final DiscoveryModel model;
    private DiscoveryService service = null;

    DiscoveryBackgroundTask(DiscoveryModel model){
        this.model = model;
    }

    @Override
    public void done() {
        Log.d(DiscoveryBackgroundTask.class.getSimpleName(), "done");
        model.setState(DiscoveryModel.State.DONE);
    }

    @Override
    public void stop(){
        super.stop();
        if(service != null)
            service.stop();
    }

    @Override
    public void execute() {
        model.setState(DiscoveryModel.State.LOADING);
        service = new DiscoveryService();
        service.discover((match) -> {
            if (DiscoveryBackgroundTask.this.isStopped()){
                Log.w("DiscoveryBackgroundTask", "Task Cancelled");
                return;
            }
            Log.d("DiscoveryBackgroundTask", "Device Found length " + match.getAddressCount() + " : " + match.getAddressAt(0));
            Log.d("DiscoveryBackgroundTask", "Name " + match.getScopeValue("name"));
            Log.d("DiscoveryBackgroundTask", "Hardware " + match.getScopeValue("hardware"));
            Log.d("DiscoveryBackgroundTask", "Location " + match.getScopeValue("location"));
            if(match.getAddressCount() > 0)
                model.addDevice(new AndrONVIFDevice(match));
        });

        service.unref();
    }
}