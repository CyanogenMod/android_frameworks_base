/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.systemui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.util.AttributeSet;
import android.view.View;

public class DockBatteryMeterView extends BatteryMeterView {

    private BatteryManager mBatteryService;
    private boolean mSupported;

    private class DockBatteryTracker extends BatteryTracker {

        public DockBatteryTracker() {
            super();
            present = false;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                if (testmode && ! intent.getBooleanExtra("testmode", false)) return;

                mSupported = mBatteryService.isDockBatterySupported();

                if (mSupported) {
                    present = intent.getBooleanExtra(BatteryManager.EXTRA_DOCK_PRESENT, false);

                    level = (int)(100f
                            * intent.getIntExtra(BatteryManager.EXTRA_DOCK_LEVEL, 0)
                            / intent.getIntExtra(BatteryManager.EXTRA_DOCK_SCALE, 100));

                    plugType = intent.getIntExtra(BatteryManager.EXTRA_DOCK_PLUGGED, 0);
                    plugged = plugType == BatteryManager.BATTERY_DOCK_PLUGGED_AC;
                    health = intent.getIntExtra(BatteryManager.EXTRA_DOCK_HEALTH,
                            BatteryManager.BATTERY_HEALTH_UNKNOWN);
                    status = intent.getIntExtra(BatteryManager.EXTRA_DOCK_STATUS,
                            BatteryManager.BATTERY_STATUS_UNKNOWN);
                    technology = intent.getStringExtra(BatteryManager.EXTRA_DOCK_TECHNOLOGY);
                    voltage = intent.getIntExtra(BatteryManager.EXTRA_DOCK_VOLTAGE, 0);
                    temperature = intent.getIntExtra(BatteryManager.EXTRA_DOCK_TEMPERATURE, 0);

                    if (present && mMeterMode != BatteryMeterMode.BATTERY_METER_GONE) {
                        setContentDescription(context.getString(
                                R.string.accessibility_dock_battery_level, level));
                        invalidateIfVisible();
                        setVisibility(View.VISIBLE);
                    } else {
                        setContentDescription(null);
                        setVisibility(View.GONE);
                    }
                } else {
                    setContentDescription(null);
                    setVisibility(View.GONE);

                    // If dock is not supported then we don't need this receiver anymore
                    getContext().unregisterReceiver(this);
                }
            } else if (action.equals(ACTION_LEVEL_TEST)) {
                testmode = true;
                post(new Runnable() {
                    int curLevel = 0;
                    int incr = 1;
                    int saveLevel = level;
                    int savePlugged = plugType;
                    Intent dummy = new Intent(Intent.ACTION_BATTERY_CHANGED);
                    @Override
                    public void run() {
                        if (curLevel < 0) {
                            testmode = false;
                            dummy.putExtra("level", saveLevel);
                            dummy.putExtra("plugged", savePlugged);
                            dummy.putExtra("testmode", false);
                        } else {
                            dummy.putExtra("level", curLevel);
                            dummy.putExtra("plugged", incr > 0
                                    ? BatteryManager.BATTERY_DOCK_PLUGGED_AC : 0);
                            dummy.putExtra("testmode", true);
                        }
                        getContext().sendBroadcast(dummy);

                        if (!testmode) return;

                        curLevel += incr;
                        if (curLevel == 100) {
                            incr *= -1;
                        }
                        postDelayed(this, 200);
                    }
                });
            }
        }
    }

    public DockBatteryMeterView(Context context) {
        this(context, null, 0);
    }

    public DockBatteryMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DockBatteryMeterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mBatteryService = ((BatteryManager) context.getSystemService(Context.BATTERY_SERVICE));
        mDemoTracker = new DockBatteryTracker();
        mTracker = new DockBatteryTracker();
    }

    @Override
    public void setMode(BatteryMeterMode mode) {
        super.setMode(mode);
        int visibility = getVisibility();
        if (visibility == View.VISIBLE && !mSupported) {
            setVisibility(View.GONE);
        }
    }

    @Override
    protected BatteryMeterDrawable createBatteryMeterDrawable(BatteryMeterMode mode) {
        Resources res = mContext.getResources();
        switch (mode) {
            case BATTERY_METER_CIRCLE:
                return new DockCircleBatteryMeterDrawable(res);

            case BATTERY_METER_TEXT:
                return new DockTextBatteryMeterDrawable(res);

            case BATTERY_METER_ICON_LANDSCAPE:
                return new DockNormalBatteryMeterDrawable(res, true);

            default:
                return new DockNormalBatteryMeterDrawable(res, false);
        }
    }

    protected class DockNormalBatteryMeterDrawable extends NormalBatteryMeterDrawable {

        public DockNormalBatteryMeterDrawable(Resources res, boolean horizontal) {
            super(res, horizontal);
        }

        @Override
        protected int getBoltPointsArrayResource() {
            return mHorizontal
                    ? R.array.dockbatterymeter_inverted_bold_points
                    : R.array.dockbatterymeter_bold_points;
        }
    }

    protected class DockCircleBatteryMeterDrawable extends CircleBatteryMeterDrawable {
        public DockCircleBatteryMeterDrawable(Resources res) {
            super(res);
        }

        @Override
        protected int getBoltPointsArrayResource() {
            return R.array.dockbatterymeter_bold_points;
        }
    }

    protected class DockTextBatteryMeterDrawable extends TextBatteryMeterDrawable {
        public DockTextBatteryMeterDrawable(Resources res) {
            super(res);
        }
    }
}
