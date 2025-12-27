package net.quedale.andronvif.list;

import android.content.Context;
import android.util.Log;

import androidx.room.Room;

import net.quedale.andronvif.db.AndrONVIFDatabase;
import net.quedale.andronvif.db.DBDevice;
import net.quedale.andronvif.db.DBDeviceDao;
import net.quedale.andronvif.discovery.DiscoveryBackgroundTask;
import net.quedale.andronvif.jni.AndrONVIFDevice;
import net.quedale.andronvif.utils.CancellableRunnable;

import java.util.ArrayList;
import java.util.List;

public class DeviceListBackgroundTask extends CancellableRunnable {
    private final DeviceListModel model;
    private final Context context;

    public DeviceListBackgroundTask(DeviceListModel model, Context context) {
        this.model = model;
        this.context = context;
    }

    @Override
    public void execute() {
        model.setState(DeviceListModel.State.LOADING);
        AndrONVIFDatabase db = Room.databaseBuilder(this.context,
                AndrONVIFDatabase.class, "devices").build();
        DBDeviceDao dao = db.deviceDao();
        List<DBDevice> dbdevices = dao.getAll();
        List<AndrONVIFDevice> androdevices = new ArrayList<>();
        for(DBDevice device : dbdevices){
            StringBuilder builder = new StringBuilder();
            if(device.tls)
                builder.append("https://");
            else
                builder.append("http://");

            builder.append(device.address);
            if((device.tls && device.port != 443) || (!device.tls && device.port != 80)) {
                builder.append(":");
                builder.append(device.port);
            }
            builder.append("/onvif/device_service");

            //TODO Using single native constructor
            AndrONVIFDevice newdev = new AndrONVIFDevice(builder.toString());
            newdev.setCredentials(device.username,device.password);

            androdevices.add(newdev);

            if(this.isStopped()){
                for(AndrONVIFDevice androdev : androdevices){
                    androdev.unref();
                }
                return;
            }
        }

        //TODO do this part from individual device coroutine
        for(AndrONVIFDevice androdev : androdevices){
            androdev.authenticate();
        }

        //The loop above should be fast enough that adding device individually would be irrelevant
        model.setDevices(androdevices);
    }

    @Override
    public void done() {
        Log.d(DiscoveryBackgroundTask.class.getSimpleName(), "done");
        model.setState(DeviceListModel.State.DONE);
    }
}
