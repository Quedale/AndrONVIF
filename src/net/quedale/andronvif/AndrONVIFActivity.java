package net.quedale.andronvif;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import net.quedale.andronvif.databinding.MainBinding;

import dalvik.annotation.optimization.FastNative;

public class AndrONVIFActivity extends AppCompatActivity {
    @FastNative
    private native String nativeGetGStreamerInfo();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.d("AndrONVIFActivity", "onCreate");

        MainBinding binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Log.d(AndrONVIFApplication.class.getSimpleName(),nativeGetGStreamerInfo());
    }

}
