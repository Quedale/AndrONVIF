package net.quedale.andronvif.utils;

import android.os.CountDownTimer;
import android.view.View;
import android.widget.ProgressBar;

public class ProgressBarTimer extends CountDownTimer {
    private final ProgressBar bar;
    private final int totalDurationMillis;
    public ProgressBarTimer(ProgressBar bar, int totalDurationMillis, int intervalMillis){
        super(totalDurationMillis, intervalMillis);
        this.bar = bar;
        this.totalDurationMillis = totalDurationMillis;
    }

    public void startProgress(){
        bar.setVisibility(View.VISIBLE);
        bar.setIndeterminate(false);
        start();
    }

    public void stopProgress(){
        cancel();
        bar.setIndeterminate(true);
        bar.setVisibility(View.GONE);
    }

    @Override
    public void onTick(long millisUntilFinished) {
        int max = bar.getMax();
        long progress = totalDurationMillis - millisUntilFinished;
        int percentage = (int) ((progress * max) / totalDurationMillis);
        bar.setProgress(percentage);
    }

    @Override
    public void onFinish() {
        bar.setProgress(bar.getMax());
    }
}
