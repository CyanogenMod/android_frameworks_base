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

import java.util.ArrayList;

public class ClfFilterDoList extends VzwTlv {

    private ArrayList<ClfFilterDo> mClfFilterDos = new ArrayList<ClfFilterDo>();

    public ClfFilterDoList(byte[] rawData, int valueIndex, int valueLength) {
        super(rawData, 0x00, valueIndex, valueLength);
    }

    public ArrayList<ClfFilterDo> getClfFilterDos() {
        return mClfFilterDos;
    }

    @Override
    public void translate() throws DoParserException {

        mClfFilterDos.clear();

        byte[] data = getRawData();
        int index = getValueIndex();

        if (getValueLength() == 0) {
            // No Access rule available for the requested reference.
            return;
        }

        if (index + getValueLength() > data.length) {
            throw new DoParserException(
                    "Not enough data for ALL_CLF_FILTER_DO!");
        }

        VzwTlv temp;
        int currentPos = index;
        int endPos = index + getValueLength();
        do {
            temp = VzwTlv.parse(data, currentPos);

            ClfFilterDo tmpClfFilterDo;

            if (temp.getTag() == ClfFilterDo._TAG) { // CLF_FILTER_DO tag
                tmpClfFilterDo = new ClfFilterDo(data, temp.getValueIndex(),
                        temp.getValueLength());
                tmpClfFilterDo.translate();
                mClfFilterDos.add(tmpClfFilterDo);
            } else {
                throw new DoParserException("Invalid DO in ALL_CLF_FILTER_DO!");
            }
            // get CLF_FILTER_DOs as long as data is available.
            currentPos = temp.getValueIndex() + temp.getValueLength();
        } while (currentPos < endPos);
    }
}
