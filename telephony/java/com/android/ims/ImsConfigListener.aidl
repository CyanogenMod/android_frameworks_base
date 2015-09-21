/*
 * Copyright (c) 2014 The Android Open Source Project
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

package com.android.ims;

/**
 * Used by IMS config client to monitor the config operation results.
 * {@hide}
 */
oneway interface ImsConfigListener {
    /**
     * Notifies client the value of the get operation result on the feature config item.
     * The arguments are the same as passed to com.android.ims.ImsConfig#getFeatureValue.
     *
     * @param feature. as defined in com.android.ims.ImsConfig#FeatureConstants.
     * @param network. as defined in android.telephony.TelephonyManager#NETWORK_TYPE_XXX.
     * @param value. as defined in com.android.ims.ImsConfig#FeatureValueConstants.
     * @param status. as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     * @return void.
     */
    void onGetFeatureResponse(int feature, int network, int value, int status);

    /**
     * Notifies client the set value operation result for feature config item.
     * Used by clients that need to be notified the set operation result.
     * The arguments are the same as passed to com.android.ims.ImsConfig#setFeatureValue.
     * The arguments are repeated in the callback to enable the listener to understand
     * which configuration attempt failed.
     *
     * @param feature. as defined in com.android.ims.ImsConfig#FeatureConstants.
     * @param network. as defined in android.telephony.TelephonyManager#NETWORK_TYPE_XXX.
     * @param value. as defined in com.android.ims.ImsConfig#FeatureValueConstants.
     * @param status. as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     *
     * @return void.
     */
    void onSetFeatureResponse(int feature, int network, int value, int status);

    /**
     * Notifies client the value of the get operation result on the video quality item.
     *
     * @param status. as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     * @param quality. as defined in com.android.ims.ImsConfig#OperationValuesConstants.
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
     void onGetVideoQuality(int status, int quality);

    /**
     * Notifies client the set value operation result for video quality item.
     * Used by clients that need to be notified the set operation result.
     *
     * @param status. as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
     void onSetVideoQuality(int status);

    /**
     * Notifies client the value of the get operation result on get packet count item.
     *
     * @param status. as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     * @param packetCount. total number of packets sent or received
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
     void onGetPacketCount(int status, long packetCount);

    /**
     * Notifies client the value of the get operation result on get packet error count item.
     *
     * @param status. as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     * @param packetErrorCount. total number of packet errors encountered
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
     void onGetPacketErrorCount(int status, long packetErrorCount);

    /**
     * Notifies client the value of the get operation result on the wifi calling preference.
     *
     * @param status. as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     * @param wifiCallingStatus. as defined in com.android.ims.ImsConfig#WifiCallingValueConstants.
     * @param wifiCallingPreference. as defined in com.android.ims.ImsConfig#WifiCallingPreference.
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
     void onGetWifiCallingPreference(int status, int wifiCallingStatus, int wifiCallingPreference);

    /**
     * Notifies client the set value operation result for wifi calling preference item.
     * Used by clients that need to be notified the set operation result.
     *
     * @param status. as defined in com.android.ims.ImsConfig#OperationStatusConstants.
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
     void onSetWifiCallingPreference(int status);
}
