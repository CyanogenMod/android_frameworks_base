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

package com.android.systemui.statusbar;

import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.util.Slog;

import com.android.systemui.R;

public class StatusBarView extends FrameLayout {
    private static final String TAG = "StatusBarView";

    static final int DIM_ANIM_TIME = 400;

    StatusBarService mService;
    boolean mTracking;
    int mStartX, mStartY;
    ViewGroup mNotificationIcons;
    ViewGroup mStatusIcons;
    View mDate;
    FixedSizeDrawable mBackground;
    //set up statusbar buttons - quiet a lot for that awesome design!
    ViewGroup mSoftButtons;
    ImageButton mHomeButton;
    ImageButton mMenuButton;
    ImageButton mBackButton;
    ImageButton mSearchButton;
    ImageButton mQuickNaButton;
    ImageButton mHideButton;
    ImageButton mEdgeLeft;
    ImageButton mEdgeRight;
    ImageButton mSeperator1;
    ImageButton mSeperator2;
    ImageButton mSeperator3;
    ImageButton mSeperator4;
    ImageButton mSeperator5;

    boolean mShowSoftButtons;  // toggled by config.xml
    boolean mIsBottom;   // this and below booleans toggled by system settings from cmparts
    boolean mIsLeft;
    boolean mShowHome;
    boolean mShowMenu;
    boolean mShowBack;
    boolean mShowSearch;
    boolean mShowQuickNa;
    ViewGroup mIcons;

    //virtual button presses - double defined in StatusBarView and PhoneWindowManager
    public static final int KEYCODE_VIRTUAL_HOME_LONG=KeyEvent.getMaxKeyCode()+1;
    public static final int KEYCODE_VIRTUAL_BACK_LONG=KeyEvent.getMaxKeyCode()+2;

    // fullscreen handling
    ActivityManager mActivityManager;
    RunningAppProcessInfo mFsCallerProcess;
    ComponentName mFsCallerActivity;
    Intent mFsForceIntent;

    Handler mHandler;

