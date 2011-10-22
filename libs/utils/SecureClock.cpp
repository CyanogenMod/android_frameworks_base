/*
 * Copyright (c) 2011, The CyanogenMod Project
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

#include <utils/SecureClock.h>

// ---------------------------------------------------------------------------

namespace android {

extern "C" int OMADRM_SecureClock_GetStatus_NTP(void)
{
    return 0;
}

extern "C" int OMADRM_SecureClock_GetStatus_OCSP(void)
{
    return 0;
}

extern "C" int OMADRM_SecureClock_GetTime_NTP(lgoem_dev_rtc_type *time)
{
    return 0;
}

extern "C" int OMADRM_SecureClock_GetTime_OCSP(lgoem_dev_rtc_type *time)
{
    return 0;
}

extern "C" int OMADRM_SecureClock_SetTime_NTP(lgoem_dev_rtc_type *time)
{
    return 0;
}

extern "C" int OMADRM_SecureClock_SetTime_OCSP(lgoem_dev_rtc_type *time)
{
    return 0;
}

}; // namespace android
