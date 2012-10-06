
/* Copyright (c) 2011,2012 Code Aurora Forum. All rights reserved.
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
import android.os.HandlerThread;
import android.os.ServiceManager;
import android.view.WindowManager;
import android.util.Log;

public class AtCssCmdHandler extends AtCmdBaseHandler implements AtCmdHandler {

    private static final String TAG = "AtCssCmdHandler";

    public AtCssCmdHandler(Context c) throws AtCmdHandlerInstantiationException
    {
        super(c);
    }

    private WindowManager getWindowManager() {
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            Log.e(TAG, "Unable to find WindowManager interface.");
        }
        return wm;
    }

    @Override
    public String getCommandName() {
        return "+CSS";
    }

    @Override
    public AtCmdResponse handleCommand(AtCmd cmd) {
        String tokens[] = cmd.getTokens();
        String result = null;
        boolean isAtCmdRespOK = false;

        Log.d(TAG, "OpCode  " + cmd.getOpcode());
        switch (cmd.getOpcode()) {
            case  AtCmd.AT_OPCODE_NA:
                // AT+CSS
                try {
                    WindowManager wm = getWindowManager();
                    if(null == wm) {
                        Log.e(TAG, "Unable to find WindowManager interface.");
                        result = cmd.getAtCmdErrStr(AtCmd.AT_ERR_PHONE_FAILURE);
                        break;
                    }
                    int screenHeight = wm.getDefaultDisplay().getHeight();
                    int screenWidth = wm.getDefaultDisplay().getWidth();
                    result = getCommandName() +": " + screenWidth + "," + screenHeight;
                    Log.d(TAG," At Result :" + result);
                    isAtCmdRespOK = true;
                }
                catch (SecurityException e) {
                    Log.e(TAG, "SecurityException: " + e);
                    result = cmd.getAtCmdErrStr(AtCmd.AT_ERR_OP_NOT_ALLOW);
                }
                break;
        }

        return isAtCmdRespOK ? new AtCmdResponse(AtCmdResponse.RESULT_OK, result) :
            new AtCmdResponse(AtCmdResponse.RESULT_ERROR, result);
    }
}

