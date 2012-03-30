/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2010-2011 CyanogenMod Project
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

import com.android.internal.R;
import com.android.internal.app.ShutdownThread;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.google.android.collect.Lists;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Profile;
import android.app.ProfileManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.CmSystem;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.app.ThemeUtils;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Helper to show the global actions dialog.  Each item is an {@link Action} that
 * may show depending on whether the keyguard is showing, and whether the device
 * is provisioned.
 */
class GlobalActions implements DialogInterface.OnDismissListener, DialogInterface.OnClickListener  {

    private static final String TAG = "GlobalActions";

    private StatusBarManager mStatusBar;

    private final Context mContext;
    private Context mUiContext;
    private final AudioManager mAudioManager;

    private ArrayList<Action> mItems;
    private AlertDialog mDialog;

    private ToggleAction mSilentModeToggle;
    private ToggleAction mAirplaneModeOn;

    private MyAdapter mAdapter;

    private boolean mKeyguardShowing = false;
    private boolean mDeviceProvisioned = false;
    private ToggleAction.State mAirplaneState = ToggleAction.State.Off;
    private boolean mIsWaitingForEcmExit = false;

    private Profile mChosenProfile;

    private IWindowManager mWindowManager;

    private int mInjectKeycode;

    private boolean mExtendPm;

    private SinglePressAction mExtendPmHome;

    private SinglePressAction mExtendPmMenu;

    private SinglePressAction mExtendPmBack;

