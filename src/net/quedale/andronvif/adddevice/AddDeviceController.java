package net.quedale.andronvif.adddevice;

import net.quedale.andronvif.utils.AbstractController;
import net.quedale.andronvif.utils.AppExecutorService;

public class AddDeviceController extends AbstractController<AddDeviceModel, AddDeviceFragment> {
    private AddDeviceBackgroundTask task = null;
    public AddDeviceController(AddDeviceFragment fragment) {
        super(fragment);
    }

    public void save(){
        getModel().setState(AddDeviceModel.State.INITIALIZING);
        task = new AddDeviceBackgroundTask(getModel());
        AppExecutorService executor = AppExecutorService.getInstance();
        executor.execute(task);
    }

    public void stop(){
        if(task != null){
            task.stop();
            task = null;
        }
    }
}
