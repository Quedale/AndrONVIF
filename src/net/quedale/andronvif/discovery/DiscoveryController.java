package net.quedale.andronvif.discovery;

import net.quedale.andronvif.utils.AbstractController;
import net.quedale.andronvif.utils.AppExecutorService;

public class DiscoveryController extends AbstractController<DiscoveryModel,DiscoveryFragment> {
    private DiscoveryBackgroundTask task = null;

    public DiscoveryController(DiscoveryFragment fragment){
        super(fragment);
    }

    public void start(){
        getModel().setState(DiscoveryModel.State.INITIALIZING);
        task = new DiscoveryBackgroundTask(getModel());
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
