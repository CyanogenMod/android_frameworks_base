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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.view.View;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class ThemesTile extends QuickSettingsTile {
    private static final String CATEGORY_THEME_CHOOSER = "cyanogenmod.intent.category.APP_THEMES";

    public ThemesTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(CATEGORY_THEME_CHOOSER);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                mStatusbarService.animateCollapsePanels();
            }
        };

        updateResources();
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

    private void updateTile() {
        mDrawable = R.drawable.ic_qs_themes;
        mLabel = mContext.getString(R.string.quick_settings_themes);
    }
}
