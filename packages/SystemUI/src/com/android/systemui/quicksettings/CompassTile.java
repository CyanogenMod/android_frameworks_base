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
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class CompassTile extends QuickSettingsTile implements SensorEventListener {
    private boolean mActive = false;
    private float currentDegree = 0f;

    final float DEFAULT_IMAGE_ROTATION = 90f;
    final int ANIMATION_DURATION = 200;

    private SensorManager mSensorManager;
    private Sensor mOrientation;

    private ImageView image;
    private TextView text;

    public CompassTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        // Initialize SensorManager
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleState();
                updateResources();
            }
        };
    }

    @Override
    void onPostCreate() {
        // Get image and text view
        image = (ImageView) mTile.findViewById(R.id.image);
        text = (TextView) mTile.findViewById(R.id.text);

        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();

        // Reset rotation of the image view
        image.setRotation(0);
    }

    private synchronized void updateTile() {
        if (mActive) {
            mDrawable = R.drawable.ic_qs_compass_on;
            mLabel = "270.0°";

            // Register orientation listner
            mSensorManager.registerListener(this, mOrientation, SensorManager.SENSOR_DELAY_GAME);
        } else {
            // Remove orientation listener
            mSensorManager.unregisterListener(this);

            mDrawable = R.drawable.ic_qs_compass_off;
            mLabel = mContext.getString(R.string.quick_settings_compass_off);
        }
    }

    protected void toggleState() {
        mActive = !mActive;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Get the angle around the z-axis rotated
        float degree = event.values[0];

        // Set rotation as tile label
        text.setText(String.format("%.1f", degree) + "°");

        // Take the default rotation on the compass image into account
        degree += DEFAULT_IMAGE_ROTATION;

        // Create a rotation animation (reverse turn degree degrees)
        RotateAnimation rotateAnimation = new RotateAnimation(currentDegree, -degree,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

        // Set animation duration
        rotateAnimation.setDuration(ANIMATION_DURATION);

        // Set the animation after the end of the reservation status
        rotateAnimation.setFillAfter(true);

        // Start the animation and update currentDegree
        image.startAnimation(rotateAnimation);
        currentDegree = -degree;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing here
    }
}
