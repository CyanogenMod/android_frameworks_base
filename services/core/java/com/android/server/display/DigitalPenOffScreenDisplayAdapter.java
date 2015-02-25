/*===========================================================================
                   DigitalPenOffScreenDisplayAdapter

Copyright (c) 2014, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
=============================================================================*/

package com.android.server.display;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.Display;
import android.view.SurfaceControl;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.content.res.Resources;
import codeaurora.ultrasound.IDigitalPenDimensionsCallback;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Provides a virtual display for the digital pen off-screen
 * work area.
 * <p>
 * Display adapters are guarded by the {@link DisplayManagerService.SyncRoot} lock.
 * </p>
 */
public final class DigitalPenOffScreenDisplayAdapter extends DisplayAdapter {
    private static final String TAG = "DigitalPenOffScreenDisplayAdapter";
    private DigitalPenOffScreenDisplayDevice mDevice;
    public static final String DISPLAY_NAME = "Digital Pen off-screen display";
    private static final boolean mDigitalPenCapable =
    Resources.getSystem().getBoolean(com.android.internal.R.bool.config_digitalPenCapable);
    private DimensionsCallback mCallback;

    // Called with SyncRoot lock held.
    public DigitalPenOffScreenDisplayAdapter(DisplayManagerService.SyncRoot syncRoot,
            Context context, Handler handler, Listener listener) {
        super(syncRoot, context, handler, listener, TAG);
        mCallback = new DimensionsCallback();
    }

    public static boolean isDigitalPenDisabled() {
        return !mDigitalPenCapable;
    }

    public static String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public void registerLocked() {
        super.registerLocked();

        IBinder displayToken = SurfaceControl.createDisplay(getDisplayName(), false);

        mDevice = new DigitalPenOffScreenDisplayDevice(displayToken);
        sendDisplayDeviceEventLocked(mDevice, DISPLAY_DEVICE_EVENT_ADDED);

        registerOffScreenDimensionsCallbackLocked();
    }

    private void registerOffScreenDimensionsCallbackLocked() {
        IBinder digitalPenService = ServiceManager.getService("DigitalPen");
        if (digitalPenService == null) {
            return;
        }
        Class digitalPenServiceClass = digitalPenService.getClass();
        try {
            Class args[] = {
                IDigitalPenDimensionsCallback.class
            };

            Method registerCallbackMethod =
            digitalPenServiceClass.getMethod("registerOffScreenDimensionsCallback", args);
            registerCallbackMethod.invoke(digitalPenService,
                                          mCallback);
        } catch (InvocationTargetException e) {
            Slog.i(TAG,
                   "DigitalPenService.registerOffScreenDimensionsCallback - InvocationTargetException");
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            Slog.i(TAG,
                   "DigitalPenService.registerOffScreenDimensionsCallback - IllegalAccessException");
            e.printStackTrace();
        } catch (Throwable t) {
            Slog.i(TAG, "DigitalPenService.registerOffScreenDimensionsCallback Not available.");
            t.printStackTrace();
        }
    }

    private final class DigitalPenOffScreenDisplayDevice extends DisplayDevice {
        private String mName;
        private int mWidth;
        private int mHeight;
        private final float mRefreshRate;
        private final int mDensityDpi;
        private final float mXDpi;
        private final float mYDpi;
        private final int mFlags;
        private final int mType;
        private final int mTouch;
        private DisplayDeviceInfo mInfo;

        public DigitalPenOffScreenDisplayDevice(IBinder displayToken) {
            super(DigitalPenOffScreenDisplayAdapter.this, displayToken);
            mName = getDisplayName();
            mWidth = 480;
            mHeight = 640;
            mRefreshRate = 60;
            mDensityDpi = DisplayMetrics.DENSITY_DEFAULT;
            mXDpi = 160;
            mYDpi = 160;
            mFlags = DisplayDeviceInfo.FLAG_ROTATES_WITH_CONTENT;
            mType = Display.TYPE_BUILT_IN;
            mTouch = DisplayDeviceInfo.TOUCH_EXTERNAL;
        }

        @Override
        public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
            if (mInfo == null) {
                Slog.i(TAG, "mInfo is null, getting a new one");
                mInfo = new DisplayDeviceInfo();
                mInfo.name = mName;
                mInfo.width = mWidth;
                mInfo.height = mHeight;
                mInfo.refreshRate = mRefreshRate;
                mInfo.densityDpi = mDensityDpi;
                mInfo.xDpi = mXDpi;
                mInfo.yDpi = mYDpi;
                mInfo.flags = mFlags;
                mInfo.type = mType;
                mInfo.touch = mTouch;
            }
            return mInfo;
        }

        public void setDimesionsLocked(int width, int height) {
            mHeight = height;
            mWidth = width;
            mInfo = null;
        }
    }

    private final class DimensionsCallback extends IDigitalPenDimensionsCallback.Stub {
        public void onDimensionsChange(int width, int height) {
            Slog.i(TAG, "DimensionsCallback was called with w: " + width + " h: " + height);
            synchronized (getSyncRoot()) {
                mDevice.setDimesionsLocked(width, height);
                sendDisplayDeviceEventLocked(mDevice, DISPLAY_DEVICE_EVENT_REMOVED);
                sendDisplayDeviceEventLocked(mDevice, DISPLAY_DEVICE_EVENT_ADDED);
            }
        }
    }
}
