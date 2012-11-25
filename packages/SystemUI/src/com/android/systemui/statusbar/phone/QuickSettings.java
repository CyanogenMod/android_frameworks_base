/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import java.util.ArrayList;

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Profile;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.view.RotationPolicy;
import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsModel.BluetoothState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.RSSIState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.RefreshCallback;
import com.android.systemui.statusbar.phone.QuickSettingsModel.State;
import com.android.systemui.statusbar.phone.QuickSettingsModel.UserState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.WifiState;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BrightnessController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.ToggleSlider;
import com.android.systemui.statusbar.powerwidget.PowerButton;

/**
 *
 */
class QuickSettings {
    private static final String TAG = "QuickSettings";
    public static final boolean SHOW_IME_TILE = false;

    public static final int USER_TILE = 0;
    public static final int BRIGHTNESS_TILE = 1;
    public static final int SETTINGS_TILE = 2;
    public static final int WIFI_TILE = 3;
    public static final int MOBILE_NETWORK_TILE = 4;
    public static final int ROTATION_LOCK_TILE = 5;
    public static final int BATTERY_TILE = 6;
    public static final int AIRPLANE_MODE_TILE = 7;
    public static final int BLUETOOTH_TILE = 8;
    public static final int RINGER_MODE_TILE = 9;
    public static final int VIBRATION_TILE = 10;
    public static final int ALARM_TILE = 11;
    public static final int BUGREPORT_TILE = 12;
    public static final int IME_TILE = 13;
    public static final int LOCATION_TILE = 14;
    public static final int MEDIA_TILE = 15;
    public static final int TIME_TILE = 16;
    public static final int WIFI_DISPLAY_TILE = 17;
    public static final int SLEEP_TILE = 18;
    public static final int LOCK_SCREEN_TILE = 19;
    public static final int SOUND_AND_VIBRATION_TILE = 20;

    private final Context mContext;
    private PanelBar mBar;
    private final QuickSettingsModel mModel;
    private final ViewGroup mContainerView;

    private final DisplayManager mDisplayManager;
    private WifiDisplayStatus mWifiDisplayStatus;
    private PhoneStatusBar mStatusBarService;
    private final BluetoothState mBluetoothState;

    private BrightnessController mBrightnessController;
    private BluetoothController mBluetoothController;

    private Dialog mBrightnessDialog;
    private final int mBrightnessDialogShortTimeout;
    private final int mBrightnessDialogLongTimeout;

    private AsyncTask<Void, Void, Pair<String, Drawable>> mUserInfoTask;

    private final LevelListDrawable mBatteryLevels;
    private final LevelListDrawable mChargingBatteryLevels;

    boolean mTilesSetUp = false;

    private final Handler mHandler;

    // The set of QuickSettingsTiles that have dynamic spans (and need to be updated on
    // configuration change)
    private final ArrayList<QuickSettingsTileView> mDynamicSpannedTiles =
            new ArrayList<QuickSettingsTileView>();

    private final RotationPolicy.RotationPolicyListener mRotationPolicyListener =
            new RotationPolicy.RotationPolicyListener() {
        @Override
        public void onChange() {
            mModel.onRotationLockChanged();
        }
    };

    public QuickSettings(Context context, QuickSettingsContainerView container) {
        mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        mContext = context;
        mContainerView = container;
        mModel = new QuickSettingsModel(context);
        mWifiDisplayStatus = new WifiDisplayStatus();
        mBluetoothState = new QuickSettingsModel.BluetoothState();
        mHandler = new Handler();

        Resources r = mContext.getResources();
        mBatteryLevels = (LevelListDrawable) r.getDrawable(R.drawable.qs_sys_battery);
        mChargingBatteryLevels =
                (LevelListDrawable) r.getDrawable(R.drawable.qs_sys_battery_charging);
        mBrightnessDialogLongTimeout =
                r.getInteger(R.integer.quick_settings_brightness_dialog_long_timeout);
        mBrightnessDialogShortTimeout =
                r.getInteger(R.integer.quick_settings_brightness_dialog_short_timeout);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(AudioManager.VIBRATE_SETTING_CHANGED_ACTION);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        mContext.registerReceiver(mReceiver, filter);

        IntentFilter profileFilter = new IntentFilter();
        profileFilter.addAction(ContactsContract.Intents.ACTION_PROFILE_CHANGED);
        profileFilter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        mContext.registerReceiverAsUser(mProfileReceiver, UserHandle.ALL, profileFilter,
                null, null);
    }

