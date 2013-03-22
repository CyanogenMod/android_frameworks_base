/*
 * Copyright (C) 2013 The CyanogenMod Project (Jens Doll)
 * This code is loosely based on portions of the ParanoidAndroid Project source, Copyright (C) 2012.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.systemui.statusbar.policy;

import android.app.ActivityOptions;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Slog;
import android.view.HapticFeedbackConstants;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.pie.PieItem;
import com.android.systemui.statusbar.pie.PieLayout;
import com.android.systemui.statusbar.pie.PieLayout.PieDrawable;
import com.android.systemui.statusbar.pie.PieLayout.PieSlice;
import com.android.systemui.statusbar.pie.PieSliceContainer;
import com.android.systemui.statusbar.pie.PieSysInfo;

/**
 * Controller class for the default pie control.
 * <p>
 * This class is responsible for setting up the pie control, activating it, and defining and
 * executing the actions that can be triggered by the pie control.
 */
public class PieController implements BaseStatusBar.NavigationBarCallback,
        PieLayout.OnSnapListener, PieItem.PieOnClickListener {
    public static final String TAG = "PieController";
    public static final boolean DEBUG = false;

    private enum ButtonType {
        BACK,
        HOME,
        RECENT,
        MENU,
        SEARCH,
        SEARCHLIGHT
    };

    public static final float EMPTY_ANGLE = 10;
    public static final float START_ANGLE = 180 + EMPTY_ANGLE;

    private static final int MSG_INJECT_KEY = 1066;

    private Context mContext;
    private PieLayout mPieContainer;
    /**
     * This is only needed for #toggleRecentApps()
     */
    private BaseStatusBar mStatusBar;
    private Vibrator mVibrator;
    private IWindowManager mWm;
    private int mBatteryLevel;
    private int mBatteryStatus;
    private boolean mHasTelephony;
    private ServiceState mServiceState;

    // all pie slices that are managed by the controller
    private PieSliceContainer mNavigationSlice;
    private PieSysInfo mSysInfo;
    private PieItem mMenuButton;
    private PieItem mSearchLight;

    private int mNavigationIconHints = 0;
    private int mDisabledFlags = 0;
    private boolean mShowMenu = false;
    private Drawable mBackIcon;
    private Drawable mBackAltIcon;

    /**
     * Defines the positions in which pie controls may appear. This enumeration is used to store
     * an index, a flag and the android gravity for each position.
     */
    public enum Position {
        LEFT(0, 0, android.view.Gravity.LEFT),
        BOTTOM(1, 1, android.view.Gravity.BOTTOM),
        RIGHT(2, 1, android.view.Gravity.RIGHT),
        TOP(3, 0, android.view.Gravity.TOP);

        Position(int index, int factor, int android_gravity) {
            INDEX = index;
            FLAG = (0x01<<index);
            ANDROID_GRAVITY = android_gravity;
            FACTOR = factor;
        }

        public final int INDEX;
        public final int FLAG;
        public final int ANDROID_GRAVITY;
        /**
         * This is 1 when the position is not at the axis (like {@link Position.RIGHT} is
         * at {@code Layout.getWidth()} not at {@code 0}).
         */
        public final int FACTOR;
    }

    private Position mPosition;

    public static class Tracker {
        public static float sDistance;
        private float initialX = 0;
        private float initialY = 0;

        private Tracker(Position position) {
            this.position = position;
        }

        public void start(MotionEvent event) {
            initialX = event.getX();
            initialY = event.getY();
            active = true;
        }

        public boolean move(MotionEvent event) {
            final float x = event.getX();
            final float y = event.getY();
            if (!active) {
                return false;
            }

            // Unroll the complete logic here - we want to be fast and out of the
            // event chain as fast as possible.
            boolean loaded = false;
            switch (position) {
                case LEFT:
                    if (initialY - y < sDistance && y - initialY < sDistance) {
                        if (x - initialX <= sDistance) {
                            return false;
                        }
                        loaded = true;
                    }
                    break;
                case BOTTOM:
                    if (initialX - x < sDistance && x - initialX < sDistance) {
                        if (initialY - y <= sDistance) {
                            return false;
                        }
                        loaded = true;
                    }
                    break;
                case TOP:
                    if (initialX - x < sDistance && x - initialX < sDistance) {
                        if (y - initialY <= sDistance) {
                            return false;
                        }
                        loaded = true;
                    }
                    break;
                case RIGHT:
                    if (initialY - y < sDistance && y - initialY < sDistance) {
                        if (initialX - x <= sDistance) {
                            return false;
                        }
                        loaded = true;
                    }
                    break;
            }
            active = false;
            return loaded;
        }

        public boolean active = false;
        public final Position position;
    }

    public Tracker buildTracker(Position position) {
        return new Tracker(position);
    }

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_INJECT_KEY:
                    final long eventTime = SystemClock.uptimeMillis();
                    final InputManager inputManager = InputManager.getInstance();

                    inputManager.injectInputEvent(new KeyEvent(eventTime - 50, eventTime - 50,
                            KeyEvent.ACTION_DOWN, m.arg1, 0),
                            InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
                    inputManager.injectInputEvent(new KeyEvent(eventTime - 50, eventTime - 25,
                            KeyEvent.ACTION_UP, m.arg1, 0),
                            InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);

                    break;
            }
        }
    }
    private H mHandler = new H();

    private void injectKeyDelayed(int keycode) {
        mHandler.removeMessages(MSG_INJECT_KEY);
        mHandler.sendMessageDelayed(Message.obtain(mHandler, MSG_INJECT_KEY, keycode, 0), 50);
    }

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_SEARCH), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            setupNavigationItems();
        }
    }

    private SettingsObserver mSettingsObserver = new SettingsObserver(mHandler);

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                         BatteryManager.BATTERY_STATUS_UNKNOWN);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                // Give up on screen off. what's the point in pie controls if you don't see them?
                if (mPieContainer != null) {
                    mPieContainer.exit();
                }
            }
        }
    };

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            mServiceState = serviceState;
        }
    };

    public PieController(Context context) {
        mContext = context;

        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        final PackageManager pm = mContext.getPackageManager();
        mHasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);

        final Resources res = mContext.getResources();
        Tracker.sDistance = res.getDimensionPixelSize(R.dimen.pie_trigger_distance);

        mBackIcon = res.getDrawable(R.drawable.ic_sysbar_back);
        mBackAltIcon = res.getDrawable(R.drawable.ic_sysbar_back_ime);
    }

    public void attachTo(BaseStatusBar statusBar) {
        mStatusBar = statusBar;
    }

    public void attachTo(PieLayout container) {
        mPieContainer = container;
        mPieContainer.clearSlices();

        if (DEBUG) {
            Slog.d(TAG, "Attaching to container: " + container);
        }

        mPieContainer.setOnSnapListener(this);

        final Resources res = mContext.getResources();

        // construct navbar slice
        int inner = res.getDimensionPixelSize(R.dimen.pie_navbar_radius);
        int outer = inner + res.getDimensionPixelSize(R.dimen.pie_navbar_height);
        mNavigationSlice = new PieSliceContainer(mPieContainer, PieSlice.IMPORTANT
                | PieDrawable.DISPLAY_ALL);
        mNavigationSlice.setGeometry(START_ANGLE, 180 - 2 * EMPTY_ANGLE, inner, outer);
        setupNavigationItems();
        mPieContainer.addSlice(mNavigationSlice);

        // construct sysinfo slice
        inner = res.getDimensionPixelSize(R.dimen.pie_sysinfo_radius);
        outer = inner + res.getDimensionPixelSize(R.dimen.pie_sysinfo_height);
        mSysInfo = new PieSysInfo(mContext, mPieContainer, this, PieDrawable.DISPLAY_NOT_AT_TOP);
        mSysInfo.setGeometry(START_ANGLE, 180 - 2 * EMPTY_ANGLE, inner, outer);
        mPieContainer.addSlice(mSysInfo);

        // start listening for changes
        mSettingsObserver.observe();

        mContext.registerReceiver(mBroadcastReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        if (mHasTelephony) {
            TelephonyManager telephonyManager =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        }
    }

    private void setupNavigationItems() {
        int minimumImageSize = (int)mContext.getResources().getDimension(R.dimen.pie_item_size);

        mNavigationSlice.clear();
        mNavigationSlice.addItem(constructItem(2, ButtonType.BACK,
                R.drawable.ic_sysbar_back, minimumImageSize));
        mNavigationSlice.addItem(constructItem(2, ButtonType.HOME,
                R.drawable.ic_sysbar_home, minimumImageSize));
        mNavigationSlice.addItem(constructItem(2, ButtonType.RECENT,
                R.drawable.ic_sysbar_recent, minimumImageSize));
        if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_SEARCH, 0) == 1) {
            mNavigationSlice.addItem(constructItem(1, ButtonType.SEARCH,
                    R.drawable.ic_sysbar_search_side, minimumImageSize));
        }

        // search light has a width of 6 to take the complete space that normally
        // BACK HOME RECENT would occupy
        mSearchLight = constructItem(6, ButtonType.SEARCHLIGHT,
                R.drawable.search_light, minimumImageSize);
        mNavigationSlice.addItem(mSearchLight);

        mMenuButton = constructItem(1, ButtonType.MENU,
                R.drawable.ic_sysbar_menu, minimumImageSize);
        mNavigationSlice.addItem(mMenuButton);

        setNavigationIconHints(mNavigationIconHints, true);
        setMenuVisibility(mShowMenu);
    }

    private PieItem constructItem(int width, ButtonType type, int image, int minimumImageSize) {
        ImageView view = new ImageView(mContext);
        view.setImageResource(image);
        view.setMinimumWidth(minimumImageSize);
        view.setMinimumHeight(minimumImageSize);
        LayoutParams lp = new LayoutParams(minimumImageSize, minimumImageSize);
        view.setLayoutParams(lp);
        PieItem item = new PieItem(mContext, mPieContainer, width, type, view);
        item.setOnClickListener(this);
        return item;
    }

    public void activateFromTrigger(View view, MotionEvent event, Position position) {
        if (mPieContainer != null && !isShowing()) {
            doHapticTriggerFeedback();

            mPosition = position;
            Point center = new Point((int) event.getRawX(), (int) event.getRawY());
            mPieContainer.activate(center, position);
            mPieContainer.invalidate();
        }
    }

    @Override
    public void setNavigationIconHints(int hints) {
        // this call may come from outside
        // check if we already have a navigation slice to manipulate
        if (mNavigationSlice != null) {
            setNavigationIconHints(hints, false);
        } else {
            mNavigationIconHints = hints;
        }
    }

    protected void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;

        if (DEBUG) Slog.v(TAG, "Pie navigation hints: " + hints);

        mNavigationIconHints = hints;

        PieItem item = findItem(ButtonType.HOME);
        if (item != null) {
            boolean isNop = (hints & StatusBarManager.NAVIGATION_HINT_HOME_NOP) != 0;
            item.setAlpha(isNop ? 0.5f : 1.0f);
        }
        item = findItem(ButtonType.RECENT);
        if (item != null) {
            boolean isNop = (hints & StatusBarManager.NAVIGATION_HINT_RECENT_NOP) != 0;
            item.setAlpha(isNop ? 0.5f : 1.0f);
        }
        item = findItem(ButtonType.BACK);
        if (item != null) {
            boolean isNop = (hints & StatusBarManager.NAVIGATION_HINT_BACK_NOP) != 0;
            boolean isAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
            item.setAlpha(isNop ? 0.5f : 1.0f);
            item.setImageDrawable(isAlt ? mBackAltIcon : mBackIcon);
        }
        setDisabledFlags(mDisabledFlags, true);
    }

    private PieItem findItem(ButtonType type) {
        for (PieItem item : mNavigationSlice.getItems()) {
            ButtonType itemType = (ButtonType) item.tag;
            if (type == itemType) {
                return item;
            }
        }

        return null;
    }

    @Override
    public void setDisabledFlags(int disabledFlags) {
        // this call may come from outside
        // check if we already have a navigation slice to manipulate
        if (mNavigationSlice != null) {
            setDisabledFlags(disabledFlags, false);
        } else {
            mDisabledFlags = disabledFlags;
        }
    }

    protected void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);

        PieItem item = findItem(ButtonType.BACK);
        if (item != null) {
            item.show(!disableBack);
        }
        item = findItem(ButtonType.HOME);
        if (item != null) {
            item.show(!disableHome);
        }
        item = findItem(ButtonType.RECENT);
        if (item != null) {
            item.show(!disableRecent);
        }
        item = findItem(ButtonType.SEARCH);
        if (item != null) {
            item.show(!disableRecent && !disableSearch);
        }
        // enable searchlight when nothing except search is enabled
        if (mSearchLight != null) {
            mSearchLight.show(disableHome && disableRecent && disableBack && !disableSearch);
        }
        setMenuVisibility(mShowMenu);
    }

    @Override
    public void setMenuVisibility(boolean showMenu) {
        // this call may come from outside
        if (mMenuButton != null) {
            final boolean disableRecent = ((mDisabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
            mMenuButton.show(showMenu && !disableRecent);
        }

        mShowMenu = showMenu;
    }

    @Override
    public void onSnap(Position position) {
        if (position == mPosition) {
            return;
        }

        doHapticTriggerFeedback();

        if (DEBUG) {
            Slog.d(TAG, "onSnap from " + position.name());
        }

        int triggerSlots = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_GRAVITY, Position.BOTTOM.FLAG);

        triggerSlots = triggerSlots & ~mPosition.FLAG | position.FLAG;

        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.PIE_GRAVITY, triggerSlots);
    }

    @Override
    public void onClick(PieItem item) {
        ButtonType type = (ButtonType) item.tag;

        // provide the same haptic feedback as if a virtual key is pressed
        mPieContainer.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        switch (type) {
            case BACK:
                injectKeyDelayed(KeyEvent.KEYCODE_BACK);
                break;
            case HOME:
                injectKeyDelayed(KeyEvent.KEYCODE_HOME);
                break;
            case MENU:
                injectKeyDelayed(KeyEvent.KEYCODE_MENU);
                break;
            case RECENT:
                if (mStatusBar != null) {
                    mStatusBar.toggleRecentApps();
                }
                break;
            case SEARCH:
            case SEARCHLIGHT:
                launchAssistAction(type == ButtonType.SEARCHLIGHT);
                break;
        }
    }

    private void doHapticTriggerFeedback() {
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            return;
        }

        int hapticSetting = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1, UserHandle.USER_CURRENT);
        if (hapticSetting != 0) {
            mVibrator.vibrate(5);
        }
    }

    private void launchAssistAction(boolean force) {
        boolean isKeyguardShowing = false;
        try {
            isKeyguardShowing = mWm.isKeyguardLocked();
        } catch (RemoteException e) {
            // oh damn ...
        }

        if (isKeyguardShowing && force) {
            // Have keyguard show the bouncer and launch the activity if the user succeeds.
            try {
                mWm.showAssistant();
            } catch (RemoteException e) {
                // too bad, so sad...
            }
        } else {
            Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                    .getAssistIntent(mContext, UserHandle.USER_CURRENT);

            if (intent != null) {
                try {
                    ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext,
                            R.anim.search_launch_enter, R.anim.search_launch_exit);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivityAsUser(intent, opts.toBundle(),
                            new UserHandle(UserHandle.USER_CURRENT));
                } catch (ActivityNotFoundException ignored) {
                    // fall through
                }
            }
        }
    }

    public boolean isShowing() {
        return mPieContainer != null && mPieContainer.isShowing();
    }

    public boolean isSearchLightEnabled() {
        return mSearchLight != null && (mSearchLight.flags & PieDrawable.VISIBLE) != 0;
    }

    public String getOperatorState() {
        if (!mHasTelephony) {
            return null;
        }
        if (mServiceState == null || mServiceState.getState() == ServiceState.STATE_OUT_OF_SERVICE) {
            return mContext.getString(R.string.pie_phone_status_no_service);
        }
        if (mServiceState.getState() == ServiceState.STATE_POWER_OFF) {
            return mContext.getString(R.string.pie_phone_status_airplane_mode);
        }
        if (mServiceState.isEmergencyOnly()) {
            return mContext.getString(R.string.pie_phone_status_emergency_only);
        }
        return mServiceState.getOperatorAlphaLong();
    }

    public String getBatteryLevel() {
        if (mBatteryStatus == BatteryManager.BATTERY_STATUS_FULL) {
            return mContext.getString(R.string.pie_battery_status_full);
        }
        if (mBatteryStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
            return mContext.getString(R.string.pie_battery_status_charging, mBatteryLevel);
        }
        return mContext.getString(R.string.pie_battery_status_discharging, mBatteryLevel);
    }
}
