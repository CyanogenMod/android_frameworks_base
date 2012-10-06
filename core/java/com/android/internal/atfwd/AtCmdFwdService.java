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

import java.util.HashMap;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.atfwd.IAtCmdFwd;
import com.android.internal.atfwd.AtCmdHandler.AtCmdHandlerInstantiationException;

public class AtCmdFwdService extends IAtCmdFwd.Stub {

    private static final String LOG_TAG = "AtCmdFwdService";

    private static final String ATFWD_PERMISSION = "android.permission.ATCMD";

    private Context mContext;

    private HashMap<String, AtCmdHandler> mCmdHandlers;

    public AtCmdFwdService(Context c)
    {
        mContext = c;
        mCmdHandlers = new HashMap<String, AtCmdHandler>();

        AtCmdHandler cmd;

        try {
            cmd = new AtCkpdCmdHandler(c);
            mCmdHandlers.put(cmd.getCommandName().toUpperCase(), cmd);
        } catch (AtCmdHandlerInstantiationException e) {
            Log.e(LOG_TAG, "Unable to instantiate command", e);
        }

        try {
            cmd = new AtCtsaCmdHandler(c);
            mCmdHandlers.put(cmd.getCommandName().toUpperCase(), cmd);
        } catch (AtCmdHandlerInstantiationException e) {
            Log.e(LOG_TAG, "Unable to instantiate command", e);
        }

        try {
            cmd = new AtCfunCmdHandler(c);
            mCmdHandlers.put(cmd.getCommandName().toUpperCase(), cmd);
        } catch (AtCmdHandlerInstantiationException e) {
            Log.e(LOG_TAG, "Unable to instantiate command", e);
        }

        try {
            cmd = new AtCrslCmdHandler(c);
            mCmdHandlers.put(cmd.getCommandName().toUpperCase(), cmd);
        } catch (AtCmdHandlerInstantiationException e) {
            Log.e(LOG_TAG, "Unable to instantiate command", e);
        }

        try {
            cmd = new AtCssCmdHandler(c);
            mCmdHandlers.put(cmd.getCommandName().toUpperCase(), cmd);
        } catch (AtCmdHandlerInstantiationException e) {
            Log.e(LOG_TAG, "Unable to instantiate command", e);
        }

        try {
            cmd = new AtCmarCmdHandler(c);
            mCmdHandlers.put(cmd.getCommandName().toUpperCase(), cmd);
        } catch (AtCmdHandlerInstantiationException e) {
            Log.e(LOG_TAG, "Unable to instantiate command", e);
        }

    }

    public AtCmdResponse processAtCmd(AtCmd cmd) throws RemoteException {
        mContext.enforceCallingPermission(ATFWD_PERMISSION, "Processing AT Command: Permission denied");
        Log.d(LOG_TAG, "processAtCmd(cmd: " + cmd.toString());
        AtCmdResponse ret;
        AtCmdHandler h = mCmdHandlers.get(cmd.getName().toUpperCase());
        if (h != null) {
            try {
            ret = h.handleCommand(cmd);
            } catch(Throwable e) {
                ret = new AtCmdResponse(AtCmdResponse.RESULT_ERROR, "+CME ERROR: 2");
            }
        } else {
            Log.e(LOG_TAG,"Unhandled command " + cmd);
            ret = new AtCmdResponse(AtCmdResponse.RESULT_ERROR, "+CME ERROR: 4");
        }
        return ret;
    }
}
