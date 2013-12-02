/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 CyanogenMod Project
 * Copyright (C) 2013 The SlimRoms Project
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

package com.android.systemui.quicksettings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

public abstract class NetworkTile extends QuickSettingsTile
        implements NetworkSignalChangedCallback {
    private NetworkController mController;

    private final long mDefaultDuration = new ValueAnimator().getDuration();
    private final long mShortDuration = mDefaultDuration / 3;

    protected NetworkTile(Context context, QuickSettingsController qsc,
            NetworkController controller, int layoutResourceId) {
        super(context, qsc, layoutResourceId);

        mController = controller;
    }

    @Override
    void onPostCreate() {
        mController.addNetworkSignalChangedCallback(this);
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mController.removeNetworkSignalChangedCallback(this);
        super.onDestroy();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    protected abstract void updateTile();

    protected void setActivity(boolean in, boolean out) {
        setVisibility(mTile.findViewById(R.id.activity_in), in);
        setVisibility(mTile.findViewById(R.id.activity_out), out);
    }

    private void setVisibility(View view, boolean visible) {
        final float newAlpha = visible ? 1 : 0;
        if (view.getAlpha() != newAlpha) {
            view.animate()
                .setDuration(visible ? mShortDuration : mDefaultDuration)
                .alpha(newAlpha)
                .start();
        }
    }
}
