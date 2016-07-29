/*
* Copyright (C) 2011-2014 MediaTek Inc.
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

package com.mediatek.internal.telephony.gsm;

import android.telecom.VideoProfile;

/**
 * Internal remote interface for GSM's video call provider.
 *
 * At least initially, this aidl mirrors telecom's {@link VideoCallCallback}. We created a
 * separate aidl interface for invoking callbacks in Telephony from the GSM Service to without
 * accessing internal interfaces. See {@link IGsmVideoCallProvider} for additional detail.
 *
 * @see android.telecom.internal.IVideoCallCallback
 * @see android.telecom.VideoCallImpl
 *
 * {@hide}
 */
oneway interface IGsmVideoCallCallback {
    void receiveSessionModifyRequest(in VideoProfile videoProfile);

    void receiveSessionModifyResponse(int status, in VideoProfile requestedProfile,
        in VideoProfile responseProfile);

    void handleCallSessionEvent(int event);

    void changePeerDimensions(int width, int height);

    /* M: ViLTE part start */
    /* Different from AOSP, additional parameter "rotation" is added. */
    void changePeerDimensionsWithAngle(int width, int height, int rotation);
    /* M: ViLTE part end */

    void changeCallDataUsage(long dataUsage);

    void changeCameraCapabilities(in VideoProfile.CameraCapabilities cameraCapabilities);

    void changeVideoQuality(int videoQuality);
}

