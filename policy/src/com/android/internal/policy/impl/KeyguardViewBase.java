/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.util.AttributeSet;

/**
 * Base class for keyguard views.  {@link #reset} is where you should
 * reset the state of your view.  Use the {@link KeyguardViewCallback} via
 * {@link #getCallback()} to send information back (such as poking the wake lock,
 * or finishing the keyguard).
 *
 * Handles intercepting of media keys that still work when the keyguard is
 * showing.
 */
public abstract class KeyguardViewBase extends FrameLayout {

    private KeyguardViewCallback mCallback;
    private AudioManager mAudioManager;
    private TelephonyManager mTelephonyManager = null;

    public KeyguardViewBase(Context context) {
        super(context);

        // drop shadow below status bar in keyguard too
        mForegroundInPadding = false;
        setForegroundGravity(Gravity.FILL_HORIZONTAL | Gravity.TOP);
        setForeground(
                context.getResources().getDrawable(
                        com.android.internal.R.drawable.title_bar_shadow));
    }

    // used to inject callback
    void setCallback(KeyguardViewCallback callback) {
        mCallback = callback;
    }

    public KeyguardViewCallback getCallback() {
        return mCallback;
    }

    /**
     * Called when you need to reset the state of your view.
     */
    abstract public void reset();

    /**
     * Called when entering/leaving an interstitial "locked but not yet secured"
     * mode.
     */
    abstract public void onLockedButNotSecured(boolean lockedButNotSecured);

    /**
     * Called when the screen turned off.
     */
    abstract public void onScreenTurnedOff();

    /**
     * Called when the screen turned on.
     */
    abstract public void onScreenTurnedOn();

    /**
     * Called when a key has woken the device to give us a chance to adjust our
     * state according the the key.  We are responsible for waking the device
     * (by poking the wake lock) once we are ready.
     *
     * The 'Tq' suffix is per the documentation in {@link android.view.WindowManagerPolicy}.
     * Be sure not to take any action that takes a long time; any significant
     * action should be posted to a handler.
     *
     * @param keyCode The wake key, which may be relevant for configuring the
     *   keyguard.
     */
    abstract public void wakeWhenReadyTq(int keyCode);

    /**
     * Verify that the user can get past the keyguard securely.  This is called,
     * for example, when the phone disables the keyguard but then wants to launch
     * something else that requires secure access.
     *
     * The result will be propogated back via {@link KeyguardViewCallback#keyguardDone(boolean)}
     */
    abstract public void verifyUnlock();

    /**
     * Called before this view is being removed.
     */
    abstract public void cleanUp();

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (shouldEventKeepScreenOnWhileKeyguardShowing(event)) {
            mCallback.pokeWakelock();
        }

        if (interceptMediaKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean shouldEventKeepScreenOnWhileKeyguardShowing(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
                return false;
            default:
                return true;
        }
    }

    /**
     * Allows the media keys to work when the keyguard is showing.
     * The media keys should be of no interest to the actual keyguard view(s),
     * so intercepting them here should not be of any harm.
     * @param event The key event
     * @return whether the event was consumed as a media key.
     */
    private boolean interceptMediaKey(KeyEvent event) {
        final int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    /* Suppress PLAYPAUSE toggle when phone is ringing or
                     * in-call to avoid music playback */
                    if (mTelephonyManager == null) {
                        mTelephonyManager = (TelephonyManager) getContext().getSystemService(
                                Context.TELEPHONY_SERVICE);
                    }
                    if (mTelephonyManager != null &&
                            mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                        return true;  // suppress key event
                    }
                case KeyEvent.KEYCODE_HEADSETHOOK: 
                case KeyEvent.KEYCODE_MEDIA_STOP: 
                case KeyEvent.KEYCODE_MEDIA_NEXT: 
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS: 
                case KeyEvent.KEYCODE_MEDIA_REWIND: 
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
                    intent.putExtra(Intent.EXTRA_KEY_EVENT, event);
                    getContext().sendOrderedBroadcast(intent, null);
                    return true;
                }

                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN: {
                    synchronized (this) {
                        if (mAudioManager == null) {
                            mAudioManager = (AudioManager) getContext().getSystemService(
                                    Context.AUDIO_SERVICE);
                        }
                    }
                    // Volume buttons should only function for music.
                    if (mAudioManager.isMusicActive()) {
                        mAudioManager.adjustStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    keyCode == KeyEvent.KEYCODE_VOLUME_UP
                                            ? AudioManager.ADJUST_RAISE
                                            : AudioManager.ADJUST_LOWER,
                                    0);
                    }
                    // Don't execute default volume behavior
                    return true;
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MUTE:
                case KeyEvent.KEYCODE_HEADSETHOOK: 
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: 
                case KeyEvent.KEYCODE_MEDIA_STOP: 
                case KeyEvent.KEYCODE_MEDIA_NEXT: 
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS: 
                case KeyEvent.KEYCODE_MEDIA_REWIND: 
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
                    intent.putExtra(Intent.EXTRA_KEY_EVENT, event);
                    getContext().sendOrderedBroadcast(intent, null);
                    return true;
                }
            }
        }
        return false;
    }

}
