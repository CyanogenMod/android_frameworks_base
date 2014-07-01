
package com.android.server;

import android.content.Context;
import android.content.Intent;
import android.hardware.ITorchService;
import android.util.Log;

public class TorchService extends ITorchService.Stub {

    private static final boolean DEBUG = true;
    private static final String TAG = TorchService.class.getSimpleName();

    private final Context mContext;
    private boolean mEatTorchLaunch = false;

    public TorchService(Context context) {
        mContext = context;
    }

    @Override
    public void onCameraOpened() {
        if (DEBUG) Log.d(TAG, "onCameraOpened()");
        if (mEatTorchLaunch) {
            if (DEBUG)
                Log.d(TAG, "eating torch suppression");
            mEatTorchLaunch = false;
        } else {
            if (DEBUG)
                Log.d(TAG, "killing torch");
            Intent i = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
            i.putExtra("stop", true);
            mContext.sendOrderedBroadcast(i, null);
        }
    }

    @Override
    public void onStartingTorch() {
        if (DEBUG) Log.d(TAG, "onStartingTorch()");
        mEatTorchLaunch = true;
    }

}
