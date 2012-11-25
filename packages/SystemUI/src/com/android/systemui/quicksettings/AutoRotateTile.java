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
import com.android.systemui.statusbar.policy.RotationLockController;
import com.android.systemui.statusbar.policy.RotationLockController.RotationLockControllerCallback;

public class AutoRotateTile extends QuickSettingsTile implements RotationLockControllerCallback {

    private boolean mRotationLocked;
    private RotationLockController mRotationLockController;

    public AutoRotateTile(Context context, QuickSettingsController qsc, Handler handler) {
        super(context, qsc);

        mRotationLockController = new RotationLockController(context);
        mRotationLockController.addRotationLockControllerCallback(this);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean locked = mRotationLockController.isRotationLocked();
                mRotationLockController.setRotationLocked(!locked);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_DISPLAY_SETTINGS);
                return true;
            }
        };
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        if(mRotationLocked){
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

    @Override
    public void onRotationLockStateChanged(boolean rotationLocked,
            boolean affordanceVisible) {
        mRotationLocked = rotationLocked;       
    }
}
