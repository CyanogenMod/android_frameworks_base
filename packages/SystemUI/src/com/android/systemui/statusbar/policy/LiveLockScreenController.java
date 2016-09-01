package com.android.systemui.statusbar.policy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.EventLog;

import android.view.View;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.EventLogTags;
import com.android.systemui.SystemUIApplication;
import com.android.systemui.keyguard.KeyguardViewMediator;
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

    private String mLlsName;
    private KeyguardViewMediator mKeyguardViewMediator;

    public LiveLockScreenController(Context context, PhoneStatusBar bar,
            NotificationPanelView panelView) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());

        mLLSM = ILiveLockScreenManager.Stub.asInterface(ServiceManager.getService(
                CMContextConstants.CM_LIVE_LOCK_SCREEN_SERVICE));
        mBar = bar;
        mPanelView = panelView;
        mPowerManager = context.getSystemService(PowerManager.class);
        mKeyguardViewMediator = ((SystemUIApplication)
                mContext.getApplicationContext()).getComponent(KeyguardViewMediator.class);
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

        if (statusBarState == StatusBarState.KEYGUARD) {
            mBar.getScrimController().forceHideScrims(false);
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
            if (isShowingLiveLockScreenView() && !mBar.isKeyguardInputRestricted()) {
                mPanelView.removeView(mLiveLockScreenView);
            }
            mLlsHasFocus = false;
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
            if (mLlsHasFocus) {
                mLlsHasFocus = false;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBar.showKeyguard();
                    }
                });
            }
        }

        @Override
        public void slideLockscreenIn() {
            if (mLlsHasFocus) {
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
        mKeyguardViewMediator.notifyKeyguardPanelFocusChanged(hasFocus);
        if (mLiveLockScreenView != null) {
            // make sure the LLS knows where the notification panel is
            mLiveLockScreenView.onLockscreenSlideOffsetChanged(hasFocus ? 0f : 1f);
        }
        // don't log focus changes when screen is not interactive
        if (hasFocus != mLlsHasFocus && mPowerManager.isInteractive()) {
            EventLog.writeEvent(EventLogTags.SYSUI_LLS_NOTIFICATION_PANEL_SHOWN,
                    hasFocus ? 0 : 1);
        }
        // Hide statusbar and scrim if live lockscreen
        // currently has focus
        mBar.setStatusBarViewVisibility(!hasFocus);
        mBar.getScrimController().forceHideScrims(hasFocus);
        mLlsHasFocus = hasFocus;
    }

    public void onKeyguardDismissed() {
        if (mLiveLockScreenView != null) mLiveLockScreenView.onKeyguardDismissed();
        EventLog.writeEvent(EventLogTags.SYSUI_LLS_KEYGUARD_DISMISSED, mLlsHasFocus ? 1 : 0);
        // Ensure we reset visibility when keyguard is dismissed
        mBar.setStatusBarViewVisibility(true);
        mBar.getScrimController().forceHideScrims(false);
    }

    public boolean getLiveLockScreenHasFocus() {
        return mLlsHasFocus;
    }

    public String getLiveLockScreenName() {
        return mLlsName;
    }

    private String getLlsNameFromComponentName(ComponentName cn) {
        if (cn == null) return null;

        PackageManager pm = mContext.getPackageManager();
        Intent intent = new Intent();
        intent.setComponent(cn);
        ResolveInfo ri = pm.resolveService(intent, 0);
        return ri != null ? ri.serviceInfo.loadLabel(pm).toString() : null;
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
                    mLlsName = getLlsNameFromComponentName(cn);
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
