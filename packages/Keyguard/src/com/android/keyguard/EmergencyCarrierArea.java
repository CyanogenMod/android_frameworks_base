/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.keyguard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.android.keyguard.R;

public class EmergencyCarrierArea extends LinearLayout {

    private CarrierText mCarrierText;
    private EmergencyButton mEmergencyButton;

    public EmergencyCarrierArea(Context context) {
        super(context);
    }

    public EmergencyCarrierArea(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (!KeyguardUpdateMonitor.sIsMultiSimEnabled) {
            // For MSIM, we need to wait until the view has been inflated to find it
            mCarrierText = (CarrierText) findViewById(R.id.carrier_text);
        }

        mEmergencyButton = (EmergencyButton) findViewById(R.id.emergency_call_button);

        // The emergency button overlaps the carrier text, only noticeable when highlighted.
        // So temporarily hide the carrier text while the emergency button is pressed.
        mEmergencyButton.setOnTouchListener(new OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (mCarrierText == null) {
                    // We're using MSIM
                    mCarrierText = (CarrierText) findViewById(R.id.msim_keyguard_carrier_area)
                            .findViewById(R.id.msim_carrier_text);
                }

                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mCarrierText.animate().alpha(0);
                        break;
                    case MotionEvent.ACTION_UP:
                        mCarrierText.animate().alpha(1);
                        break;
                }
                return false;
            }});
    }
}
