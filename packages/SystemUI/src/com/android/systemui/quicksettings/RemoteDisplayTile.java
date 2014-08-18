package com.android.systemui.quicksettings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteInfo;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class RemoteDisplayTile extends QuickSettingsTile{

    private boolean enabled = false;
    private boolean connecting;
    private final MediaRouter mMediaRouter;
    private final RemoteDisplayRouteCallback mRemoteDisplayRouteCallback;
    private MediaRouter.RouteInfo connectedRoute;
    private final ExecutorService mExecutor;

    public RemoteDisplayTile(Context context, 
            QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_WIFI_DISPLAY_SETTINGS);
            }
        };
        mMediaRouter = (MediaRouter)context.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mRemoteDisplayRouteCallback = new RemoteDisplayRouteCallback();
        mExecutor = Executors.newSingleThreadExecutor();
    }

    /** Callback for changes to remote display routes. */
    private class RemoteDisplayRouteCallback extends MediaRouter.SimpleCallback {
        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo route) {
            updateRemoteDisplays();
        }
        @Override
        public void onRouteChanged(MediaRouter router, RouteInfo route) {
            updateRemoteDisplays();
        }
        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo route) {
            updateRemoteDisplays();
        }
        @Override
        public void onRouteSelected(MediaRouter router, int type, RouteInfo route) {
            updateRemoteDisplays();
        }
        @Override
        public void onRouteUnselected(MediaRouter router, int type, RouteInfo route) {
            updateRemoteDisplays();
        }
    }

    private void updateRemoteDisplays() {
        connectedRoute = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY);
        enabled = connectedRoute != null && (connectedRoute.getSupportedTypes()
                & MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY) != 0;

        if (enabled) {
            connecting = connectedRoute.isConnecting();
        } else {
            connectedRoute = null;
            connecting = false;
            enabled = mMediaRouter.isRouteAvailable(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY,
                    MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE);
        }

        updateResources();
    }

    private final Runnable mRegisterRunnable = new Runnable() {
        @Override
        public void run() {
            mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY,
                    mRemoteDisplayRouteCallback,
                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
            updateRemoteDisplays();
        }
    };

    private final Runnable mUnRegisterRunnable = new Runnable() {
        @Override
        public void run() {
            mMediaRouter.removeCallback(mRemoteDisplayRouteCallback);
        }
    };

    @Override
    void onPostCreate() {
        mTile.setOnPrepareListener(new QuickSettingsTileView.OnPrepareListener() {
            @Override
            public void onPrepare() {
                mExecutor.submit(mRegisterRunnable);
            }
            @Override
            public void onUnprepare() {
                mExecutor.submit(mUnRegisterRunnable);
            }
        });

        updateRemoteDisplays();

        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdownNow();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        if(enabled && (connectedRoute != null)) {
            mLabel = connectedRoute.getName().toString();
            mDrawable = connecting ?
                R.drawable.ic_qs_cast_connecting : R.drawable.ic_qs_cast_connected;
        }else{
            mLabel = mContext.getString(R.string.quick_settings_remote_display_no_connection_label);
            mDrawable = R.drawable.ic_qs_cast_available;
        }
    }

    @Override
    void updateQuickSettings() {
        mTile.setVisibility(enabled ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }
}
