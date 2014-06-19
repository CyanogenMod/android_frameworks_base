/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.policy.impl;

import com.android.internal.app.AlertController;
import com.android.internal.app.AlertController.AlertParams;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.R;

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Profile;
import android.app.ProfileManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.service.gesture.IEdgeGestureService;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.Manifest;
import android.view.WindowManagerPolicy.WindowManagerFuncs;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ListView;
import android.widget.TextView;

import com.android.internal.app.ThemeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.slim.ButtonConfig;
import com.android.internal.util.slim.ImageHelper;
import com.android.internal.util.slim.PolicyConstants;
import com.android.internal.util.slim.PolicyHelper;
import com.android.internal.util.slim.SlimActions;
import com.android.internal.util.nameless.NamelessActions;

/**
 * Helper to show the global actions dialog.  Each item is an {@link Action} that
 * may show depending on whether the keyguard is showing, and whether the device
 * is provisioned.
 */
class GlobalActions implements DialogInterface.OnDismissListener, DialogInterface.OnClickListener  {

    private static final String TAG = "GlobalActions";

    private static final boolean SHOW_SILENT_TOGGLE = true;

    private Context mUiContext;

    private final Context mContext;
    private final WindowManagerFuncs mWindowManagerFuncs;

    private final AudioManager mAudioManager;
    private final IDreamManager mDreamManager;
    private IEdgeGestureService mEdgeGestureService;
    private Object mServiceAquireLock = new Object();

    private ArrayList<Action> mItems;
    private GlobalActionsDialog mDialog;
    private Handler mObservHandler = new Handler();

    private Action mSilentModeAction;
    private ToggleAction mAirplaneModeOn;
    private ToggleAction mExpandDesktopModeOn;
    private ToggleAction mPieModeOn;
    private ToggleAction mPAPieModeOn;
    private ToggleAction mNavBarModeOn;
    private ToggleAction mMobileDataOn;
    private ToggleAction mWifiOn;

    private MyAdapter mAdapter;

    private boolean mKeyguardShowing = false;
    private boolean mDeviceProvisioned = false;
    private ToggleAction.State mAirplaneState = ToggleAction.State.Off;
    private ToggleAction.State mExpandDesktopState = ToggleAction.State.Off;
    private ToggleAction.State mPieState = ToggleAction.State.Off;
    private ToggleAction.State mPAPieState = ToggleAction.State.Off;
    private ToggleAction.State mNavBarState = ToggleAction.State.Off;
    private ToggleAction.State mMobileDataState = ToggleAction.State.Off;
    private ToggleAction.State mWifiState = ToggleAction.State.Off;
    private boolean mIsWaitingForEcmExit = false;
    private boolean mHasTelephony;
    private boolean mHasVibrator;
    private Profile mChosenProfile;
    private final boolean mShowSilentToggle;
    private ConnectivityManager mConnectivityManager;
    private WifiManager mWifiManager;

    private static int mTextColor;

