/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.imftest.samples;

import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;

import com.android.imftest.R;


public class BottomEditTextActivityResizeTests extends ImfBaseTestCase<BottomEditTextActivityResize> {

    public final String TAG = "BottomEditTextActivityResizeTests";
    
    public BottomEditTextActivityResizeTests() {
        super(BottomEditTextActivityResize.class);
    }
    
    @LargeTest
    public void testAppAdjustmentResize() {
        // Give the IME 2 seconds to appear.
        pause(2000);
        
        View rootView = ((BottomEditTextActivityResize) mTargetActivity).getRootView();
        View servedView = ((BottomEditTextActivityResize) mTargetActivity).getDefaultFocusedView();
        
        assertNotNull(rootView);
        assertNotNull(servedView);
        
        destructiveCheckImeInitialState(rootView, servedView);
        
        verifyEditTextAdjustment(servedView, rootView.getMeasuredHeight());
    }
    
}
