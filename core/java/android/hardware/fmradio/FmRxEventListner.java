/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Code Aurora nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.hardware.fmradio;

import android.util.Log;


class FmRxEventListner {


    private final int EVENT_LISTEN = 1;

    private enum FmRxEvents {
      READY_EVENT,
      TUNE_EVENT,
      SEEK_COMPLETE_EVENT,
      SCAN_NEXT_EVENT,
      RAW_RDS_EVENT,
      RT_EVENT,
      PS_EVENT,
      ERROR_EVENT,
      BELOW_TH_EVENT,
      ABOVE_TH_EVENT,
      STEREO_EVENT,
      MONO_EVENT,
      RDS_AVAL_EVENT,
      RDS_NOT_AVAL_EVENT,
      TAVARUA_EVT_NEW_SRCH_LIST,
      TAVARUA_EVT_NEW_AF_LIST
    }

    private Thread mThread;
    private static final String TAG = "FMRadio";

    public void startListner (final int fd, final FmRxEvCallbacks cb) {
        /* start a thread and listen for messages */
        mThread = new Thread(){
            public void run(){

                Log.d(TAG, "Starting listener " + fd);

                while (true) {

                    byte []buff = new byte[128];
                    int i = FmReceiverJNI.getBufferNative (fd, buff, EVENT_LISTEN);
                    Log.d(TAG, "Received <" +buff[0]+ "> event. Int: " + i);

                    switch(buff[0]){

                    case 0:
                        Log.d(TAG, "Got READY_EVENT");
                        cb.FmRxEvEnableReceiver();
                        break;
                    case 1:
                        Log.d(TAG, "Got TUNE_EVENT");
                        cb.FmRxEvRadioTuneStatus(FmReceiverJNI.getFreqNative(fd));
                        break;
                    case 2:
                        Log.d(TAG, "Got SEEK_COMPLETE_EVENT");
                        cb.FmRxEvSearchComplete(FmReceiverJNI.getFreqNative(fd));
                        break;
                    case 3:
                        Log.d(TAG, "Got SCAN_NEXT_EVENT");
                        cb.FmRxEvSearchInProgress();
                        break;
                    case 4:
                        Log.d(TAG, "Got RAW_RDS_EVENT");
                        cb.FmRxEvRdsGroupData();
                        break;
                    case 5:
                        Log.d(TAG, "Got RT_EVENT");
                        cb.FmRxEvRdsRtInfo();
                        break;
                    case 6:
                        Log.d(TAG, "Got PS_EVENT");
                        cb.FmRxEvRdsPsInfo();
                        break;
                    case 7:
                        Log.d(TAG, "Got ERROR_EVENT");
                        break;
                    case 8:
                        Log.d(TAG, "Got BELOW_TH_EVENT");
                        cb.FmRxEvServiceAvailable (false);
                        break;
                    case 9:
                        Log.d(TAG, "Got ABOVE_TH_EVENT");
                        cb.FmRxEvServiceAvailable(true);
                        break;
                    case 10:
                        Log.d(TAG, "Got STEREO_EVENT");
                        cb.FmRxEvStereoStatus (true);
                        break;
                    case 11:
                        Log.d(TAG, "Got MONO_EVENT");
                        cb.FmRxEvStereoStatus (false);
                        break;
                    case 12:
                        Log.d(TAG, "Got RDS_AVAL_EVENT");
                        cb.FmRxEvRdsLockStatus (true);
                        break;
                    case 13:
                        Log.d(TAG, "Got RDS_NOT_AVAL_EVENT");
                        cb.FmRxEvRdsLockStatus (false);
                        break;
                    case 14:
                        Log.d(TAG, "Got NEW_SRCH_LIST");
                        cb.FmRxEvSearchListComplete ();
                        break;
                    case 15:
                        Log.d(TAG, "Got NEW_AF_LIST");
                        cb.FmRxEvRdsAfInfo();
                        break;
                    default:
                        Log.d(TAG, "Unknown event");
                        break;
                    }
                }
            }
        };
        mThread.start();
    }

    public void stopListener(){
        mThread.stop();
    }

}
