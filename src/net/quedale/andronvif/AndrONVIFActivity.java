package net.quedale.andronvif;

import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.fragment.NavHostFragment;

import net.quedale.andronvif.databinding.MainBinding;
import net.quedale.andronvif.player.PlayerFragment;

public class AndrONVIFActivity extends AppCompatActivity {

    public static AndrONVIFActivity ACTIVITY;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ACTIVITY = this;
        Log.d("AndrONVIFActivity", "onCreate");

        MainBinding binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        PermissionHandler.getInstance();
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    private Fragment getCurrentFragment(){
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment currentFragment = fragmentManager.findFragmentById(R.id.nav_host_fragment_content_main);
        if (currentFragment instanceof NavHostFragment) {
            FragmentManager childFragmentManager = currentFragment.getChildFragmentManager();
            currentFragment = childFragmentManager.getPrimaryNavigationFragment();
        }
        return currentFragment;
    }

    @Override
    public void onUserLeaveHint () {
        super.onUserLeaveHint();

        Fragment currentFragment = getCurrentFragment();

        if (currentFragment instanceof PlayerFragment) {
            Log.d("AndrONVIFActivity","leaving application");
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                Log.d("AndrONVIFActivity", "switching to Picture-In-Picture");
                // TODO Extract aspectRation from surface dimension
                // Rational aspectRatio = new Rational(16, 9); // Example: 16:9 aspect ratio
                enterPictureInPictureMode(new PictureInPictureParams.Builder()
                        // .setAspectRatio(aspectRatio)
                        // Add custom actions if needed
                        // .setActions(...)
                        .build());
            }
        }
    }
}
