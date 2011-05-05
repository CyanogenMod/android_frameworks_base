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

package android.view;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

/**
 * Delegate used to provide new implementation of a select few methods of {@link View}
 *
 * Through the layoutlib_create tool, the original  methods of View have been replaced
 * by calls to methods of the same name in this delegate class.
 *
 */
public class View_Delegate {

    @LayoutlibDelegate
    /*package*/ static boolean isInEditMode(View thisView) {
        return true;
    }
}
