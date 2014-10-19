/*
 * Copyright (C) 2010 ParanoidAndroid Project
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

package com.android.systemui.statusbar;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.ResolveInfo;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.Process;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.PanelBar;
import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.statusbar.PieControl.OnNavButtonPressedListener;
import com.android.internal.util.slim.TorchConstants;
import com.android.internal.util.omni.OmniSwitchConstants;

import java.util.List;

public class PieControlPanel extends FrameLayout implements StatusBarPanel, OnNavButtonPressedListener {

    private final static String SysUIPackage = "com.android.systemui";

    private Handler mHandler;
    private boolean mShowing;
    private boolean mMenuButton;
    private PieControl mPieControl;
    private int mInjectKeycode;
    private long mDownTime;
    private Context mContext;
    private int mOrientation;
    private int mWidth;
    private int mHeight;
    private View mTrigger;
    private WindowManager mWindowManager;
    private Display mDisplay;
    private KeyguardManager mKeyguardManger;

    // ScreenShot
    private final Object mScreenshotLock = new Object();
    private ServiceConnection mScreenshotConnection = null;

    ViewGroup mContentFrame;
    Rect mContentArea = new Rect();

    private BaseStatusBar mStatusBar;

    public PieControlPanel(Context context) {
        this(context, null);
    }

    public PieControlPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mKeyguardManger = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mPieControl = new PieControl(context, this);
        mPieControl.setOnNavButtonPressedListener(this);
        mOrientation = Gravity.BOTTOM;
        mMenuButton = false;
    }

    public boolean currentAppUsesMenu() {
        return mMenuButton;
    }

    public void setMenu(boolean state) {
        mMenuButton = state;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public int getDegree() {
        switch (mOrientation) {
            case Gravity.LEFT:
                return 180;
            case Gravity.TOP:
                return -90;
            case Gravity.RIGHT:
                return 0;
            case Gravity.BOTTOM:
                return 90;
        }
        return 0;
    }

    public BaseStatusBar getBar() {
        return mStatusBar;
    }

    public void animateCollapsePanels() {
        mPieControl.getPieMenu().getStatusPanel().hidePanels(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mPieControl.onTouchEvent(event);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onAttachedToWindow () {
        super.onAttachedToWindow();
    }

    static private int[] gravityArray = {
            Gravity.BOTTOM, Gravity.LEFT, Gravity.TOP, Gravity.RIGHT, Gravity.BOTTOM, Gravity.LEFT
    };

    static public int findGravityOffset(int gravity) {
        for (int gravityIndex = 1; gravityIndex < gravityArray.length - 2; gravityIndex++) {
            if (gravity == gravityArray[gravityIndex])
                return gravityIndex;
        }
        return 4;
    }

    public void bumpConfiguration() {
        if (Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PIE_STICK, 0, UserHandle.USER_CURRENT) != 0); {

            // Get original offset
            int gravityIndex = findGravityOffset(convertPieGravitytoGravity(mStatusBar.mPieGravity));

            // Orient Pie to that place
            reorient(gravityArray[gravityIndex], false);

            // Now re-orient it for landscape orientation
            switch (mDisplay.getRotation()) {
                case Surface.ROTATION_270:
                    reorient(gravityArray[gravityIndex + 1], false);
                    break;
                case Surface.ROTATION_90:
                    reorient(gravityArray[gravityIndex - 1], false);
                    break;
            }
        }
        show(false);
        if (mPieControl != null)
            mPieControl.onPieConfigurationChanged();
    }

    public void init(Handler h, BaseStatusBar statusbar, View trigger, int orientation) {
        mHandler = h;
        mStatusBar = (BaseStatusBar) statusbar;
        mTrigger = trigger;
        mOrientation = orientation;
        mPieControl.init();
    }

    static public int convertGravitytoPieGravity(int gravity) {
        switch (gravity) {
            case Gravity.LEFT:
                return 0;
            case Gravity.TOP:
                return 1;
            case Gravity.RIGHT:
                return 2;
            default:
                return 3;
        }
    }

    static public int convertPieGravitytoGravity(int gravity) {
        switch (gravity) {
            case 0:
                return Gravity.LEFT;
            case 1:
                return Gravity.TOP;
            case 2:
                return Gravity.RIGHT;
            default:
                return Gravity.BOTTOM;
        }
    }

    public void reorient(int orientation, boolean storeSetting) {
        mOrientation = orientation;
        mWindowManager.removeView(mTrigger);
        mWindowManager.addView(mTrigger, 
                mStatusBar.pieGetTriggerLayoutParams(mContext, mOrientation));
        if (storeSetting) {
            int gravityOffset = mOrientation;
            if (mStatusBar.mPieStick) {

                gravityOffset = findGravityOffset(mOrientation);
                switch (mDisplay.getRotation()) {
                    case Surface.ROTATION_270:
                        gravityOffset = gravityArray[gravityOffset - 1];
                        break;
                    case Surface.ROTATION_90:
                        gravityOffset = gravityArray[gravityOffset + 1];
                        break;
                    default:
                        gravityOffset = mOrientation;
                        break;
                }
            }
            Settings.System.putIntForUser(mContext.getContentResolver(),
                    Settings.System.PIE_GRAVITY, convertGravitytoPieGravity(gravityOffset), UserHandle.USER_CURRENT);
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mContentFrame = (ViewGroup)findViewById(R.id.content_frame);
        setWillNotDraw(false);
        mPieControl.setIsAssistantAvailable(getAssistIntent() != null);
        mPieControl.attachToContainer(this);
        mPieControl.forceToTop(this);
        show(false);
    }

    public boolean isShowing() {
        return mShowing;
    }

    public PointF getSize() {
        return new PointF(mWidth, mHeight);
    }

    public void show(boolean show) {
        mShowing = show;
        setVisibility(show ? View.VISIBLE : View.GONE);
        mPieControl.show(show);
    }

    // verticalPos == -1 -> center PIE
    public void show(int verticalPos) {
        mShowing = true;
        setVisibility(View.VISIBLE);
        Point outSize = new Point(0,0);
        WindowManager windowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealSize(outSize);
        mWidth = outSize.x;
        mHeight = outSize.y;
        switch(mOrientation) {
            case Gravity.LEFT:
                mPieControl.setCenter(0, (verticalPos != -1 ? verticalPos : mHeight / 2));
                break;
            case Gravity.TOP:
                mPieControl.setCenter((verticalPos != -1 ? verticalPos : mWidth / 2), 0);
                break;
            case Gravity.RIGHT:
                mPieControl.setCenter(mWidth, (verticalPos != -1 ? verticalPos : mHeight / 2));
                break;
            case Gravity.BOTTOM:
                mPieControl.setCenter((verticalPos != -1 ? verticalPos : mWidth / 2), mHeight);
                break;
        }
        mPieControl.show(true);
    }

    public boolean isInContentArea(int x, int y) {
        mContentArea.left = mContentFrame.getLeft() + mContentFrame.getPaddingLeft();
        mContentArea.top = mContentFrame.getTop() + mContentFrame.getPaddingTop();
        mContentArea.right = mContentFrame.getRight() - mContentFrame.getPaddingRight();
        mContentArea.bottom = mContentFrame.getBottom() - mContentFrame.getPaddingBottom();

        return mContentArea.contains(x, y);
    }

    public void onNavButtonPressed(String buttonName) {
        if (buttonName.equals(PieControl.BACK_BUTTON)) {
            injectKeyDelayed(KeyEvent.KEYCODE_BACK);
        } else if (buttonName.equals(PieControl.HOME_BUTTON)) {
            injectKeyDelayed(KeyEvent.KEYCODE_HOME);
        } else if (buttonName.equals(PieControl.MENU_BUTTON)) {
            injectKeyDelayed(KeyEvent.KEYCODE_MENU);
        } else if (buttonName.equals(PieControl.RECENT_BUTTON)) {
            boolean mOmniSwitch = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.PIE_OMNISWITCH, 0, UserHandle.USER_CURRENT) == 1;
            if (mOmniSwitch) {
                Intent omniswitch = new Intent(OmniSwitchConstants.ACTION_TOGGLE_OVERLAY);
                mContext.sendBroadcastAsUser(omniswitch, new UserHandle(UserHandle.USER_CURRENT));  
            } else {
                mStatusBar.toggleRecentApps();
            }
        } else if (buttonName.equals(PieControl.SEARCH_BUTTON)) {
            launchAssistAction();
        } else if (buttonName.equals(PieControl.LAST_APP_BUTTON)) {
            toggleLastApp();
        } else if (buttonName.equals(PieControl.SCREENSHOT_BUTTON)) {
            takeScreenshot();
        } else if (buttonName.equals(PieControl.POWER_BUTTON)) {
            injectKeyDelayed(KeyEvent.KEYCODE_POWER);
        } else if (buttonName.equals(PieControl.KILL_TASK_BUTTON)) {
            KillTask mKillTask = new KillTask(mContext);
            mHandler.post(mKillTask);
        } else if (buttonName.equals(PieControl.APP_WINDOW_BUTTON)) {
            Intent appWindow = new Intent();
            appWindow.setAction("com.android.systemui.ACTION_SHOW_APP_WINDOW");
            mContext.sendBroadcast(appWindow);
        } else if (buttonName.equals(PieControl.ACT_NOTIF_BUTTON)) {
            try {
                IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).expandNotificationsPanel();
            } catch (RemoteException e) {
                // A RemoteException is like a cold
                // Let's hope we don't catch one!
            }
        } else if (buttonName.equals(PieControl.ACT_QS_BUTTON)) {
            try {
                IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).toggleQSShade();
            } catch (RemoteException e) {
                // wtf is this
            }
        } else if (buttonName.equals(PieControl.TORCH_BUTTON)) {
                Intent torch = new Intent(TorchConstants.ACTION_TOGGLE_STATE);
                torch.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                mContext.sendBroadcastAsUser(torch, new UserHandle(UserHandle.USER_CURRENT));
        } else if (buttonName.equals(PieControl.GESTURE_BUTTON)) {
                Intent gesture = new Intent(Intent.TOGGLE_GESTURE_ACTIONS);
                mContext.sendBroadcastAsUser(gesture, new UserHandle(UserHandle.USER_CURRENT));
        }
    }

    private Intent getAssistIntent() {
        Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, true, UserHandle.USER_CURRENT);
        return intent;
    }

    private void launchAssistAction() {
        Intent intent = getAssistIntent();
        if(intent != null) {
            try {
                ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext,
                        R.anim.search_launch_enter, R.anim.search_launch_exit);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivityAsUser(intent, opts.toBundle(),
                        new UserHandle(UserHandle.USER_CURRENT));
            } catch (ActivityNotFoundException e) {
            }
        }
    }

    private void toggleLastApp() {
        int lastAppId = 0;
        int looper = 1;
        String packageName;
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        final ActivityManager am = (ActivityManager) mContext
                .getSystemService(Activity.ACTIVITY_SERVICE);
        String defaultHomePackage = "com.android.launcher";
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
        if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
            defaultHomePackage = res.activityInfo.packageName;
        }
        List <ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(5);
        // lets get enough tasks to find something to switch to
        // Note, we'll only get as many as the system currently has - up to 5
        while ((lastAppId == 0) && (looper < tasks.size())) {
            packageName = tasks.get(looper).topActivity.getPackageName();
            if (!packageName.equals(defaultHomePackage) && !packageName.equals("com.android.systemui")) {
                lastAppId = tasks.get(looper).id;
            }
            looper++;
        }
        if (lastAppId != 0) {
            am.moveTaskToFront(lastAppId, am.MOVE_TASK_NO_USER_ACTION);
        }
    }

    public static class KillTask implements Runnable {
         private Context mContext;
         public KillTask(Context context) {
             this.mContext = context;
         }
         public void run() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            final ActivityManager am = (ActivityManager) mContext
                    .getSystemService(Activity.ACTIVITY_SERVICE);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            String packageName = am.getRunningTasks(1).get(0).topActivity.getPackageName();
            if (SysUIPackage.equals(packageName))
                return; // don't kill SystemUI
            if (!defaultHomePackage.equals(packageName)) {
                am.forceStopPackage(packageName);
                Toast.makeText(mContext, R.string.app_killed_message, Toast.LENGTH_SHORT).show();
            }
        }
     }

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override
        public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    mContext.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                }
            }
        }
    };

    private void takeScreenshot() {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null) {
                return;
            }
            ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.screenshot.TakeScreenshotService");
            Intent intent = new Intent();
            intent.setComponent(cn);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(HDL.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        HDL.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;
                        /*
                         * remove for the time being if (mStatusBar != null &&
                         * mStatusBar.isVisibleLw()) msg.arg1 = 1; if
                         * (mNavigationBar != null &&
                         * mNavigationBar.isVisibleLw()) msg.arg2 = 1;
                         */

                        /* wait for the dialog box to close */
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                        }

                        /* take the screenshot */
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            };
            if (mContext.bindService(intent, conn, mContext.BIND_AUTO_CREATE)) {
                mScreenshotConnection = conn;
                HDL.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }

    private Handler HDL = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {

            }
        }
    };

    public void injectKeyDelayed(int keycode){
        mInjectKeycode = keycode;
        mDownTime = SystemClock.uptimeMillis();
        mHandler.removeCallbacks(onInjectKeyDelayed);
        mHandler.postDelayed(onInjectKeyDelayed, 100);
    }

    final Runnable onInjectKeyDelayed = new Runnable() {
        public void run() {
            final long eventTime = SystemClock.uptimeMillis();
            InputManager.getInstance().injectInputEvent(
                    new KeyEvent(mDownTime, eventTime - 100, KeyEvent.ACTION_DOWN, mInjectKeycode, 0),
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            InputManager.getInstance().injectInputEvent(
                    new KeyEvent(mDownTime, eventTime - 50, KeyEvent.ACTION_UP, mInjectKeycode, 0),
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    public boolean getKeyguardStatus() {
        return mKeyguardManger.isKeyguardLocked() && mKeyguardManger.isKeyguardSecure() || mKeyguardManger.isKeyguardLocked() && !mKeyguardManger.isKeyguardSecure();
    }
}
