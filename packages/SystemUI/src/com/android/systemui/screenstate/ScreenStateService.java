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
package com.android.systemui.screenstate;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.os.Handler;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class ScreenStateService extends Service  {

    private static final String TAG = "ScreenStateService";
    private BroadcastReceiver mPowerKeyReceiver;
    private TwoGToggle mTwoGToggle;
    private GpsToggle mGpsToggle;
    private MobileDataToggle mMobileDataToggle;
    private boolean mEnabled = true;
    private Context mContext;
    private List<ScreenStateToggle> fEnabledToggles;
    private List<ScreenStateToggle> fAllToggles;
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mEnabled){
            unregisterReceiver();
        }
    }

    @Override
    public void onStart(Intent intent, int startid)
    {
        Log.d(TAG, "onStart");
        mContext = getApplicationContext();
        
        // firewall
        mEnabled = Settings.System.getBoolean(mContext.getContentResolver(), Settings.System.START_SCREEN_STATE_SERVICE, false);
        
        if (mEnabled){
            registerBroadcastReceiver();
        }

        fAllToggles = new ArrayList<ScreenStateToggle>();
        mTwoGToggle = new TwoGToggle(mContext);
        fAllToggles.add(mTwoGToggle);
        mGpsToggle = new GpsToggle(mContext);
        fAllToggles.add(mGpsToggle);
        mMobileDataToggle = new MobileDataToggle(mContext);
        fAllToggles.add(mMobileDataToggle);
                
        updateEnabledToggles();
    }
      
    private void registerBroadcastReceiver() {
        final IntentFilter theFilter = new IntentFilter();
        /** System Defined Broadcast */
        theFilter.addAction(Intent.ACTION_SCREEN_ON);
        theFilter.addAction(Intent.ACTION_SCREEN_OFF);
        theFilter.addAction("android.intent.action.SCREEN_STATE_SERVICE_UPDATE");

        mPowerKeyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String strAction = intent.getAction();
    
                if (strAction.equals(Intent.ACTION_SCREEN_OFF)){
                    Log.d(TAG, "scren off");
                    Iterator<ScreenStateToggle> nextToggle = fEnabledToggles.iterator();
                    while(nextToggle.hasNext()){
                        ScreenStateToggle toggle = nextToggle.next();
                        toggle.doScreenOff();
                    }
                }
                if (strAction.equals(Intent.ACTION_SCREEN_ON)) {
                    Log.d(TAG, "scren on");
                    Iterator<ScreenStateToggle> nextToggle = fEnabledToggles.iterator();
                    while(nextToggle.hasNext()){
                        ScreenStateToggle toggle = nextToggle.next();
                        toggle.doScreenOn();
                    }
                }
                if (strAction.equals("android.intent.action.SCREEN_STATE_SERVICE_UPDATE")){
                    Log.d(TAG, "update enabled toggles");
                    updateEnabledToggles();
                }
            }
        };

        Log.d(TAG, "registerBroadcastReceiver");
        mContext.registerReceiver(mPowerKeyReceiver, theFilter);
    }

    private void unregisterReceiver() {
        try {
            Log.d(TAG, "unregisterReceiver");
            mContext.unregisterReceiver(mPowerKeyReceiver);
        }
        catch (IllegalArgumentException e) {
            mPowerKeyReceiver = null;
        }
    }
    
    private void updateEnabledToggles() {
        fEnabledToggles = new ArrayList<ScreenStateToggle>();
        Iterator<ScreenStateToggle> nextToggle = fAllToggles.iterator();
        while(nextToggle.hasNext()){
            ScreenStateToggle toggle = nextToggle.next();
            if (toggle.isEnabled()){
                Log.d(TAG, "active toggle "+ toggle.getClass().getName());
                fEnabledToggles.add(toggle);
            }
        }
    }
}
