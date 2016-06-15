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

public class RoutingModeDo extends VzwTlv {

    public final static int _TAG = 0xB2;

    private byte mRoutingInfo = 0x00;

    private boolean mLowPowerModeAllowed = false;
    private boolean mFullPowerModeAllowed = false;
    private boolean mNoPowerModeAllowed = false;

    public RoutingModeDo(byte[] rawData, int valueIndex, int valueLength) {
        super(rawData, _TAG, valueIndex, valueLength);
    }

    public RoutingModeDo(boolean low_power, boolean full_power, boolean no_power) {
        super(null, _TAG, 0, 0);
        mLowPowerModeAllowed = low_power;
        mFullPowerModeAllowed = full_power;
        mNoPowerModeAllowed = no_power;
    }

    public RoutingModeDo(byte route_info) {
        super(null, _TAG, 0, 0);
        mRoutingInfo = route_info;
    }

    public byte getRoutingInfo() {
        return mRoutingInfo;
    }

    public boolean isLowPowerModeAllowed() {
        return mLowPowerModeAllowed;
    }

    public boolean isFullPowerModeAllowed() {
        return mFullPowerModeAllowed;
    }

    public boolean isNoPowerModeAllowed() {
        return mNoPowerModeAllowed;
    }

    @Override
    public void translate() throws DoParserException {

        mRoutingInfo = 0x00;

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

        mRoutingInfo = data[index];

        mNoPowerModeAllowed = ((mRoutingInfo & 0x01) != 0x00 ? true : false);
        mLowPowerModeAllowed = ((mRoutingInfo & 0x02) != 0x00 ? true : false);
        mFullPowerModeAllowed = ((mRoutingInfo & 0x04) != 0x00 ? true : false);
    }
}
