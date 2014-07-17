/*
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
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
        private static final boolean VDBG = false;

        private static final int MSB_MASK = 0xFF00;
        private static final int LSB_MASK = 0x00FF;

        /* ADC conversions for voaltag and current */
        private static final float VREG_ADC_TO_mV_RATIO = ((float)(2.44/256)*10000);
        private static final float IREG_ADC_TO_mA_RATIO = ((float)(2.44/256)*500);

        /* Over volatage protection parameters */
        private static final byte OVP_BIT = (byte)0x80;
        private static final short OVP_THRESHHOLD_VAL = 21500;


        /**
        * Default Constructor
        * {@hide}
        */
        public WipowerDynamicParam() {
            mOptValidity = 0x00;
            mRectVoltage = 0x00;
            mRectCurrent = 0x00;
            mOutputVoltage = 0x00;
            mOutputCurrent = 0x00;
            mTemperature = 0x00;
            mMinRectVoltageDyn = 0x00;
            mMaxRectVoltageDyn = 0x00;
            mSetRectVoltageDyn = 0x00;
            mAlert = 0x00;
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
            if (VDBG) Log.v(LOGTAG, "mOptValidity " +  toHex(mOptValidity) +
              "mRectVoltage " +  toHex(mRectVoltage) +  "mRectCurrent " +
              toHex(mRectCurrent) + "mOutputVoltage " +  toHex(mOutputVoltage));
            if (VDBG) Log.v(LOGTAG, "mOutputCurrent " +  toHex(mOutputCurrent) +
               "mTemperature " +  toHex(mTemperature) + "mMinRectVoltageDyn " +
               toHex(mMinRectVoltageDyn) + "mMaxRectVoltageDyn " +  toHex(mMaxRectVoltageDyn));
            if (VDBG) Log.v(LOGTAG, "mSetRectVoltageDyn " +
               toHex(mSetRectVoltageDyn) + "mAlert " +  toHex(mAlert) +
               "mReserved1 " +  toHex(mReserved1) + "mReserved2 " +  toHex(mReserved2));
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
            print();
            res[0] = mOptValidity;
            res[1] = (byte)(LSB_MASK & mRectVoltage);
            res[2] = (byte)((MSB_MASK & mRectVoltage) >> 8);
            res[3] = (byte)(LSB_MASK & mRectCurrent);
            res[4] = (byte)((MSB_MASK & mRectCurrent) >> 8);
            res[5] = (byte)(LSB_MASK & mOutputVoltage);
            res[6] = (byte)((MSB_MASK & mOutputVoltage) >> 8);
            res[7] = (byte)(LSB_MASK & mOutputCurrent);
            res[8] = (byte)((MSB_MASK & mOutputCurrent) >> 8);
            res[9] =  mTemperature;
            res[10] = (byte)(LSB_MASK & mMinRectVoltageDyn);
            res[11] = (byte)((MSB_MASK & mMinRectVoltageDyn) >> 8);
            res[12] = (byte)(LSB_MASK & mSetRectVoltageDyn);
            res[13] = (byte)((MSB_MASK & mSetRectVoltageDyn) >> 8);
            res[14] = (byte)(LSB_MASK & mMaxRectVoltageDyn);
            res[15] = (byte)((MSB_MASK & mMaxRectVoltageDyn) >> 8);
            res[16] = mAlert;
            if (((res[16] & OVP_BIT) == OVP_BIT) && (mRectVoltage < OVP_THRESHHOLD_VAL))
               res[16]  = (byte)(res[16] & ~OVP_BIT);

            Log.i(LOGTAG, "mPruDynamicParam.getValue");
            return res;
        }

        void resetValues() {
            mOptValidity = 0x00;
            mRectVoltage = 0x00;
            mRectCurrent = 0x00;
            mOutputVoltage = 0x00;
            mOutputCurrent = 0x00;
            mTemperature = 0x00;
            mMinRectVoltageDyn = 0x00;
            mMaxRectVoltageDyn = 0x00;
            mSetRectVoltageDyn = 0x00;
            mAlert = 0x00;
            mReserved1 = 0x00;
            mReserved2 = 0x00;
        }

        public static short toUnsigned(byte b) {
           return (short)(b & 0xff);
        }

        public static short VREG_ADC_TO_mV(short adc){
           return (short)((adc)*(VREG_ADC_TO_mV_RATIO));
        }

        public static short IREG_ADC_TO_mA(short adc) {
           return (short)((adc)*(IREG_ADC_TO_mA_RATIO));
        }

        /**
        * {@hide}
        * Sets the PRU dynamic parameter values for A4WP App in bytes
        *
        * @return none
        */
        public void setValue(byte[] value) {

            resetValues();
            mOptValidity = value[0];
            mRectVoltage = (short)toUnsigned(value[1]);
            mRectVoltage |= (short)(toUnsigned(value[2]) << 8);
            mRectCurrent = (short)toUnsigned(value[3]);
            mRectCurrent |= (short)(toUnsigned(value[4]) << 8);
            mOutputVoltage = (short)toUnsigned(value[5]);
            mOutputVoltage |= (short)(toUnsigned(value[6]) << 8);
            mOutputCurrent = (short)toUnsigned(value[7]);
            mOutputCurrent |= (short)(toUnsigned(value[8]) << 8);
            mTemperature = value[9];
            mMinRectVoltageDyn = (short)toUnsigned(value[10]);
            mMinRectVoltageDyn |= (short)(toUnsigned(value[11]) << 8);
            mSetRectVoltageDyn = (short)toUnsigned(value[12]);
            mSetRectVoltageDyn |= (short)(toUnsigned(value[13]) << 8);
            mMaxRectVoltageDyn = (short)toUnsigned(value[14]);
            mMaxRectVoltageDyn |= (short)(toUnsigned(value[15]) << 8);

            mAlert = value[16];
            mReserved1 = (short)(toUnsigned(value[17]));
            mReserved1 = (short)(toUnsigned(value[18]) << 8);
            mReserved2 = value[19];
            Log.i(LOGTAG, "mPruDynamicParam.setAppValue");
            print();
            return;
        }
    }

