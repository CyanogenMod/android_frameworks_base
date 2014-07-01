package com.android.server;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.ITorchService;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Log;

public class TorchService extends ITorchService.Stub {
    private static final boolean DEBUG = false;
    private static final String TAG = TorchService.class.getSimpleName();

    private final Context mContext;
    private int mTorchAppUid = 0;

    public TorchService(Context context) {
        mContext = context;
    }

    @Override
    public void onCameraOpened() {
        if (DEBUG) Log.d(TAG, "onCameraOpened()");
        if (mTorchAppUid != 0 && Binder.getCallingUid() == mTorchAppUid) {
            if (DEBUG) Log.d(TAG, "camera was opened by torch app");
        } else {
            if (DEBUG) Log.d(TAG, "killing torch");
            Intent i = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
            i.putExtra("stop", true);
            mContext.sendOrderedBroadcastAsUser(i, UserHandle.CURRENT_OR_SELF, null,
                    null, null, Activity.RESULT_OK, null, null);
        }
    }

    @Override
    public void onStartingTorch() {
        if (DEBUG) Log.d(TAG, "onStartingTorch()");
        mTorchAppUid = Binder.getCallingUid();
    }
}
