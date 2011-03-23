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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.util.AttributeSet;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.util.Slog;
import com.android.systemui.R;
import com.android.systemui.statusbar.StatusBarService.SettingsObserver;

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
    //set up statusbar buttons
    ImageButton mStatusBarHomeButton;
    ImageButton mStatusBarBackButton;
    ImageButton mStatusBarMenuButton;
    ImageButton mStatusBarQuickNaButton;
    boolean mStatusBarButtons;
    boolean mSoftButtonsLeft;
    boolean mSoftButtonsShowHome;
    boolean mSoftButtonsShowMenu;
    boolean mSoftButtonsShowBack;
    boolean mSoftButtonsShowQuickNa;
    ViewGroup mIcons;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTONS_LEFT), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTON_SHOW_HOME), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTON_SHOW_MENU), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTON_SHOW_BACK), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SOFT_BUTTON_SHOW_QUICK_NA), false, this);
            onChange(true);
        }

        @Override
        public void onChange(boolean selfChange) {
            ContentResolver resolver = mContext.getContentResolver();

            // find a sane way to get the default behavior, in case cmparts wasnt started yet.
            mSoftButtonsLeft = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTONS_LEFT, 1) == 1);
            mSoftButtonsShowHome = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTON_SHOW_HOME, 1) == 1);
            mSoftButtonsShowMenu = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTON_SHOW_MENU, 1) == 1);
            mSoftButtonsShowBack = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTON_SHOW_BACK, 1) == 1);
            mSoftButtonsShowQuickNa = (Settings.System.getInt(resolver,
                    Settings.System.SOFT_BUTTON_SHOW_QUICK_NA, 1) == 1);
            updateSoftButtons();
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
            mStatusBarButtons = mContext.getResources().getBoolean(
                    R.bool.config_statusbar_buttons);
        } catch (Exception e) {
                  mStatusBarButtons = false;
        }

        /**
         * All this is skipped if config_statusbar_buttons is false or missing in config.xml
         * If true then add statusbar buttons and set listeners and intents
         */
        if (mStatusBarButtons) {
            mStatusBarHomeButton = (ImageButton)findViewById(R.id.status_home);
            mStatusBarHomeButton.setOnClickListener(
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
            mStatusBarMenuButton = (ImageButton)findViewById(R.id.status_menu);
            mStatusBarMenuButton.setOnClickListener(
                new ImageButton.OnClickListener() {
                    public void onClick(View v) {
                        Slog.i(TAG, "Menu clicked");
                        StatusBarView.this.simulateKeypress(KeyEvent.KEYCODE_MENU);
                    }
                }
            );
            mStatusBarBackButton = (ImageButton)findViewById(R.id.status_back);
            mStatusBarBackButton.setOnClickListener(
                new ImageButton.OnClickListener() {
                    public void onClick(View v) {
                        Slog.i(TAG, "Back clicked");
                        StatusBarView.this.simulateKeypress(KeyEvent.KEYCODE_BACK);
                    }
                }
            );
            mStatusBarQuickNaButton = (ImageButton)findViewById(R.id.status_quick_na);
            mStatusBarQuickNaButton.setOnClickListener(
                new ImageButton.OnClickListener() {
                    public void onClick(View v) {
                        if(mService.mExpanded){
                            mService.performCollapse();
                            mStatusBarQuickNaButton.setBackgroundResource(R.drawable.ic_statusbar_na_open);
                        }else {
                            mService.performExpand();
                            mService.mDateView.setVisibility(View.INVISIBLE);
                            mStatusBarQuickNaButton.setBackgroundResource(R.drawable.ic_statusbar_na_close);
                        }
                        Slog.i(TAG, "Quick Notification Area clicked");
                    }
                }
            );

            // set up settings observer
            mHandler=new Handler();
            SettingsObserver settingsObserver = new SettingsObserver(mHandler);
            settingsObserver.observe();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mService.onBarViewAttached();
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

        if(isEventInButton(mStatusBarHomeButton, event)) {
            mStatusBarHomeButton.onTouchEvent(event);
            return true;
        }
        if(isEventInButton(mStatusBarBackButton, event)) {
            mStatusBarBackButton.onTouchEvent(event);
            return true;
        }
        if(isEventInButton(mStatusBarMenuButton, event)) {
            mStatusBarMenuButton.onTouchEvent(event);
            return true;
        }
        if(isEventInButton(mStatusBarMenuButton, event)) {
            mStatusBarQuickNaButton.onTouchEvent(event);
            return true;
        }

        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            mService.interceptTouchEvent(event);
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if(isEventInButton(mStatusBarHomeButton, event)
            || isEventInButton(mStatusBarBackButton, event)
            || isEventInButton(mStatusBarMenuButton, event)
            || isEventInButton(mStatusBarQuickNaButton, event)) {
                return super.onInterceptTouchEvent(event);
            }
        return mService.interceptTouchEvent(event)
            ? true : super.onInterceptTouchEvent(event);
    }

    private boolean isEventInButton(final ImageButton button, final MotionEvent event) {
        return mStatusBarButtons && button != null
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

    private void updateSoftButtons() {
        mIcons.removeView(mStatusBarHomeButton);
        mIcons.removeView(mStatusBarMenuButton);
        mIcons.removeView(mStatusBarBackButton);
        mIcons.removeView(mStatusBarQuickNaButton);

        if(!mStatusBarButtons)
            return;

        if(mSoftButtonsLeft){
            if(mSoftButtonsShowQuickNa)
                mIcons.addView(mStatusBarQuickNaButton, 0);
            if(mSoftButtonsShowBack)
                mIcons.addView(mStatusBarBackButton, 0);
            if(mSoftButtonsShowMenu)
                mIcons.addView(mStatusBarMenuButton, 0);
            if(mSoftButtonsShowHome)
                mIcons.addView(mStatusBarHomeButton, 0);
        }else {
            if(mSoftButtonsShowHome)
                mIcons.addView(mStatusBarHomeButton, mIcons.getChildCount());
            if(mSoftButtonsShowMenu)
                mIcons.addView(mStatusBarMenuButton, mIcons.getChildCount());
            if(mSoftButtonsShowBack)
                mIcons.addView(mStatusBarBackButton, mIcons.getChildCount());
            if(mSoftButtonsShowQuickNa)
                mIcons.addView(mStatusBarQuickNaButton, mIcons.getChildCount());
        }
    }

    public int getSoftButtonsWidth() {
        if(!mStatusBarButtons)
            return 0;

        int ret=0;
        if(mSoftButtonsShowHome)
            ret+=mStatusBarHomeButton.getWidth();
        if(mSoftButtonsShowMenu)
            ret+=mStatusBarMenuButton.getWidth();
        if(mSoftButtonsShowBack)
            ret+=mStatusBarBackButton.getWidth();
        if(mSoftButtonsShowQuickNa)
            ret+=mStatusBarQuickNaButton.getWidth();

        return ret;
    }
}
