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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class CompassTile extends QuickSettingsTile implements SensorEventListener {
    private boolean mActive = false;
    private float mCurrentDegree = 0;

    private final static float DEFAULT_IMAGE_ROTATION = 90;
    private final static int ANIMATION_DURATION = 200;

    private SensorManager mSensorManager;
    private Sensor mAcclerometerSensor;
    private Sensor mMagneticFieldSensor;

    private float[] mGravity;
    private float[] mGeomagnetic;

    private TextView mText;
    private ImageView mImage;

    public CompassTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        // Initialize sensors
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAcclerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagneticFieldSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

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
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        if (mActive) {
            mDrawable = R.drawable.ic_qs_compass_on;
            mLabel = "270° - " + mContext.getString(R.string.quick_settings_compass_west);

            // Register listeners
            mSensorManager.registerListener(this, mAcclerometerSensor, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this, mMagneticFieldSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            mDrawable = R.drawable.ic_qs_compass_off;
            mLabel = mContext.getString(R.string.quick_settings_compass_off);

            // Reset rotation of image view
            mImage.setRotation(0);

            // Remove listeners
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mGravity = event.values;
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = event.values;
        }

        if (mActive && mGravity != null && mGeomagnetic != null) {
            float r[] = new float[9];
            float i[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(r, i, mGravity, mGeomagnetic);

            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(r, orientation);

                // Get the angle around the z-axis rotated
                float degree =  (float) Math.toDegrees(orientation[0]);
                if (degree < 0) {
                    degree += 360;
                }

                // Set rotation as tile label
                mText.setText(String.format("%s", Math.round(degree))
                        + "° - " + getCardinalDirection(degree));

                // Take the default rotation of the compass image into account
                degree += DEFAULT_IMAGE_ROTATION;

                // Create a rotation animation (reverse turn degree degrees)
                RotateAnimation rotateAnimation = new RotateAnimation(mCurrentDegree, -degree,
                        Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

                // Set animation duration
                rotateAnimation.setDuration(ANIMATION_DURATION);

                // Set the animation after the end of the reservation status
                rotateAnimation.setFillAfter(true);

                // Start the animation and update mCurrentDegree
                mImage.startAnimation(rotateAnimation);
                mCurrentDegree = -degree;
            }
        }
    }

    private String getCardinalDirection(float degree) {
        String cardinalDirection = "";
        if (degree > 348.75 || degree <= 11.25) {
            // N
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_north);
        } else if (degree > 11.25 && degree <= 33.75) {
            // NNE
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_north)
                    + mContext.getString(R.string.quick_settings_compass_north)
                    + mContext.getString(R.string.quick_settings_compass_east);
        } else if (degree > 33.75 && degree <= 56.25) {
            // NE
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_north)
                    + mContext.getString(R.string.quick_settings_compass_east);
        } else if (degree > 56.25 && degree <= 78.75) {
            // ENE
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_east)
                    + mContext.getString(R.string.quick_settings_compass_north)
                    + mContext.getString(R.string.quick_settings_compass_east);
        } else if (degree > 78.75 && degree <= 101.25) {
            // E
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_east);
        } else if (degree > 101.25 && degree <= 123.75) {
            // ESE
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_east)
                    + mContext.getString(R.string.quick_settings_compass_south)
                    + mContext.getString(R.string.quick_settings_compass_east);
        } else if (degree > 123.75 && degree <= 146.25) {
            // SE
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_south)
                    + mContext.getString(R.string.quick_settings_compass_east);
        } else if (degree > 146.25 && degree <= 168.75) {
            // SSE
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_south)
                    + mContext.getString(R.string.quick_settings_compass_south)
                    + mContext.getString(R.string.quick_settings_compass_east);
        } else if (degree > 168.75 && degree <= 191.25) {
            // S
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_south);
        } else if (degree > 191.25 && degree <= 213.75) {
            // SSW
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_south)
                    + mContext.getString(R.string.quick_settings_compass_south)
                    + mContext.getString(R.string.quick_settings_compass_west);
        } else if (degree > 213.75 && degree <= 236.25) {
            // SW
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_south)
                    + mContext.getString(R.string.quick_settings_compass_west);
        } else if (degree > 236.25 && degree <= 258.75) {
            // WSW
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_west)
                    + mContext.getString(R.string.quick_settings_compass_south)
                    + mContext.getString(R.string.quick_settings_compass_west);
        } else if (degree > 258.75 && degree <= 281.25) {
            // W
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_west);
        } else if (degree > 281.25 && degree <= 303.75) {
            // WNW
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_west)
                    + mContext.getString(R.string.quick_settings_compass_north)
                    + mContext.getString(R.string.quick_settings_compass_west);
        } else if (degree > 303.75 && degree <= 326.25) {
            // NW
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_north)
                    + mContext.getString(R.string.quick_settings_compass_west);
        } else if (degree > 326.25 && degree <= 348.75) {
            // NNW
            cardinalDirection = mContext.getString(R.string.quick_settings_compass_north)
                    + mContext.getString(R.string.quick_settings_compass_north)
                    + mContext.getString(R.string.quick_settings_compass_west);
        }

        return cardinalDirection;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing here
    }
}
