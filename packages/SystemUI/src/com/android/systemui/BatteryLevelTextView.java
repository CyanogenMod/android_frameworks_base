package com.android.systemui;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.policy.BatteryController;

public class BatteryLevelTextView extends TextView implements
        BatteryController.BatteryStateChangeCallback{

    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";

    private BatteryController mBatteryController;
    private boolean mShow;

    private SettingsObserver mObserver = new SettingsObserver(new Handler());

    private class SettingsObserver extends UserContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }
        @Override
        protected void observe() {
            super.observe();
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    STATUS_BAR_SHOW_BATTERY_PERCENT), false, this, UserHandle.USER_ALL);
        }
        @Override
        protected void unobserve() {
            super.unobserve();
            getContext().getContentResolver().unregisterContentObserver(this);
        }
        @Override
        public void update() {
            loadShowBatteryTextSetting();
            setVisibility(mShow ? View.VISIBLE : View.GONE);
        }
    };

    public BatteryLevelTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadShowBatteryTextSetting();
        setVisibility(mShow ? View.VISIBLE : View.GONE);
    }

    private void loadShowBatteryTextSetting() {
        int currentUserId = ActivityManager.getCurrentUser();
        mShow = 0 != Settings.System.getIntForUser(
                getContext().getContentResolver(),
                Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0, currentUserId);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        setText(getResources().getString(R.string.battery_level_template, level));
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        mBatteryController.addStateChangedCallback(this);
    }

    @Override
    public void onPowerSaveChanged() {
        // Not used
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mObserver.observe();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mObserver.unobserve();

        if (mBatteryController != null) {
            mBatteryController.removeStateChangedCallback(this);
        }
    }
}
