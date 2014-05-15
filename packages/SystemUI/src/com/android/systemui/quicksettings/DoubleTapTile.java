package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import org.cyanogenmod.hardware.TapToWake;

public class DoubleTapTile extends QuickSettingsTile {

    public DoubleTapTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleState();
                updateResources();
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        };

        qsc.registerObservedContent(
                Settings.System.getUriFor(Settings.System.DOUBLE_TAP_WAKE_GESTURE), this);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        if (TapToWake.isEnabled()) {
            mDrawable = R.drawable.ic_qs_double_tap_on;
            mLabel = mContext.getString(R.string.quick_settings_double_tap);
        } else {
            mDrawable = R.drawable.ic_qs_double_tap_off;
            mLabel = mContext.getString(R.string.quick_settings_double_tap_off);
        }
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    private void toggleState() {
        boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.DOUBLE_TAP_WAKE_GESTURE, 1) == 1;
        if (TapToWake.setEnabled(!enabled)) {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.DOUBLE_TAP_WAKE_GESTURE, !enabled ? 1 : 0);
        }
    }
}
