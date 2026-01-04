package net.quedale.andronvif.utils;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

/*
* Simple class to mock FloatingActionButton as simple Button for Accessibility service
*/
public class AccessibleFloatingActionButton extends FloatingActionButton {
    public AccessibleFloatingActionButton(@NonNull Context context) {
        super(context);
    }

    public AccessibleFloatingActionButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AccessibleFloatingActionButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return "android.widget.Button";
    }
}
