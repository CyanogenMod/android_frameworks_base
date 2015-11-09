/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
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

package com.android.internal.app;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.util.Log;

public class ActivityTrigger
{
    private static final String TAG = "ActivityTrigger";

    private static final int FLAG_OVERRIDE_RESOLUTION = 1;
    private static final int FLAG_HARDWARE_ACCELERATED =
            ActivityInfo.FLAG_HARDWARE_ACCELERATED;


    /** &hide */
    public ActivityTrigger() {
        //Log.d(TAG, "ActivityTrigger initialized");
    }

    /** &hide */
    protected void finalize() {
        native_at_deinit();
    }

    /** &hide */
    public void activityStartProcessTrigger(String process, int pid) {
        native_at_startProcessActivity(process, pid);
    }

    /** &hide */
    public void activityStartTrigger(Intent intent, ActivityInfo acInfo, ApplicationInfo appInfo) {
        ComponentName cn = intent.getComponent();
        int overrideFlags = 0;
        String activity = null;

        if(cn != null)
            activity = cn.flattenToString() + "/" + appInfo.versionCode;

        overrideFlags = native_at_startActivity(activity, overrideFlags);

        if((overrideFlags & FLAG_HARDWARE_ACCELERATED) != 0) {
            acInfo.flags |= ActivityInfo.FLAG_HARDWARE_ACCELERATED;
        }
        if((overrideFlags & FLAG_OVERRIDE_RESOLUTION) != 0) {
            appInfo.setOverrideRes(1);
        }
    }

    /** &hide */
    public void activityResumeTrigger(Intent intent, ActivityInfo acInfo, ApplicationInfo appInfo) {
        ComponentName cn = intent.getComponent();
        String activity = null;

        if (cn != null)
            activity = cn.flattenToString() + "/" + appInfo.versionCode;
        native_at_resumeActivity(activity);
    }

    private native int native_at_startActivity(String activity, int flags);
    private native void native_at_resumeActivity(String activity);
    private native void native_at_deinit();
    private native void native_at_startProcessActivity(String process, int pid);
}
