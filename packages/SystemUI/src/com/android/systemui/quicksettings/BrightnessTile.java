package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.settings.BrightnessController.BrightnessStateChangeCallback;

public class BrightnessTile extends QuickSettingsTile implements BrightnessStateChangeCallback {

    private static final String TAG = "BrightnessTile";

    public BrightnessTile(Context context, final QuickSettingsController qsc) {
        super(context, qsc);

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
        Intent intent = new Intent(Intent.ACTION_SHOW_BRIGHTNESS_DIALOG);
        mContext.sendBroadcast(intent);
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
        updateResources();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }
}
