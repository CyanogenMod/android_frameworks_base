package com.android.systemui.quicksettings;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.BrightnessController;
import com.android.systemui.statusbar.policy.BrightnessController.BrightnessStateChangeCallback;
import com.android.systemui.statusbar.policy.ToggleSlider;

public class BrightnessTile extends QuickSettingsTile implements BrightnessStateChangeCallback {

    private static final String TAG = "BrightnessTile";

    private final int mBrightnessDialogLongTimeout;
    private Dialog mBrightnessDialog;
    private final Handler mHandler;

    public BrightnessTile(Context context, final QuickSettingsController qsc, Handler handler) {
        super(context, qsc);

        mHandler = handler;
        mBrightnessDialogLongTimeout = mContext.getResources().getInteger(R.integer.quick_settings_brightness_dialog_long_timeout);

        mOnClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                qsc.mBar.collapseAllPanels(true);
                showBrightnessDialog();
            }
        };

        mOnLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_DISPLAY_SETTINGS);
                return true;
            }

        };

        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), this);
        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), this);
        onBrightnessLevelChanged();
    }

    private void showBrightnessDialog() {
        if (mBrightnessDialog == null) {
            mBrightnessDialog = new Dialog(mContext);
            mBrightnessDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mBrightnessDialog.setContentView(R.layout.quick_settings_brightness_dialog);
            mBrightnessDialog.setCanceledOnTouchOutside(true);

            new BrightnessController(mContext,
                    (ImageView) mBrightnessDialog.findViewById(R.id.brightness_icon),
                    (ToggleSlider) mBrightnessDialog.findViewById(R.id.brightness_slider));

            mBrightnessDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            mBrightnessDialog.getWindow().getAttributes().privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
            mBrightnessDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        if (!mBrightnessDialog.isShowing()) {
            try {
                WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
            } catch (RemoteException e) {
                // Do nothing here
            }
            mBrightnessDialog.show();
            dismissBrightnessDialog(mBrightnessDialogLongTimeout);
        }
    }

    private void dismissBrightnessDialog(int timeout) {
        if (mBrightnessDialog != null) {
            mHandler.postDelayed(mDismissBrightnessDialogRunnable, timeout);
        }
    }

    private final Runnable mDismissBrightnessDialogRunnable = new Runnable() {
        @Override
        public void run() {
            if (mBrightnessDialog != null && mBrightnessDialog.isShowing()) {
                mBrightnessDialog.dismiss();
            }
        };
    };

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        // Dismiss dialog before nullifying it.
        mDismissBrightnessDialogRunnable.run();
        mBrightnessDialog = null;
        super.updateResources();
    }

    private void updateTileResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        int mode;
        try {
            mode = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            boolean autoBrightness = (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            mDrawable = autoBrightness
                    ? R.drawable.ic_qs_brightness_auto_on
                    : R.drawable.ic_qs_brightness_auto_off;
            mLabel = mContext.getString(R.string.quick_settings_brightness_label);
        } catch (SettingNotFoundException e) {
            Log.e(TAG, "Brightness setting not found", e);
        }
    }

    @Override
    public void onBrightnessLevelChanged() {
        updateTileResources();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateTileResources();
    }
}
