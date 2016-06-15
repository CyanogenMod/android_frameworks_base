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

public class AidMaskDo extends VzwTlv {

    public final static int _TAG = 0xC3;

    private byte[] mAidMask = null;

    public AidMaskDo(byte[] rawData, int valueIndex, int valueLength) {
        super(rawData, _TAG, valueIndex, valueLength);
    }

    public AidMaskDo(byte[] aid_mask) {
        super(null, _TAG, 0, 0);
        mAidMask = aid_mask;
    }

    public byte[] getAidMask() {
        return mAidMask;
    }

    @Override
    public void translate() throws DoParserException {

        mAidMask = null;

        byte[] data = getRawData();
        int index = getValueIndex();

        if (index + getValueLength() > data.length) {
            throw new DoParserException("Not enough data for AID-MASK-DO!");
        }

        mAidMask = new byte[getValueLength()];
        System.arraycopy(data, index, mAidMask, 0, getValueLength());
    }

}
