package com.android.server.statusbar;

import android.app.CustomTile;
import android.os.UserHandle;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Created by Adnan on 3/1/15.
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

    public String getKey() {
        return sbTile.getKey();
    }
}
