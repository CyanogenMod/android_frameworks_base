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

/**
 * This class represents the CLF filter rule data object (CLF_FILTER_DO),
 * according to verizone requirement.
 *
 * The CLF_FILTER_DO contains one access rules of type FILTER-ENTRY.
 *
 * @author love khanna
 */
public class ClfFilterDo extends VzwTlv {

    public final static int _TAG = 0xFE;

    private FilterEntryDo mFilterEntryAr = null;

    public ClfFilterDo(byte[] rawData, int valueIndex, int valueLength) {
        super(rawData, _TAG, valueIndex, valueLength);
    }

    public ClfFilterDo(FilterEntryDo filter_entry_do) {
        super(null, _TAG, 0, 0);
        mFilterEntryAr = filter_entry_do;
    }

    public FilterEntryDo getFilterEntryDo() {
        return mFilterEntryAr;
    }

    @Override
    public void translate() throws DoParserException {

        this.mFilterEntryAr = null;

        byte[] data = getRawData();
        int index = getValueIndex();

        if (index + getValueLength() > data.length) {
            throw new DoParserException("Not enough data for CLF_FILTER_DO!");
        }

        do {
            VzwTlv temp = VzwTlv.parse(data, index);

            if (temp.getTag() == FilterEntryDo._TAG) { // NFC-AR-DO
                mFilterEntryAr = new FilterEntryDo(data, temp.getValueIndex(),
                        temp.getValueLength());
                mFilterEntryAr.translate();
            } else {
                throw new DoParserException(
                        "Invalid FILTER_ENTRY_DO in CLF_FILTER_DO!");
            }
            index = temp.getValueIndex() + temp.getValueLength();
        } while (getValueIndex() + getValueLength() > index);

        if (mFilterEntryAr == null) {
            throw new DoParserException(
                    "Invalid FILTER_ENTRY_DO in CLF_FILTER_DO!");
        }
    }
}
