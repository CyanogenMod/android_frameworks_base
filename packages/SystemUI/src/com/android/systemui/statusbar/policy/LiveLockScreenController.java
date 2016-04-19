package com.android.systemui.statusbar.policy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.EventLog;

import com.android.systemui.EventLogTags;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.phone.NotificationPanelView;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import cyanogenmod.app.CMContextConstants;
import cyanogenmod.app.ILiveLockScreenChangeListener;
import cyanogenmod.app.ILiveLockScreenManager;
import cyanogenmod.app.LiveLockScreenInfo;
import cyanogenmod.externalviews.KeyguardExternalView;

import java.util.Objects;

public class LiveLockScreenController {
    private static final String TAG = LiveLockScreenController.class.getSimpleName();

    private ILiveLockScreenManager mLLSM;
    private Context mContext;
    private PhoneStatusBar mBar;
    private NotificationPanelView mPanelView;
    private ComponentName mLiveLockScreenComponentName;
    private KeyguardExternalView mLiveLockScreenView;
    private Handler mHandler;

    private int mStatusBarState;

    private PowerManager mPowerManager;

    private boolean mLlsHasFocus = false;

    private boolean mScreenOnAndInteractive;

    public LiveLockScreenController(Context context, PhoneStatusBar bar,
            NotificationPanelView panelView) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());

        mLLSM = ILiveLockScreenManager.Stub.asInterface(ServiceManager.getService(
                CMContextConstants.CM_LIVE_LOCK_SCREEN_SERVICE));
        mBar = bar;
        mPanelView = panelView;
        mPowerManager = context.getSystemService(PowerManager.class);
        registerListener();
        try {
            LiveLockScreenInfo llsInfo = mLLSM.getCurrentLiveLockScreen();
            if (llsInfo != null && llsInfo.component != null) {
                updateLiveLockScreenView(llsInfo.component);
            }
        } catch (RemoteException e) {
            /* ignore */
        }
    }

    public void cleanup() {
        unregisterListener();
        mPanelView = null;
        if (mLiveLockScreenView != null) {
            mLiveLockScreenView.setProviderComponent(null);
        }
        mLiveLockScreenView = null;
        mLiveLockScreenComponentName = null;
    }

    public void setBarState(int statusBarState) {
        if (mStatusBarState != StatusBarState.SHADE && statusBarState == StatusBarState.SHADE) {
            // going from KEYGUARD or SHADE_LOCKED to SHADE so device has been unlocked
            onKeyguardDismissed();
        }

        mStatusBarState = statusBarState;
        if (statusBarState == StatusBarState.KEYGUARD ||
                statusBarState == StatusBarState.SHADE_LOCKED) {
            if (mLiveLockScreenComponentName != null) {
                if (mLiveLockScreenView == null) {
                    mLiveLockScreenView =
                            getExternalKeyguardView(mLiveLockScreenComponentName);
                    if (mLiveLockScreenView != null) {
                        mLiveLockScreenView.registerKeyguardExternalViewCallback(
                                mExternalKeyguardViewCallbacks);
                    }
                }
                if (mLiveLockScreenView != null && !mLiveLockScreenView.isAttachedToWindow()) {
                    mBar.updateRowStates();
                    mPanelView.addView(mLiveLockScreenView, 0);
                }
            }
        } else {
            if (isShowingLiveLockScreenView()) {
                mPanelView.removeView(mLiveLockScreenView);
            }
        }
    }

    private ILiveLockScreenChangeListener mChangeListener =
            new ILiveLockScreenChangeListener.Stub() {
        @Override
        public void onLiveLockScreenChanged(LiveLockScreenInfo llsInfo) throws RemoteException {
            if (mPanelView != null) {
                updateLiveLockScreenView(llsInfo != null ? llsInfo.component : null);
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

    private KeyguardExternalView getExternalKeyguardView(ComponentName componentName) {
        try {
            return new KeyguardExternalView(mContext, null, componentName);
        } catch (Exception e) {
            // just return null below and move on
        }
        return null;
    }

    private KeyguardExternalView.KeyguardExternalViewCallbacks mExternalKeyguardViewCallbacks =
            new KeyguardExternalView.KeyguardExternalViewCallbacks() {
        @Override
        public boolean requestDismiss() {
            if (isShowingLiveLockScreenView()) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBar.showKeyguard();
                        mBar.showBouncer();
                    }
                });
                return true;
            }
            return false;
        }

        @Override
        public boolean requestDismissAndStartActivity(final Intent intent) {
            if (isShowingLiveLockScreenView()) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBar.showKeyguard();
                        mBar.startActivityDismissingKeyguard(intent, false, true, true,
                                null);
                    }
                });
                return true;
            }
            return false;
        }

        @Override
        public void providerDied() {
            mLiveLockScreenView.unregisterKeyguardExternalViewCallback(
                    mExternalKeyguardViewCallbacks);
            mLiveLockScreenView = null;
            // make sure we're showing the notification panel if the LLS crashed while it had focus
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBar.showKeyguard();
                }
            });
        }

        @Override
        public void slideLockscreenIn() {
            if (mPanelView.mShowingExternalKeyguard) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBar.showKeyguard();
                    }
                });
            }
        }
    };

    public boolean isShowingLiveLockScreenView() {
        return mLiveLockScreenView != null && mLiveLockScreenView.isAttachedToWindow();
    }

    public boolean isLiveLockScreenInteractive() {
        return mLiveLockScreenView != null && mLiveLockScreenView.isInteractive();
    }

    public KeyguardExternalView getLiveLockScreenView() {
        return mLiveLockScreenView;
    }

    public void onScreenTurnedOn() {
        mScreenOnAndInteractive = mPowerManager.isInteractive();
        if (mScreenOnAndInteractive) {
            if (mLiveLockScreenView != null) mLiveLockScreenView.onScreenTurnedOn();
            EventLog.writeEvent(EventLogTags.SYSUI_LLS_KEYGUARD_SHOWING, 1);
        }
    }

    public void onScreenTurnedOff() {
        if (mScreenOnAndInteractive) {
            if (mLiveLockScreenView != null) mLiveLockScreenView.onScreenTurnedOff();
            if (mStatusBarState != StatusBarState.SHADE) {
                EventLog.writeEvent(EventLogTags.SYSUI_LLS_KEYGUARD_SHOWING, 0);
            }
            mScreenOnAndInteractive = false;
        }
    }

    public void onLiveLockScreenFocusChanged(boolean hasFocus) {
        if (hasFocus != mLlsHasFocus) {
            mLlsHasFocus = hasFocus;
            // don't log focus changes when screen is not interactive
            if (mPowerManager.isInteractive()) {
                EventLog.writeEvent(EventLogTags.SYSUI_LLS_NOTIFICATION_PANEL_SHOWN,
                        hasFocus ? 0 : 1);
            }
        }
    }

    public void onKeyguardDismissed() {
        if (mLiveLockScreenView != null) mLiveLockScreenView.onKeyguardDismissed();
        EventLog.writeEvent(EventLogTags.SYSUI_LLS_KEYGUARD_DISMISSED, mLlsHasFocus ? 1 : 0);
    }

    private Runnable mAddNewLiveLockScreenRunnable = new Runnable() {
        @Override
        public void run() {
            if (mLiveLockScreenComponentName != null) {
                mLiveLockScreenView =
                        getExternalKeyguardView(mLiveLockScreenComponentName);
                mLiveLockScreenView.registerKeyguardExternalViewCallback(
                        mExternalKeyguardViewCallbacks);
                if (mStatusBarState != StatusBarState.SHADE) {
                    mPanelView.addView(mLiveLockScreenView);
                    mLiveLockScreenView.onKeyguardShowing(true);
                }
            } else {
                mLiveLockScreenView = null;
            }
        }
    };

    private void updateLiveLockScreenView(final ComponentName cn) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                // If mThirdPartyKeyguardViewComponent differs from cn, go ahead and update
                if (!Objects.equals(mLiveLockScreenComponentName, cn)) {
                    mLiveLockScreenComponentName = cn;
                    if (mLiveLockScreenView != null) {
                        mLiveLockScreenView.unregisterKeyguardExternalViewCallback(
                                mExternalKeyguardViewCallbacks);
                        // setProviderComponent(null) will unbind the existing service
                        mLiveLockScreenView.setProviderComponent(null);
                        if (mPanelView.indexOfChild(mLiveLockScreenView) >= 0) {
                            mLiveLockScreenView.registerOnWindowAttachmentChangedListener(
                                    new KeyguardExternalView.OnWindowAttachmentChangedListener() {
                                        @Override
                                        public void onAttachedToWindow() {
                                        }

                                        @Override
                                        public void onDetachedFromWindow() {
                                            mLiveLockScreenView
                                                    .unregisterOnWindowAttachmentChangedListener(
                                                            this);
                                            mHandler.post(mAddNewLiveLockScreenRunnable);
                                        }
                                    }
                            );
                            mPanelView.removeView(mLiveLockScreenView);
                        } else {
                            mAddNewLiveLockScreenRunnable.run();
                        }
                    }
                }
            }
        });
    }
}
