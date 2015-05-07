/* Copyright (c) 2015-2016 The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*       notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*       with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*       contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*/

package android.util;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.os.SystemProperties;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;

/** @hide */
public class ResolutionOverride {
    /** @hide */
    static private final boolean DEBUG = false;
    static private final String TAG = "ResolutionOverride";
    private static final String RES_OVERRIDE = "persist.debug.app_res_override";
    private boolean mIsEnabled = false;
    private int mOverrideXres = 0;
    private int mOverrideYres = 0;

    /** @hide */
    public ResolutionOverride(SurfaceView view) {
        boolean enable = (view.getContext().getApplicationInfo().canOverrideRes() == 1);
        String resStr = SystemProperties.get(RES_OVERRIDE, null);

        if (!enable || resStr == null || resStr.length() == 0 ||
                view.getResources() == null) {
            return;
        }

        int orientation = view.getResources().getConfiguration().orientation;

        if(orientation == Configuration.ORIENTATION_PORTRAIT ||
                orientation == Configuration.ORIENTATION_LANDSCAPE) {
            resStr = resStr.toLowerCase();
            final int pos = resStr.indexOf('x');
            if (pos > 0 && resStr.lastIndexOf('x') == pos) {
                try {
                    mOverrideXres = Integer.parseInt(resStr.substring(0, pos));
                    mOverrideYres = Integer.parseInt(resStr.substring(pos + 1));
                } catch (NumberFormatException ex) {
                    Log.e(TAG, "Error in extracting the overriding xres and yres");
                }
            }

            if(orientation == Configuration.ORIENTATION_LANDSCAPE) {
                int tmp = mOverrideXres;
                mOverrideXres = mOverrideYres;
                mOverrideYres = tmp;
            }

            if(mOverrideXres > 0 && mOverrideYres > 0) {
                mIsEnabled = true;
                if (DEBUG) Log.i(TAG, "Orientation: " + orientation +
                        " Overriding resolution to" + " xres: " + mOverrideXres
                        + " yres: " + mOverrideYres);
            }
        }
    }

    /** @hide */
    public void setFixedSize(SurfaceView view) {
        if(!mIsEnabled) {
            return;
        }

        view.getHolder().setFixedSize(mOverrideXres, mOverrideYres);
    }

    /** @hide */
    public void handleTouch(SurfaceView view, MotionEvent ev) {
        if(!mIsEnabled) {
            return;
        }

        Matrix matrix = new Matrix();
        //mOverride{Xres, Yres} are already swapped if orientation is landscape
        float xscale = (mOverrideXres * 1.0f) / view.getWidth();
        float yscale = (mOverrideYres * 1.0f) / view.getHeight();

        if (DEBUG) Log.i(TAG, "Before overriding the touch event x/y : " + ev);
        matrix.postScale(xscale, yscale);
        ev.transform(matrix);
        if (DEBUG) Log.i(TAG, "After overriding the touch event x/y : " + ev);
    }

    /** @hide */
    public void handleResize(final SurfaceView surfaceView) {
        if(!mIsEnabled) {
            return;
        }

        /* Change the visibility to GONE and back to VISIBLE and post it
         * on the main thread for the touch events to be effective on the
         * changed SurfaceView with the new dimensions
         */
        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                surfaceView.setVisibility(View.GONE);
            }
        });

        surfaceView.postDelayed(new Runnable() {
            @Override
            public void run() {
                surfaceView.setVisibility(View.VISIBLE);
            }
        }, 100);
    }
};
