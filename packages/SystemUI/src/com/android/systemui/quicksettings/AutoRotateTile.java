package com.android.systemui.quicksettings;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.internal.view.RotationPolicy;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class AutoRotateTile extends QuickSettingsTile {

    private boolean enabled = false;
    private ContentObserver mContentObserver;
    private static final String TAG = "AutoRotateButton";

    public AutoRotateTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mContentObserver = new AutoRotationObserver(handler);

        onClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                RotationPolicy.setRotationLock(mContext, !enabled);
            }
        };

        onLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_DISPLAY_SETTINGS);
                return true;
            }
        };
    }

    void applyAutoRotationChanges() {
        if(enabled){
            mDrawable = R.drawable.ic_qs_rotation_locked;
            mLabel = mContext.getString(R.string.quick_settings_rotation_locked_label);
        }else{
            mDrawable = R.drawable.ic_qs_auto_rotate;
            mLabel = mContext.getString(R.string.quick_settings_rotation_unlocked_label);
        }
        updateQuickSettings();
    }

    @Override
    void onPostCreate() {
        applyAutoRotationChanges();
        super.onPostCreate();
    }

    private class AutoRotationObserver extends ContentObserver {

        public AutoRotationObserver(Handler handler) {
            super(handler);
            observe();
        }

        public void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if(uri.equals(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION))){
                enabled = Settings.System.getInt(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) != 1;
                applyAutoRotationChanges();
            }
            super.onChange(selfChange, uri);
        }

        public void unObserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

    }

}
