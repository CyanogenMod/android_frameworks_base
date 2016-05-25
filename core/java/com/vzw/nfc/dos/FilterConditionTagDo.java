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

public class FilterConditionTagDo extends VzwTlv {

    public final static int _TAG = 0xD2;
    public final static byte SCREEN_OFF_TAG = (byte) 0xF1;

    private byte mFilterConditionTag = 0x00;

    public FilterConditionTagDo(byte[] rawData, int valueIndex, int valueLength) {
        super(rawData, _TAG, valueIndex, valueLength);
    }

    public FilterConditionTagDo(byte filter_cond_tag) {
        super(null, _TAG, 0, 0);
        mFilterConditionTag = filter_cond_tag;
    }

    public byte getFilterConditionTag() {
        return mFilterConditionTag;
    }

    @Override
    public void translate() throws DoParserException {

        mFilterConditionTag = 0x00;

        byte[] data = getRawData();
        int index = getValueIndex();

        if (index + getValueLength() > data.length) {
            throw new DoParserException(
                    "Not enough data for FILTER_CONDITION_TAG_DO!");
        }

        if (getValueLength() != 1) {
            throw new DoParserException(
                    "Invalid length of FILTER_CONDITION_TAG_DO!");
        }
        mFilterConditionTag = data[index];
    }

}
