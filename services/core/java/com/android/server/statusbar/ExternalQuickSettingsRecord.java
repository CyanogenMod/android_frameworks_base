package com.android.server.statusbar;

import android.app.CustomTile;
import android.os.UserHandle;
import android.service.statusbar.StatusBarPanelCustomTile;
import com.android.internal.annotations.VisibleForTesting;

/**
 * @hide
 */
public class ExternalQuickSettingsRecord {
    final StatusBarPanelCustomTile sbTile;
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
        return sbTile.getUid();
    }

    public String getKey() {
        return sbTile.getKey();
    }
}
