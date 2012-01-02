package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.IWindowManager;

import java.util.ArrayList;
import java.util.List;

public class AutoRotateButton extends PowerButton {

    private static final String TAG = "AutoRotateButton";

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION));
    }

    public AutoRotateButton() { mType = BUTTON_AUTOROTATE; }

    @Override
    protected void updateState() {
        if (getAutoRotation()) {
            mIcon = R.drawable.stat_orientation_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_orientation_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        setAutoRotation(!getAutoRotation());
    }

    @Override
    protected boolean handleLongClick() {
        Intent intent = new Intent("android.settings.DISPLAY_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
        return true;
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    private boolean getAutoRotation() {
        ContentResolver cr = mView.getContext().getContentResolver();
        return 0 != Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0);
    }

    private void setAutoRotation(final boolean autorotate) {
        AsyncTask.execute(new Runnable() {
                public void run() {
                    try {
                        IWindowManager wm = IWindowManager.Stub.asInterface(
                                ServiceManager.getService(Context.WINDOW_SERVICE));
                        if (autorotate) {
                            wm.thawRotation();
                        } else {
                            wm.freezeRotation(-1);
                        }
                    } catch (RemoteException exc) {
                        Log.w(TAG, "Unable to save auto-rotate setting");
                    }
                }
            });
    }
}
