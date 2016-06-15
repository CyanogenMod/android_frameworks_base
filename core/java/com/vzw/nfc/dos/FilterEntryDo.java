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

public class FilterEntryDo extends VzwTlv {

    public final static int _TAG = 0xA1;

    private AidRangeDo mAidRangeDo = null;
    private AidMaskDo mAidMaskDo = null;
    private RoutingModeDo mRoutingModeDo = null;
    private FilterConditionTagDo mFilterConditionTagDo = null;
    private VzwPermissionDo mVzwArDo = null;

    public FilterEntryDo(byte[] rawData, int valueIndex, int valueLength) {
        super(rawData, _TAG, valueIndex, valueLength);
    }

    public FilterEntryDo(AidRangeDo aid_range, AidMaskDo aid_mask,
            RoutingModeDo routing_mode,
            FilterConditionTagDo filter_condition_tag) {
        super(null, _TAG, 0, 0);
        mAidMaskDo = aid_mask;
        mAidRangeDo = aid_range;
        mFilterConditionTagDo = filter_condition_tag;
        mRoutingModeDo = routing_mode;
    }

    public AidRangeDo getAidRangeDo() {
        return mAidRangeDo;
    }

    public AidMaskDo getAidMaskDo() {
        return mAidMaskDo;
    }

    public RoutingModeDo getRoutingModeDo() {
        return mRoutingModeDo;
    }

    public FilterConditionTagDo getFilterConditionTagDo() {
        return mFilterConditionTagDo;
    }

    public VzwPermissionDo getVzwArDo() {
        return mVzwArDo;
    }

    @Override
    public void translate() throws DoParserException {

        this.mAidMaskDo = null;
        this.mAidRangeDo = null;
        this.mFilterConditionTagDo = null;
        this.mRoutingModeDo = null;

        byte[] data = getRawData();
        int index = getValueIndex();

        if (index + getValueLength() > data.length) {
            throw new DoParserException("Not enough data for FILTER_ENTRY_DO!");
        }

        do {
            VzwTlv temp = VzwTlv.parse(data, index);

            if (temp.getTag() == AidMaskDo._TAG) { // AID_MASK_DO
                mAidMaskDo = new AidMaskDo(data, temp.getValueIndex(),
                        temp.getValueLength());
                mAidMaskDo.translate();
            } else if (temp.getTag() == AidRangeDo._TAG) { // AID_RANGE_DO
                mAidRangeDo = new AidRangeDo(data, temp.getValueIndex(),
                        temp.getValueLength());
                mAidRangeDo.translate();
            } else if (temp.getTag() == RoutingModeDo._TAG) { // ROUTING_MODE_DO
                mRoutingModeDo = new RoutingModeDo(data, temp.getValueIndex(),
                        temp.getValueLength());
                mRoutingModeDo.translate();
            } else if (temp.getTag() == FilterConditionTagDo._TAG) { // FILTER_CONDITION_TAG_DO
                mFilterConditionTagDo = new FilterConditionTagDo(data,
                        temp.getValueIndex(), temp.getValueLength());
                mFilterConditionTagDo.translate();
            } else if (temp.getTag() == VzwPermissionDo._TAG) { // VZW_AR_DO
                mVzwArDo = new VzwPermissionDo(data, temp.getValueIndex(),
                        temp.getValueLength());
                mVzwArDo.translate();
            } else {
                throw new DoParserException("Invalid DO in FILTER_ENTRY_DO!");
            }
            index = temp.getValueIndex() + temp.getValueLength();
        } while (getValueIndex() + getValueLength() > index);

        if (mAidMaskDo == null
                || mAidRangeDo == null
                || mRoutingModeDo == null
                || mVzwArDo == null
                || mAidMaskDo.getAidMask().length != mAidRangeDo.getAidRange().length) {
            throw new DoParserException("missing DO in FILTER_ENTRY_DO!");
        }
    }
}
