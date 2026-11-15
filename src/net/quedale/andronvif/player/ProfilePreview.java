package net.quedale.andronvif.player;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import net.quedale.andronvif.R;
import net.quedale.andronvif.jni.AndrONVIFMediaProfiles;

public class ProfilePreview extends FrameLayout {
    public ProfilePreview(Context context) {
        super(context);
    }

    public ProfilePreview(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ProfilePreview(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setProfile(AndrONVIFMediaProfiles.AndrONVIFProfile profile){
        TextView view = this.findViewById(R.id.profile_name_txt);
        view.setText(getContext().getString(R.string.profile_name_lbl,profile.getName()));
        view = this.findViewById(R.id.profile_token_txt);
        view.setText(getContext().getString(R.string.profile_token_label,profile.getName()));

        ImageView iview = this.findViewById(R.id.profileFixedIcon);
        iview.setVisibility(VISIBLE);
    }
}
