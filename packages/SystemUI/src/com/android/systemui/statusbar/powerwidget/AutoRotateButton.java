package com.android.systemui.statusbar.powerwidget;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import com.android.internal.view.RotationPolicy;
import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

public class AutoRotateButton extends PowerButton {

    private static final String TAG = "AutoRotateButton";

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION));
    }

    public AutoRotateButton() { mType = BUTTON_AUTOROTATE; }

    @Override
    protected void updateState(Context context) {
        if (getAutoRotation(context)) {
            mIcon = R.drawable.stat_orientation_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_orientation_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState(Context context) {
        RotationPolicy.setRotationLock(context, getAutoRotation(context));
    }

    @Override
    protected boolean handleLongClick(Context context) {
        Intent intent = new Intent("android.settings.DISPLAY_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    private boolean getAutoRotation(Context context) {
        return !RotationPolicy.isRotationLocked(context);
    }

}
