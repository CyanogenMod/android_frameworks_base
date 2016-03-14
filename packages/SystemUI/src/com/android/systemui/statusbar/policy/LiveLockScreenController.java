package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.systemui.statusbar.phone.NotificationPanelView;

import cyanogenmod.app.CMContextConstants;
import cyanogenmod.app.ILiveLockScreenChangeListener;
import cyanogenmod.app.ILiveLockScreenManager;
import cyanogenmod.app.LiveLockScreenInfo;

public class LiveLockScreenController {
    private static final String TAG = LiveLockScreenController.class.getSimpleName();

    private ILiveLockScreenManager mLLSM;
    private Context mContext;
    private NotificationPanelView mPanelView;

    public LiveLockScreenController(Context context, NotificationPanelView panelView) {
        mContext = context;
        mLLSM = ILiveLockScreenManager.Stub.asInterface(ServiceManager.getService(
                CMContextConstants.CM_LIVE_LOCK_SCREEN_SERVICE));
        mPanelView = panelView;
        registerListener();
        try {
            LiveLockScreenInfo llsInfo = mLLSM.getCurrentLiveLockScreen();
            if (llsInfo != null && llsInfo.component != null) {
                panelView.setExternalKeyguardViewComponent(llsInfo.component);
            }
        } catch (RemoteException e) {
            /* ignore */
        }
    }

    public void cleanup() {
        unregisterListener();
        mPanelView = null;
    }

    private ILiveLockScreenChangeListener mChangeListener =
            new ILiveLockScreenChangeListener.Stub() {
        @Override
        public void onLiveLockScreenChanged(LiveLockScreenInfo llsInfo) throws RemoteException {
            if (mPanelView != null) {
                mPanelView.setExternalKeyguardViewComponent(llsInfo != null
                        ? llsInfo.component : null);
            }
        }
    };

    private void registerListener() {
        try {
            mLLSM.registerChangeListener(mChangeListener);
        } catch (RemoteException e) {
            /* ignore */
        }
    }

    private void unregisterListener() {
        try {
            mLLSM.unregisterChangeListener(mChangeListener);
        } catch (RemoteException e) {
            /* ignore */
        }
    }
}
