/*
 * Copyright (C) 2014 The NamelessRom Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.internal.util.nameless.NamelessActions;
import com.android.systemui.R;
import com.android.systemui.nameless.onthego.OnTheGoDialog;
import com.android.systemui.nameless.onthego.OnTheGoService;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class OnTheGoTile extends QuickSettingsTile {

    private static final int CAMERA_BACK  = 0;
    private static final int CAMERA_FRONT = 1;

    public OnTheGoTile(final Context context, final QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NamelessActions.processAction(mContext, NamelessActions.ACTION_ONTHEGO_TOGGLE);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new OnTheGoDialog(mContext).show();
                return true;
            }
        };

        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.ON_THE_GO_CAMERA), this);
    }

    @Override
    public void onFlingRight() {
        toggleCamera();
        super.onFlingRight();
    }

    @Override
    public void onFlingLeft() {
        toggleCamera();
        super.onFlingLeft();
    }

    private void toggleCamera() {
        final ContentResolver resolver = mContext.getContentResolver();
        final int camera = Settings.System.getInt(resolver,
                Settings.System.ON_THE_GO_CAMERA,
                0);

        int newValue;
        if (camera == 0) {
            newValue = 1;
        } else {
            newValue = 0;
        }

        Settings.System.putInt(resolver,
                Settings.System.ON_THE_GO_CAMERA,
                newValue);

        updateResources();
        sendCameraBroadcast();
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    private synchronized void updateTile() {
        final int cameraMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.ON_THE_GO_CAMERA,
                0);

        switch (cameraMode) {
            default:
            case CAMERA_BACK:
                mLabel = mContext.getString(R.string.quick_settings_onthego_back);
                mDrawable = R.drawable.ic_qs_onthego;
                break;
            case CAMERA_FRONT:
                mLabel = mContext.getString(R.string.quick_settings_onthego_front);
                mDrawable = R.drawable.ic_qs_onthego_front;
                break;
        }

    }

    private void sendCameraBroadcast() {
        final Intent cameraBroadcast = new Intent();
        cameraBroadcast.setAction(OnTheGoService.ACTION_TOGGLE_CAMERA);
        mContext.sendBroadcast(cameraBroadcast);
    }

}
