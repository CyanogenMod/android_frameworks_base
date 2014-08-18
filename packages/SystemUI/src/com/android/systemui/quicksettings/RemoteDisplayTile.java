/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.content.Intent;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteInfo;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class RemoteDisplayTile extends QuickSettingsTile implements
        QuickSettingsTileView.OnPrepareListener {
    private final MediaRouter mMediaRouter;
    private RouteInfo mConnectedRoute;
    private boolean mEnabled;
    private final ExecutorService mExecutor;

    /** Callback for changes to remote display routes. */
    private final MediaRouter.SimpleCallback mRemoteDisplayRouteCallback =
            new MediaRouter.SimpleCallback() {
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
    };

    public RemoteDisplayTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_WIFI_DISPLAY_SETTINGS);
            }
        };

        mMediaRouter = (MediaRouter) context.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mExecutor = Executors.newSingleThreadExecutor();
    }

    private void updateRemoteDisplays() {
        RouteInfo connectedRoute =
                mMediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY);

        if (connectedRoute != null &&
                (connectedRoute.getSupportedTypes() & MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY) != 0) {
            mEnabled = true;
            mConnectedRoute = connectedRoute;
        } else {
            mEnabled = mMediaRouter.isRouteAvailable(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY,
                    MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE);
            mConnectedRoute = null;
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
        mTile.setOnPrepareListener(this);
        updateRemoteDisplays();
        super.onPostCreate();
    }

    @Override
    public void onPrepare() {
        mExecutor.submit(mRegisterRunnable);
    }

    @Override
    public void onUnprepare() {
        mExecutor.submit(mUnRegisterRunnable);
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

    private void updateTile() {
        if (mEnabled && mConnectedRoute != null) {
            mLabel = mConnectedRoute.getName().toString();
            mDrawable = mConnectedRoute.isConnecting() ?
                    R.drawable.ic_qs_cast_connecting : R.drawable.ic_qs_cast_connected;
        } else {
            mLabel = mContext.getString(R.string.quick_settings_remote_display_no_connection_label);
            mDrawable = R.drawable.ic_qs_cast_available;
        }
    }

    @Override
    void updateQuickSettings() {
        mTile.setVisibility(mEnabled ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }
}
