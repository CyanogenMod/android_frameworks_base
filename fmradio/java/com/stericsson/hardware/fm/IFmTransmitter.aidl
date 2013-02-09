/*
 * Copyright (C) ST-Ericsson SA 2010
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
 *
 * Author: Markus Grape (markus.grape@stericsson.com) for ST-Ericsson
 */

package com.stericsson.hardware.fm;

import com.stericsson.hardware.fm.FmBand;
import com.stericsson.hardware.fm.IOnStateChangedListener;
import com.stericsson.hardware.fm.IOnStartedListener;
import com.stericsson.hardware.fm.IOnErrorListener;
import com.stericsson.hardware.fm.IOnBlockScanListener;
import com.stericsson.hardware.fm.IOnForcedPauseListener;
import com.stericsson.hardware.fm.IOnForcedResetListener;
import com.stericsson.hardware.fm.IOnExtraCommandListener;
import android.os.Bundle;

/**
 * {@hide}
 */
interface IFmTransmitter
{
    void start(in FmBand band);
    void startAsync(in FmBand band);
    void reset();
    void pause();
    void resume();
    int getState();
    int getFrequency();
    void setFrequency(int frequency);
    boolean isBlockScanSupported();
    void startBlockScan(int startFrequency, int endFrequency);
    void stopScan();
    void setRdsData(in Bundle rdsData);
    boolean sendExtraCommand(String command, in String[] extras);
    void addOnStateChangedListener(in IOnStateChangedListener listener);
    void removeOnStateChangedListener(in IOnStateChangedListener listener);
    void addOnStartedListener(in IOnStartedListener listener);
    void removeOnStartedListener(in IOnStartedListener listener);
    void addOnErrorListener(in IOnErrorListener listener);
    void removeOnErrorListener(in IOnErrorListener listener);
    void addOnBlockScanListener(in IOnBlockScanListener listener);
    void removeOnBlockScanListener(in IOnBlockScanListener listener);
    void addOnForcedPauseListener(in IOnForcedPauseListener listener);
    void removeOnForcedPauseListener(in IOnForcedPauseListener listener);
    void addOnForcedResetListener(in IOnForcedResetListener listener);
    void removeOnForcedResetListener(in IOnForcedResetListener listener);
    void addOnExtraCommandListener(in IOnExtraCommandListener listener);
    void removeOnExtraCommandListener(in IOnExtraCommandListener listener);
}