    void setBar(PanelBar bar) {
        mBar = bar;
    }

    public void setService(PhoneStatusBar phoneStatusBar) {
        mStatusBarService = phoneStatusBar;
    }

    public PhoneStatusBar getService() {
        return mStatusBarService;
    }

    public void setImeWindowStatus(boolean visible) {
        mModel.onImeWindowStatusChanged(visible);
    }

    void setup(NetworkController networkController, BluetoothController bluetoothController,
            BatteryController batteryController, LocationController locationController) {
        mBluetoothController = bluetoothController;

        setupQuickSettings();
        updateWifiDisplayStatus();
        updateResources();

        networkController.addNetworkSignalChangedCallback(mModel);
        bluetoothController.addStateChangedCallback(mModel);
        batteryController.addStateChangedCallback(mModel);
        locationController.addStateChangedCallback(mModel);
        RotationPolicy.registerRotationPolicyListener(mContext, mRotationPolicyListener,
                UserHandle.USER_ALL);
    }

    private void queryForUserInformation() {
        Context currentUserContext = null;
        UserInfo userInfo = null;
        try {
            userInfo = ActivityManagerNative.getDefault().getCurrentUser();
            currentUserContext = mContext.createPackageContextAsUser("android", 0,
                    new UserHandle(userInfo.id));
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Couldn't create user context", e);
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            Log.e(TAG, "Couldn't get user info", e);
        }
        final int userId = userInfo.id;
        final String userName = userInfo.name;

        final Context context = currentUserContext;
        mUserInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
            @Override
            protected Pair<String, Drawable> doInBackground(Void... params) {
                final UserManager um =
                        (UserManager) mContext.getSystemService(Context.USER_SERVICE);

                // Fall back to the UserManager nickname if we can't read the name from the local
                // profile below.
                String name = userName;
                Drawable avatar = null;
                Bitmap rawAvatar = um.getUserIcon(userId);
                if (rawAvatar != null) {
                    avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                } else {
                    avatar = mContext.getResources().getDrawable(R.drawable.ic_qs_default_user);
                }

                // If it's a single-user device, get the profile name, since the nickname is not
                // usually valid
                if (um.getUsers().size() <= 1) {
                    // Try and read the display name from the local profile
                    final Cursor cursor = context.getContentResolver().query(
                            Profile.CONTENT_URI, new String[] {Phone._ID, Phone.DISPLAY_NAME},
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME));
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
                return new Pair<String, Drawable>(name, avatar);
            }

            @Override
            protected void onPostExecute(Pair<String, Drawable> result) {
                super.onPostExecute(result);
                mModel.setUserTileInfo(result.first, result.second);
                mUserInfoTask = null;
            }
        };
        mUserInfoTask.execute();
    }

    private void setupQuickSettings() {
        // Setup the tiles that we are going to be showing (including the temporary ones)
        LayoutInflater inflater = LayoutInflater.from(mContext);

        addUserTiles(mContainerView, inflater);
        addSystemTiles(mContainerView, inflater);
        addTemporaryTiles(mContainerView, inflater);

        queryForUserInformation();
        mTilesSetUp = true;
    }

    private void startSettingsActivity(String action) {
        Intent intent = new Intent(action);
        startSettingsActivity(intent);
    }

    private void startSettingsActivity(Intent intent) {
        startSettingsActivity(intent, true);
    }

    private void startSettingsActivity(Intent intent, boolean onlyProvisioned) {
        if (onlyProvisioned && !getService().isDeviceProvisioned()) return;
        try {
            // Dismiss the lock screen when Settings starts.
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        getService().animateCollapsePanels();
    }

    private void addUserTiles(ViewGroup parent, LayoutInflater inflater) {
        QuickSettingsTileView userTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        userTile.setContent(R.layout.quick_settings_tile_user, inflater);
        userTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBar.collapseAllPanels(true);
                final UserManager um =
                        (UserManager) mContext.getSystemService(Context.USER_SERVICE);
                if (um.getUsers(true).size() > 1) {
                    try {
                        WindowManagerGlobal.getWindowManagerService().lockNow(
                                LockPatternUtils.USER_SWITCH_LOCK_OPTIONS);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Couldn't show user switcher", e);
                    }
                } else {
                    Intent intent = ContactsContract.QuickContact.composeQuickContactsIntent(
                            mContext, v, ContactsContract.Profile.CONTENT_URI,
                            ContactsContract.QuickContact.MODE_LARGE, null);
                    mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                }
            }
        });
        mModel.addUserTile(userTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                UserState us = (UserState) state;
                ImageView iv = (ImageView) view.findViewById(R.id.user_imageview);
                TextView tv = (TextView) view.findViewById(R.id.user_textview);
                tv.setText(state.label);
                iv.setImageDrawable(us.avatar);
                view.setContentDescription(mContext.getString(
                        R.string.accessibility_quick_settings_user, state.label));
            }
        });
        parent.addView(userTile);
        mDynamicSpannedTiles.add(userTile);

        // Brightness
        QuickSettingsTileView brightnessTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        brightnessTile.setContent(R.layout.quick_settings_tile_brightness, inflater);
        brightnessTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBar.collapseAllPanels(true);
                showBrightnessDialog();
            }
        });
        mModel.addBrightnessTile(brightnessTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.brightness_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);
                tv.setText(state.label);
                dismissBrightnessDialog(mBrightnessDialogShortTimeout);
            }
        });
        parent.addView(brightnessTile);
        mDynamicSpannedTiles.add(brightnessTile);

        // Time tile
        /*
        QuickSettingsTileView timeTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        timeTile.setContent(R.layout.quick_settings_tile_time, inflater);
        timeTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Quick. Clock. Quick. Clock. Quick. Clock.
                startSettingsActivity(Intent.ACTION_QUICK_CLOCK);
            }
        });
        mModel.addTimeTile(timeTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State alarmState) {}
        });
        parent.addView(timeTile);
        mDynamicSpannedTiles.add(timeTile);
        */
    }

    private void addSystemTiles(ViewGroup parent, LayoutInflater inflater) {
        // Sound and Vibration
        addGenericTile(parent, inflater, SOUND_AND_VIBRATION_TILE);

        // Wi-fi
       addGenericTile(parent, inflater, WIFI_TILE);

        if (mModel.deviceSupportsTelephony()) {
            // RSSI
            addGenericTile(parent, inflater, MOBILE_NETWORK_TILE);
        }

        // Bluetooth
        if (mModel.deviceSupportsBluetooth()) {
            addGenericTile(parent, inflater, BLUETOOTH_TILE);
        }

        // Battery
        addGenericTile(parent, inflater, BATTERY_TILE);

        // Airplane Mode
        addGenericTile(parent, inflater, AIRPLANE_MODE_TILE);

        // Settings
        addGenericTile(parent, inflater, SETTINGS_TILE);

        // Rotation Lock
        //if (mContext.getResources().getBoolean(R.bool.quick_settings_show_rotation_lock)) {
            addGenericTile(parent, inflater, ROTATION_LOCK_TILE);
        //}

        // Sleep
        addGenericTile(parent, inflater, SLEEP_TILE);

        // Lockscreen
        //addGenericTile(parent, inflater, LOCK_SCREEN_TILE);

    }

    private void addTemporaryTiles(final ViewGroup parent, final LayoutInflater inflater) {
        // Alarm tile
        QuickSettingsTileView alarmTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        alarmTile.setContent(R.layout.quick_settings_tile_alarm, inflater);
        alarmTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Jump into the alarm application
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.android.deskclock",
                        "com.android.deskclock.AlarmClock"));
                startSettingsActivity(intent);
            }
        });
        mModel.addAlarmTile(alarmTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State alarmState) {
                TextView tv = (TextView) view.findViewById(R.id.alarm_textview);
                tv.setText(alarmState.label);
                view.setVisibility(alarmState.enabled ? View.VISIBLE : View.GONE);
                view.setContentDescription(mContext.getString(
                        R.string.accessibility_quick_settings_alarm, alarmState.label));
            }
        });
        parent.addView(alarmTile);

        // Location
        QuickSettingsTileView locationTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        locationTile.setContent(R.layout.quick_settings_tile_location, inflater);
        locationTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            }
        });
        mModel.addLocationTile(locationTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.location_textview);
                tv.setText(state.label);
                view.setVisibility(state.enabled ? View.VISIBLE : View.GONE);
            }
        });
        parent.addView(locationTile);

        // Wifi Display
        QuickSettingsTileView wifiDisplayTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        wifiDisplayTile.setContent(R.layout.quick_settings_tile_wifi_display, inflater);
        wifiDisplayTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_WIFI_DISPLAY_SETTINGS);
            }
        });
        mModel.addWifiDisplayTile(wifiDisplayTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.wifi_display_textview);
                tv.setText(state.label);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);
                view.setVisibility(state.enabled ? View.VISIBLE : View.GONE);
            }
        });
        parent.addView(wifiDisplayTile);

        if (SHOW_IME_TILE) {
            // IME
            QuickSettingsTileView imeTile = (QuickSettingsTileView)
                    inflater.inflate(R.layout.quick_settings_tile, parent, false);
            imeTile.setContent(R.layout.quick_settings_tile_ime, inflater);
            imeTile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        mBar.collapseAllPanels(true);
                        Intent intent = new Intent(Settings.ACTION_SHOW_INPUT_METHOD_PICKER);
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
                        pendingIntent.send();
                    } catch (Exception e) {}
                }
            });
            mModel.addImeTile(imeTile, new QuickSettingsModel.RefreshCallback() {
                @Override
                public void refreshView(QuickSettingsTileView view, State state) {
                    TextView tv = (TextView) view.findViewById(R.id.ime_textview);
                    if (state.label != null) {
                        tv.setText(state.label);
                    }
                    view.setVisibility(state.enabled ? View.VISIBLE : View.GONE);
                }
            });
            parent.addView(imeTile);
        }

        // Bug reports
        QuickSettingsTileView bugreportTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        bugreportTile.setContent(R.layout.quick_settings_tile_bugreport, inflater);
        bugreportTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBar.collapseAllPanels(true);
                showBugreportDialog();
            }
        });
        mModel.addBugreportTile(bugreportTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                view.setVisibility(state.enabled ? View.VISIBLE : View.GONE);
            }
        });
        parent.addView(bugreportTile);
        /*
        QuickSettingsTileView mediaTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        mediaTile.setContent(R.layout.quick_settings_tile_media, inflater);
        parent.addView(mediaTile);
        QuickSettingsTileView imeTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        imeTile.setContent(R.layout.quick_settings_tile_ime, inflater);
        imeTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parent.removeViewAt(0);
            }
        });
        parent.addView(imeTile);
        */
    }

    void updateResources() {
        Resources r = mContext.getResources();

        // Update the model
        mModel.updateResources();

        // Update the User, Time, and Settings tiles spans, and reset everything else
        int span = r.getInteger(R.integer.quick_settings_user_time_settings_tile_span);
        for (QuickSettingsTileView v : mDynamicSpannedTiles) {
            v.setColumnSpan(span);
        }
        ((QuickSettingsContainerView)mContainerView).updateResources();
        mContainerView.requestLayout();

        // Reset the dialog
        boolean isBrightnessDialogVisible = false;
        if (mBrightnessDialog != null) {
            removeAllBrightnessDialogCallbacks();

            isBrightnessDialogVisible = mBrightnessDialog.isShowing();
            mBrightnessDialog.dismiss();
        }
        mBrightnessDialog = null;
        if (isBrightnessDialogVisible) {
            showBrightnessDialog();
        }
    }

    private void removeAllBrightnessDialogCallbacks() {
        mHandler.removeCallbacks(mDismissBrightnessDialogRunnable);
    }

    private final Runnable mDismissBrightnessDialogRunnable = new Runnable() {
        @Override
        public void run() {
            if (mBrightnessDialog != null && mBrightnessDialog.isShowing()) {
                mBrightnessDialog.dismiss();
            }
            removeAllBrightnessDialogCallbacks();
        };
    };

    private void showBrightnessDialog() {
        if (mBrightnessDialog == null) {
            mBrightnessDialog = new Dialog(mContext);
            mBrightnessDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mBrightnessDialog.setContentView(R.layout.quick_settings_brightness_dialog);
            mBrightnessDialog.setCanceledOnTouchOutside(true);

            mBrightnessController = new BrightnessController(mContext,
                    (ImageView) mBrightnessDialog.findViewById(R.id.brightness_icon),
                    (ToggleSlider) mBrightnessDialog.findViewById(R.id.brightness_slider));
            mBrightnessController.addStateChangedCallback(mModel);
            mBrightnessDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    mBrightnessController = null;
                }
            });

            mBrightnessDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            mBrightnessDialog.getWindow().getAttributes().privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
            mBrightnessDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        if (!mBrightnessDialog.isShowing()) {
            try {
                WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
            } catch (RemoteException e) {
            }
            mBrightnessDialog.show();
            dismissBrightnessDialog(mBrightnessDialogLongTimeout);
        }
    }

    private void dismissBrightnessDialog(int timeout) {
        removeAllBrightnessDialogCallbacks();
        if (mBrightnessDialog != null) {
            mHandler.postDelayed(mDismissBrightnessDialogRunnable, timeout);
        }
    }

    private void showBugreportDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setPositiveButton(com.android.internal.R.string.report, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Add a little delay before executing, to give the
                    // dialog a chance to go away before it takes a
                    // screenshot.
                    mHandler.postDelayed(new Runnable() {
                        @Override public void run() {
                            try {
                                ActivityManagerNative.getDefault()
                                        .requestBugReport();
                            } catch (RemoteException e) {
                            }
                        }
                    }, 500);
                }
            }
        });
        builder.setMessage(com.android.internal.R.string.bugreport_message);
        builder.setTitle(com.android.internal.R.string.bugreport_title);
        builder.setCancelable(true);
        final Dialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        try {
            WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
        } catch (RemoteException e) {
        }
        dialog.show();
    }

    private void updateWifiDisplayStatus() {
        mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        applyWifiDisplayStatus();
    }

    private void applyWifiDisplayStatus() {
        mModel.onWifiDisplayStateChanged(mWifiDisplayStatus);
    }

    private void applyBluetoothStatus() {
        mModel.onBluetoothStateChange(mBluetoothState);
    }

    void reloadUserInfo() {
        if (mUserInfoTask != null) {
            mUserInfoTask.cancel(false);
            mUserInfoTask = null;
        }
        if (mTilesSetUp) {
            queryForUserInformation();
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED.equals(action)) {
                WifiDisplayStatus status = (WifiDisplayStatus)intent.getParcelableExtra(
                        DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
                mWifiDisplayStatus = status;
                applyWifiDisplayStatus();
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                mBluetoothState.enabled = (state == BluetoothAdapter.STATE_ON);
                applyBluetoothStatus();
            } else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int status = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        BluetoothAdapter.STATE_DISCONNECTED);
                mBluetoothState.connected = (status == BluetoothAdapter.STATE_CONNECTED);
                applyBluetoothStatus();
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                reloadUserInfo();
            } else if(AudioManager.VIBRATE_SETTING_CHANGED_ACTION.equals(action)){
                mModel.onVibrationChanged();
                mModel.onVibrationAndSoundChanged();
            } else if(AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)){
                mModel.onSystemSoundStateChanged();
                mModel.onVibrationAndSoundChanged();
            }
        }
    };

    private final BroadcastReceiver mProfileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ContactsContract.Intents.ACTION_PROFILE_CHANGED.equals(action) ||
                    Intent.ACTION_USER_INFO_CHANGED.equals(action)) {
                try {
                    final int userId = ActivityManagerNative.getDefault().getCurrentUser().id;
                    if (getSendingUserId() == userId) {
                        reloadUserInfo();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Couldn't get current user id for profile change", e);
                }
            }

        }
    };

    private void addGenericTile(ViewGroup parent, LayoutInflater inflater, int type){
        QuickSettingsTileView genericTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        genericTile = addContents(genericTile, inflater, type);
        addActions(genericTile, inflater, parent, type);
    }

    private QuickSettingsTileView addContents(QuickSettingsTileView original, LayoutInflater inflater, int type){
        int contentID = -1;
        if(type == USER_TILE){
            contentID = R.layout.quick_settings_tile_user;
        }else if(type == BATTERY_TILE){
            contentID = R.layout.quick_settings_tile_battery;
        }else if(type == MOBILE_NETWORK_TILE){
            contentID = R.layout.quick_settings_tile_rssi;
        }else if(type == TIME_TILE){
            contentID = R.layout.quick_settings_tile_time;
        }else{
            contentID = R.layout.quick_settings_tile_generic;
        }
        original.setContent(contentID, inflater);
        return original;
    }

    private void addActions(QuickSettingsTileView original, LayoutInflater inflater, ViewGroup parent, int type){
        switch(type){
        case USER_TILE:
            addUserTileActions(original, parent);
            break;
        case BRIGHTNESS_TILE:
            addBrightnessTileActions(original, parent);
            break;
        case SETTINGS_TILE:
            addSettingsTileActions(original, parent);
            break;
        case WIFI_TILE:
            addWiFiTileActions(original, parent);
            break;
        case MOBILE_NETWORK_TILE:
            addMobileNetworkTileActions(original, parent);
            break;
        case ROTATION_LOCK_TILE:
            addRotationTileActions(original, parent);
            break;
        case BATTERY_TILE:
            addBatteryTileActions(original, parent);
            break;
        case AIRPLANE_MODE_TILE:
            addAirplaneTileActions(original, parent);
            break;
        case BLUETOOTH_TILE:
            addBluetoothTileActions(original, parent);
            break;
        case RINGER_MODE_TILE:
            addRingerModeTileActions(original, parent);
            break;
        case VIBRATION_TILE:
            addVibrationTileActions(original, parent);
            break;
        case SLEEP_TILE:
            addSleepTileActions(original, parent);
            break;
        case LOCK_SCREEN_TILE:
            addLockscreenEnableTileActions(original, parent);
            break;
        case SOUND_AND_VIBRATION_TILE:
            addVibrationAndSoundTileActions(original, parent);
            break;
        }
        parent.addView(original);
    }

    private void addUserTileActions(QuickSettingsTileView original, ViewGroup parent){
        original.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBar.collapseAllPanels(true);
                final UserManager um =
                        (UserManager) mContext.getSystemService(Context.USER_SERVICE);
                if (um.getUsers(true).size() > 1) {
                    try {
                        WindowManagerGlobal.getWindowManagerService().lockNow(
                                LockPatternUtils.USER_SWITCH_LOCK_OPTIONS);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Couldn't show user switcher", e);
                    }
                } else {
                    Intent intent = ContactsContract.QuickContact.composeQuickContactsIntent(
                            mContext, v, ContactsContract.Profile.CONTENT_URI,
                            ContactsContract.QuickContact.MODE_LARGE, null);
                    mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                }
            }
        });
        mModel.addUserTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                UserState us = (UserState) state;
                ImageView iv = (ImageView) view.findViewById(R.id.user_imageview);
                TextView tv = (TextView) view.findViewById(R.id.user_textview);
                tv.setText(state.label);
                iv.setImageDrawable(us.avatar);
                view.setContentDescription(mContext.getString(
                        R.string.accessibility_quick_settings_user, state.label));
            }
        });

        mDynamicSpannedTiles.add(original);
    }

    private void addBrightnessTileActions(QuickSettingsTileView original, ViewGroup parent){
        original.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBar.collapseAllPanels(true);
                showBrightnessDialog();
            }
        });
        mModel.addBrightnessTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);
                tv.setText(state.label);
                dismissBrightnessDialog(mBrightnessDialogShortTimeout);
            }
        });
        mDynamicSpannedTiles.add(original);
    }

    private void addSettingsTileActions(QuickSettingsTileView original, ViewGroup parent){
        original.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_SETTINGS);
            }
        });
        mModel.addSettingsTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_qs_settings, 0, 0);
                tv.setText(state.label);
            }
        });
        mDynamicSpannedTiles.add(original);
    }

    private void addWiFiTileActions(QuickSettingsTileView original, ViewGroup parent){
        original.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_WIFI_SETTINGS);
                return true;
            }
        });
        original.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WifiManager wfm = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
                wfm.setWifiEnabled(!wfm.isWifiEnabled());
            }
        });
        mModel.addWifiTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                WifiState wifiState = (WifiState) state;
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, wifiState.iconId, 0, 0);
                tv.setText(wifiState.label);
                view.setContentDescription(mContext.getString(
                        R.string.accessibility_quick_settings_wifi,
                        wifiState.signalContentDescription,
                        (wifiState.connected) ? wifiState.label : ""));
            }
        });
    }

    private void addMobileNetworkTileActions(QuickSettingsTileView original, ViewGroup parent){
        original.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                ConnectivityManager conMan = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                if(tm.getDataState() == TelephonyManager.DATA_DISCONNECTED){
                    conMan.setMobileDataEnabled(true);
                }else{
                    conMan.setMobileDataEnabled(false);
                }
            }
        });
        original.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings$DataUsageSummaryActivity"));
                startSettingsActivity(intent);
                return true;
            }
        });
        mModel.addRSSITile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                RSSIState rssiState = (RSSIState) state;
                ImageView iv = (ImageView) view.findViewById(R.id.rssi_image);
                ImageView iov = (ImageView) view.findViewById(R.id.rssi_overlay_image);
                TextView tv = (TextView) view.findViewById(R.id.rssi_textview);
                iv.setImageResource(rssiState.signalIconId);

                if (rssiState.dataTypeIconId > 0) {
                    iov.setImageResource(rssiState.dataTypeIconId);
                } else {
                    iov.setImageDrawable(null);
                }
                tv.setText(state.label);
                view.setContentDescription(mContext.getResources().getString(
                        R.string.accessibility_quick_settings_mobile,
                        rssiState.signalContentDescription, rssiState.dataContentDescription,
                        state.label));
            }
        });
    }

    private void addRotationTileActions(QuickSettingsTileView original, ViewGroup parent){
        original.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean locked = RotationPolicy.isRotationLocked(mContext);
                RotationPolicy.setRotationLock(mContext, !locked);
            }
        });
        mModel.addRotationLockTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);
                tv.setText(state.label);
            }
        });
    }

    private void addBatteryTileActions(QuickSettingsTileView original, ViewGroup parent){
        original.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
            }
        });
        mModel.addBatteryTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                QuickSettingsModel.BatteryState batteryState =
                        (QuickSettingsModel.BatteryState) state;
                TextView tv = (TextView) view.findViewById(R.id.battery_textview);
                ImageView iv = (ImageView) view.findViewById(R.id.battery_image);
                Drawable d = batteryState.pluggedIn
                        ? mChargingBatteryLevels
                        : mBatteryLevels;
                String t;
                if (batteryState.batteryLevel == 100) {
                    t = mContext.getString(R.string.quick_settings_battery_charged_label);
                } else {
                    t = batteryState.pluggedIn
                        ? mContext.getString(R.string.quick_settings_battery_charging_label,
                                batteryState.batteryLevel)
                        : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                                batteryState.batteryLevel);
                }
                iv.setImageDrawable(d);
                iv.setImageLevel(batteryState.batteryLevel);
                tv.setText(t);
                view.setContentDescription(
                        mContext.getString(R.string.accessibility_quick_settings_battery, t));
            }
        });
    }

    private void addAirplaneTileActions(QuickSettingsTileView original, ViewGroup parent) {
        mModel.addAirplaneModeTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);

                String airplaneState = mContext.getString(
                        (state.enabled) ? R.string.accessibility_desc_on
                                : R.string.accessibility_desc_off);
                view.setContentDescription(
                        mContext.getString(R.string.accessibility_quick_settings_airplane, airplaneState));
                tv.setText(state.label);
            }
        });
    }

    private void addBluetoothTileActions(QuickSettingsTileView original,
            ViewGroup parent) {
        original.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if(mBluetoothAdapter.isEnabled()){
                    mBluetoothAdapter.disable();
                }else{
                    mBluetoothAdapter.enable();
                }
            }
        });
        original.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                return true;
            }
        });
        mModel.addBluetoothTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                BluetoothState bluetoothState = (BluetoothState) state;
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);

                Resources r = mContext.getResources();
                String label = state.label;
                /*
                //TODO: Show connected bluetooth device label
                Set<BluetoothDevice> btDevices =
                        mBluetoothController.getBondedBluetoothDevices();
                if (btDevices.size() == 1) {
                    // Show the name of the bluetooth device you are connected to
                    label = btDevices.iterator().next().getName();
                } else if (btDevices.size() > 1) {
                    // Show a generic label about the number of bluetooth devices
                    label = r.getString(R.string.quick_settings_bluetooth_multiple_devices_label,
                            btDevices.size());
                }
                */
                view.setContentDescription(mContext.getString(
                        R.string.accessibility_quick_settings_bluetooth,
                        bluetoothState.stateContentDescription));
                tv.setText(label);
            }
        });
    }

    private void addRingerModeTileActions(QuickSettingsTileView original,
            ViewGroup parent) {
        original.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL){
                    if(am.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == AudioManager.VIBRATE_SETTING_ON){
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    }else{
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    }
                }else{
                    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                }
            }
        });
        original.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_SOUND_SETTINGS);
                return true;
            }
        });
        mModel.addSystemVolumeTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);

                String airplaneState = mContext.getString(
                        (state.enabled) ? R.string.quicksettings_ringer_on
                                : R.string.quicksettings_ringer_off);
                view.setContentDescription(
                        mContext.getString(R.string.accessibility_quick_settings_ringer, airplaneState));
                tv.setText(state.label);
            }
        });
    }

    private void addVibrationTileActions(QuickSettingsTileView original,
            ViewGroup parent) {
        original.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                if(am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE){
                    //Vibrate -> Silent
                    am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                }else if(am.getRingerMode() == AudioManager.RINGER_MODE_SILENT){
                    //Silent -> Vibrate
                    vibrator.vibrate(300);
                    am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_ON);
                }else{
                    if(am.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == AudioManager.VIBRATE_SETTING_ON){
                        //Sound + Vibrate -> Sound
                        am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                    }else{
                        //Sound -> Sound + Vibrate
                        vibrator.vibrate(300);
                        am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_ON);
                    }
                }
            }
        });
        original.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_SOUND_SETTINGS);
                return true;
            }
        });
        mModel.addVibrationTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);

                String airplaneState = mContext.getString(
                        (state.enabled) ? R.string.quicksettings_vibration_on
                                : R.string.quicksettings_vibration_off);
                view.setContentDescription(
                        mContext.getString(R.string.accessibility_quick_settings_vibration, airplaneState));
                tv.setText(state.label);
            }
        });
    }

    private void addSleepTileActions(QuickSettingsTileView original, ViewGroup parent){
        original.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PowerManager pm = (PowerManager)
                        mContext.getSystemService(Context.POWER_SERVICE);
                pm.goToSleep(SystemClock.uptimeMillis());
            }
        });
        original.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity("android.settings.DISPLAY_SETTINGS");
                return true;
            }
        });
        mModel.addNoStateTile(original, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_qs_sleep, 0, 0);
                tv.setText(mContext.getString(R.string.quicksettings_screen_sleep));
            }
        });
    }

    private void addLockscreenEnableTileActions(QuickSettingsTileView original, ViewGroup parent){
        original.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                KeyguardManager keyguardManager = (KeyguardManager)
                        mContext.getSystemService(Context.KEYGUARD_SERVICE);
                KeyguardLock lock = keyguardManager.newKeyguardLock("PowerWidget");
                SharedPreferences sp = mContext.getSharedPreferences("PowerButton-" + PowerButton.BUTTON_LOCKSCREEN, Context.MODE_PRIVATE);
                Editor editor = sp.edit();
                Log.d(TAG,"BEFORE: Lockscreen: "+sp.getBoolean("lockscreen_disabled", false));
                if(!sp.getBoolean("lockscreen_disabled", false)){
                    lock.reenableKeyguard();
                    editor.putBoolean("lockscreen_disabled", true);
                }else{
                    lock.disableKeyguard();
                    editor.putBoolean("lockscreen_disabled", false);
                }
                editor.apply();
                Log.d(TAG,"AFTER: Lockscreen: "+sp.getBoolean("lockscreen_disabled", false));
            }
        });
        mModel.addLockscreenEnablerTile(original, new RefreshCallback() {

            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);
                tv.setText(mContext.getResources().getString(R.string.accessibility_desc_on));
            }
        });
    }

    private void addVibrationAndSoundTileActions(QuickSettingsTileView original, ViewGroup parent){
        original.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
               // Vibration + Sound -> Silent -> Vibration -> Sound ...
                AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                boolean vibrate = am.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == AudioManager.VIBRATE_SETTING_ON;
                if(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && vibrate){
                    // Switch to Silent
                    am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                    am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                }else if(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL){
                    // Switch to Sound + Vibration
                    vibrator.vibrate(300);
                    am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_ON);
                    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                }else if(am.getRingerMode() == AudioManager.RINGER_MODE_SILENT){
                    // Switch to Vibration
                    vibrator.vibrate(300);
                    am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_ON);
                }else if(am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE){
                    // Switch to Sound
                    am.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                }
            }
        });
        mModel.addVibrationAndSoundTile(original, new RefreshCallback() {

            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.tile_textview);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);
                tv.setText(state.label);
            }
        });
    }
}


