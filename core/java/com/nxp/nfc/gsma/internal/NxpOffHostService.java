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
package com.nxp.nfc.gsma.internal;

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import android.graphics.drawable.Drawable;
import android.nfc.cardemulation.AidGroup;

public class NxpOffHostService {
    public int mUserId;
    public String mDescription = null;
    public String mSEName = null;
    public Drawable mBanner;
    public String mPackageName = null;
    public String mServiceName = null;
    public boolean mModifiable = true;
    public List<AidGroup> mAidGroupList = new ArrayList<AidGroup>();
    public NxpNfcController mNxpNfcController;
    public int mBannerId;
    public Context mContext = null;
    public NxpOffHostService(int userId, String description, String SELocation,String packageName,
                             String serviceName, boolean modifiable) {
        mUserId = userId;
        mDescription = description;
        mSEName = SELocation;
        mPackageName = packageName;
        mServiceName = serviceName;
        mModifiable = modifiable;
    }

    /**
     * Return the Secure Element name which holds the "Off-Host" service.
     * @return Secure Element name holding the "Off-Host" service
     */
    public String getLocation() {
        return mSEName;
    }

    /**
     * Return the description of the "NxpOff-Host" service.
     * @return The Description of the "NxpOff-Host" service
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Return the Service Name of the "NxpOff-Host" service.
     * @return The Service name of the "NxpOff-Host" service
     */
    protected String getServiceName() {
        return mServiceName;
    }

    /**
     * Set a banner for the "NxpOff-Host" service.
     * @param banner A {@link Drawable} object representing the banner
     */
    public void setBanner(Drawable banner) {
        mBanner = banner;
    }


    /**
     * Set a banner for the "NxpOff-Host" service.
     * @param bannerid representing the banner
     */
    public void setBannerId(int bannerid) {
        mBannerId = bannerid;
    }
    /**
     * Get a modifiable for dynamic "NxpOff-Host" service.
     * @return modifiable for "NxpOff-Host service
     */
    protected boolean getModifiable() {
        return mModifiable;
    }

    /**
     * Return the banner linked to the "NxpOff-Host" service.
     * @return {@link Drawable} object representing the banner or <code>null</code> if no banner has been set
     */
    public Drawable getBanner() {
        return mBanner;
    }

    public int getBannerId() {
        return mBannerId;
    }


    public void setContext(Context context) {
        mContext = context;
    }


    public Context getContext() {
        return mContext;
    }

    public void setNxpNfcController(NxpNfcController nxpNfcController) {
        mNxpNfcController = nxpNfcController;
    }

   }
