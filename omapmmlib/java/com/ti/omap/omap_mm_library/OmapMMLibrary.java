/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ti.omap.omap_mm_library;

import android.util.Config;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.media.MediaPlayer;

public final class OmapMMLibrary {
private String TAG = "OmapMMJavaLib";
static {
        /*
        * Load the library.  If it's already loaded, this does nothing.
        */
        System.loadLibrary("omap_mm_library_jni");
    }

    private Surface mSurface; // accessed by native methods
    private MediaPlayer mMediaPlayer; // accessed by native methods

    public OmapMMLibrary() {
    }

    public void setVideoSurface(SurfaceHolder sh) {
        if (sh != null) {
            mSurface = sh.getSurface();
        } else {
            mSurface = null;
	    return;
        }
        //This is native implemented, to get the associated ISurface
        setVideoISurface();
    }

    public void setMediaPlayer(MediaPlayer mp) {
            mMediaPlayer = mp;
	    return;
        }

    /*
    * init will mount required fields
    */
    native public void native_init();
    native private void deinit();
    native public void setVideoISurface();
    native public void setDisplayId(int displayId);

}

