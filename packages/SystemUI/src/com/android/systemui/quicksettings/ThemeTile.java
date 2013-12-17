/*
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.internal.util.slim.ButtonsConstants;
import com.android.internal.util.slim.SlimActions;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class ThemeTile extends QuickSettingsTile {

    private static final int THEME_MODE_MANUAL       = 0;
    private static final int THEME_MODE_LIGHT_SENSOR = 1;
    private static final int THEME_MODE_TWILIGHT     = 2;

    private int mThemeAutoMode;

    public ThemeTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // SlimAction can take care of it
                // will collapse as well automatically
                // the drawer to reconstruct it or show
                // the toast message if not possible
                SlimActions.processAction(mContext, ButtonsConstants.ACTION_THEME_SWITCH, false);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mThemeAutoMode == THEME_MODE_TWILIGHT) {
                    mThemeAutoMode = THEME_MODE_MANUAL;
                } else {
                    mThemeAutoMode = mThemeAutoMode + 1;
                }

                Settings.Secure.putIntForUser(mContext.getContentResolver(),
                        Settings.Secure.UI_THEME_AUTO_MODE, mThemeAutoMode,
                        UserHandle.USER_CURRENT);
                return true;
            }
        };

        qsc.registerObservedContent(Settings.Secure.getUriFor(
                    Settings.Secure.UI_THEME_AUTO_MODE), this);
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
        mThemeAutoMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.UI_THEME_AUTO_MODE, THEME_MODE_MANUAL,
                UserHandle.USER_CURRENT);

        switch (mThemeAutoMode) {
            case THEME_MODE_MANUAL:
                mDrawable = R.drawable.ic_qs_theme_manual;
                break;
            case THEME_MODE_LIGHT_SENSOR:
                mDrawable = R.drawable.ic_qs_theme_lightsensor;
                break;
            case THEME_MODE_TWILIGHT:
                mDrawable = R.drawable.ic_qs_theme_twilight;
                break;
        }

        if (mContext.getResources().getConfiguration().uiThemeMode
                == Configuration.UI_THEME_MODE_HOLO_DARK) {
            mLabel = mContext.getString(R.string.quick_settings_theme_switch_dark);
        } else {
            mLabel = mContext.getString(R.string.quick_settings_theme_switch_light);
        }
    }

}
