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

package com.android.systemui.qs.tiles;

import android.content.Intent;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import org.cyanogenmod.internal.logging.CMMetricsLogger;

public class ThemesTile extends QSTile<QSTile.State> {
    private static final String CATEGORY_THEME_CHOOSER = "cyanogenmod.intent.category.APP_THEMES";

    Icon mIcon;

    public ThemesTile(Host host) {
        super(host);
        mIcon = ResourceIcon.get(R.drawable.ic_qs_themes);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(CATEGORY_THEME_CHOOSER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivity(intent);
        getHost().collapsePanels();
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        state.visible = true;
        state.label = mHost.getContext().getString(R.string.quick_settings_themes_label);
        state.icon = mIcon;
    }

    @Override
    public int getMetricsCategory() {
        return CMMetricsLogger.TILE_THEMES;
    }

    @Override
    public void setListening(boolean listening) {
    }
}