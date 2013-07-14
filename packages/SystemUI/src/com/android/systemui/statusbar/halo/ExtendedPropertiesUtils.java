/*
 * Copyright (C) 2012 ParanoidAndroid Project
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

package com.android.systemui.statusbar.halo;

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;
import android.view.Display;
import android.util.DisplayMetrics;

import java.lang.Math;

public class ExtendedPropertiesUtils {

	public static Context mContext;
    public static Display mDisplay;

    /**
     * Returns whether if device is on tablet UI or not
     *
     * @return device is tablet
     */
    public static boolean isTablet() {

		double size = 0;
        try {
            // Compute screen size
            DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
            float screenWidth  = dm.widthPixels / dm.xdpi;
            float screenHeight = dm.heightPixels / dm.ydpi;
            size = Math.sqrt(Math.pow(screenWidth, 2) + Math.pow(screenHeight, 2));
        } catch(Throwable t) {
        }

        return size >= 6.4;
    }

}
