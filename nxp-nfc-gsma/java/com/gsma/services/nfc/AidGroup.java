/*
 *
 *  Copyright (C) 2015 NXP Semiconductors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.gsma.services.nfc;

import java.util.ArrayList;
import java.util.List;

/**
 * This class handles group of AID defined in an "Off-Host" service
 * @since NFCHST6.0 <I>(REQ_127)</I>
 */
public class AidGroup {
    private String mDescription = null;
    private String mCategory = null;
    private List<String> mAidList = new ArrayList<String>();

    AidGroup() {}

    AidGroup(String description, String category) {
        mDescription = description;
        mCategory = category;
    }

    /**
     * Return the category of the group of AIDs.
     * @return Category of the group of AIDs:<BR><UL>
     * <LI><code>android.nfc.cardemulation.CardEmulation.CATEGORY_PAYMENT</code></LI>
     * <LI><code>android.nfc.cardemulation.CardEmulation.CATEGORY_OTHER</code></IL></UL>
     * @since NFCHST6.0
     */
    public String getCategory() {
        return mCategory;
    }

    /**
     * Return the description of the group of AIDs.
     * @return The description of the group of AIDs
     * @since NFCHST6.0
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Add a new AID to the current group.
     * @param aid Application IDentifier to add to the current group
     * @exception IllegalArgumentException <BR>Indicate that a method has been passed an illegal or inappropriate argument.
     * @since NFCHST6.0
     */
    public void addNewAid(String aid) {
        mAidList.add(aid);
    }

    /**
     * Remove an AID from the current group.
     * @param aid Application IDentifier to remove from the current group
     * @exception IllegalArgumentException <BR>Indicate that a method has been passed an illegal or inappropriate argument.
     * @since NFCHST6.0
     */
    public void removeAid(String aid) {
        mAidList.remove(aid);
    }

    public List<String> getAidList() {
        return mAidList;
    }

    public String[] getAids() {
        String [] aidArray = mAidList.toArray(new String[mAidList.size()]);
        return aidArray;
    }

}
