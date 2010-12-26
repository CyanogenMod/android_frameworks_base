/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *            notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *            notice, this list of conditions and the following disclaimer in the
 *            documentation and/or other materials provided with the distribution.
 *        * Neither the name of Code Aurora nor
 *            the names of its contributors may be used to endorse or promote
 *            products derived from this software without specific prior written
 *            permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
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


class FmRxControls
{

   private boolean mStateStereo;
   private boolean mStateMute;
   private int mFreq;

   static final int SEEK_FORWARD = 0;
   static final int SEEK_BACKWARD = 1;
   static final int SCAN_FORWARD = 2;
   static final int SCAN_BACKWARD = 3;
   private int mSrchMode;
   private int mScanTime;
   private int mSrchDir;
   private int mSrchListMode;
   private int mPrgmType;
   private int mPrgmId;
   private static final String TAG = "FmRxControls";


   /* V4l2 Controls */
   private static final int V4L2_CID_PRIVATE_BASE = 0x8000000;
   private static final int V4L2_CID_PRIVATE_TAVARUA_SRCHMODE = V4L2_CID_PRIVATE_BASE + 1;
   private static final int V4L2_CID_PRIVATE_TAVARUA_SCANDWELL = V4L2_CID_PRIVATE_BASE + 2;
   private static final int V4L2_CID_PRIVATE_TAVARUA_SRCHON = V4L2_CID_PRIVATE_BASE + 3;
   private static final int V4L2_CID_PRIVATE_TAVARUA_STATE = V4L2_CID_PRIVATE_BASE + 4;
   private static final int V4L2_CID_PRIVATE_TAVARUA_TRANSMIT_MODE = V4L2_CID_PRIVATE_BASE + 5;
   private static final int V4L2_CID_PRIVATE_TAVARUA_RDSGROUP_MASK = V4L2_CID_PRIVATE_BASE + 6;;
   private static final int V4L2_CID_PRIVATE_TAVARUA_REGION = V4L2_CID_PRIVATE_BASE + 7;
   private static final int V4L2_CID_PRIVATE_TAVARUA_SIGNAL_TH = V4L2_CID_PRIVATE_BASE + 8;
   private static final int V4L2_CID_PRIVATE_TAVARUA_SRCH_PTY = V4L2_CID_PRIVATE_BASE + 9;
   private static final int V4L2_CID_PRIVATE_TAVARUA_SRCH_PI = V4L2_CID_PRIVATE_BASE + 10;
   private static final int V4L2_CID_PRIVATE_TAVARUA_SRCH_CNT = V4L2_CID_PRIVATE_BASE + 11;
   private static final int V4L2_CID_PRIVATE_TAVARUA_EMPHASIS = V4L2_CID_PRIVATE_BASE + 12;
   private static final int V4L2_CID_PRIVATE_TAVARUA_RDS_STD = V4L2_CID_PRIVATE_BASE + 13;
   private static final int V4L2_CID_PRIVATE_TAVARUA_SPACING = V4L2_CID_PRIVATE_BASE + 14;
   private static final int V4L2_CID_PRIVATE_TAVARUA_RDSON = V4L2_CID_PRIVATE_BASE + 15;
   private static final int V4L2_CID_PRIVATE_TAVARUA_RDSGROUP_PROC = V4L2_CID_PRIVATE_BASE + 16;
   private static final int V4L2_CID_PRIVATE_TAVARUA_LP_MODE = V4L2_CID_PRIVATE_BASE + 17;

   private static final int V4L2_CTRL_CLASS_USER = 0x980000;
   private static final int V4L2_CID_BASE = V4L2_CTRL_CLASS_USER | 0x900;

   private static final int V4L2_CID_AUDIO_MUTE = V4L2_CID_BASE + 9;



   /*
    * Turn on FM Rx/Tx.
    * Rx = 1 and Tx = 2
    */
   public void fmOn(int fd, int device) {
      FmReceiverJNI.setControlNative(fd, V4L2_CID_PRIVATE_TAVARUA_STATE, 1 );
   }

   /*
    * Turn off FM Rx/Tx
    */
   public void fmOff(int fd){
      FmReceiverJNI.setControlNative(fd, V4L2_CID_PRIVATE_TAVARUA_STATE, 2 );
   }

   /*
    * set mute control
    */
   public void muteControl(int fd, boolean on) {
      if (on)
      {
         int err = FmReceiverJNI.setControlNative(fd, V4L2_CID_AUDIO_MUTE, 3 );
      } else
      {
         int err = FmReceiverJNI.setControlNative(fd, V4L2_CID_AUDIO_MUTE, 4 );
      }
   }

   /*
    * Tune FM core to specified freq.
    */
   public void setStation(int fd) {
      Log.d(TAG, "** Tune Using: "+fd);
      int ret = FmReceiverJNI.setFreqNative(fd, mFreq);
      Log.d(TAG, "** Returned: "+ret);
   }

  /*
   * Get currently tuned freq
   */
   public int getTunedFrequency(int fd) {
      int frequency = FmReceiverJNI.getFreqNative(fd);
      Log.d(TAG, "getTunedFrequency: "+frequency);
      return frequency;
   }

   public int getFreq (){
      return mFreq;
   }

   public void setFreq (int f){
      mFreq = f;
   }

