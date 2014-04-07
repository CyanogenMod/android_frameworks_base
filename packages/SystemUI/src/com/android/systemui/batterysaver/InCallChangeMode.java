/*
 * Copyright (C) 2014 The OmniRom Project
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
package com.android.systemui.batterysaver;

import android.content.Context;

import com.android.systemui.R;
import com.android.systemui.batterysaver.BatterySaverService.State;

public class InCallChangeMode implements Runnable {

    private Context mContext;
    private BatterySaverService mServices;
    private State mWhatState = State.UNKNOWN;
    private boolean mForce = false;
    private boolean mChecks = false;

    public InCallChangeMode(Context context, BatterySaverService services) {
        mContext = context;
        mServices = services;
    }

    public void InCallChangeState(State newState, boolean force, boolean checks) {
        mWhatState = newState;
        mForce = force;
        mChecks = checks;
    }

    public State getState() {
        return mWhatState;
    }

    public boolean isForce() {
        return mForce;
    }

    public boolean isChecks() {
        return mChecks;
    }

    @Override
    public void run() {
        mServices.switchToState(mWhatState, mForce, mChecks);
    }

    public void callPosted() {
        run();
    }
}
