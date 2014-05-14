/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.content.Context;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class CompassTile extends QuickSettingsTile implements SensorEventListener {
    private boolean mActive = false;
    private boolean mWasActive = false;
    private float mCurrentDegree = 0;

    private final static float DEFAULT_IMAGE_ROTATION = 90;
    private final static int ANIMATION_DURATION = 200;
    private final static float ALPHA = 0.97f;

    private SensorManager mSensorManager;
    private Sensor mAccelerationSensor;
    private Sensor mGeomagneticFieldSensor;

    private float[] mAcceleration = new float[3];
    private float[] mGeomagnetic = new float[3];

    private TextView mText;
    private ImageView mImage;

    public CompassTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        // Initialize sensors
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGeomagneticFieldSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActive = !mActive;
                updateResources();
            }
        };
    }

    @Override
    void onPostCreate() {
        // Get text and image view of the tile
        mText = (TextView) mTile.findViewById(R.id.text);
        mImage = (ImageView) mTile.findViewById(R.id.image);

        updateTile();
        super.onPostCreate();

        mTile.setOnPrepareListener(new QuickSettingsTileView.OnPrepareListener() {
            @Override
            public void onPrepare() {
                if (mWasActive) {
                    mWasActive = false;

                    mActive = true;
                    updateResources();
                }
            }

            @Override
            public void onUnprepare() {
                if (mActive) {
                    mWasActive = true;

                    mActive = false;
                    updateResources();
                }
            }
        });
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        if (mActive) {
            mDrawable = R.drawable.ic_qs_compass_on;
            mLabel = "270° " + mContext.getString(R.string.quick_settings_compass_west);

            // Register listeners
            mSensorManager.registerListener(
                    this, mAccelerationSensor, SensorManager.SENSOR_DELAY_GAME);
            mSensorManager.registerListener(
                    this, mGeomagneticFieldSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            mDrawable = R.drawable.ic_qs_compass_off;
            mLabel = mContext.getString(R.string.quick_settings_compass_off);

            // Reset rotation of the ImageView
            RotateAnimation rotateAnimation = new RotateAnimation(mCurrentDegree, 0,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnimation.setDuration(0);
            rotateAnimation.setFillAfter(true);
            mImage.startAnimation(rotateAnimation);

            // Remove listeners
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mAcceleration[0] = ALPHA * mAcceleration[0] + (1 - ALPHA) * event.values[0];
                mAcceleration[1] = ALPHA * mAcceleration[1] + (1 - ALPHA) * event.values[1];
                mAcceleration[2] = ALPHA * mAcceleration[2] + (1 - ALPHA) * event.values[2];
            }

            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mGeomagnetic[0] = ALPHA * mGeomagnetic[0] + (1 - ALPHA) * event.values[0];
                mGeomagnetic[1] = ALPHA * mGeomagnetic[1] + (1 - ALPHA) * event.values[1];
                mGeomagnetic[2] = ALPHA * mGeomagnetic[2] + (1 - ALPHA) * event.values[2];
            }

            if (mActive && mAcceleration != null && mGeomagnetic != null) {
                float R[] = new float[9];
                float I[] = new float[9];
                boolean success = SensorManager.getRotationMatrix(
                        R, I, mAcceleration, mGeomagnetic);

                if (success) {
                    float orientation[] = new float[3];
                    SensorManager.getOrientation(R, orientation);

                    // Convert azimuth zu degree
                    float degree = (float) Math.toDegrees(orientation[0]);
                    degree = (degree + 360) % 360;

                    // Set rotation in degrees as tile title
                    mText.setText(String.format("%s", Math.round(degree))
                            + "° " + getCardinalDirection(degree));

                    // Take the default rotation of the compass image into account
                    degree += DEFAULT_IMAGE_ROTATION;

                    // Create a rotation animation
                    RotateAnimation rotateAnimation = new RotateAnimation(mCurrentDegree, -degree,
                            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

                    // Set animation duration
                    rotateAnimation.setDuration(ANIMATION_DURATION);
                    rotateAnimation.setFillAfter(true);

                    // Start the animation and update mCurrentDegree
                    mImage.startAnimation(rotateAnimation);
                    mCurrentDegree = -degree;
                }
            }
        }
    }

    private String getCardinalDirection(float degree) {
        String cardinalDirection = "";
        switch ((int) Math.round(((degree % 360) / 45))) {
            case 0:
                // North (N)
                cardinalDirection = mContext.getString(R.string.quick_settings_compass_north);
                break;
            case 1:
                // Northeast (NE)
                cardinalDirection = mContext.getString(R.string.quick_settings_compass_north)
                        + mContext.getString(R.string.quick_settings_compass_east);
                break;
            case 2:
                // East (E)
                cardinalDirection = mContext.getString(R.string.quick_settings_compass_east);
                break;
            case 3:
                // Southeast (SE)
                cardinalDirection = mContext.getString(R.string.quick_settings_compass_south)
                        + mContext.getString(R.string.quick_settings_compass_east);
                break;
            case 4:
                // South (S)
                cardinalDirection = mContext.getString(R.string.quick_settings_compass_south);
                break;
            case 5:
                // Southwest (SW)
                cardinalDirection = mContext.getString(R.string.quick_settings_compass_south)
                        + mContext.getString(R.string.quick_settings_compass_west);
                break;
            case 6:
                // West (W)
                cardinalDirection = mContext.getString(R.string.quick_settings_compass_west);
                break;
            case 7:
                // Northwest (NW)
                cardinalDirection = mContext.getString(R.string.quick_settings_compass_north)
                        + mContext.getString(R.string.quick_settings_compass_west);
                break;
        }

        return cardinalDirection;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing here
    }
}
