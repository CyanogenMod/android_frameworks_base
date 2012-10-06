/* Copyright (c) 2010,2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.atfwd;

import android.content.Context;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.HandlerThread;
import android.util.Log;

/**
 * @author jaimel
 *
 */
public class AtCfunCmdHandler extends AtCmdBaseHandler implements AtCmdHandler {

    private static final String TAG = "AtCfunCmdHandler";

    public AtCfunCmdHandler(Context c) throws AtCmdHandlerInstantiationException
    {
        super(c);
    }

    @Override
    public String getCommandName() {
        return "+CFUN";
    }

    @Override
    public AtCmdResponse handleCommand(AtCmd cmd) {
        AtCmdResponse ret = null;
        Thread rebootThread;
        String tokens[] = cmd.getTokens();

        if (tokens.length != 2 || !tokens[0].equals("1") || !tokens[1].equals("1")) {
            /* We currently support +CFUN=1,1 only. other values have to be
             * handled elsewhere.
             */
            Log.e(TAG, "+CFUN: Only +CFUN=1,1 supported");
            ret = new AtCmdResponse(AtCmdResponse.RESULT_ERROR, "+CME ERROR: 1");
        } else {
            rebootThread = new Thread() {
                public void run() {
                    try {
                        IPowerManager pm = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
                        pm.reboot(null);
                    } catch (RemoteException e) {
                        Log.e(TAG, "PowerManager service died!", e);
                        return;
                    }
                }
            };
            rebootThread.start();
            ret = new AtCmdResponse(AtCmdResponse.RESULT_OK, null);
        }

        return ret;
    }

}
