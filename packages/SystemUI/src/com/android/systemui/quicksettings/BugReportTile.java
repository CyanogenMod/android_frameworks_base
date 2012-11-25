package com.android.systemui.quicksettings;

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class BugReportTile extends QuickSettingsTile{

    private boolean enabled = false;
    private final Handler mHandler;

    public BugReportTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mHandler = handler;
        mLabel = mContext.getString(R.string.quick_settings_report_bug);
        mDrawable = com.android.internal.R.drawable.stat_sys_adb;

        try {
            enabled = (Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.BUGREPORT_IN_POWER_MENU) != 0);
        } catch (SettingNotFoundException e) {
        }

        BugreportObserver observer = new BugreportObserver(mHandler);
        observer.startObserving();

        onClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mQsc.mBar.collapseAllPanels(true);
                showBugreportDialog();
            }
        };
    }

    private class BugreportObserver extends ContentObserver {
        public BugreportObserver(Handler handler) {
            super(handler);
        }

        @Override public void onChange(boolean selfChange) {
            onBugreportChanged();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BUGREPORT_IN_POWER_MENU), false, this);
        }
    }

    public void onBugreportChanged() {
        final ContentResolver cr = mContext.getContentResolver();
        try {
            enabled = (Settings.Secure.getInt(cr, Settings.Secure.BUGREPORT_IN_POWER_MENU) != 0);
        } catch (SettingNotFoundException e) {
        }
        updateQuickSettings();
    }

    @Override
    void updateQuickSettings() {
        mTile.setVisibility(enabled ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }

    private void showBugreportDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setPositiveButton(com.android.internal.R.string.report, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Add a little delay before executing, to give the
                    // dialog a chance to go away before it takes a
                    // screenshot.
                    mHandler.postDelayed(new Runnable() {
                        @Override public void run() {
                            try {
                                ActivityManagerNative.getDefault()
                                        .requestBugReport();
                            } catch (RemoteException e) {
                            }
                        }
                    }, 500);
                }
            }
        });
        builder.setMessage(com.android.internal.R.string.bugreport_message);
        builder.setTitle(com.android.internal.R.string.bugreport_title);
        builder.setCancelable(true);
        final Dialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        try {
            WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
        } catch (RemoteException e) {
        }
        dialog.show();
    }

}
