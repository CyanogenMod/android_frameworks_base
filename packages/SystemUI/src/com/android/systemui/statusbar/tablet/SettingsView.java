/*
 * Copyright (C) 2010 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2012 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.tablet;

import android.app.StatusBarManager;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.AirplaneModeController;
import com.android.systemui.statusbar.policy.AutoRotateController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BrightnessController;
import com.android.systemui.statusbar.policy.DoNotDisturbController;
import com.android.systemui.statusbar.policy.ToggleSlider;
import com.android.systemui.statusbar.policy.VolumeController;
import com.android.systemui.statusbar.policy.WifiController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.policy.MobileDataController;
import com.android.systemui.statusbar.policy.NetworkModeController;
import com.android.systemui.statusbar.policy.SoundController;

public class SettingsView extends LinearLayout implements View.OnClickListener {
    static final String TAG = "SettingsView";

    private static final String NO_TOGGLES = "no_toggles";
    private static final int[] TOGGLE_ID_ARRAY = new int[6];
    private static final int AIRPLANE_ID = 0;
    private static final int ROTATE_ID = 1;
    private static final int BLUETOOTH_ID = 2;
    private static final int GPS_ID = 3;
    private static final int WIFI_ID = 4;
    private static final int FLASHLIGHT_ID = 5;
    private static final int MOBILE_DATA_ID = 6;
    private static final int NETWORK_MODE_ID = 7;
    private static final int SOUND_ID = 8;

    private static final int[] mToggleIds = new int[]{
        R.id.toggle_1, R.id.toggle_2, R.id.toggle_3, R.id.toggle_4, R.id.toggle_5, R.id.toggle_6};
    private static final int[] mToggleIcons = new int[]{
        R.id.toggle_1_icon, R.id.toggle_2_icon, R.id.toggle_3_icon, R.id.toggle_4_icon, R.id.toggle_5_icon, R.id.toggle_6_icon};
    private static final int[] mToggleLabels = new int[]{
        R.id.toggle_1_label, R.id.toggle_2_label, R.id.toggle_3_label, R.id.toggle_4_label, R.id.toggle_5_label, R.id.toggle_6_label};
    private static final int[] mToggleCheckBoxes = new int[]{
        R.id.toggle_1_checkbox, R.id.toggle_2_checkbox, R.id.toggle_3_checkbox, R.id.toggle_4_checkbox, R.id.toggle_5_checkbox, R.id.toggle_6_checkbox};

    private static final String[] KEY_TOGGLES = new String[]{"pref_airplane_toggle", "pref_rotate_toggle", "pref_bluetooth_toggle", "pref_gps_toggle", "pref_wifi_toggle", "pref_flashlight_toggle", "pref_mobile_data_toggle", "pref_network_mode_toggle", "pref_sound_toggle"};

    private static final String BUTTON_DELIMITER = "\\|";
    private static final String BUTTONS_DEFAULT = KEY_TOGGLES[0]
        + BUTTON_DELIMITER + KEY_TOGGLES[4]
        + BUTTON_DELIMITER + KEY_TOGGLES[2]
        + BUTTON_DELIMITER + KEY_TOGGLES[1];

    AirplaneModeController mAirplane;
    AutoRotateController mRotate;
    BluetoothController mBluetooth;
    BrightnessController mBrightness;
    DoNotDisturbController mDoNotDisturb;
    FlashlightController mFlashLight;
    LocationController mGps;
    MobileDataController mMobileData;
    NetworkModeController mNetworkMode;
    SoundController mSound;
    WifiController mWifi;
    View mRotationLockContainer;
    View mRotationLockSeparator;

    private Context mContext;
    private Handler mHandler;
    private String mToggleContainer;
    private String[] mToggles;

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.WIDGET_BUTTONS_TABLET), false, this);
            updateToggleContainer();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateToggleContainer();
        }

        void updateToggleContainer(){
            mToggleContainer = Settings.System.getString(getContext().getContentResolver(), Settings.System.WIDGET_BUTTONS_TABLET);
        }
    }

    public SettingsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SettingsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = getContext();
        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final Context context = getContext();

        mRotationLockSeparator = findViewById(R.id.rotate_separator);

        mBrightness = new BrightnessController(context,
                (ToggleSlider)findViewById(R.id.brightness));
        mDoNotDisturb = new DoNotDisturbController(context,
                (CompoundButton)findViewById(R.id.do_not_disturb_checkbox));

        if(mToggleContainer == null)
           mToggleContainer = BUTTONS_DEFAULT;
        if(!mToggleContainer.equals(NO_TOGGLES)){
            updateToggles();

            int mLastVisible = mToggles.length;
            for(int i=0; i<mToggleIds.length; i++){
                if(i<mLastVisible)
                    findViewById(mToggleIds[i]).setOnClickListener(this);
                else
                    findViewById(mToggleIds[i]).setVisibility(View.GONE);
            }
        } else {
            for(int i=0; i<mToggleIds.length; i++){
                findViewById(mToggleIds[i]).setVisibility(View.GONE);
            }
        }

        findViewById(R.id.settings).setOnClickListener(this);
    }

     private void updateToggles(){
        mToggles = mToggleContainer.split("\\|");
        for(int i=0; i<mToggles.length; i++){
                String mToggleName = mToggles[i].replace("\\", "");
                int[] resources = getResourcesById(mToggleName);
                setToggleResources(mToggleIds[i], mToggleIcons[i], mToggleLabels[i], resources[0], resources[1]);
                TOGGLE_ID_ARRAY[i] = resources[2];
                setToggleController(mToggleName, i);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(mAirplane != null)
            mAirplane.release();
        if(mGps != null)
            mGps.release();
        if(mSound != null)
            mSound.release();
        if(mRotate != null)
            mRotate.release();
        mDoNotDisturb.release();
    }

    private void setToggleController(String id, int i){
        int checkBox = mToggleCheckBoxes[i];
        if(id.equals(KEY_TOGGLES[0]))
           mAirplane = new AirplaneModeController(mContext, (CompoundButton)findViewById(checkBox));
        else if(id.equals(KEY_TOGGLES[1])){
            mRotationLockContainer = findViewById(mToggleIds[i]);
            mRotate = new AutoRotateController(mContext,
                (CompoundButton)findViewById(checkBox),
                new AutoRotateController.RotationLockCallbacks() {
                    @Override
                    public void setRotationLockControlVisibility(boolean show) {
                        mRotationLockContainer.setVisibility(show ? View.VISIBLE : View.GONE);
                        mRotationLockSeparator.setVisibility(show ? View.VISIBLE : View.GONE);
                    }
                });
        }
        else if(id.equals(KEY_TOGGLES[2]))
           mBluetooth = new BluetoothController(mContext, (CompoundButton)findViewById(checkBox));
        else if(id.equals(KEY_TOGGLES[3]))
           mGps = new LocationController(mContext, (CompoundButton)findViewById(checkBox));
        else if(id.equals(KEY_TOGGLES[4]))
           mWifi = new WifiController(mContext, (CompoundButton)findViewById(checkBox));
        else if(id.equals(KEY_TOGGLES[5]))
           mFlashLight = new FlashlightController(mContext, (CompoundButton)findViewById(checkBox));
        else if(id.equals(KEY_TOGGLES[6]))
           mMobileData = new MobileDataController(mContext, (CompoundButton)findViewById(checkBox));
        else if(id.equals(KEY_TOGGLES[7]))
           mNetworkMode = new NetworkModeController(mContext, (CompoundButton)findViewById(checkBox));
        else if(id.equals(KEY_TOGGLES[8]))
           mSound = new SoundController(mContext, (CompoundButton)findViewById(checkBox));
    }

    private int[] getResourcesById(String id){
        if(id.equals(KEY_TOGGLES[0]))
           return new int[]{R.drawable.ic_sysbar_airplane_on, R.string.status_bar_settings_airplane, AIRPLANE_ID};
        else if(id.equals(KEY_TOGGLES[1]))
           return new int[]{R.drawable.ic_sysbar_rotate_on, R.string.status_bar_settings_auto_rotation, ROTATE_ID};
        else if(id.equals(KEY_TOGGLES[2]))
           return new int[]{R.drawable.stat_sys_data_bluetooth, R.string.status_bar_settings_bluetooth_button, BLUETOOTH_ID};
        else if(id.equals(KEY_TOGGLES[3]))
           return new int[]{R.drawable.stat_gps_on, R.string.status_bar_settings_location, GPS_ID};
        else if(id.equals(KEY_TOGGLES[4]))
           return new int[]{R.drawable.ic_sysbar_wifi_on, R.string.status_bar_settings_wifi_button, WIFI_ID};
        else if(id.equals(KEY_TOGGLES[5]))
           return new int[]{R.drawable.stat_flashlight_on, R.string.status_bar_settings_flashlight, FLASHLIGHT_ID};
        else if(id.equals(KEY_TOGGLES[6]))
           return new int[]{R.drawable.stat_data_on, R.string.status_bar_settings_mobile_data, MOBILE_DATA_ID};
        else if(id.equals(KEY_TOGGLES[7]))
           return new int[]{R.drawable.stat_2g3g_on, R.string.status_bar_settings_network_mode, NETWORK_MODE_ID};
        else if(id.equals(KEY_TOGGLES[8]))
           return new int[]{R.drawable.stat_ring_on, R.string.status_bar_settings_sound_mode, SOUND_ID};
        else
           return new int[]{0, 0};
    }

    private void setToggleResources(int resourceId, int resourceIcon, int resourceLabel, int newResourceIcon, int newResourceLabel){
        LinearLayout mToggle = (LinearLayout) findViewById(resourceId);
        ImageView mToggleImageView = (ImageView) mToggle.findViewById(resourceIcon);
        mToggleImageView.setImageResource(newResourceIcon);
        TextView mToggleTextView = (TextView) mToggle.findViewById(resourceLabel);
        mToggleTextView.setText(newResourceLabel);
    }

    private StatusBarManager getStatusBarManager() {
        return (StatusBarManager)getContext().getSystemService(Context.STATUS_BAR_SERVICE);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.toggle_1:
                onClickToggle(TOGGLE_ID_ARRAY[0]);
                break;
            case R.id.toggle_2:
                onClickToggle(TOGGLE_ID_ARRAY[1]);
                break;
            case R.id.toggle_3:
                onClickToggle(TOGGLE_ID_ARRAY[2]);
                break;
            case R.id.toggle_4:
                onClickToggle(TOGGLE_ID_ARRAY[3]);
                break;
            case R.id.toggle_5:
                onClickToggle(TOGGLE_ID_ARRAY[4]);
                break;
            case R.id.toggle_6:
                onClickToggle(TOGGLE_ID_ARRAY[5]);
                break;
            case R.id.settings:
                onClickSettings();
                break;
        }
    }

    // Network
    // ----------------------------
    private void onClickNetwork() {
        getContext().startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /*
     * OnClickListener for custom toggles
     */
    private void onClickToggle(int id) {
        switch(id){
                case WIFI_ID:
                getContext().startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
                case BLUETOOTH_ID:
                getContext().startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
                case GPS_ID:
                getContext().startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
                case FLASHLIGHT_ID:
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("net.cactii.flash2", "net.cactii.flash2.MainActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                break;
                case MOBILE_DATA_ID:
                getContext().startActivity(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
                case NETWORK_MODE_ID:
                getContext().startActivity(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
                case SOUND_ID:
                getContext().startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
        }

        getStatusBarManager().collapse();
    }

    // Settings
    // ----------------------------
    private void onClickSettings() {
        getContext().startActivity(new Intent(Settings.ACTION_SETTINGS)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getStatusBarManager().collapse();
    }
}

