package net.quedale.andronvif.list;

import net.quedale.andronvif.utils.AbstractController;
import net.quedale.andronvif.utils.AppExecutorService;

public class DeviceListController extends AbstractController<DeviceListModel, DeviceListFragment>  {
    private DeviceListBackgroundTask task = null;

    public DeviceListController(DeviceListFragment fragment) {
        super(fragment);
    }

    public void start(){
        getModel().setState(DeviceListModel.State.INITIALIZING);
        task = new DeviceListBackgroundTask(getModel(),this.getFragment().requireContext());
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
