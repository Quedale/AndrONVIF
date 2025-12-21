package net.quedale.andronvif.utils;

import android.os.Handler;
import android.os.Looper;

public abstract class CancellableRunnable implements Runnable {
    private boolean stopped;

    public abstract void execute();
    public void stopped(){}
    public void done() {}

    void dispatchDone (){
        new Handler(Looper.getMainLooper()).post(this::done);
    }

    @Override
    public void run() {
        this.execute();

        if(this.isStopped()){
            this.stopped();
        } else {
            this.dispatchDone();
        }
    }

    public void stop(){
        this.stopped = true;
    }

    public boolean isStopped() {
        return this.stopped;
    }
}
