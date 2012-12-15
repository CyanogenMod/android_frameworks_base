package com.android.systemui.quicksettings;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class FlashLightTile extends QuickSettingsTile {

    public FlashLightTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);
        TorchObserver observer = new TorchObserver(handler);
        observer.startObserving();
        updateTileState();
        onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
                mContext.sendBroadcast(i);
            }
        };
        onLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("net.cactii.flash2", "net.cactii.flash2.MainActivity");
                startSettingsActivity(intent);
                return true;
            }
        };
    }

    private void updateTileState() {
        boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.TORCH_STATE, 0) == 1;
        if(enabled) {
            mDrawable = R.drawable.ic_qs_flashlight_on;
            mLabel = mContext.getString(R.string.quick_settings_label_enabled);
        } else {
            mDrawable = R.drawable.ic_qs_flashlight_off;
            mLabel = mContext.getString(R.string.quick_settings_label_disabled);
        }
    }

    private class TorchObserver extends ContentObserver {
        public TorchObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateTileState();
            updateQuickSettings();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.TORCH_STATE), false, this);
        }
    }

}
