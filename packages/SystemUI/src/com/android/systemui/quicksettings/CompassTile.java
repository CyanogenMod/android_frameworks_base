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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class CompassTile extends QuickSettingsTile implements
        SensorEventListener, View.OnClickListener {
    private boolean mActive = false;
    private boolean mPrepared = false;
    private float mCurrentDegree = 0;

    private final static float DEFAULT_IMAGE_ROTATION = 90;
    private final static int ANIMATION_DURATION = 200;
    private final static float ALPHA = 0.97f;

    private SensorManager mSensorManager;
    private Sensor mAccelerationSensor;
    private Sensor mGeomagneticFieldSensor;

    private float[] mAcceleration;
    private float[] mGeomagnetic;

    private TextView mText;
    private ImageView mImage;

    public CompassTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        // Initialize sensors
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGeomagneticFieldSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    public void setupQuickSettingsTile(LayoutInflater inflater,
            QuickSettingsContainerView container) {
        super.setupQuickSettingsTile(inflater, container);
        // override click listener to prevent collapsing the panel on click
        mTile.setOnClickListener(this);
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
                mPrepared = true;
                if (mActive) {
                    updateResources();
                }
            }

            @Override
            public void onUnprepare() {
                mPrepared = false;
                if (mActive) {
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

    @Override
    public void onClick(View v) {
        mActive = !mActive;
        updateResources();
    }

    private synchronized void updateTile() {
        if (mActive && mPrepared) {
            mDrawable = R.drawable.ic_qs_compass_on;
            mLabel = mContext.getString(R.string.quick_settings_compass_init);

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

    private String formatValueWithCardinalDirection(float degree) {
        int cardinalDirectionIndex = (int) (Math.floor(((degree - 22.5) % 360) / 45) + 1) % 8;
        String[] cardinalDirections = mContext.getResources().getStringArray(
                R.array.cardinal_directions);

        return mContext.getString(R.string.quick_settings_compass_value, degree,
                cardinalDirections[cardinalDirectionIndex]);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            float[] values;
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                if (mAcceleration == null) {
                    mAcceleration = event.values.clone();
                }
                values = mAcceleration;
            } else {
                // magnetic field sensor
                if (mGeomagnetic == null) {
                    mGeomagnetic = event.values.clone();
                }
                values = mGeomagnetic;
            }

            for (int i = 0; i < 3; i++) {
                values[i] = ALPHA * values[i] + (1 - ALPHA) * event.values[i];
            }

            if (!mActive || !mPrepared || mAcceleration == null || mGeomagnetic == null) {
                // nothing to do at this moment
                return;
            }

            float R[] = new float[9];
            float I[] = new float[9];
            if (!SensorManager.getRotationMatrix(R, I, mAcceleration, mGeomagnetic)) {
                // rotation matrix couldn't be calculated
                return;
            }

            float orientation[] = new float[3];
            SensorManager.getOrientation(R, orientation);

            // Convert azimuth to degrees
            float degree = (float) Math.toDegrees(orientation[0]);
            degree = (degree + 360) % 360;

            // Set rotation in degrees as tile title
            mText.setText(formatValueWithCardinalDirection(degree));

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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing here
    }
}
