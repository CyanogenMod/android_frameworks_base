/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
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

package android.wipower;

import android.util.Log;


/**
  * Class holds the PRU Dynamic parameter and related attributes
  *
  * {@hide}
  */

public class WipowerDynamicParam {
        private byte mOptValidity;
        private short mRectVoltage;
        private short mRectCurrent;
        private short mOutputVoltage;
        private short mOutputCurrent;
        private byte mTemperature;
        private short mMinRectVoltageDyn;
        private short mMaxRectVoltageDyn;
        private short mSetRectVoltageDyn;
        private byte mAlert;
        private short mReserved1;
        private byte mReserved2;

        private static final String LOGTAG = "WipowerDynamicParam";

        private static final int MSB_MASK = 0xFF00;
        private static final int LSB_MASK = 0x00FF;


        /**
        * Default Constructor
        * {@hide}
        */
        public WipowerDynamicParam() {
            mOptValidity = 0;
            mRectVoltage = 0;
            mRectCurrent = 0;
            mOutputVoltage = 0;
            mOutputCurrent = 0;
            mTemperature = 0;
            mMinRectVoltageDyn = 0;
            mMaxRectVoltageDyn = 0;
            mSetRectVoltageDyn = 0;
            mAlert = 0;
            mReserved1 = 0;
            mReserved2 = 0;
        }

       /**
        * helper to convert num to hex
        * {@hide}
        */
        private static String toHex(int num) {
            return String.format("0x%8s", Integer.toHexString(num)).replace(' ', '0');
        }

       /**
        * helper print function
        * {@hide}
        */
        void print() {
            Log.v(LOGTAG, "mOptValidity" +  toHex(mOptValidity) + "mRectVoltage" +  toHex(mRectVoltage) +  "mRectCurrent" +  toHex(mRectCurrent) + "mOutputVoltage" +  toHex(mOutputVoltage));
            Log.v(LOGTAG, "mOutputCurrent" +  toHex(mOutputCurrent) + "mTemperature" +  toHex(mTemperature) + "mMinRectVoltageDyn" +  toHex(mMinRectVoltageDyn) + "mMaxRectVoltageDyn" +  toHex(mMaxRectVoltageDyn));
            Log.v(LOGTAG, "mSetRectVoltageDyn" +  toHex(mSetRectVoltageDyn) + "mAlert" +  toHex(mAlert) + "mReserved1" +  toHex(mReserved1) + "mReserved2" +  toHex(mReserved2));
        }

       /**
        * {@hide}
        * Gets the PRU dynamic parameter values in bytes
        *
        * @return byte array of PRU Dynamic parameter
        *
        */
        public byte[] getValue() {
            byte[] res = new byte[20];
            res[0] = mOptValidity;
            res[1] = (byte)(LSB_MASK & mRectVoltage);
            res[2] = (byte)(MSB_MASK & mRectVoltage);
            res[3] = (byte)(LSB_MASK & mRectCurrent);
            res[4] = (byte)(MSB_MASK & mRectCurrent);
            res[5] = (byte)(LSB_MASK & mOutputVoltage);
            res[6] = (byte)(MSB_MASK & mOutputVoltage);
            res[7] = (byte)(LSB_MASK & mOutputCurrent);
            res[8] = (byte)(MSB_MASK & mOutputCurrent);
            res[9] =  mTemperature;
            res[10] = (byte)(LSB_MASK & mMinRectVoltageDyn);
            res[11] = (byte)(MSB_MASK & mMinRectVoltageDyn);
            res[12] = (byte)(LSB_MASK & mMaxRectVoltageDyn);
            res[13] = (byte)(MSB_MASK & mMaxRectVoltageDyn);
            res[14] = (byte)(LSB_MASK & mSetRectVoltageDyn);
            res[15] = (byte)(MSB_MASK & mSetRectVoltageDyn);

            res[16] = mAlert;

            Log.i(LOGTAG, "mPruDynamicParam.getValue" + res);
            return res;
        }

       /**
        * {@hide}
        * Sets the PRU dynamic parameter values in bytes
        *
        * @return none
        */
        public void setValue(byte[] value) {
            mOptValidity = value[0];
            mRectVoltage = value[1];
            mRectVoltage |= (short)(8<<value[2]);
            mRectCurrent = value[3];
            mRectCurrent |= (short)(8<<value[4]);
            mOutputVoltage = value[5];
            mOutputVoltage |= (short)(8<<value[6]);
            mOutputCurrent = value[7];
            mOutputCurrent |= (short)(8<<value[8]);
            mTemperature = value[9];
            mMinRectVoltageDyn = value[10];
            mMinRectVoltageDyn = (short)(8<<value[11]);
            mMaxRectVoltageDyn = value[12];
            mMaxRectVoltageDyn |= (short)(8<<value[13]);
            mSetRectVoltageDyn = value[14];
            mSetRectVoltageDyn |= (short)(8<<value[15]);

            mAlert = value[16];
            return;
        }
    }

