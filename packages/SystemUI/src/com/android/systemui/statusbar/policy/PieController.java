/*
 * Copyright (C) 2013 Jens Doll for the CyanogenMod Project
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

import java.text.SimpleDateFormat;
import java.util.Date;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;

import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.NotificationManager;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
import android.util.Slog;
import android.view.HapticFeedbackConstants;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.android.systemui.statusbar.pie.PieItem;
import com.android.systemui.statusbar.pie.PieLayout;
import com.android.systemui.statusbar.pie.PieLayout.PieDrawable;
import com.android.systemui.statusbar.pie.PieLayout.PieSlice;
import com.android.systemui.statusbar.pie.PieSliceContainer;
import com.android.systemui.statusbar.pie.PieSysInfo;

/**
 * Controller class for the default pie control.
 * <p>
 * This class is responsible for setting up the pie control, activating it, and defining and executing the
 * actions that can be triggered by the pie control.
 */
public class PieController implements BaseStatusBar.NavigationHintCallback, BaseStatusBar.MenuVisibilityCallback, PieLayout.OnSnapListener, PieItem.PieOnClickListener {
    public static final String TAG = "PieController";
    public static final boolean DEBUG = false;

    public static final String BUTTON_BACK = "##back##";
    public static final String BUTTON_HOME = "##home##";
    public static final String BUTTON_RECENT = "##recent##";
    public static final String BUTTON_MENU = "##menu##";
    public static final String BUTTON_SEARCH = "##search##";
    public static final String BUTTON_SEARCHLIGHT = "##light##";


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
    private boolean mTelephony;
    private int mBatteryLevel;

    // all pie slices that are managed by the controller
    private PieSliceContainer mNavigationSlice;
    private PieSysInfo mSysInfo;
    private PieItem mMenuButton;
    private PieItem mSearchLight;

    private int mNavigationIconHints = 0;
    private int mDisabledFlags = 0;
    private Drawable mBackIcon;
    private Drawable mBackAltIcon;

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
            if (!active)
                return false;

