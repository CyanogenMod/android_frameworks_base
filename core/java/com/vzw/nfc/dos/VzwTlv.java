 /*
  * Copyright (C) 2015 NXP Semiconductors
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

package com.vzw.nfc.dos;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class VzwTlv {

    private byte[] mRawData = null;

    private int mTag = 0;

    private int mValueIndex = 0;
    private int mValueLength = 0;

    public VzwTlv(byte[] rawData, int tag, int valueIndex, int valueLength) {
        mRawData = rawData;
        mTag = tag;
        mValueIndex = valueIndex;
        mValueLength = valueLength;
    }

    public static VzwTlv parse(byte[] data, int startIndex)
            throws DoParserException {

        if (data == null || data.length == 0) {
            throw new DoParserException("No data given!");
        }

        int curIndex = startIndex;
        int tag = 0;

        /* tag */
        if (curIndex < data.length) {
            tag = data[curIndex++] & 0xff;
        } else {
            throw new DoParserException("Index out of bound");
        }

        /* length */
        int length;
        if (curIndex < data.length) {
            length = data[curIndex++] & 0xff;
        } else {
            throw new DoParserException("Index " + curIndex
                    + " out of range! [0..[" + data.length);
        }

        return new VzwTlv(data, tag, curIndex, length);

    }

    public void translate() throws DoParserException {
    }

    public int getTag() {
        return mTag;
    }

    public int getValueIndex() {
        return mValueIndex;
    }

    public byte[] getValue() {
        if (mRawData == null || mValueLength == 0 || mValueIndex < 0
                || mValueIndex > mRawData.length
                || mValueIndex + mValueLength > mRawData.length)
            return null;

        byte[] data = new byte[mValueLength];

        System.arraycopy(mRawData, mValueIndex, data, 0, mValueLength);
        return data;
    }

    protected byte[] getRawData() {
        return mRawData;
    }

    public int getValueLength() {
        return mValueLength;
    }

    public static void encodeLength(int length, ByteArrayOutputStream stream) {
        stream.write((length & 0x000000FF));
    }

    @Override
    public boolean equals(Object obj) {
        boolean equals = false;

        if (obj instanceof VzwTlv) {
            VzwTlv berTlv = (VzwTlv) obj;

            equals = this.mTag == berTlv.mTag;

            if (equals) {
                byte[] test1 = this.getValue();
                byte[] test2 = berTlv.getValue();

                if (test1 != null) {
                    // equals &= test1.equals(test2);
                    equals &= Arrays.equals(test1, test2);
                } else if (test1 == null && test2 == null) {
                    equals &= true;
                }
            }
        }
        return equals;
    }
}
