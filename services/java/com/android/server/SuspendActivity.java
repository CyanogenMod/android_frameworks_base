/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.server;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.os.SystemClock;

import android.content.BroadcastReceiver;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.content.Context;
import android.view.KeyEvent;

import android.app.ActivityManager;
import java.util.List;
import android.content.res.Configuration;
import android.view.Display;
import android.os.SystemProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * SuspendAcitivity provides the Warmboot feature which enables Device to enter into
 * and resume from "suspend2ram" state
 */
public class SuspendActivity extends Activity {

    private static final String TAG = "SuspendActivity";

    private static final String INPUT_METHOD_SERVICE = "com.android.inputmethod.latin";

    // Airplane Mode needs to be set to enter "TCXO Shutdown" (complete-suspend) state. With out this, device can enter only "Power-collapse" state
    private boolean mAirplaneModeSetBy = false;

    // Indicates whether this Activity was already paused or not */
    private boolean mWasPaused = false;

    // Indicates whether this Activity was already resumed & state-restored or not
    private boolean mStateRestored = false;

    // Splash screen events
    private static final int START_BOOT_SPLASH = 1;
    private static final int STOP_BOOT_SPLASH = 2;

    // Splash screen state handling
    private SplashHandler mHandler = new SplashHandler();

    class SplashHandler extends Handler {
        /**
         * Handler's Main function which looks after the transitions
         */
        public void handleMessage(Message msg) {

            switch( msg.what ) {
                case START_BOOT_SPLASH:
                    Log.d(TAG, "handleMessage() Event : START_BOOT_SPLASH");
                    SystemProperties.set( "ctl.start",  "bootanim");
                    break;

                case STOP_BOOT_SPLASH:
                    Log.d(TAG, "handleMessage() Event : STOP_BOOT_SPLASH");
                    SystemProperties.set( "ctl.stop",  "bootanim");
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Display getOrient = getWindowManager().getDefaultDisplay();

        mWasPaused = false;
        Log.d(TAG, "onCreate() : " + SystemClock.elapsedRealtime() + " WIDTH = " + getOrient.getWidth() + ", HEIGHT = " + getOrient.getHeight() );

        setSuspendState();
    }

    @Override
    protected void onStart( ) {
        super.onStart( );
        Log.d(TAG, "onStart() : " + SystemClock.elapsedRealtime());
    }

    @Override
    protected void onRestart( ) {
        super.onRestart( );
        Log.d(TAG, "onRestart() : " + SystemClock.elapsedRealtime());
    }

    @Override
    protected void onResume( ) {
        super.onResume( );
        Log.d(TAG, "onResume() - mWasPaused = " + mWasPaused + " : " + SystemClock.elapsedRealtime());

        // RESUME needs to be handled only after PAUSE was done
        if ( mWasPaused ) {

            mHandler.sendMessage( Message.obtain(mHandler, START_BOOT_SPLASH) );
            mHandler.sendMessageDelayed( Message.obtain(mHandler, STOP_BOOT_SPLASH), 2000 );

            // Activity can just quit as the device is back now
            restoreOriginalState();

            // Go to "Home-Screen" when device resumes
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            startActivity(intent);

            // Destory this activity as Device is resumed & restored to original state
            finish();

            mStateRestored = true;
        }
    }

    @Override
    protected void onPause( ) {
        super.onPause( );
        Log.d(TAG, "onPause() : " + SystemClock.elapsedRealtime());
        mWasPaused = true;
    }

    @Override
    protected void onStop( ) {
        super.onStop( );
        Log.d(TAG, "onStop() : " + SystemClock.elapsedRealtime());

        // Incase, if something stops/kills us before RESUME was handled.
        if ( mStateRestored == false ) {

            // Activity can just quit as the device is back now
            restoreOriginalState();

            // Destory this activity as Device is resumed & restored to original state
            finish();

            mStateRestored = true;
        }
    }

    @Override
    protected void onDestroy( ) {
        super.onDestroy( );
        Log.d(TAG, "onDestroy() : " + SystemClock.elapsedRealtime());
    }

    /**
     * Ensure smooth state for the device to enter SUSPEND smoothly
     */
    private void setSuspendState() {
        Log.d( TAG, "setSuspendState()");

        // Should n't suspend during Monkey-run ( Similar to shutdown scenario )
        boolean isDebuggableMonkeyBuild =
                                SystemProperties.getBoolean("ro.monkey", false);
        if (isDebuggableMonkeyBuild) {
            Log.d(TAG, "Rejected suspend as monkey is detected to be running. Destroying activity..");
            // Destory this activity as Device is NOT suspending
            finish();
            return;
        }

        Log.d(TAG, "Notifying thread to start radio shutdown");

        forceKillActiveServices();

        // Disable keypad , Enable airplane-mode as we are entering RESUME state
        setKeypadEnabled(false);
        setAirplaneModeEnabled(true);

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        pm.goToSleep( SystemClock.uptimeMillis() );
    }

    /**
     * Does n't need to do anything wrt Device-Resume., This activity's onResume() is called soon after
     * the Device-Resume happens by POWER-key press.,
     * So, if the Airplane Mode was earlier enabled by us, lets disable it and bring back the state to
     * the Device's original state.
     */
    private void restoreOriginalState() {
        Log.d(TAG, "restoreOriginalState() : " + SystemClock.elapsedRealtime());

        // Enable keypad , Disable airplane-mode as we are entering RESUME state
        setKeypadEnabled(true);
        setAirplaneModeEnabled(false);
    }

    /**
     * Checks whether any active services are present., Its better to force-stop them( and their originating process'es)
     * to ensure smooth sleep (power-collapse) state is entered.,
     * One Example - MediaplaybackService., Music Application doesn't handle onPause/suspend properly.,
     * Force-Stop == stops the process gracefully and then restarts it in another 5 secs
     */
    private void forceKillActiveServices() {
        Log.d(TAG, "forceKillActiveServices() : " + SystemClock.elapsedRealtime() );

        List<ActivityManager.RunningServiceInfo> runningServices;
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        // AM's API getRunningServices() requires a ?int? parameter. Here, we are getting first 50 services used/active by user applications.
        // Though this actul figure is quite less
        runningServices = activityManager.getRunningServices(50);

        for (int i = 0; i < runningServices.size(); i++) {
            ActivityManager.RunningServiceInfo si = runningServices.get(i);
            // Don't touch NON-started services
            if (!si.started && si.clientLabel == 0) {
                    Log.i( TAG, "Ignore [ !si.started && si.clientLabel == 0  ] : Process = " + si.process + " with component " + si.service.getClassName() );
                    continue;
            }
            // Don't touch PERSISTENT services. They are already part of system and wont trouble much
            if ((si.flags&ActivityManager.RunningServiceInfo.FLAG_PERSISTENT_PROCESS) != 0) {
                    Log.i( TAG, "Ignore [ FLAG_PERSISTENT_PROCESS  ] : Process = " + si.process + " with component " + si.service.getClassName() + " clientLabel = " + si.clientLabel );
                    continue;
            }
            // Don't touch Inputmethod.latin service. If stopped, this (current foreground) activity might be destroyed & recreated.
            if ( si.process.startsWith(INPUT_METHOD_SERVICE) ) {
                    Log.i( TAG, "No POINT in killing System-Input Process = " + si.process + " with component " + si.service.getClassName() + " clientLabel = " + si.clientLabel );
                    continue;
            }
            try {
                    activityManager.forceStopPackage(si.process);
                    //  activityManager.killBackgroundProcesses(si.process);
                    Log.i( TAG, "forceStopPackage [SUCCESS] : Process = " + si.process + " with component " + si.service.getClassName() + " clientLabel = " + si.clientLabel );
            }
            catch (Exception e) {
                    Log.i( TAG, "forceStopPackage [FAIL] ! : Process = " + si.process + " with component " + si.service.getClassName() + " clientLabel = " + si.clientLabel );
            }
        }
    }

    /**
    * Disable Keypad (Except POWER-Key) when device enters suspend state
    */
    private void setKeypadEnabled( boolean on ) {
        Log.d( TAG, "setKeypadEnabled() : on = " + on);

        String disableStr = (on ? "0" : "1" );
        SystemProperties.set( "hw.keyboard.disable" , disableStr );
    }

    /**
    * Enable Airplane-mode when device enters suspend state., Restore to original state when device resumes.
    */
    private void setAirplaneModeEnabled( boolean on ) {
        Log.d( TAG, "setAirplaneModeEnabled() : on = " + on);

        if ( on ) {
            int airplaneMode = Settings.System.getInt( getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0 );

            mAirplaneModeSetBy = ( airplaneMode == 0 );
            changeAirplaneModeSystemSetting(true);

            if ( mAirplaneModeSetBy ) {
                Log.d( TAG, "NO airplaneMode set. Lets enable it!");
            }
            else {
                Log.d( TAG, "AirplaneMode already set. Lets not worry :-)");
            }
        }
        else {
            if ( mAirplaneModeSetBy ) {
                Log.d( TAG, "Suspend has enabled Airplane mode. Lets disable it!" );
                changeAirplaneModeSystemSetting(false);
            }
            else {
                Log.d( TAG, "AirplaneMode NOT set by SUSPEND. Lets not worry :-)");
            }
        }
    }

    /**
    * Change the airplane mode system setting
    * Copied from "frameworks/policies/base/phone/com/android/internal/policy/impl/GlobalActions.java"
    */
    private void changeAirplaneModeSystemSetting( boolean on ) {
        Log.d( TAG, "changeAirplaneModeSystemSetting() : " + SystemClock.elapsedRealtime());
        Settings.System.putInt(
                                getContentResolver(),
                                Settings.System.AIRPLANE_MODE_ON,
                                on ? 1 : 0);

        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("state", on);
        sendBroadcast(intent);
    }

}