            // Unroll the complete logic here - we want to be fast and out of the
            // event chain as fast as possible.
            boolean loaded = false;
            switch (position) {
                case LEFT:
                    if (initialY - y < sDistance && y - initialY < sDistance) {
                        if (x - initialX > sDistance) loaded = true; else return false;
                    }
                    break;
                case BOTTOM:
                    if (initialX - x < sDistance && x - initialX < sDistance) {
                        if (initialY - y > sDistance) loaded = true; else return false;
                    }
                    break;
                case TOP:
                    if (initialX - x < sDistance && x - initialX < sDistance) {
                        if (y - initialY > sDistance) loaded = true; else return false;
                    }
                    break;
                case RIGHT:
                    if (initialY - y < sDistance && y - initialY < sDistance) {
                        if (initialX - x > sDistance) loaded = true; else return false;
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
                            KeyEvent.ACTION_DOWN, m.arg1, 0), InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
                    inputManager.injectInputEvent(new KeyEvent(eventTime - 50, eventTime - 25,
                            KeyEvent.ACTION_UP, m.arg1, 0), InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);

                    break;
            }
        }
    }
    private H mHandler = new H();

    private void injectKeyDelayed(int keycode){
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

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel = intent.getIntExtra("level", 0);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (mPieContainer != null) {
                    mPieContainer.exit();
                }
            }
        }
    };

    public PieController(Context context) {
        mContext = context;

        mVibrator = (Vibrator)mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
        mTelephony = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);

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

        if (DEBUG)
            Slog.d(TAG, "Attaching to container: " + container);

        mPieContainer.setOnSnapListener(this);

        final Resources res = mContext.getResources();

        // construct navbar slice
        int inner = res.getDimensionPixelSize(R.dimen.pie_navbar_radius);
        int outer = inner + res.getDimensionPixelSize(R.dimen.pie_navbar_height);
        mNavigationSlice = new PieSliceContainer(mPieContainer, PieSlice.IMPORTANT | PieDrawable.DISPLAY_ALL);
        mNavigationSlice.setGeometry(START_ANGLE, 180 - 2 * EMPTY_ANGLE, inner, outer);
        setupNavigationItems();
        mPieContainer.addSlice(mNavigationSlice);

        // construct sysinfo slice
        inner = res.getDimensionPixelSize(R.dimen.pie_sysinfo_radius);
        outer = inner + res.getDimensionPixelSize(R.dimen.pie_sysinfo_height);
        mSysInfo = new PieSysInfo(mContext, mPieContainer, this, /* not IMPORTANT */ PieDrawable.DISPLAY_NOT_AT_TOP);
        mSysInfo.setGeometry(START_ANGLE, 180 - 2 * EMPTY_ANGLE, inner, outer);
        mPieContainer.addSlice(mSysInfo);

        // start listening for changes
        mSettingsObserver.observe();
        mSettingsObserver.observe();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    private void setupNavigationItems() {
        int minimumImageSize = (int)mContext.getResources().getDimension(R.dimen.pie_item_size);

        mNavigationSlice.clear();
        mNavigationSlice.addItem(constructItem(2, BUTTON_BACK, R.drawable.ic_sysbar_back, minimumImageSize));
        mNavigationSlice.addItem(constructItem(2, BUTTON_HOME, R.drawable.ic_sysbar_home, minimumImageSize));
        mNavigationSlice.addItem(constructItem(2, BUTTON_RECENT, R.drawable.ic_sysbar_recent, minimumImageSize));
        if (Settings.System.getInt(mContext.getContentResolver(), Settings.System.PIE_SEARCH, 0) == 1) {
            mNavigationSlice.addItem(constructItem(1, BUTTON_SEARCH, R.drawable.ic_sysbar_search_side, minimumImageSize));
        }
        // search light has a width of 6 to take the complete space that normaly BACk HOME RECENT would occupy
        mSearchLight = constructItem(6, BUTTON_SEARCHLIGHT, R.drawable.search_light, minimumImageSize);
        mNavigationSlice.addItem(mSearchLight);
        mMenuButton = constructItem(1, BUTTON_MENU, R.drawable.ic_sysbar_menu, minimumImageSize);
        mNavigationSlice.addItem(mMenuButton);

        setNavigationIconHints(mNavigationIconHints, true);
    }

    private PieItem constructItem(int width, String name, int image, int minimumImageSize) {
        ImageView view = new ImageView(mContext);
        view.setImageResource(image);
        view.setMinimumWidth(minimumImageSize);
        view.setMinimumHeight(minimumImageSize);
        LayoutParams lp = new LayoutParams(minimumImageSize, minimumImageSize);
        view.setLayoutParams(lp);
        PieItem item = new PieItem(mContext, mPieContainer, width, name, view);
        item.setOnClickListener(this);
        return item;
    }

    public void activateFromTrigger(View view, MotionEvent event, Position position) {
        if (mPieContainer != null && !isShowing()) {
            mPosition = position;
            Point center = new Point((int)(event.getRawX()), 
                    (int)(event.getRawY()));
            mPieContainer.activate(center, position);
            mPieContainer.invalidate();
        }
    }

    @Override
    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;

        if (DEBUG) Slog.v(TAG, "Pie navigation hints: " + hints);

        mNavigationIconHints = hints;

        PieItem item = mNavigationSlice.findItem(BUTTON_HOME);
        if (item != null) {
            item.setAlpha(
                    (0 != (hints & StatusBarManager.NAVIGATION_HINT_HOME_NOP)) ? 0.5f : 1.0f);
        }
        item = mNavigationSlice.findItem(BUTTON_RECENT);
        if (item != null) {
            item.setAlpha(
                    (0 != (hints & StatusBarManager.NAVIGATION_HINT_RECENT_NOP)) ? 0.5f : 1.0f);
        }
        item = mNavigationSlice.findItem(BUTTON_BACK);
        if (item != null) {
            item.setAlpha(
                    (0 != (hints & StatusBarManager.NAVIGATION_HINT_BACK_NOP)) ? 0.5f : 1.0f);
            item.setImageDrawable(
                    (0 != (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT))
                    ? mBackAltIcon : mBackIcon);
        }
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);

        PieItem item = mNavigationSlice.findItem(BUTTON_BACK);
        if (item != null) item.show(!disableBack);
        item = mNavigationSlice.findItem(BUTTON_HOME);
        if (item != null) item.show(!disableHome);
        item = mNavigationSlice.findItem(BUTTON_RECENT);
        if (item != null) item.show(!disableRecent);
        if (mMenuButton != null) mMenuButton.show(!disableRecent);
        item = mNavigationSlice.findItem(BUTTON_SEARCH);
        if (item != null) item.show(!disableRecent && !disableSearch);
        // enable searchlight when nothing except search is enabled
        mSearchLight.show(disableHome && disableRecent && disableBack && !disableSearch);
    }

    @Override
    public void setMenuVisibility(boolean showMenu) {
        if (mMenuButton != null)
            mMenuButton.show(showMenu);
    }

    @Override
    public void onSnap(Position position) {
        if (position == mPosition)
            return;

        mVibrator.vibrate(3);

        if (DEBUG)
            Slog.d(TAG, "onSnap from " + position.name());

        int triggerSlots = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_GRAVITY, (0x01<<1));

        triggerSlots = triggerSlots & ~mPosition.FLAG | position.FLAG;

        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.PIE_GRAVITY, triggerSlots);
    }

    @Override
    public void OnClick(PieItem item, String name) {
        // provide the same haptic feedback as if a virtual key is pressed
        mPieContainer.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        if (name.equals(BUTTON_BACK)) {
            injectKeyDelayed(KeyEvent.KEYCODE_BACK);
        } else if (name.equals(BUTTON_HOME)) {
            injectKeyDelayed(KeyEvent.KEYCODE_HOME);
        } else if (name.equals(BUTTON_MENU)) {
            injectKeyDelayed(KeyEvent.KEYCODE_MENU);
        } else if (name.equals(BUTTON_RECENT)) {
            if (mStatusBar != null)
                mStatusBar.toggleRecentApps();
        } else if (name.equals(BUTTON_SEARCH) || name.equals(BUTTON_SEARCHLIGHT)) {
            launchAssistAction(name.equals(BUTTON_SEARCHLIGHT));
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

            if(intent != null) {
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

    public int getBatteryLevel() {
        return mBatteryLevel;
    }

    public String getBatteryLevelReadable() {
        return mContext.getString(R.string.battery_low_percent_format, mBatteryLevel).toUpperCase();
    }

    public boolean hasTelephony() {
        return mTelephony;
    }

}
