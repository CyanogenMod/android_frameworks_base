package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
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

    public AutoRotateTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                RotationPolicy.setRotationLock(mContext, getAutoRotation());
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_DISPLAY_SETTINGS);
                return true;
            }
        };
        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)
                , this);
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        if(!getAutoRotation()){
            mDrawable = R.drawable.ic_qs_rotation_locked;
            mLabel = mContext.getString(R.string.quick_settings_rotation_locked_label);
        }else{
            mDrawable = R.drawable.ic_qs_auto_rotate;
            mLabel = mContext.getString(R.string.quick_settings_rotation_unlocked_label);
        }
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    private boolean getAutoRotation() {
        return !RotationPolicy.isRotationLocked(mContext);
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }
}