    private boolean mExtendPmShowHome;
    private boolean mExtendPmShowMenu;
    private boolean mExtendPmShowBack;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXTEND_PM), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXTEND_PM_SHOW_HOME), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXTEND_PM_SHOW_MENU), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXTEND_PM_SHOW_BACK), false, this);
            onChange(true);
        }

        @Override
        public void onChange(boolean selfChange) {
            int defValue;

            defValue=(CmSystem.getDefaultBool(mContext, CmSystem.CM_DEFAULT_EXTEND_POWER_MENU) ? 1 : 0);
            mExtendPm = (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.EXTEND_PM, defValue) == 1);
            defValue=(CmSystem.getDefaultBool(mContext, CmSystem.CM_DEFAULT_POWER_MENU_HOME) ? 1 : 0);
            mExtendPmShowHome = (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.EXTEND_PM_SHOW_HOME, defValue) == 1);
            defValue=(CmSystem.getDefaultBool(mContext, CmSystem.CM_DEFAULT_POWER_MENU_MENU) ? 1 : 0);
            mExtendPmShowMenu = (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.EXTEND_PM_SHOW_MENU, defValue) == 1);
            defValue=(CmSystem.getDefaultBool(mContext, CmSystem.CM_DEFAULT_POWER_MENU_BACK) ? 1 : 0);
            mExtendPmShowBack = (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.EXTEND_PM_SHOW_BACK, defValue) == 1);
        }
    }

    /**
     * @param context everything needs a context :(
     */
    public GlobalActions(Context context) {
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

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

        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();

        // get window manager to inject key events
        mWindowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
    }

    /**
     * Show the global actions dialog (creating if necessary)
     * @param keyguardShowing True if keyguard is showing
     */
    public void showDialog(boolean keyguardShowing, boolean isDeviceProvisioned) {
        mKeyguardShowing = keyguardShowing;
        mDeviceProvisioned = isDeviceProvisioned;
        if (mDialog != null && mUiContext == null) {
            mDialog.dismiss();
            mDialog = null;
        }
        if (mDialog == null) {
            mStatusBar = (StatusBarManager)mContext.getSystemService(Context.STATUS_BAR_SERVICE);
            mDialog = createDialog();
        }
        prepareDialog();

        mStatusBar.disable(StatusBarManager.DISABLE_EXPAND);
        mDialog.show();
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
    private AlertDialog createDialog() {
        mSilentModeToggle = new ToggleAction(
                R.drawable.ic_lock_silent_mode,
                R.drawable.ic_lock_silent_mode_off,
                R.string.global_action_toggle_silent_mode,
                R.string.global_action_silent_mode_on_status,
                R.string.global_action_silent_mode_off_status) {

            void willCreate() {
                // XXX: FIXME: switch to ic_lock_vibrate_mode when available
                mEnabledIconResId = (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.VIBRATE_IN_SILENT, 1) == 1)
                    ? R.drawable.ic_lock_silent_mode_vibrate
                    : R.drawable.ic_lock_silent_mode;
            }

            void onToggle(boolean on) {
                if (on) {
                    mAudioManager.setRingerMode((Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.VIBRATE_IN_SILENT, 1) == 1)
                        ? AudioManager.RINGER_MODE_VIBRATE
                        : AudioManager.RINGER_MODE_SILENT);
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
        };

        mAirplaneModeOn = new ToggleAction(
                R.drawable.ic_lock_airplane_mode,
                R.drawable.ic_lock_airplane_mode_off,
                R.string.global_actions_toggle_airplane_mode,
                R.string.global_actions_airplane_mode_on_status,
                R.string.global_actions_airplane_mode_off_status) {

            void onToggle(boolean on) {
                if (Boolean.parseBoolean(
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

        // tablet: extendPm - Home
        mExtendPmHome = new SinglePressAction(com.android.internal.R.drawable.ic_lock_home,
                R.string.global_action_home) {

            public void onPress() {
                injectKeyDelayed(KeyEvent.KEYCODE_HOME);
            }

            public boolean showDuringKeyguard() {
                return false;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };
        // tablet: extendPm - Menu
        mExtendPmMenu = new SinglePressAction(com.android.internal.R.drawable.ic_lock_menu,
                R.string.global_action_menu) {

            public void onPress() {
                injectKeyDelayed(KeyEvent.KEYCODE_MENU);
            }

            public boolean showDuringKeyguard() {
                return false;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };
        // tablet: extendPm - Back
        mExtendPmBack = new SinglePressAction(com.android.internal.R.drawable.ic_lock_back,
                R.string.global_action_back) {

            public void onPress() {
                injectKeyDelayed(KeyEvent.KEYCODE_BACK);
            }

            public boolean showDuringKeyguard() {
                return false;
            }

            public boolean showBeforeProvisioning() {
                return false;
            }
        };

        mItems = Lists.newArrayList(
                // silent mode
                mSilentModeToggle,
                // next: airplane mode
                mAirplaneModeOn,
                // next: choose profile
                new ProfileChooseAction() {
                    public void onPress() {
                        createProfileDialog();
                    }

                    public boolean showDuringKeyguard() {
                        return false;
                    }

                    public boolean showBeforeProvisioning() {
                        return false;
                    }
                },
                // next: screenshot
                new SinglePressAction(com.android.internal.R.drawable.ic_lock_screenshot, R.string.global_action_screenshot) {
                    public void onPress() {
                        Intent intent = new Intent("android.intent.action.SCREENSHOT");
                        mContext.sendBroadcast(intent);
                    }

                    public boolean showDuringKeyguard() {
                        return true;
                    }

                    public boolean showBeforeProvisioning() {
                        return true;
                    }
                },
                // next: reboot
                new SinglePressAction(com.android.internal.R.drawable.ic_lock_reboot, R.string.global_action_reboot) {
                    public void onPress() {
                        ShutdownThread.reboot(getUiContext(), null, (Settings.System.getInt(mContext.getContentResolver(),
                                Settings.System.POWER_DIALOG_PROMPT, 1) == 1));
                    }

                    public boolean showDuringKeyguard() {
                        return true;
                    }

                    public boolean showBeforeProvisioning() {
                        return true;
                    }
                },
                // last: power off
                new SinglePressAction(
                        com.android.internal.R.drawable.ic_lock_power_off,
                        R.string.global_action_power_off) {

                    public void onPress() {
                        // shutdown by making sure radio and power are handled accordingly.
                        ShutdownThread.shutdown(getUiContext(),(Settings.System.getInt(mContext.getContentResolver(),
                                Settings.System.POWER_DIALOG_PROMPT, 1) == 1));
                    }

                    public boolean showDuringKeyguard() {
                        return true;
                    }

                    public boolean showBeforeProvisioning() {
                        return true;
                    }
                });

        mAdapter = new MyAdapter();

        final AlertDialog.Builder ab = new AlertDialog.Builder(getUiContext());

        ab.setAdapter(mAdapter, this)
                .setInverseBackgroundForced(true);

        final AlertDialog dialog = ab.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        if (!mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sf_slowBlur)) {
            dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }

        dialog.setOnDismissListener(this);

        return dialog;
    }

    public void injectKeyDelayed(int keycode){
        mInjectKeycode = keycode;
        mHandler.removeCallbacks(onInjectKeyDelayed);
        mHandler.postDelayed(onInjectKeyDelayed, 50);
    }

    final Runnable onInjectKeyDelayed = new Runnable() {
        public void run() {
            try {
                mWindowManager.injectKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, mInjectKeycode), true);
                mWindowManager.injectKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, mInjectKeycode), true);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private void createProfileDialog(){
        final ProfileManager profileManager = (ProfileManager)mContext.getSystemService(Context.PROFILE_SERVICE);

        final Profile[] profiles = profileManager.getProfiles();
        UUID activeProfile = profileManager.getActiveProfile().getUuid();
        final CharSequence[] names = new CharSequence[profiles.length];

        int i=0;
        int checkedItem = 0;
        for(Profile profile : profiles){
            if(profile.getUuid().equals(activeProfile)){
                checkedItem = i;
                mChosenProfile = profile;
            }
            names[i++] = profile.getName();
        }

        final AlertDialog.Builder ab = new AlertDialog.Builder(getUiContext());

        AlertDialog dialog = ab
                .setSingleChoiceItems(names, checkedItem, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < 0)
                            return;
                        mChosenProfile = profiles[which];
                    }
                })
                .setPositiveButton(com.android.internal.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                profileManager.setActiveProfile(mChosenProfile.getUuid());
                            }
                        })
                .setNegativeButton(com.android.internal.R.string.no,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        }).create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        if (!mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sf_slowBlur)) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }
        dialog.show();
    }

    private void prepareDialog() {
        // care about extended power menu entries
        mItems.remove(mExtendPmHome);
        mItems.remove(mExtendPmMenu);
        mItems.remove(mExtendPmBack);

        if(mExtendPm){
            if(mExtendPmShowBack && !mItems.contains(mExtendPmBack))
                mItems.add(0, mExtendPmBack);
            if(mExtendPmShowMenu && !mItems.contains(mExtendPmMenu))
                mItems.add(0, mExtendPmMenu);
            if(mExtendPmShowHome && !mItems.contains(mExtendPmHome))
                mItems.add(0, mExtendPmHome);
        }

        final boolean silentModeOn =
                mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
        mSilentModeToggle.updateState(
                silentModeOn ? ToggleAction.State.On : ToggleAction.State.Off);
        mAirplaneModeOn.updateState(mAirplaneState);
        mAdapter.notifyDataSetChanged();
        if (mKeyguardShowing) {
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        } else {
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        }
        mDialog.setTitle(R.string.global_actions);
    }


    /** {@inheritDoc} */
    public void onDismiss(DialogInterface dialog) {
        mStatusBar.disable(StatusBarManager.DISABLE_NONE);
    }

    /** {@inheritDoc} */
    public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
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
            final Context context = getUiContext();
            return action.create(context, convertView, parent, LayoutInflater.from(context));
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
        private final int mMessageResId;

        protected SinglePressAction(int iconResId, int messageResId) {
            mIconResId = iconResId;
            mMessageResId = messageResId;
        }

        public boolean isEnabled() {
            return true;
        }

        abstract public void onPress();

        public View create(
                Context context, View convertView, ViewGroup parent, LayoutInflater inflater) {
            View v = (convertView != null) ?
                    convertView :
                    inflater.inflate(R.layout.global_actions_item, parent, false);

            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            TextView messageView = (TextView) v.findViewById(R.id.message);

            v.findViewById(R.id.status).setVisibility(View.GONE);

            icon.setImageDrawable(context.getResources().getDrawable(mIconResId));
            messageView.setText(mMessageResId);

            return v;
        }
    }

    /**
     * A single press action maintains no state, just responds to a press
     * and takes an action.
     */
    private abstract class ProfileChooseAction implements Action {
        private ProfileManager mProfileManager;

        protected ProfileChooseAction() {
            mProfileManager = (ProfileManager)mContext.getSystemService(Context.PROFILE_SERVICE);
        }

        public boolean isEnabled() {
            return true;
        }

        abstract public void onPress();

        public View create(
                Context context, View convertView, ViewGroup parent, LayoutInflater inflater) {
            View v = (convertView != null) ?
                    convertView :
                    inflater.inflate(R.layout.global_actions_item, parent, false);

            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            TextView messageView = (TextView) v.findViewById(R.id.message);
            TextView statusView = (TextView) v.findViewById(R.id.status);
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(mProfileManager.getActiveProfile().getName());

            icon.setImageDrawable(context.getResources().getDrawable(com.android.internal.R.drawable.ic_lock_profile));
            messageView.setText(R.string.global_action_choose_profile);

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
        protected int mEnabledIconResId;
        protected int mDisabledIconResid;
        protected int mMessageResId;
        protected int mEnabledStatusMessageResId;
        protected int mDisabledStatusMessageResId;

        /**
         * @param enabledIconResId The icon for when this action is on.
         * @param disabledIconResid The icon for when this action is off.
         * @param essage The general information message, e.g 'Silent Mode'
         * @param enabledStatusMessageResId The on status message, e.g 'sound disabled'
         * @param disabledStatusMessageResId The off status message, e.g. 'sound enabled'
         */
        public ToggleAction(int enabledIconResId,
                int disabledIconResid,
                int essage,
                int enabledStatusMessageResId,
                int disabledStatusMessageResId) {
            mEnabledIconResId = enabledIconResId;
            mDisabledIconResid = disabledIconResid;
            mMessageResId = essage;
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

            View v = (convertView != null) ?
                    convertView :
                    inflater.inflate(R
                            .layout.global_actions_item, parent, false);

            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            TextView messageView = (TextView) v.findViewById(R.id.message);
            TextView statusView = (TextView) v.findViewById(R.id.status);

            messageView.setText(mMessageResId);

            boolean on = ((mState == State.On) || (mState == State.TurningOn));
            icon.setImageDrawable(context.getResources().getDrawable(
                    (on ? mEnabledIconResId : mDisabledIconResid)));
            statusView.setText(on ? mEnabledStatusMessageResId : mDisabledStatusMessageResId);
            statusView.setVisibility(View.VISIBLE);

            final boolean enabled = isEnabled();
            messageView.setEnabled(enabled);
            statusView.setEnabled(enabled);
            icon.setEnabled(enabled);
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

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                String reason = intent.getStringExtra(CmPhoneWindowManager.SYSTEM_DIALOG_REASON_KEY);
                if (!CmPhoneWindowManager.SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS.equals(reason)) {
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

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            final boolean inAirplaneMode = serviceState.getState() == ServiceState.STATE_POWER_OFF;
            mAirplaneState = inAirplaneMode ? ToggleAction.State.On : ToggleAction.State.Off;
            mAirplaneModeOn.updateState(mAirplaneState);
            mAdapter.notifyDataSetChanged();
        }
    };

    private static final int MESSAGE_DISMISS = 0;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_DISMISS) {
                if (mDialog != null) {
                    mDialog.dismiss();
                }
            }
        }
    };

    /**
     * Change the airplane mode system setting
     */
    private void changeAirplaneModeSystemSetting(boolean on) {
        Settings.System.putInt(
                mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON,
                on ? 1 : 0);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("state", on);
        mContext.sendBroadcast(intent);
    }
}
