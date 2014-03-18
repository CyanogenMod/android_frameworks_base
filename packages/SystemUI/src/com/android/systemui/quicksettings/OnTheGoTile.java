/*
* <!--
*    Copyright (C) 2014 The NamelessROM Project
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
* -->
*/

package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.internal.util.nameless.NamelessActions;
import com.android.internal.util.nameless.NamelessUtils;
import com.android.systemui.R;
import com.android.systemui.nameless.onthego.OnTheGoDialog;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

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
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private void toggleCamera() {
        final ContentResolver resolver = mContext.getContentResolver();
        final int camera = Settings.System.getInt(resolver,
                Settings.System.ON_THE_GO_CAMERA,
                CAMERA_BACK);

        int newValue;
        if (camera == CAMERA_BACK) {
            newValue = CAMERA_FRONT;
        } else {
            newValue = CAMERA_BACK;
        }

        Settings.System.putInt(resolver,
                Settings.System.ON_THE_GO_CAMERA,
                newValue);

        updateResources();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    private synchronized void updateTile() {

        int cameraMode;

        if (NamelessUtils.hasFrontCamera(mContext)) {
            cameraMode = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.ON_THE_GO_CAMERA,
                    CAMERA_BACK);
        } else {
            cameraMode = CAMERA_BACK;
        }

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

}