   /*
    * Start search list for auto presets
    */
   public int searchStationList (int fd, int mode, int preset_num,
                                   int dir, int pty )
   {
      int re;


     /* set search mode. */
      re = FmReceiverJNI.setControlNative (fd, V4L2_CID_PRIVATE_TAVARUA_SRCHMODE, mode);
      if (re != 0) {
         return re;
      }

      /* set number of stations to be returned in the list */
      re = FmReceiverJNI.setControlNative (fd, V4L2_CID_PRIVATE_TAVARUA_SRCH_CNT, preset_num);
      if (re != 0) {
         return re;
      }

      // RDS search list?
      if (pty > 0 ){
        re = FmReceiverJNI.setControlNative (fd, V4L2_CID_PRIVATE_TAVARUA_SRCH_PTY, pty);
      }
      if (re != 0) {
         return re;
      }

      /* This triigers the search and once completed the FM core generates
       * searchListComplete event */
      re = FmReceiverJNI.startSearchNative (fd, dir );
      if (re != 0) {
         return re;
      }
      else {
         return 0;
      }

   }

   /* Read search list from buffer */
   public int[] stationList (int fd)
   {
         int freq = 0;
         int i=0;
         int station_num;
         float real_freq = 0;
         int [] stationList;
         byte [] sList = new byte[100];
         int tmpFreqByte1=0;
         int tmpFreqByte2=0;
         float lowBand;


         lowBand = (float) (FmReceiverJNI.getLowerBandNative(fd) / 1000.00);
         Log.d(TAG, "lowBand: " + lowBand);
         FmReceiverJNI.getBufferNative(fd, sList, 0);

         station_num = (int)sList[0];
         stationList = new int[station_num+1];
         Log.d(TAG, "station_num: " + station_num);

         for (i=0;i<station_num;i++) {
            freq = 0;
            Log.d(TAG, " Byte1 = " + sList[i*2+1]);
            Log.d(TAG, " Byte2 = " + sList[i*2+2]);
            tmpFreqByte1 = sList[i*2+1] & 0xFF;
            tmpFreqByte2 = sList[i*2+2] & 0xFF;
            Log.d(TAG, " tmpFreqByte1 = " + tmpFreqByte1);
            Log.d(TAG, " tmpFreqByte2 = " + tmpFreqByte2);
            freq = (tmpFreqByte1 & 0x03) << 8;
            freq |= tmpFreqByte2;
            Log.d(TAG, " freq: " + freq);
            real_freq  = (float)(freq * 0.05) + lowBand;//tuner.rangelow / FREQ_MUL;
            Log.d(TAG, " real_freq: " + real_freq);
            stationList[i] = (int)(real_freq*1000);
            Log.d(TAG, " stationList: " + stationList[i]);
        }

        try {
          // mark end of list
           stationList[station_num] = 0;
        }
        catch (ArrayIndexOutOfBoundsException e) {
           Log.d(TAG, "ArrayIndexOutOfBoundsException !!");
        }

        return stationList;

   }


   /* configure various search parameters and start search */
   public void searchStations (int fd, int mode, int dwell,
                               int dir, int pty, int pi){
      int re = 0;


      Log.d(TAG, "Mode is " + mode + " Dwell is " + dwell);
      Log.d(TAG, "dir is "  + dir + " PTY is " + pty);
      Log.d(TAG, "pi is " + pi + " id " +  V4L2_CID_PRIVATE_TAVARUA_SRCHMODE);



      re = FmReceiverJNI.setControlNative (fd, V4L2_CID_PRIVATE_TAVARUA_SRCHMODE, mode);

      re = FmReceiverJNI.setControlNative (fd, V4L2_CID_PRIVATE_TAVARUA_SCANDWELL, dwell);

      if (pty != 0)
      {
         re = FmReceiverJNI.setControlNative (fd, V4L2_CID_PRIVATE_TAVARUA_SRCH_PTY, pty);
      }

      if (pi != 0)
      {
         re = FmReceiverJNI.setControlNative (fd, V4L2_CID_PRIVATE_TAVARUA_SRCH_PI, pi);
      }

      re = FmReceiverJNI.startSearchNative (fd, dir );

   }

   /* force mono/stereo mode */
   public int stereoControl(int fd, boolean stereo) {

     if (stereo){
       return  FmReceiverJNI.setMonoStereoNative (fd, 1);
     }
     else {
       return  FmReceiverJNI.setMonoStereoNative (fd, 0);
     }


   }


   public void searchRdsStations(int mode,int dwelling,
                                 int direction, int RdsSrchPty, int RdsSrchPI){
   }

   /*   public void searchStationList(int listMode,int direction,
                                 int listMax,int pgmType) {
   }
   */

   /* cancel search in progress */
   public void cancelSearch (int fd){
      FmReceiverJNI.cancelSearchNative(fd);
   }

   /* Set LPM. This disables all FM core interrupts */
   public int setLowPwrMode (int fd, boolean lpmOn){

      int re=0;

      if (lpmOn){
        re = FmReceiverJNI.setControlNative (fd, V4L2_CID_PRIVATE_TAVARUA_LP_MODE, 1);
      }
      else {
        re = FmReceiverJNI.setControlNative (fd, V4L2_CID_PRIVATE_TAVARUA_LP_MODE, 0);
      }

      return re;

   }

   /* get current powermode of the FM core. 1 for LPM and 0 Normal mode */
   public int getPwrMode (int fd) {

      int re=0;

      re = FmReceiverJNI.getControlNative (fd, V4L2_CID_PRIVATE_TAVARUA_LP_MODE);

      return re;

   }

}
