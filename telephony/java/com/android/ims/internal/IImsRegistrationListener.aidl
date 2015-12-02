/*
 * Copyright (c) 2013 The Android Open Source Project
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

package com.android.ims.internal;

import com.android.ims.ImsReasonInfo;

/**
 * A listener type for receiving notifications about the changes to
 * the IMS connection(registration).
 *
 * {@hide}
 */
interface IImsRegistrationListener {
    /**
     * Notifies the application when the device is connected to the IMS network.
     *
     * @param imsRadioTech the radio access technology. Valid values are {@code
     * RIL_RADIO_TECHNOLOGY_*} defined in {@link ServiceState}.
     */
    void registrationConnected(int imsRadioTech) = 0;

    /**
     * Notifies the application when the device is trying to connect the IMS network.
     *
     * @param imsRadioTech the radio access technology. Valid values are {@code
     * RIL_RADIO_TECHNOLOGY_*} defined in {@link ServiceState}.
     */
    void registrationProgressing(int imsRadioTech) = 1;

    /**
     * Notifies the application when the device is disconnected from the IMS network.
     */
    void registrationDisconnected(in ImsReasonInfo imsReasonInfo) = 2;

    /**
     * Notifies the application when its suspended IMS connection is resumed,
     * meaning the connection now allows throughput.
     */
    void registrationResumed() = 3;

    /**
     * Notifies the application when its current IMS connection is suspended,
     * meaning there is no data throughput.
     */
    void registrationSuspended() = 4;

    /**
     * Notifies the application when its current IMS connection is updated
     * since the service setting is changed or the service is added/removed.
     *
     * @param serviceClass a service class specified in {@link ImsServiceClass}
     * @param event an event type when this callback is called
     *    If {@code event} is 0, meaning the specified service is removed from the IMS connection.
     *    Else ({@code event} is 1), meaning the specified service is added to the IMS connection.
     */
    void registrationServiceCapabilityChanged(int serviceClass, int event) = 5;

    /**
     * Notifies the application when features on a particular service enabled or
     * disabled successfully based on user preferences.
     *
     * @param serviceClass a service class specified in {@link ImsServiceClass}
     * @param enabledFeatures features enabled as defined in com.android.ims.ImsConfig#FeatureConstants.
     * @param disabledFeatures features disabled as defined in com.android.ims.ImsConfig#FeatureConstants.
     */
    void registrationFeatureCapabilityChanged(int serviceClass,
            in int[] enabledFeatures, in int[] disabledFeatures) = 6;

    /**
     * Updates the application with the waiting voice message count.
     * @param count The number of waiting voice messages.
     */
    void voiceMessageCountUpdate(int count) = 7;

    /**
     * Compatibility with AOSP
     */
    void registrationConnected() = 8;
    void registrationProgressing() = 9;
}
