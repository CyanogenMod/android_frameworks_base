/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cyanogenmod.internal.statusbar;

import android.os.UserHandle;
import com.android.internal.annotations.VisibleForTesting;

import cyanogenmod.app.CustomTile;

/**
 * @hide
 */
public class ExternalQuickSettingsRecord {
    public final StatusBarPanelCustomTile sbTile;
    public boolean isUpdate;
    public boolean isCanceled;

    @VisibleForTesting
    public ExternalQuickSettingsRecord(StatusBarPanelCustomTile tile) {
        sbTile = tile;
    }

    public CustomTile getCustomTile() {
        return sbTile.getCustomTile();
    }

    public UserHandle getUser() {
        return sbTile.getUser();
    }

    public int getUserId() {
        return sbTile.getUserId();
    }

    public String getKey() {
        return sbTile.getKey();
    }
}
