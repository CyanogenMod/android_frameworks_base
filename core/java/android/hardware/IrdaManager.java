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
 *
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
     * Sending codes is done by writing it to /sys/class/sec/sec_ir/ir_send
     *
     */
    public void write_irsend(String ircode) {
        try {
            mService.write_irsend(ircode);
        } catch (RemoteException ex) {
        }
    }
}
