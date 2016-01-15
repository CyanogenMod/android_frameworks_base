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

package com.android.systemui.statusbar.policy;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.ThemeConfig;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.NavbarEditor;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK;

import cyanogenmod.power.PerformanceManager;

public class KeyButtonView extends ImageView {

    public static final int CURSOR_REPEAT_FLAGS = KeyEvent.FLAG_SOFT_KEYBOARD
            | KeyEvent.FLAG_KEEP_TOUCH_MODE;

    private int mContentDescriptionRes;
    private long mDownTime;
    private int mCode;
    private boolean mIsSmall;
    private int mTouchSlop;
    private boolean mSupportsLongpress = true;
    private boolean mInEditMode;
    private AudioManager mAudioManager;
    private boolean mGestureAborted;
    private boolean mPerformedLongClick;

    private PerformanceManager mPerf;

    private final Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (isPressed()) {
                // Log.d("KeyButtonView", "longpressed: " + this);
                if (mCode == KeyEvent.KEYCODE_DPAD_LEFT || mCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    sendEvent(KeyEvent.ACTION_UP, CURSOR_REPEAT_FLAGS,
                            System.currentTimeMillis(), false);
                    sendEvent(KeyEvent.ACTION_DOWN, CURSOR_REPEAT_FLAGS,
                            System.currentTimeMillis(), false);
                    postDelayed(mCheckLongPress, ViewConfiguration.getKeyRepeatDelay());
                } else if (isLongClickable()) {
                    // Just an old-fashioned ImageView
                    mPerformedLongClick = true;
                    performLongClick();
                } else {
                    sendEvent(KeyEvent.ACTION_DOWN, KeyEvent.FLAG_LONG_PRESS);
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                }
            }
        }
    };

    public KeyButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.KeyButtonView,
                defStyle, 0);

        mCode = a.getInteger(R.styleable.KeyButtonView_keyCode, 0);

        mSupportsLongpress = a.getBoolean(R.styleable.KeyButtonView_keyRepeat, true);

        TypedValue value = new TypedValue();
        if (a.getValue(R.styleable.KeyButtonView_android_contentDescription, value)) {
            mContentDescriptionRes = value.resourceId;
        }

        a.recycle();


        setClickable(true);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        setBackground(new KeyButtonRipple(context, this));
        mPerf = PerformanceManager.getInstance(context);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mContentDescriptionRes != 0) {
            setContentDescription(mContext.getString(mContentDescriptionRes));
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (mCode != 0) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK, null));
            if (mSupportsLongpress || isLongClickable()) {
                info.addAction(
                        new AccessibilityNodeInfo.AccessibilityAction(ACTION_LONG_CLICK, null));
            }
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE) {
            jumpDrawablesToCurrentState();
        }
    }

    @Override
    public boolean performAccessibilityActionInternal(int action, Bundle arguments) {
        if (action == ACTION_CLICK && mCode != 0) {
            sendEvent(KeyEvent.ACTION_DOWN, 0, SystemClock.uptimeMillis());
            sendEvent(KeyEvent.ACTION_UP, 0);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        } else if (action == ACTION_LONG_CLICK && mCode != 0) {
            sendEvent(KeyEvent.ACTION_DOWN, KeyEvent.FLAG_LONG_PRESS);
            sendEvent(KeyEvent.ACTION_UP, 0);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
            return true;
        }
        return super.performAccessibilityActionInternal(action, arguments);
    }

    @Override
    public Resources getResources() {
        ThemeConfig themeConfig = mContext.getResources().getConfiguration().themeConfig;
        Resources res = null;
        if (themeConfig != null) {
            try {
                final String navbarThemePkgName = themeConfig.getOverlayForNavBar();
                final String sysuiThemePkgName = themeConfig.getOverlayForStatusBar();
                // Check if the same theme is applied for systemui, if so we can skip this
                if (navbarThemePkgName != null && !navbarThemePkgName.equals(sysuiThemePkgName)) {
                    res = mContext.getPackageManager().getThemedResourcesForApplication(
                            mContext.getPackageName(), navbarThemePkgName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // don't care since we'll handle res being null below
            }
        }

        return res != null ? res : super.getResources();
    }

    public void setEditMode(boolean editMode) {
        mInEditMode = editMode;
        updateVisibility();
    }

    public void setInfo(NavbarEditor.ButtonInfo item, boolean isVertical, boolean isSmall) {
        final Resources res = getResources();
        setInfo(item, isVertical, isSmall, res);
    }

    public void setInfo(NavbarEditor.ButtonInfo item, boolean isVertical, boolean isSmall,
            Resources res) {
        final int keyDrawableResId;

        setTag(item);
        setContentDescription(res.getString(item.contentDescription));
        mCode = item.keyCode;
        mIsSmall = isSmall;

        if (isSmall) {
            keyDrawableResId = item.sideResource;
        } else if (!isVertical) {
            keyDrawableResId = item.portResource;
        } else {
            keyDrawableResId = item.landResource;
        }
        // The reason for setImageDrawable vs setImageResource is because setImageResource calls
        // relayout() w/o any checks. setImageDrawable performs size checks and only calls relayout
        // if necessary. We rely on this because otherwise the setX/setY attributes which are post
        // layout cause it to mess up the layout.
        setImageDrawable(res.getDrawable(keyDrawableResId));
        updateVisibility();
    }

    private void updateVisibility() {
        if (mInEditMode) {
            setVisibility(View.VISIBLE);
            return;
        }

        NavbarEditor.ButtonInfo info = (NavbarEditor.ButtonInfo) getTag();
        if (info == NavbarEditor.NAVBAR_EMPTY) {
            setVisibility(mIsSmall ? View.INVISIBLE : View.GONE);
        } else if (info == NavbarEditor.NAVBAR_CONDITIONAL_MENU) {
            setVisibility(View.INVISIBLE);
        }
    }

    private boolean supportsLongPress() {
        return mSupportsLongpress;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (mInEditMode) {
            return false;
        }
        final int action = ev.getAction();
        int x, y;
        if (action == MotionEvent.ACTION_DOWN) {
            mGestureAborted = false;
        }
        if (mGestureAborted) {
            return false;
        }

        // A lot of stuff is about to happen. Lets get ready.
        mPerf.cpuBoost(750000);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = SystemClock.uptimeMillis();
                setPressed(true);
                if (mCode == KeyEvent.KEYCODE_DPAD_LEFT || mCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    sendEvent(KeyEvent.ACTION_DOWN, KeyEvent.FLAG_VIRTUAL_HARD_KEY
                            | KeyEvent.FLAG_KEEP_TOUCH_MODE, mDownTime, false);
                } else if (mCode != 0) {
                    sendEvent(KeyEvent.ACTION_DOWN, 0, mDownTime);
                } else {
                    // Provide the same haptic feedback that the system offers for virtual keys.
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }

                if (supportsLongPress()) {
                    removeCallbacks(mCheckLongPress);
                    postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout());
                }

                break;
            case MotionEvent.ACTION_MOVE:
                x = (int) ev.getX();
                y = (int) ev.getY();
                setPressed(x >= -mTouchSlop
                        && x < getWidth() + mTouchSlop
                        && y >= -mTouchSlop
                        && y < getHeight() + mTouchSlop);
                break;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if (mCode != 0) {
                    sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
                }

                removeCallbacks(mCheckLongPress);

                if (supportsLongPress()) {
                    removeCallbacks(mCheckLongPress);
                }

                break;
            case MotionEvent.ACTION_UP:
                final boolean doIt = isPressed();
                setPressed(false);
                if (mCode != 0) {
                    if (doIt) {
                        sendEvent(KeyEvent.ACTION_UP, 0);
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                        playSoundEffect(SoundEffectConstants.CLICK);
                    } else {
                        sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
                    }
                } else {
                    // no key code, just a regular ImageView
                    if (doIt && !mPerformedLongClick) {
                        performClick();
                    }
                }

                removeCallbacks(mCheckLongPress);

                if (supportsLongPress()) {
                    removeCallbacks(mCheckLongPress);
                }
                mPerformedLongClick = false;
                break;
        }

        return true;
    }

    public void playSoundEffect(int soundConstant) {
        mAudioManager.playSoundEffect(soundConstant, ActivityManager.getCurrentUser());
    };

    public void sendEvent(int action, int flags) {
        sendEvent(action, flags, SystemClock.uptimeMillis());
    }

    void sendEvent(int action, int flags, long when) {
        sendEvent(action, flags, when, true);
    }

    void sendEvent(int action, int flags, long when, boolean applyDefaultFlags) {
        final int repeatCount = (flags & KeyEvent.FLAG_LONG_PRESS) != 0 ? 1 : 0;
        if (applyDefaultFlags) {
            flags |= KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY;
        }
        final KeyEvent ev = new KeyEvent(mDownTime, when, action, mCode, repeatCount,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags,
                InputDevice.SOURCE_KEYBOARD);
        InputManager.getInstance().injectInputEvent(ev,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    public void abortCurrentGesture() {
        setPressed(false);
        mGestureAborted = true;
    }
}


