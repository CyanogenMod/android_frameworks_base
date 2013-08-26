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

package android.hardware;

import android.os.RemoteException;

/**
 * This class provides access to the system irda services.
 * @hide
 * <p>You do not
 * instantiate this class directly; instead, retrieve it through
 * {@link android.content.Context#getSystemService
 * Context.getSystemService(Context.IRDA_SERVICE)}.
 */
public class IrdaManager
{
    private final IIrdaManager mService;

    /**
     * package private on purpose
     */
    public IrdaManager(IIrdaManager service) {
        mService = service;
    }

    /**
     * Send IR code via the emitter
     *
     * The format of the code can be converted from the pronto format
     * as follows
     *
     * Example pronto code:
     * 0000 006D 0000 0022 00AC 00AB 0015 0041 0015 0041 0015 0041 0015
     * 0016 0015 0016 0015 0016 0015 0016 0015 0016 0015 0041 0015 0041
     * 0015 0041 0015 0016 0015 0016 0015 0016 0015 0016 0015 0016 0015
     * 0016 0015 0016 0015 0016 0015 0041 0015 0041 0015 0016 0015 0016
     * 0015 0041 0015 0041 0015 0041 0015 0041 0015 0016 0015 0016 0015
     * 0041 0015 0041 0015 0016 0015 0689
     *
     * 1. word 0000: not needed
     * 2. word 006D: this has to be converted to the frequency as follows:
     *    first vonvert it to decimal 109
     *    now caculate the frequency 1000000/(109 * .241246) = 38000 rounded
     * 3. word 0000: not needed
     * 4. word 0022: not needed
     * all the remaining numbers have to be converted to decimal
     *
     * now build a comma separated string without spaces with the frequency
     * and the remaining numbers as follows:
     *
     * 38000,172,171,21,65,21,65,21,65,21,22,21,22,21,22,21,22,21,22,21,65,
     * 21,65,21,65,21,22,21,22,21,22,21,22,21,22,21,22,21,22,21,22,21,65,21,
     * 65,21,22,21,22,21,65,21,65,21,65,21,65,21,22,21,22,21,65,21,65,21,22,
     * 21,1673
     *
     */
    public void write_irsend(String code) {
        try {
            mService.write_irsend(code);
        } catch (RemoteException ex) {
        }
    }
}