    /**
     * @param context everything needs a context :(
     */
    public GlobalActions(Context context, WindowManagerFuncs windowManagerFuncs) {
        mContext = context;
        mWindowManagerFuncs = windowManagerFuncs;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mDreamManager = IDreamManager.Stub.asInterface(
                ServiceManager.getService(DreamService.DREAM_SERVICE));
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        context.registerReceiver(mBroadcastReceiver, filter);

        ThemeUtils.registerThemeChangeReceiver(context, mThemeChangeReceiver);

        // get notified of phone state changes
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        mConnectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mHasTelephony = mConnectivityManager.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), true,
                mAirplaneModeObserver);
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = vibrator != null && vibrator.hasVibrator();

        mShowSilentToggle = SHOW_SILENT_TOGGLE && !mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_useFixedVolume);

        // set the initial status of airplane mode toggle
        mAirplaneState = getUpdatedAirplaneToggleState();
    }

    /**
     * Show the global actions dialog (creating if necessary)
     * @param keyguardShowing True if keyguard is showing
     */
    public void showDialog(boolean keyguardShowing, boolean isDeviceProvisioned) {
        mKeyguardShowing = keyguardShowing;
        mDeviceProvisioned = isDeviceProvisioned;
        if (mDialog != null) {
	    if (mUiContext != null) {
                mUiContext = null;
            }
            mDialog.dismiss();
            mDialog = null;
	    mDialog = createDialog();
            // Show delayed, so that the dismiss of the previous dialog completes
            mHandler.sendEmptyMessage(MESSAGE_SHOW);
        } else {
	    mDialog = createDialog();
            handleShow();
        }
    }

    private void awakenIfNecessary() {
        if (mDreamManager != null) {
            try {
                if (mDreamManager.isDreaming()) {
                    mDreamManager.awaken();
                }
            } catch (RemoteException e) {
                // we tried
            }
        }
    }

    private void handleShow() {
        mTextColor = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.POWER_MENU_TEXT_COLOR, -2,
                UserHandle.USER_CURRENT);
        if (mTextColor == -2) {
            mTextColor = mContext.getResources().getColor(
                com.android.internal.R.color.power_menu_icon_default_color);
        }

        awakenIfNecessary();
        mDialog = createDialog();
        prepareDialog();

        final IStatusBarService barService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        try {
            barService.collapsePanels();
        } catch (RemoteException ex) {
            // bad bad
        }

        WindowManager.LayoutParams attrs = mDialog.getWindow().getAttributes();
        attrs.setTitle("GlobalActions");
        mDialog.getWindow().setAttributes(attrs);
        mDialog.show();
        mDialog.getWindow().getDecorView().setSystemUiVisibility(View.STATUS_BAR_DISABLE_EXPAND);
    }

    private Context getUiContext() {
        if (mUiContext == null) {
            mUiContext = ThemeUtils.createUiContext(mContext);
        }
        return mUiContext != null ? mUiContext : mContext;
    }

    /**
     * Create the global actions dialog.
     * @return A new dialog.
     */
    private GlobalActionsDialog createDialog() {
        ArrayList<ButtonConfig> powerMenuConfig =
                PolicyHelper.getPowerMenuConfigWithDescription(
                mContext, "shortcut_action_power_menu_values",
                "shortcut_action_power_menu_entries");

        // Simple toggle style if there's no vibrator, otherwise use a tri-state
        if (!mHasVibrator) {
            mSilentModeAction = new SilentModeToggleAction();
        } else {
            mSilentModeAction = new SilentModeTriStateAction(mContext, mAudioManager, mHandler);
        }
        mItems = new ArrayList<Action>();

    	final ContentResolver cr = mContext.getContentResolver();

        // bug report, if enabled
        if (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.BUGREPORT_IN_POWER_MENU, 0) != 0 && isCurrentUserOwner()) {
            mItems.add(
                new SinglePressAction(com.android.internal.R.drawable.stat_sys_adb,
                        R.string.global_action_bug_report) {

                    public void onPress() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                        builder.setTitle(com.android.internal.R.string.bugreport_title);
                        builder.setMessage(com.android.internal.R.string.bugreport_message);
                        builder.setNegativeButton(com.android.internal.R.string.cancel, null);
                        builder.setPositiveButton(com.android.internal.R.string.report,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
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
                                });
                        AlertDialog dialog = builder.create();
                        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
                        dialog.show();
                    }

                    public boolean onLongPress() {
                        return false;
                    }

                    public boolean showDuringKeyguard() {
                        return true;
                    }

                    public boolean showBeforeProvisioning() {
                        return false;
                    }
                });
        }

        for (final ButtonConfig config : powerMenuConfig) {
            // power off
            if (config.getClickAction().equals(PolicyConstants.ACTION_POWER_OFF)) {
                mItems.add(
                    new SinglePressAction(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription()) {

                        public void onPress() {
                            // Check quickboot status
                            boolean quickbootAvailable = false;
                            final PackageManager pm = mContext.getPackageManager();
                            try {
                                pm.getPackageInfo("com.qapp.quickboot", PackageManager.GET_META_DATA);
                                quickbootAvailable = true;
                            } catch (NameNotFoundException e) {
                                // Ignore
                            }
                            final boolean quickbootEnabled = Settings.Global.getInt(
                                    mContext.getContentResolver(), Settings.Global.ENABLE_QUICKBOOT,
                                    1) == 1;

                        // goto quickboot mode
                        if (quickbootAvailable && quickbootEnabled) {
                            startQuickBoot();
                            return;
                        }

                        // shutdown by making sure radio and power are handled accordingly.
                        mWindowManagerFuncs.shutdown(true);
                        }

                        public boolean showDuringKeyguard() {
                            return true;
                        }

                        public boolean showBeforeProvisioning() {
                            return true;
                        }
                    });
            // reboot
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_REBOOT)) {
                mItems.add(
                    new SinglePressAction(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription()) {
                        public void onPress() {
                            mWindowManagerFuncs.reboot();
                        }

                        public boolean onLongPress() {
                            mWindowManagerFuncs.rebootSafeMode(true);
                            return true;
                        }

                        public boolean showDuringKeyguard() {
                            return true;
                        }

                        public boolean showBeforeProvisioning() {
                            return true;
                        }
                    });
            // screenshot
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_SCREENSHOT)) {
                mItems.add(
                    new SinglePressAction(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription()) {
                        public void onPress() {
                            SlimActions.processAction(
                                mContext, config.getClickAction(), false);
                        }

                        public boolean showDuringKeyguard() {
                            return true;
                        }
                        public boolean showBeforeProvisioning() {
                            return true;
                        }
                    });
            // CyanogenMod profiles
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_PROFILE)) {
                    mItems.add(
                        new SinglePressAction(PolicyHelper.getPowerMenuIconImage(mContext,
                                config.getClickAction(), config.getIcon(), true),
                                config.getClickActionDescription()) {
                        public void onPress() {
                            createProfileDialog();
                        }

                        public boolean onLongPress() {
                            return true;
                        }

                        public boolean showDuringKeyguard() {
                            return false;
                        }

                        public boolean showBeforeProvisioning() {
                            return false;
                        }
                });
            // next: screenrecord
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_SCREENRECORD)) {
                mItems.add(
                    new SinglePressAction(R.drawable.ic_lock_screenrecord, R.string.global_action_screenrecord) {
                        public void onPress() {
                            toggleScreenRecord();
                        }

                        public boolean showDuringKeyguard() {
                            return true;
                        }

                        public boolean showBeforeProvisioning() {
                            return true;
                        }
                });
            // next: onthego
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_ONTHEGO)) {
                mItems.add(
                    new SinglePressAction(R.drawable.ic_lock_onthego, R.string.global_action_onthego) {
                        public void onPress() {
                            NamelessActions.processAction(mContext,
                                    NamelessActions.ACTION_ONTHEGO_TOGGLE);
                        }

                        public boolean showDuringKeyguard() {
                            return true;
                        }

                        public boolean showBeforeProvisioning() {
                            return true;
                        }
                });
            // airplane mode
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_AIRPLANE)) {
                constructAirPlaneModeToggle(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription());
                mItems.add(mAirplaneModeOn);
            // expanded desktop mode
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_EXPANDED_DESKTOP)) {
                constructExpandedDesktopToggle(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription());
                mItems.add(mExpandDesktopModeOn);
            // Pie controls
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_PIE)) {
                constructPieToggle(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription());
                mItems.add(mPieModeOn);
            // PA Pie controls
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_PAPIE)) {
                constructPAPieToggle(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription());
                mItems.add(mPAPieModeOn);
            // Mobile Data
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_MOBILEDATA)) {
                constructMobileDataToggle(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription());
                mItems.add(mMobileDataOn);
            // Wifi Switch
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_WIFI)) {
                constructWifiToggle(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription());
                mItems.add(mWifiOn);
            // Navigation bar
            } else if (config.getClickAction().equals(PolicyConstants.ACTION_NAVBAR)) {
                constructNavBarToggle(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription());
                mItems.add(mNavBarModeOn);
            // silent mode
            } else if ((config.getClickAction().equals(PolicyConstants.ACTION_SOUND)) && (mShowSilentToggle)) {
                mItems.add(mSilentModeAction);
            // must be a custom app or action shorcut
            } else if (config.getClickAction() != null) {
                mItems.add(
                    new SinglePressAction(PolicyHelper.getPowerMenuIconImage(mContext,
                            config.getClickAction(), config.getIcon(), true),
                            config.getClickActionDescription()) {
                        public void onPress() {
                            SlimActions.processAction(
                                mContext, config.getClickAction(), false);
                        }

                        public boolean showDuringKeyguard() {
                            return true;
                        }
                        public boolean showBeforeProvisioning() {
                            return true;
                        }
                    });
            }
        }

        // one more thing: optionally add a list of users to switch to
        if (SystemProperties.getBoolean("fw.power_user_switcher", false)) {
            addUsersToMenu(mItems);
        }

        mAdapter = new MyAdapter();

        AlertParams params = new AlertParams(mContext);
        params.mAdapter = mAdapter;
        params.mOnClickListener = this;
        params.mForceInverseBackground = true;

        GlobalActionsDialog dialog = new GlobalActionsDialog(mContext, params);
        dialog.setCanceledOnTouchOutside(false); // Handled by the custom class.

        dialog.getListView().setItemsCanFocus(true);
        dialog.getListView().setLongClickable(true);
        dialog.getListView().setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                            long id) {
                        return mAdapter.getItem(position).onLongPress();
                    }
        });

        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);

        dialog.setOnDismissListener(this);

        return dialog;
    }

    private void constructAirPlaneModeToggle(Drawable icon, String description) {
        mAirplaneModeOn = new ToggleAction(
                icon,
                icon,
                description,
                R.string.global_actions_airplane_mode_on_status,
                R.string.global_actions_airplane_mode_off_status) {

            void onToggle(boolean on) {
                if (mHasTelephony && Boolean.parseBoolean(
                        SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
                    mIsWaitingForEcmExit = true;
                    // Launch ECM exit dialog
                    Intent ecmDialogIntent =
                            new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null);
                    ecmDialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(ecmDialogIntent);
                } else {
                    changeAirplaneModeSystemSetting(on);
                }
            }

            @Override
            protected void changeStateFromPress(boolean buttonOn) {
                if (!mHasTelephony) return;

                // In ECM mode airplane state cannot be changed
                if (!(Boolean.parseBoolean(
                        SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE)))) {
                    mState = buttonOn ? State.TurningOn : State.TurningOff;
                    mAirplaneState = mState;
                }
            }

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };
        onAirplaneModeChanged();
    }

    private void constructMobileDataToggle(Drawable icon, String description) {
        mMobileDataOn = new ToggleAction(
                icon,
                icon,
                description,
                R.string.global_actions_mobile_data_on_status,
                R.string.global_actions_mobile_data_off_status) {

            void onToggle(boolean on) {
                // comment
                Log.i(TAG, "MobileData Toggle On");
                boolean currentState = mConnectivityManager.getMobileDataEnabled();
                mConnectivityManager.setMobileDataEnabled(!currentState);
            }

            @Override
            protected void changeStateFromPress(boolean buttonOn) {
                // comment
                Log.i(TAG, "changeStateFromPress");
            }

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };
    }

    private void constructWifiToggle(Drawable icon, String description) {
        mWifiOn = new ToggleAction(
                icon,
                icon,
                description,
                R.string.global_actions_wifi_on_status,
                R.string.global_actions_wifi_off_status) {

            void onToggle(boolean on) {
                // comment
                Log.i(TAG, "Wifi Toggle On");
                mWifiManager.setWifiEnabled(!mWifiManager.isWifiEnabled());
            }

            @Override
            protected void changeStateFromPress(boolean buttonOn) {
                // comment
                Log.i(TAG, "changeStateFromPress");
            }

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };
    }

    private void constructExpandedDesktopToggle(Drawable icon, String description) {
        mExpandDesktopModeOn = new ToggleAction(
                icon,
                icon,
                description,
                R.string.global_actions_expanded_desktop_mode_on_status,
                R.string.global_actions_expanded_desktop_mode_off_status) {

            void onToggle(boolean on) {
                SlimActions.processAction(
                    mContext, PolicyConstants.ACTION_EXPANDED_DESKTOP, false);
            }

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };
        onExpandDesktopModeChanged();
    }

    private void constructPieToggle(Drawable icon, String description) {
        mPieModeOn = new ToggleAction(
                icon,
                icon,
                description,
                R.string.global_actions_pie_mode_on_status,
                R.string.global_actions_pie_mode_off_status) {

            void onToggle(boolean on) {
                SlimActions.processAction(
                    mContext, PolicyConstants.ACTION_PIE, false);
            }

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };
        onPieModeChanged();
    }

    private void constructPAPieToggle(Drawable icon, String description) {
        mPAPieModeOn = new ToggleAction(
                icon,
                icon,
                description,
                R.string.global_actions_papie_mode_on_status,
                R.string.global_actions_papie_mode_off_status) {

            void onToggle(boolean on) {
                SlimActions.processAction(
                    mContext, PolicyConstants.ACTION_PAPIE, false);
            }

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };
        onPAPieModeChanged();
    }

    private void constructNavBarToggle(Drawable icon, String description) {
        mNavBarModeOn = new ToggleAction(
                icon,
                icon,
                description,
                R.string.global_actions_nav_bar_mode_on_status,
                R.string.global_actions_nav_bar_mode_off_status) {

            void onToggle(boolean on) {
                SlimActions.processAction(
                    mContext, PolicyConstants.ACTION_NAVBAR, false);
            }

            public boolean showDuringKeyguard() {
                return true;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };
        onNavBarModeChanged();
    }

    private UserInfo getCurrentUser() {
        try {
            return ActivityManagerNative.getDefault().getCurrentUser();
        } catch (RemoteException re) {
            return null;
        }
    }

    private boolean isCurrentUserOwner() {
        UserInfo currentUser = getCurrentUser();
        return currentUser == null || currentUser.isPrimary();
    }

    private void addUsersToMenu(ArrayList<Action> items) {
        List<UserInfo> users = ((UserManager) mContext.getSystemService(Context.USER_SERVICE))
                .getUsers();
        if (users.size() > 1) {
            UserInfo currentUser = getCurrentUser();
            for (final UserInfo user : users) {
                boolean isCurrentUser = currentUser == null
                        ? user.id == 0 : (currentUser.id == user.id);
                Drawable icon = user.iconPath != null ? Drawable.createFromPath(user.iconPath)
                        : null;
                SinglePressAction switchToUser = new SinglePressAction(
                        com.android.internal.R.drawable.ic_menu_cc, icon,
                        (user.name != null ? user.name : "Primary")
                        + (isCurrentUser ? " \u2714" : "")) {
                    public void onPress() {
                        try {
                            ActivityManagerNative.getDefault().switchUser(user.id);
                        } catch (RemoteException re) {
                            Log.e(TAG, "Couldn't switch user " + re);
                        }
                    }

                    public boolean showDuringKeyguard() {
                        return true;
                    }

                    public boolean showBeforeProvisioning() {
                        return false;
                    }
                };
                items.add(switchToUser);
            }
        }
    }

    private void createProfileDialog() {
        final ProfileManager profileManager = (ProfileManager) mContext
                .getSystemService(Context.PROFILE_SERVICE);

        final Profile[] profiles = profileManager.getProfiles();
        UUID activeProfile = profileManager.getActiveProfile().getUuid();
        final CharSequence[] names = new CharSequence[profiles.length];

        int i = 0;
        int checkedItem = 0;

        for (Profile profile : profiles) {
            if (profile.getUuid().equals(activeProfile)) {
                checkedItem = i;
                mChosenProfile = profile;
            }
           names[i++] = profile.getName();
        }

        final AlertDialog.Builder ab = new AlertDialog.Builder(mContext);

        AlertDialog dialog = ab.setSingleChoiceItems(names, checkedItem,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < 0)
                            return;
                        mChosenProfile = profiles[which];
                        profileManager.setActiveProfile(mChosenProfile.getUuid());
                        dialog.cancel();
                    }
                }).create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        dialog.show();
    }

    private void toggleScreenRecord() {
        final Intent recordIntent = new Intent("org.chameleonos.action.NOTIFY_RECORD_SERVICE");
        mContext.sendBroadcast(recordIntent, Manifest.permission.RECORD_SCREEN);
    }

    private void prepareDialog() {
        refreshSilentMode();
        if (mAirplaneModeOn != null) {
            mAirplaneModeOn.updateState(mAirplaneState);
        }
        if (mMobileDataOn != null) {
            mMobileDataOn.updateState(mConnectivityManager.getMobileDataEnabled() ? ToggleAction.State.On : ToggleAction.State.Off);
        }
        if (mWifiOn != null) {
            mWifiOn.updateState(mWifiManager.isWifiEnabled() ? ToggleAction.State.On : ToggleAction.State.Off);
        }
        if (mExpandDesktopModeOn != null) {
            mExpandDesktopModeOn.updateState(mExpandDesktopState);
        }
        if (mPieModeOn != null) {
            mPieModeOn.updateState(mPieState);
        }
        if (mPAPieModeOn != null) {
            mPAPieModeOn.updateState(mPAPieState);
        }
        if (mNavBarModeOn != null) {
            mNavBarModeOn.updateState(mNavBarState);
        }

        // Start observing setting changes during
        // dialog shows up
        mSettingsObserver.observe();

        // Global menu is showing. Notify EdgeGestureService.
        IEdgeGestureService edgeGestureService = getEdgeGestureService();
        try {
            if (edgeGestureService != null) {
                edgeGestureService.setOverwriteImeIsActive(true);
            }
        } catch (RemoteException e) {
             mEdgeGestureService = null;
        }

        mAdapter.notifyDataSetChanged();
        mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);

        mDialog.setTitle(R.string.global_actions);

        if (mShowSilentToggle) {
            IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
            mContext.registerReceiver(mRingerModeReceiver, filter);
        }
    }

    private void refreshSilentMode() {
        if (!mHasVibrator) {
            final boolean silentModeOn =
                    mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
            ((ToggleAction)mSilentModeAction).updateState(
                    silentModeOn ? ToggleAction.State.On : ToggleAction.State.Off);
        }
    }

    /** {@inheritDoc} */
    public void onDismiss(DialogInterface dialog) {
        if (mShowSilentToggle) {
            try {
                mContext.unregisterReceiver(mRingerModeReceiver);
            } catch (Exception ie) {
                // ignore this
                Log.w(TAG, ie);
            }
        }
        // Global menu dismiss. Notify EdgeGestureService.
        IEdgeGestureService edgeGestureService = getEdgeGestureService();
        try {
            if (edgeGestureService != null) {
                edgeGestureService.setOverwriteImeIsActive(false);
            }
        } catch (RemoteException e) {
             mEdgeGestureService = null;
        }
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
    }

    /** {@inheritDoc} */
    public void onClick(DialogInterface dialog, int which) {
        if (!(mAdapter.getItem(which) instanceof SilentModeTriStateAction)) {
            dialog.dismiss();
        }
        mAdapter.getItem(which).onPress();
    }

    /**
     * The adapter used for the list within the global actions dialog, taking
     * into account whether the keyguard is showing via
     * {@link GlobalActions#mKeyguardShowing} and whether the device is provisioned
     * via {@link GlobalActions#mDeviceProvisioned}.
     */
    private class MyAdapter extends BaseAdapter {

        public int getCount() {
            int count = 0;

            for (int i = 0; i < mItems.size(); i++) {
                final Action action = mItems.get(i);

                if (mKeyguardShowing && !action.showDuringKeyguard()) {
                    continue;
                }
                if (!mDeviceProvisioned && !action.showBeforeProvisioning()) {
                    continue;
                }
                count++;
            }
            return count;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).isEnabled();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        public Action getItem(int position) {

            int filteredPos = 0;
            for (int i = 0; i < mItems.size(); i++) {
                final Action action = mItems.get(i);
                if (mKeyguardShowing && !action.showDuringKeyguard()) {
                    continue;
                }
                if (!mDeviceProvisioned && !action.showBeforeProvisioning()) {
                    continue;
                }
                if (filteredPos == position) {
                    return action;
                }
                filteredPos++;
            }

            throw new IllegalArgumentException("position " + position
                    + " out of range of showable actions"
                    + ", filtered count=" + getCount()
                    + ", keyguardshowing=" + mKeyguardShowing
                    + ", provisioned=" + mDeviceProvisioned);
        }


        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Action action = getItem(position);
            return action.create(mContext, convertView, parent, LayoutInflater.from(mContext));
        }
    }

    // note: the scheme below made more sense when we were planning on having
    // 8 different things in the global actions dialog.  seems overkill with
    // only 3 items now, but may as well keep this flexible approach so it will
    // be easy should someone decide at the last minute to include something
    // else, such as 'enable wifi', or 'enable bluetooth'

    /**
     * What each item in the global actions dialog must be able to support.
     */
    private interface Action {
        View create(Context context, View convertView, ViewGroup parent, LayoutInflater inflater);

        void onPress();

        public boolean onLongPress();

        /**
         * @return whether this action should appear in the dialog when the keygaurd
         *    is showing.
         */
        boolean showDuringKeyguard();

        /**
         * @return whether this action should appear in the dialog before the
         *   device is provisioned.
         */
        boolean showBeforeProvisioning();

        boolean isEnabled();
    }

    /**
     * A single press action maintains no state, just responds to a press
     * and takes an action.
     */
    private static abstract class SinglePressAction implements Action {
        private final int mIconResId;
        private final Drawable mIcon;
        private final int mMessageResId;
        private final CharSequence mMessage;

        protected SinglePressAction(int iconResId, int messageResId) {
            mIconResId = iconResId;
            mMessageResId = messageResId;
            mMessage = null;
            mIcon = null;
        }

        protected SinglePressAction(int iconResId, Drawable icon, CharSequence message) {
            mIconResId = iconResId;
            mMessageResId = 0;
            mMessage = message;
            mIcon = icon;
        }

        protected SinglePressAction(Drawable icon, CharSequence message) {
            mIconResId = 0;
            mMessageResId = 0;
            mMessage = message;
            mIcon = icon;
        }

        protected SinglePressAction(int iconResId, CharSequence message) {
            mIconResId = iconResId;
            mMessageResId = 0;
            mMessage = message;
            mIcon = null;
        }

        public boolean isEnabled() {
            return true;
        }

        abstract public void onPress();

        public boolean onLongPress() {
            return false;
        }

        public View create(
                Context context, View convertView, ViewGroup parent, LayoutInflater inflater) {
            View v = inflater.inflate(R.layout.global_actions_item, parent, false);

            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            TextView messageView = (TextView) v.findViewById(R.id.message);

            v.findViewById(R.id.status).setVisibility(View.GONE);
            if (mIcon != null) {
                icon.setImageDrawable(mIcon);
                if (mIconResId != 0) {
                    icon.setScaleType(ScaleType.CENTER_CROP);
                }
            } else if (mIconResId != 0) {
                icon.setImageDrawable(context.getResources().getDrawable(mIconResId));
            }
            if (mMessage != null) {
                messageView.setText(mMessage);
            } else {
                messageView.setText(mMessageResId);
            }
            messageView.setTextColor(mTextColor);

            return v;
        }
    }

    /**
     * A toggle action knows whether it is on or off, and displays an icon
     * and status message accordingly.
     */
    private static abstract class ToggleAction implements Action {

        enum State {
            Off(false),
            TurningOn(true),
            TurningOff(true),
            On(false);

            private final boolean inTransition;

            State(boolean intermediate) {
                inTransition = intermediate;
            }

            public boolean inTransition() {
                return inTransition;
            }
        }

        protected State mState = State.Off;

        // prefs
        protected Drawable mEnabledIcon;
        protected Drawable mDisabledIcon;
        protected String mMessage;
        protected int mEnabledStatusMessageResId;
        protected int mDisabledStatusMessageResId;

        /**
         * @param enabledIconResId The icon for when this action is on.
         * @param disabledIconResid The icon for when this action is off.
         * @param essage The general information message, e.g 'Silent Mode'
         * @param enabledStatusMessageResId The on status message, e.g 'sound disabled'
         * @param disabledStatusMessageResId The off status message, e.g. 'sound enabled'
         */
        public ToggleAction(Drawable enabledIcon,
                Drawable disabledIcon,
                String message,
                int enabledStatusMessageResId,
                int disabledStatusMessageResId) {
            mEnabledIcon = enabledIcon;
            mDisabledIcon = disabledIcon;
            mMessage = message;
            mEnabledStatusMessageResId = enabledStatusMessageResId;
            mDisabledStatusMessageResId = disabledStatusMessageResId;
        }

        /**
         * Override to make changes to resource IDs just before creating the
         * View.
         */
        void willCreate() {

        }

        public View create(Context context, View convertView, ViewGroup parent,
                LayoutInflater inflater) {
            willCreate();

            View v = inflater.inflate(R
                            .layout.global_actions_item, parent, false);

            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            TextView messageView = (TextView) v.findViewById(R.id.message);
            TextView statusView = (TextView) v.findViewById(R.id.status);
            final boolean enabled = isEnabled();

            if (messageView != null) {
                messageView.setText(mMessage);
                messageView.setEnabled(enabled);
                messageView.setTextColor(mTextColor);
            }

            boolean on = ((mState == State.On) || (mState == State.TurningOn));
            if (icon != null) {
                icon.setImageDrawable(on ? mEnabledIcon : mDisabledIcon);
                icon.setAlpha(on ? 1.0f : 0.3f);
                icon.setEnabled(enabled);
            }

            if (statusView != null) {
                statusView.setText(on ? mEnabledStatusMessageResId : mDisabledStatusMessageResId);
                statusView.setVisibility(View.VISIBLE);
                statusView.setEnabled(enabled);
                statusView.setTextColor(mTextColor);
            }
            v.setEnabled(enabled);

            return v;
        }

        public final void onPress() {
            if (mState.inTransition()) {
                Log.w(TAG, "shouldn't be able to toggle when in transition");
                return;
            }

            final boolean nowOn = !(mState == State.On);
            onToggle(nowOn);
            changeStateFromPress(nowOn);
        }

        public boolean onLongPress() {
            return false;
        }

        public boolean isEnabled() {
            return !mState.inTransition();
        }

        /**
         * Implementations may override this if their state can be in on of the intermediate
         * states until some notification is received (e.g airplane mode is 'turning off' until
         * we know the wireless connections are back online
         * @param buttonOn Whether the button was turned on or off
         */
        protected void changeStateFromPress(boolean buttonOn) {
            mState = buttonOn ? State.On : State.Off;
        }

        abstract void onToggle(boolean on);

        public void updateState(State state) {
            mState = state;
        }
    }

    private class SilentModeToggleAction extends ToggleAction {
        public SilentModeToggleAction() {
            super(mContext.getResources().getDrawable(R.drawable.ic_audio_vol_mute),
                    mContext.getResources().getDrawable(R.drawable.ic_audio_vol),
                    mContext.getResources().getString(R.string.global_action_toggle_silent_mode),
                    R.string.global_action_silent_mode_on_status,
                    R.string.global_action_silent_mode_off_status);
        }

        void onToggle(boolean on) {
            if (on) {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            } else {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
        }

        public boolean showDuringKeyguard() {
            return true;
        }

        public boolean showBeforeProvisioning() {
            return false;
        }
    }

    private static class SilentModeTriStateAction implements Action, View.OnClickListener {

        private final int[] ITEM_IDS = { R.id.option1, R.id.option2, R.id.option3 };
        private final int[] ICON_IDS = { R.id.icon1, R.id.icon2, R.id.icon3 };

        private final AudioManager mAudioManager;
        private final Handler mHandler;
        private final Context mContext;

        SilentModeTriStateAction(Context context, AudioManager audioManager, Handler handler) {
            mAudioManager = audioManager;
            mHandler = handler;
            mContext = context;
        }

        private int ringerModeToIndex(int ringerMode) {
            // They just happen to coincide
            return ringerMode;
        }

        private int indexToRingerMode(int index) {
            // They just happen to coincide
            return index;
        }

        public View create(Context context, View convertView, ViewGroup parent,
                LayoutInflater inflater) {
            View v = inflater.inflate(R.layout.global_actions_silent_mode, parent, false);

            int selectedIndex = ringerModeToIndex(mAudioManager.getRingerMode());

            int iconColor = Settings.System.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.System.POWER_MENU_ICON_COLOR, -2,
                    UserHandle.USER_CURRENT);
            int colorMode = Settings.System.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.System.POWER_MENU_ICON_COLOR_MODE, 0,
                    UserHandle.USER_CURRENT);

            if (iconColor == -2) {
                iconColor = mContext.getResources().getColor(
                    com.android.internal.R.color.power_menu_icon_default_color);
            }

            for (int i = 0; i < 3; i++) {
                View itemView = v.findViewById(ITEM_IDS[i]);
                itemView.setSelected(selectedIndex == i);
                // Set up click handler
                itemView.setTag(i);
                if (colorMode != 3) {
                    ImageView icon = (ImageView) itemView.findViewById(ICON_IDS[i]);
                    if (icon != null) {
                        icon.setImageDrawable(ImageHelper.resize(mContext, new BitmapDrawable(
                            ImageHelper.getColoredBitmap(icon.getDrawable(), iconColor)), 35));
                    }
                }
                itemView.setOnClickListener(this);
            }
            return v;
        }

        public void onPress() {
        }

        public boolean onLongPress() {
            return false;
        }

        public boolean showDuringKeyguard() {
            return true;
        }

        public boolean showBeforeProvisioning() {
            return false;
        }

        public boolean isEnabled() {
            return true;
        }

        void willCreate() {
        }

        public void onClick(View v) {
            if (!(v.getTag() instanceof Integer)) return;

            int index = (Integer) v.getTag();
            mAudioManager.setRingerMode(indexToRingerMode(index));
            mHandler.sendEmptyMessageDelayed(MESSAGE_DISMISS, DIALOG_DISMISS_DELAY);
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                String reason = intent.getStringExtra(PhoneWindowManager.SYSTEM_DIALOG_REASON_KEY);
                if (!PhoneWindowManager.SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS.equals(reason)) {
                    mHandler.sendEmptyMessage(MESSAGE_DISMISS);
                }
            } else if (TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED.equals(action)) {
                // Airplane mode can be changed after ECM exits if airplane toggle button
                // is pressed during ECM mode
                if (!(intent.getBooleanExtra("PHONE_IN_ECM_STATE", false)) &&
                        mIsWaitingForEcmExit) {
                    mIsWaitingForEcmExit = false;
                    changeAirplaneModeSystemSetting(true);
                }
            }
        }
    };

    private BroadcastReceiver mThemeChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            mUiContext = null;
        }
    };

    private SettingsObserver mSettingsObserver = new SettingsObserver(new Handler());
    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SPIE_CONTROLS), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_CONTROLS), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.EXPANDED_DESKTOP_STATE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_SHOW), false, this,
                    UserHandle.USER_ALL);

        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.System.getUriFor(
                    Settings.System.SPIE_CONTROLS))) {
                onPieModeChanged();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.PIE_CONTROLS))) {
                onPAPieModeChanged();
            } else if (uri.equals(Settings.System.getUriFor(
                Settings.System.EXPANDED_DESKTOP_STATE))) {
                onExpandDesktopModeChanged();
            } else if (uri.equals(Settings.System.getUriFor(
                Settings.System.NAVIGATION_BAR_SHOW))) {
                onNavBarModeChanged();
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (!mHasTelephony || mAirplaneModeOn == null || mAdapter == null) return;
            final boolean inAirplaneMode = serviceState.getState() == ServiceState.STATE_POWER_OFF;
            mAirplaneState = inAirplaneMode ? ToggleAction.State.On : ToggleAction.State.Off;
            if (mAirplaneModeOn != null) {
                mHandler.sendEmptyMessage(MESSAGE_REFRESH_AIRPLANEMODE);
            }
        }
    };

    private BroadcastReceiver mRingerModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                mHandler.sendEmptyMessage(MESSAGE_REFRESH);
            }
        }
    };

    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onAirplaneModeChanged();
        }
    };

    private static final int MESSAGE_DISMISS = 0;
    private static final int MESSAGE_REFRESH = 1;
    private static final int MESSAGE_SHOW = 2;
    private static final int MESSAGE_REFRESH_AIRPLANEMODE = 3;
    private static final int DIALOG_DISMISS_DELAY = 300; // ms

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_DISMISS:
                if (mDialog != null) {
                    mDialog.dismiss();
                }
                break;
            case MESSAGE_REFRESH:
                refreshSilentMode();
                mAdapter.notifyDataSetChanged();
                break;
            case MESSAGE_SHOW:
                handleShow();
                break;
            case MESSAGE_REFRESH_AIRPLANEMODE:
                mAirplaneModeOn.updateState(mAirplaneState);
                mAdapter.notifyDataSetChanged();
                break;
            }
        }
    };

    private ToggleAction.State getUpdatedAirplaneToggleState() {
        return (Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 1) ?
                ToggleAction.State.On : ToggleAction.State.Off;
    }

    private void onAirplaneModeChanged() {
        // Let the service state callbacks handle the state.
        if (mHasTelephony) return;

        mAirplaneState = getUpdatedAirplaneToggleState();
        if (mAirplaneModeOn != null) {
            mAirplaneModeOn.updateState(mAirplaneState);
        }
    }

    private void onExpandDesktopModeChanged() {
        boolean expandDesktopModeOn = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.EXPANDED_DESKTOP_STATE,
                0, UserHandle.USER_CURRENT) == 1;
        mExpandDesktopState = expandDesktopModeOn ? ToggleAction.State.On : ToggleAction.State.Off;
        if (mExpandDesktopModeOn != null) {
            mExpandDesktopModeOn.updateState(mExpandDesktopState);
        }
    }

    private void onPieModeChanged() {
        boolean pieModeOn = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.SPIE_CONTROLS,
                0, UserHandle.USER_CURRENT) == 1;
        mPieState = pieModeOn ? ToggleAction.State.On : ToggleAction.State.Off;
        if (mPieModeOn != null) {
            mPieModeOn.updateState(mPieState);
        }
    }

    private void onPAPieModeChanged() {
        boolean papieModeOn = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.PIE_CONTROLS,
                0, UserHandle.USER_CURRENT) == 1;
        mPAPieState = papieModeOn ? ToggleAction.State.On : ToggleAction.State.Off;
        if (mPAPieModeOn != null) {
            mPAPieModeOn.updateState(mPieState);
        }
    }

    private void onNavBarModeChanged() {
        boolean defaultValue = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar);
        boolean navBarModeOn = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.NAVIGATION_BAR_SHOW,
                defaultValue ? 1 : 0, UserHandle.USER_CURRENT) == 1;
        mNavBarState = navBarModeOn ? ToggleAction.State.On : ToggleAction.State.Off;
        if (mNavBarModeOn != null) {
            mNavBarModeOn.updateState(mNavBarState);
        }
    }

    /**
     * Change the airplane mode system setting
     */
    private void changeAirplaneModeSystemSetting(boolean on) {
        Settings.Global.putInt(
                mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                on ? 1 : 0);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("state", on);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        if (!mHasTelephony) {
            mAirplaneState = on ? ToggleAction.State.On : ToggleAction.State.Off;
        }
    }

    /**
     * If not set till now get EdgeGestureService.
     */
    private IEdgeGestureService getEdgeGestureService() {
        synchronized (mServiceAquireLock) {
            if (mEdgeGestureService == null) {
                mEdgeGestureService = IEdgeGestureService.Stub.asInterface(
                            ServiceManager.getService("edgegestureservice"));
            }
            return mEdgeGestureService;
        }
    }

    private void startQuickBoot() {

        Intent intent = new Intent("org.codeaurora.action.QUICKBOOT");
        intent.putExtra("mode", 0);
        try {
            mContext.startActivityAsUser(intent,UserHandle.CURRENT);
        } catch (ActivityNotFoundException e) {
        }
    }

    private static final class GlobalActionsDialog extends Dialog implements DialogInterface {
        private final Context mContext;
        private final int mWindowTouchSlop;
        private final AlertController mAlert;

        private EnableAccessibilityController mEnableAccessibilityController;

        private boolean mIntercepted;
        private boolean mCancelOnUp;

        public GlobalActionsDialog(Context context, AlertParams params) {
            super(context, getDialogTheme(context));
            mContext = context;
            mAlert = new AlertController(mContext, this, getWindow());
            mWindowTouchSlop = ViewConfiguration.get(context).getScaledWindowTouchSlop();
            params.apply(mAlert);
        }

        private static int getDialogTheme(Context context) {
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(com.android.internal.R.attr.alertDialogTheme,
                    outValue, true);
            return outValue.resourceId;
        }

        @Override
        protected void onStart() {
            // If global accessibility gesture can be performed, we will take care
            // of dismissing the dialog on touch outside. This is because the dialog
            // is dismissed on the first down while the global gesture is a long press
            // with two fingers anywhere on the screen.
            if (EnableAccessibilityController.canEnableAccessibilityViaGesture(mContext)) {
                mEnableAccessibilityController = new EnableAccessibilityController(mContext);
                super.setCanceledOnTouchOutside(false);
            } else {
                mEnableAccessibilityController = null;
                super.setCanceledOnTouchOutside(true);
            }
            super.onStart();
        }

        @Override
        protected void onStop() {
            if (mEnableAccessibilityController != null) {
                mEnableAccessibilityController.onDestroy();
            }
            super.onStop();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (mEnableAccessibilityController != null) {
                final int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    View decor = getWindow().getDecorView();
                    final int eventX = (int) event.getX();
                    final int eventY = (int) event.getY();
                    if (eventX < -mWindowTouchSlop
                            || eventY < -mWindowTouchSlop
                            || eventX >= decor.getWidth() + mWindowTouchSlop
                            || eventY >= decor.getHeight() + mWindowTouchSlop) {
                        mCancelOnUp = true;
                    }
                }
                try {
                    if (!mIntercepted) {
                        mIntercepted = mEnableAccessibilityController.onInterceptTouchEvent(event);
                        if (mIntercepted) {
                            final long now = SystemClock.uptimeMillis();
                            event = MotionEvent.obtain(now, now,
                                    MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
                            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                            mCancelOnUp = true;
                        }
                    } else {
                        return mEnableAccessibilityController.onTouchEvent(event);
                    }
                } finally {
                    if (action == MotionEvent.ACTION_UP) {
                        if (mCancelOnUp) {
                            cancel();
                        }
                        mCancelOnUp = false;
                        mIntercepted = false;
                    }
                }
            }
            return super.dispatchTouchEvent(event);
        }

        public ListView getListView() {
            return mAlert.getListView();
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mAlert.installContent();
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (mAlert.onKeyDown(keyCode, event)) {
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            if (mAlert.onKeyUp(keyCode, event)) {
                return true;
            }
            return super.onKeyUp(keyCode, event);
        }
    }
}
