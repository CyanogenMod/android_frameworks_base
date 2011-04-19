/*
 * Created by Sven Dawitz; Copyright (C) 2011 CyanogenMod Project
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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.LocalPowerManager;
import android.provider.CmSystem;
import android.provider.Settings;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.WindowManagerPolicy;

import com.android.internal.policy.impl.CmButtonTracker.OnLongPressListener;
import com.android.internal.policy.impl.CmButtonTracker.OnPressListener;

public class CmPhoneWindowManager extends PhoneWindowManager implements OnPressListener, OnLongPressListener {
    static final String TAG = "CmPhoneWindowManager";

    static final int ACTION_NOTHING = 0;
    // for more easy to read if statements on button handling
    static final int ACTION_MEDIA_NEXT = 9;
    static final int ACTION_MEDIA_PREVIOUS = 10;

    //virtual button presses - double defined in StatusBarView and PhoneWindowManager
    public static final int KEYCODE_VIRTUAL_HOME_LONG=KeyEvent.getMaxKeyCode()+1;
    public static final int KEYCODE_VIRTUAL_BACK_LONG=KeyEvent.getMaxKeyCode()+2;

    // variables connected to soft buttons
    private int mUnhideKeyCode;
    // variables connected to volume key remapping
    private boolean mRevVolBehavior;
    private int mLongVolPlusAction;
    private int mLongVolMinusAction;
    private int mVolBothAction;
    private int mLongVolBothAction;

    // variables for broadcast processing to get the fullscreen handling done
    Intent mFsRemoveIntent;
    boolean mIsRealFullscreen=false;
    boolean mHandleFsUpEvent=false;
    String mFsOnAction="android.intent.action.FULLSCREEN_REAL_ON";
    String mFsOffAction="android.intent.action.FULLSCREEN_REAL_OFF";

    // CmButtonTracker
    private CmButtonTracker mVolDownTracker;
    private CmButtonTracker mVolUpTracker;

    // receives fullscreen related broadcast events
    class FullscreenReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent){
            if(mFsOnAction.equals(intent.getAction()))
                mIsRealFullscreen=true;
            if(mFsOffAction.equals(intent.getAction()))
                mIsRealFullscreen=false;
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.REVERSE_VOLUME_BEHAVIOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LONG_VOLP_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LONG_VOLM_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.VOL_BOTH_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LONG_VOL_BOTH_ACTION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.UNHIDE_BUTTON), false, this);
            onChange(true);
        }

        @Override public void onChange(boolean selfChange) {
            int defValue;
            ContentResolver resolver = mContext.getContentResolver();

            defValue=(CmSystem.getDefaultBool(mContext, CmSystem.CM_DEFAULT_REVERSE_VOLUME_BEHAVIOR) ? 1 : 0);
            mRevVolBehavior = (Settings.System.getInt(resolver,
                    Settings.System.REVERSE_VOLUME_BEHAVIOR, defValue) == 1);
            defValue=CmSystem.getDefaultInt(mContext, CmSystem.CM_DEFAULT_REMAPPED_LONG_VOL_UP_INDEX);
            mLongVolPlusAction = Settings.System.getInt(resolver,
                    Settings.System.LONG_VOLP_ACTION, defValue);
            defValue=CmSystem.getDefaultInt(mContext, CmSystem.CM_DEFAULT_REMAPPED_LONG_VOL_DOWN_INDEX);
            mLongVolMinusAction = Settings.System.getInt(resolver,
                    Settings.System.LONG_VOLM_ACTION, defValue);
            defValue=CmSystem.getDefaultInt(mContext, CmSystem.CM_DEFAULT_REMAPPED_BOTH_VOL_INDEX);
            mVolBothAction = Settings.System.getInt(resolver,
                    Settings.System.VOL_BOTH_ACTION, defValue);
            defValue=CmSystem.getDefaultInt(mContext, CmSystem.CM_DEFAULT_REMAPPED_LONG_BOTH_VOL_INDEX);
            mLongVolBothAction = CmSystem.translateActionToKeycode(Settings.System.getInt(resolver,
                    Settings.System.LONG_VOL_BOTH_ACTION, defValue));
            defValue=CmSystem.getDefaultInt(mContext, CmSystem.CM_DEFAULT_UNHIDE_BUTTON_INDEX);
            mUnhideKeyCode = CmSystem.translateUnhideIndexToKeycode(Settings.System.getInt(resolver,
                    Settings.System.UNHIDE_BUTTON, defValue));

            // set Reverse mode of button trackers
            mVolDownTracker.setReverseMode(mRevVolBehavior);
            mVolUpTracker.setReverseMode(mRevVolBehavior);
        }
    }

    @Override
    public void init(Context context, IWindowManager windowManager,
                LocalPowerManager powerManager){
        mKeyguardMediator = new KeyguardViewMediator(context, this, powerManager);

        super.init(context, windowManager, powerManager);

        // set up the button trackers
        mVolDownTracker=new CmButtonTracker(KeyEvent.KEYCODE_VOLUME_DOWN);
        mVolUpTracker=new CmButtonTracker(KeyEvent.KEYCODE_VOLUME_UP);
        mVolDownTracker.setOnPressListener(this);
        mVolDownTracker.setOnLongPressListener(this);
        mVolUpTracker.setOnPressListener(this);
        mVolUpTracker.setOnLongPressListener(this);

        // set up broadcast receiver and intent which is used multiple times (less GC)
        FullscreenReceiver fullscreenReceiver = new FullscreenReceiver();
        mContext.registerReceiver(fullscreenReceiver, new IntentFilter(mFsOnAction));
        mContext.registerReceiver(fullscreenReceiver, new IntentFilter(mFsOffAction));
        mFsRemoveIntent=new Intent("android.intent.action.REMOVE_FULLSCREEN");

        SettingsObserver so = new SettingsObserver(mHandler);
        so.observe();
    }

    /** {@inheritDoc} */
    @Override
    public int interceptKeyBeforeQueueing(long whenNanos, int action, int flags,
            int keyCode, int scanCode, int policyFlags, boolean isScreenOn) {
        final boolean down = action == KeyEvent.ACTION_DOWN;
        final boolean isInjected = (policyFlags & WindowManagerPolicy.FLAG_INJECTED) != 0;

        // If screen is off then we treat the case where the keyguard is open but hidden
        // the same as if it were open and in front.
        // This will prevent any keys other than the power button from waking the screen
        // when the keyguard is hidden by another activity.
        final boolean keyguardActive = (isScreenOn ?
                                        mKeyguardMediator.isShowingAndNotHidden() :
                                        mKeyguardMediator.isShowing());

        // virtual keycodes for long-pressing soft-buttons
        // return 0 used, since there is no ACTION_DO_NOTHING defined
        if(keyCode==KEYCODE_VIRTUAL_HOME_LONG){
            if(!down)
                mHandler.post(mHomeLongPress);
            return ACTION_NOTHING;
        }
        if(keyCode==KEYCODE_VIRTUAL_BACK_LONG){
            if(!down)
                mHandler.post(mBackLongPress);
            return ACTION_NOTHING;
        }

        // unhide button handling - when in fullscreen mode, and soft buttons are active,
        // this shows the statusbar including soft buttons again
        if(isScreenOn && hasHandledUnhideButton(keyCode, down))
            return ACTION_NOTHING;

        return super.interceptKeyBeforeQueueing(whenNanos, action, flags, keyCode, scanCode, policyFlags, isScreenOn);

        // cm71 nightlies: will be re-enabled to replace PhoneWindowManager's crappy volume handling
        /*        // update the volume Trackers
        if(!isInjected && (keyCode==KeyEvent.KEYCODE_VOLUME_DOWN || keyCode==KeyEvent.KEYCODE_VOLUME_UP)){
            mVolDownTracker.track(keyCode, down);
            mVolUpTracker.track(keyCode, down);
        }

        if(keyguardActive || !isScreenOn)
            return super.interceptKeyBeforeQueueing(whenNanos, action, flags, keyCode, scanCode, policyFlags, isScreenOn);

        return super.interceptKeyBeforeQueueing(whenNanos, action, flags, keyCode, scanCode, policyFlags, isScreenOn);*/
    }

    private boolean hasHandledUnhideButton(int keyCode, boolean down){
        // at least on vega, pressing power key injects in fact a endcall key.
        // so this needs special treatment
        if(keyCode!=mUnhideKeyCode)
            return false;

        // according to broadcasts, we are in fullscreen. end fullscreen now.
        if(mIsRealFullscreen==true){
            mContext.sendBroadcast(mFsRemoveIntent);

            mIsRealFullscreen=false;
            mHandleFsUpEvent=!down;

            return true;
        }

        // we gotta handle the up even, since we also handled the down event
        if(mHandleFsUpEvent){
            mHandleFsUpEvent=false;
            return true;
        }

        return false;
    }

    private void handleVolumeActions(int customAction)
    {
        int translated=CmSystem.translateActionToKeycode(customAction);

        if(translated==CmSystem.VOLUME_ACTION_LONG_HOME)
            mHandler.postDelayed(mHomeLongPress, 10);
        else if(translated==KeyEvent.KEYCODE_MEDIA_NEXT ||
                translated==KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                translated==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            sendMediaButtonEvent(translated);
        else
            sendHwButtonEvent(translated);
    }

    @Override
    public void onLongPress(int KeyCode) {
        if(!isScreenOn() && mVolBtnMusicControls && isMusicActive()){
            handleVolumeKey(AudioManager.STREAM_MUSIC, KeyCode);
            return;
        }
        if(!isScreenOn())
            return;
        if(KeyCode==KeyEvent.KEYCODE_VOLUME_DOWN)
            handleVolumeActions(mLongVolMinusAction);
        if(KeyCode==KeyEvent.KEYCODE_VOLUME_UP)
            handleVolumeActions(mLongVolPlusAction);
    }

    @Override
    public void onPress(int KeyCode) {
        if(isScreenOn())
            sendHwButtonEvent(KeyCode);
    }
}