    class FullscreenReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent){
            onFullscreenAttempt();
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_BOTTOM), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTONS_LEFT), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTON_SHOW_HOME), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTON_SHOW_MENU), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTON_SHOW_BACK), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTON_SHOW_SEARCH), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTON_SHOW_QUICK_NA), false, this);
            onChange(true);
        }

        @Override
        public void onChange(boolean selfChange) {
            ContentResolver resolver = mContext.getContentResolver();

            // find a sane way to get the default behavior, in case cmparts wasnt started yet.
            mIsBottom = (Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_BOTTOM, 0) == 1);
            mIsLeft = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTONS_LEFT, 1) == 1);
            mShowHome = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTON_SHOW_HOME, 1) == 1);
            mShowMenu = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTON_SHOW_MENU, 1) == 1);
            mShowBack = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTON_SHOW_BACK, 1) == 1);
            mShowSearch = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTON_SHOW_SEARCH, 0) == 1);
            mShowQuickNa = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTON_SHOW_QUICK_NA, 1) == 1);
            updateSoftButtons();
            updateQuickNaImage();
        }
    }

    public StatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIcons = (ViewGroup)findViewById(R.id.icons);
        mNotificationIcons = (ViewGroup)findViewById(R.id.notificationIcons);
        mStatusIcons = (ViewGroup)findViewById(R.id.statusIcons);
        mDate = findViewById(R.id.date);

        mBackground = new FixedSizeDrawable(mDate.getBackground());
        mBackground.setFixedBounds(0, 0, 0, 0);
        mDate.setBackgroundDrawable(mBackground);

        // load config to determine if we want statusbar buttons
        try {
            mShowSoftButtons = mContext.getResources().getBoolean(
                    R.bool.config_statusbar_buttons);
        } catch (Exception e) {
                  mShowSoftButtons = false;
        }

        /**
         * All this is skipped if config_statusbar_buttons is false or missing in config.xml
         * If true then add statusbar buttons and set listeners and intents
         */
        if (mShowSoftButtons) {
            mHomeButton = (ImageButton)findViewById(R.id.status_home);
            mHomeButton.setOnClickListener(
                new ImageButton.OnClickListener() {
                    public void onClick(View v) {
                        Slog.i(TAG, "Home clicked");
                        Intent setIntent = new Intent(Intent.ACTION_MAIN);
                        setIntent.addCategory(Intent.CATEGORY_HOME);
                        setIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        StatusBarView.this.getContext().startActivity(setIntent);
                    }
                }
            );
            mHomeButton.setOnLongClickListener(
                new ImageButton.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        simulateKeypress(KEYCODE_VIRTUAL_HOME_LONG);
                        return true;
                    }
                }
            );
            mMenuButton = (ImageButton)findViewById(R.id.status_menu);
            mMenuButton.setOnClickListener(
                new ImageButton.OnClickListener() {
                    public void onClick(View v) {
                        Slog.i(TAG, "Menu clicked");
                        StatusBarView.this.simulateKeypress(KeyEvent.KEYCODE_MENU);
                    }
                }
            );
            mBackButton = (ImageButton)findViewById(R.id.status_back);
            mBackButton.setOnClickListener(
                new ImageButton.OnClickListener() {
                    public void onClick(View v) {
                        Slog.i(TAG, "Back clicked");
                        StatusBarView.this.simulateKeypress(KeyEvent.KEYCODE_BACK);
                    }
                }
            );
            mBackButton.setOnLongClickListener(
                    new ImageButton.OnLongClickListener() {
                        public boolean onLongClick(View v) {
                            simulateKeypress(KEYCODE_VIRTUAL_BACK_LONG);
                            return true;
                        }
                    }
                );
            mSearchButton = (ImageButton)findViewById(R.id.status_search);
            mSearchButton.setOnClickListener(
                new ImageButton.OnClickListener() {
                    public void onClick(View v) {
                        Slog.i(TAG, "Search clicked");
                        StatusBarView.this.simulateKeypress(KeyEvent.KEYCODE_SEARCH);
                    }
                }
            );
            mSearchButton.setOnLongClickListener(
                    new ImageButton.OnLongClickListener() {
                        public boolean onLongClick(View v) {
                            // start custom app
                            boolean mCustomLongSearchAppToggle=(Settings.System.getInt(getContext().getContentResolver(),
                                    Settings.System.USE_CUSTOM_LONG_SEARCH_APP_TOGGLE, 0) == 1);

                            if(mCustomLongSearchAppToggle){
                                runCustomApp(Settings.System.getString(getContext().getContentResolver(),
                                    Settings.System.USE_CUSTOM_LONG_SEARCH_APP_ACTIVITY));
                            }
                            return true;
                        }
                    }
                );
            mQuickNaButton = (ImageButton)findViewById(R.id.status_quick_na);
            mQuickNaButton.setOnClickListener(
                new ImageButton.OnClickListener() {
                    public void onClick(View v) {
                        if(mService.mExpanded){
                            mService.animateCollapse(); // with regards to flawed sources. doesnt work without animating call. blame google (:
                            mService.performCollapse();
                        }else {
                            mService.performExpand();
                        }
                        Slog.i(TAG, "Quick Notification Area clicked");
                    }
                }
            );
            mHideButton = (ImageButton)findViewById(R.id.status_hide);
            mHideButton.setOnClickListener(
                new ImageButton.OnClickListener() {
                    public void onClick(View v) {
                        if(isStillActive(mFsCallerProcess, mFsCallerActivity))
                            mContext.sendBroadcast(mFsForceIntent);
                        Slog.i(TAG, "Fullscreen Hide clicked");
                    }
                }
            );
            mSoftButtons = (ViewGroup)findViewById(R.id.buttons);
            mEdgeLeft = (ImageButton)findViewById(R.id.status_edge_left);
            mEdgeRight = (ImageButton)findViewById(R.id.status_edge_right);
            mSeperator1 = (ImageButton)findViewById(R.id.status_sep1);
            mSeperator2 = (ImageButton)findViewById(R.id.status_sep2);
            mSeperator3 = (ImageButton)findViewById(R.id.status_sep3);
            mSeperator4 = (ImageButton)findViewById(R.id.status_sep4);
            mSeperator5 = (ImageButton)findViewById(R.id.status_sep5);

            // set up settings observer
            mHandler=new Handler();
            SettingsObserver settingsObserver = new SettingsObserver(mHandler);
            settingsObserver.observe();

            // catching fullscreen attempts
            FullscreenReceiver fullscreenReceiver = new FullscreenReceiver();
            mContext.registerReceiver(fullscreenReceiver, new IntentFilter("android.intent.action.FULLSCREEN_ATTEMPT"));
            mFsForceIntent=new Intent("android.intent.action.FORCE_FULLSCREEN");
        }
    }

    public void updateQuickNaImage(){
        if(getParent()==null)
            return;

        int resCollapsed=mIsBottom ? R.drawable.ic_statusbar_na_up_bottom : R.drawable.ic_statusbar_na_down_top;
        int resExpanded=mIsBottom ? R.drawable.ic_statusbar_na_down_bottom : R.drawable.ic_statusbar_na_up_top;
        int resUse=mService.mExpanded ? resExpanded : resCollapsed;

        mQuickNaButton.setBackgroundResource(resUse);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mService.onBarViewAttached();
        updateQuickNaImage();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mService.onBarViewDetached();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mService.updateExpandedViewPos(StatusBarService.EXPANDED_LEAVE_ALONE);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        // put the date date view quantized to the icons
        int oldDateRight = mDate.getRight();
        int newDateRight;

        newDateRight = getDateSize(mNotificationIcons, oldDateRight,
                getViewOffset(mNotificationIcons));
        if (newDateRight < 0) {
            int offset = getViewOffset(mStatusIcons);
            if (oldDateRight < offset) {
                newDateRight = oldDateRight;
            } else {
                newDateRight = getDateSize(mStatusIcons, oldDateRight, offset);
                if (newDateRight < 0) {
                    newDateRight = r;
                }
            }
        }
        int max = r - getPaddingRight();
        if (newDateRight > max) {
            newDateRight = max;
        }

        mDate.layout(mDate.getLeft(), mDate.getTop(), newDateRight, mDate.getBottom());
        mBackground.setFixedBounds(-mDate.getLeft(), -mDate.getTop(), (r-l), (b-t));
    }

    /**
     * Gets the left position of v in this view.  Throws if v is not
     * a child of this.
     */
    private int getViewOffset(View v) {
        int offset = 0;
        while (v != this) {
            offset += v.getLeft();
            ViewParent p = v.getParent();
            if (v instanceof View) {
                v = (View)p;
            } else {
                throw new RuntimeException(v + " is not a child of " + this);
            }
        }
        return offset;
    }

    private int getDateSize(ViewGroup g, int w, int offset) {
        final int N = g.getChildCount();
        for (int i=0; i<N; i++) {
            View v = g.getChildAt(i);
            int l = v.getLeft() + offset;
            int r = v.getRight() + offset;
            if (w >= l && w <= r) {
                return r;
            }
        }
        return -1;
    }

    /**
     * Ensure that, if there is no target under us to receive the touch,
     * that we process it ourself.  This makes sure that onInterceptTouchEvent()
     * is always called for the entire gesture.
     */
    @Override
    public boolean onTouchEvent(final MotionEvent event) {

        if(isEventInButton(mHomeButton, event)) {
            mHomeButton.onTouchEvent(event);
            return true;
        }
        if(isEventInButton(mMenuButton, event)) {
            mMenuButton.onTouchEvent(event);
            return true;
        }
        if(isEventInButton(mBackButton, event)) {
            mBackButton.onTouchEvent(event);
            return true;
        }
        if(isEventInButton(mSearchButton, event)) {
            mSearchButton.onTouchEvent(event);
            return true;
        }
        if(isEventInButton(mMenuButton, event)) {
            mQuickNaButton.onTouchEvent(event);
            return true;
        }

        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            mService.interceptTouchEvent(event);
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if(isEventInButton(mHomeButton, event)
            || isEventInButton(mMenuButton, event)
            || isEventInButton(mBackButton, event)
            || isEventInButton(mSearchButton, event)
            || isEventInButton(mQuickNaButton, event)) {
                return super.onInterceptTouchEvent(event);
            }
        return mService.interceptTouchEvent(event)
            ? true : super.onInterceptTouchEvent(event);
    }

    private boolean isEventInButton(final ImageButton button, final MotionEvent event) {
        return mShowSoftButtons && button != null
            && button.getLeft() <= event.getRawX()
            && button.getRight() >= event.getRawX()
            && button.getTop() <= event.getRawY()
            && button.getBottom() >= event.getRawY();
        }
    /**
     * Runnable to hold simulate a keypress.
     *
     * This is executed in a separate Thread to avoid blocking
     */
    private void simulateKeypress(final int keyCode) {
        new Thread( new KeyEventInjector( keyCode ) ).start();
    }

    private class KeyEventInjector implements Runnable {
        private int keyCode;

        KeyEventInjector(final int keyCode) {
            this.keyCode = keyCode;
        }

        public void run() {
            try {
                if(! (IWindowManager.Stub
                    .asInterface(ServiceManager.getService("window")))
                         .injectKeyEvent(
                              new KeyEvent(KeyEvent.ACTION_DOWN, keyCode), true) ) {
                                   Slog.w(TAG, "Key down event not injected");
                                   return;
                              }
                if(! (IWindowManager.Stub
                    .asInterface(ServiceManager.getService("window")))
                         .injectKeyEvent(
                             new KeyEvent(KeyEvent.ACTION_UP, keyCode), true) ) {
                                  Slog.w(TAG, "Key up event not injected");
                             }
           } catch(RemoteException ex) {
               Slog.w(TAG, "Error injecting key event", ex);
           }
        }
    }

    Runnable mHideButtonUpdater = new Runnable() {
        public void run() {
            boolean appStillForeground=false;

            // get foreground app delayed, so we get what we want
            if(mFsCallerProcess==null){
                mFsCallerProcess=getForegroundApp();
                Slog.i(TAG, "Fullscreen Attempt ProcessName: " + (mFsCallerProcess==null ? "null" : mFsCallerProcess.processName));
                mFsCallerActivity=getActivityForApp(mFsCallerProcess);
                Slog.i(TAG, "Fullscreen Attempt Top Activity: " + (mFsCallerActivity==null ? "null" : mFsCallerActivity.flattenToShortString()));
            }

            if(isStillActive(mFsCallerProcess, mFsCallerActivity))
                appStillForeground=true;

            if(appStillForeground)
                mHandler.postDelayed(mHideButtonUpdater, 500);
            else{
                mFsCallerProcess=null;
                mHideButton.setVisibility(View.GONE);
                mSeperator5.setVisibility(View.GONE);
                updateSoftButtons();
            }
        }
    };

    private boolean isStillActive(RunningAppProcessInfo process, ComponentName activity)
    {
        if(process==null || activity==null)
            return false;

        RunningAppProcessInfo currentFg=getForegroundApp();
        ComponentName currentActivity=getActivityForApp(currentFg);

        if(currentFg!=null && currentFg.processName.equals(process.processName) &&
                currentActivity!=null && currentActivity.compareTo(activity)==0)
            return true;

        return false;
    }

    private ComponentName getActivityForApp(RunningAppProcessInfo target){
        ComponentName result=null;
        ActivityManager.RunningTaskInfo info;

        if(target==null)
            return null;

        if(mActivityManager==null)
            mActivityManager = (ActivityManager)mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List <ActivityManager.RunningTaskInfo> l = mActivityManager.getRunningTasks(9999);
        Iterator <ActivityManager.RunningTaskInfo> i = l.iterator();

        while(i.hasNext()){
            info=i.next();
            if(info.baseActivity.getPackageName().equals(target.processName)){
                result=info.topActivity;
                break;
            }
        }

        return result;
    }

    private RunningAppProcessInfo getForegroundApp() {
        RunningAppProcessInfo result=null, info=null;

        if(mActivityManager==null)
            mActivityManager = (ActivityManager)mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List <RunningAppProcessInfo> l = mActivityManager.getRunningAppProcesses();
        Iterator <RunningAppProcessInfo> i = l.iterator();
        while(i.hasNext()){
            info = i.next();
            // skip phone and system activity, since they are always importance_foreground
            if(info.processName.equals("com.android.phone") || info.processName.equals("system"))
                continue;
            if(info.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND){
                result=info;
                break;
            }
        }

        return result;
    }

    private void onFullscreenAttempt()
    {
        mHideButton.setVisibility(View.VISIBLE);
        if(mShowQuickNa || mShowSearch || mShowBack || mShowMenu || mShowHome)
            mSeperator5.setVisibility(View.VISIBLE);
        updateSoftButtons();

        mFsCallerProcess=null;
        mHandler.postDelayed(mHideButtonUpdater, 500);
    }

    private void updateSoftButtons() {
        mIcons.removeView(mSoftButtons);
        mIcons.addView(mSoftButtons, mIsLeft ? 0 : mIcons.getChildCount());

        // toggle visibility of edges
        mEdgeLeft.setVisibility(mIsLeft ? View.GONE : View.VISIBLE);
        mEdgeRight.setVisibility(mIsLeft ? View.VISIBLE : View.GONE);

        // toggle visibility of buttons - at first, toggle all visible
        mHomeButton.setVisibility(View.VISIBLE);
        mSeperator1.setVisibility(View.VISIBLE);
        mMenuButton.setVisibility(View.VISIBLE);
        mSeperator2.setVisibility(View.VISIBLE);
        mBackButton.setVisibility(View.VISIBLE);
        mSeperator3.setVisibility(View.VISIBLE);
        mSearchButton.setVisibility(View.VISIBLE);
        mSeperator4.setVisibility(View.VISIBLE);
        mQuickNaButton.setVisibility(View.VISIBLE);

        // now toggle off unneeded stuff
        if(!mShowHome){
            mHomeButton.setVisibility(View.GONE);
            mSeperator1.setVisibility(View.GONE);
        }
        if(!mShowMenu){
            mMenuButton.setVisibility(View.GONE);
            mSeperator2.setVisibility(View.GONE);
        }
        if(!mShowBack){
            mBackButton.setVisibility(View.GONE);
            mSeperator3.setVisibility(View.GONE);
        }
        if(!mShowSearch){
            mSearchButton.setVisibility(View.GONE);
            mSeperator4.setVisibility(View.GONE);
        }
        if(!mShowQuickNa)
            mQuickNaButton.setVisibility(View.GONE);

        // adjust seperators
        if(!mShowQuickNa)
            mSeperator4.setVisibility(View.GONE);
        if(!mShowQuickNa && !mShowSearch)
            mSeperator3.setVisibility(View.GONE);
        if(!mShowQuickNa && !mShowSearch && !mShowBack)
            mSeperator2.setVisibility(View.GONE);
        if(!mShowQuickNa && !mShowSearch && !mShowBack && !mShowMenu)
            mSeperator1.setVisibility(View.GONE);
        // nothing displayed at all
        if(!mShowQuickNa && !mShowSearch && !mShowBack && !mShowMenu && !mShowHome && mHideButton.getVisibility()==View.GONE){
            mEdgeLeft.setVisibility(View.GONE);
            mEdgeRight.setVisibility(View.GONE);
        }

        // replace resources depending on top or bottom bar
        if(mIsBottom){
            mEdgeLeft.setBackgroundResource(R.drawable.ic_statusbar_edge_right_bottom);
            mHomeButton.setBackgroundResource(R.drawable.ic_statusbar_home_bottom);
            mSeperator1.setBackgroundResource(R.drawable.ic_statusbar_sep_bottom);
            mMenuButton.setBackgroundResource(R.drawable.ic_statusbar_menu_bottom);
            mSeperator2.setBackgroundResource(R.drawable.ic_statusbar_sep_bottom);
            mBackButton.setBackgroundResource(R.drawable.ic_statusbar_back_bottom);
            mSeperator3.setBackgroundResource(R.drawable.ic_statusbar_sep_bottom);
            mSearchButton.setBackgroundResource(R.drawable.ic_statusbar_search_bottom);
            mSeperator4.setBackgroundResource(R.drawable.ic_statusbar_sep_bottom);
            mQuickNaButton.setBackgroundResource(R.drawable.ic_statusbar_na_up_bottom);
            mEdgeRight.setBackgroundResource(R.drawable.ic_statusbar_edge_left_bottom);
        }else{
            mEdgeLeft.setBackgroundResource(R.drawable.ic_statusbar_edge_right_top);
            mHomeButton.setBackgroundResource(R.drawable.ic_statusbar_home_top);
            mSeperator1.setBackgroundResource(R.drawable.ic_statusbar_sep_top);
            mMenuButton.setBackgroundResource(R.drawable.ic_statusbar_menu_top);
            mSeperator2.setBackgroundResource(R.drawable.ic_statusbar_sep_top);
            mBackButton.setBackgroundResource(R.drawable.ic_statusbar_back_top);
            mSeperator3.setBackgroundResource(R.drawable.ic_statusbar_sep_top);
            mSearchButton.setBackgroundResource(R.drawable.ic_statusbar_search_top);
            mSeperator4.setBackgroundResource(R.drawable.ic_statusbar_sep_top);
            mQuickNaButton.setBackgroundResource(R.drawable.ic_statusbar_na_up_top);
            mEdgeRight.setBackgroundResource(R.drawable.ic_statusbar_edge_left_top);
        }
    }

    public int getSoftButtonsWidth() {
        if(!mShowSoftButtons)
            return 0;

        int ret=0;
        if(mShowHome)
            ret+=mHomeButton.getWidth();
        if(mShowMenu)
            ret+=mMenuButton.getWidth()+mSeperator1.getWidth();
        if(mShowBack)
            ret+=mBackButton.getWidth()+mSeperator2.getWidth();
        if(mShowSearch)
            ret+=mSearchButton.getWidth()+mSeperator3.getWidth();
        if(mShowQuickNa)
            ret+=mQuickNaButton.getWidth()+mSeperator4.getWidth();
        if(mHideButton.getVisibility()==View.VISIBLE)
            ret+=mHideButton.getWidth()+mSeperator5.getWidth();
        if(ret>0)
            ret+=mEdgeLeft.getWidth();

        return ret;
    }

    private void runCustomApp(String uri) {
        if (uri != null) {
            try {
                Intent i = Intent.parseUri(uri, 0);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                getContext().startActivity(i);
            } catch (URISyntaxException e) {

            } catch (ActivityNotFoundException e) {

            }
        }
    }
}
