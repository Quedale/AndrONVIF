package net.quedale.andronvif;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import net.quedale.andronvif.databinding.MainBinding;
import net.quedale.andronvif.jni.AndrONVIFPlayer;

public class AndrONVIFActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.d("AndrONVIFActivity", "onCreate");

        MainBinding binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        AndrONVIFPlayer.nativeInit();
    }

    protected void onDestroy() {
        AndrONVIFPlayer.nativeFinalize();
        super.onDestroy();
    }
}
