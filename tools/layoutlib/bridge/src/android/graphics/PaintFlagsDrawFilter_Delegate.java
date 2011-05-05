/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.graphics;

import com.android.layoutlib.bridge.impl.DelegateManager;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

/**
 * Delegate implementing the native methods of android.graphics.PaintFlagsDrawFilter
 *
 * Through the layoutlib_create tool, the original native methods of PaintFlagsDrawFilter have been
 * replaced by calls to methods of the same name in this delegate class.
 *
 * This class behaves like the original native implementation, but in Java, keeping previously
 * native data into its own objects and mapping them to int that are sent back and forth between
 * it and the original PaintFlagsDrawFilter class.
 *
 * Because this extends {@link DrawFilter_Delegate}, there's no need to use a
 * {@link DelegateManager}, as all the DrawFilter classes will be added to the manager owned by
 * {@link DrawFilter_Delegate}.
 *
 * @see DrawFilter_Delegate
 *
 */
public class PaintFlagsDrawFilter_Delegate extends DrawFilter_Delegate {

    // ---- delegate data ----

    // ---- Public Helper methods ----

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public String getSupportMessage() {
        return "Paint Flags Draw Filters are not supported.";
    }

    // ---- native methods ----

    @LayoutlibDelegate
    /*package*/ static int nativeConstructor(int clearBits, int setBits) {
        PaintFlagsDrawFilter_Delegate newDelegate = new PaintFlagsDrawFilter_Delegate();
        return sManager.addNewDelegate(newDelegate);
    }

    // ---- Private delegate/helper methods ----
}
