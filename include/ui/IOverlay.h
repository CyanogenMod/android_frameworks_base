/*
 * Copyright (C) 2007 The Android Open Source Project
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

#ifndef ANDROID_IOVERLAY_H
#define ANDROID_IOVERLAY_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/Errors.h>
#include <binder/IInterface.h>
#include <utils/RefBase.h>
#include <ui/PixelFormat.h>

namespace android {

class IOverlay : public IInterface
{
public: 
    DECLARE_META_INTERFACE(Overlay);

    virtual void destroy() = 0; // one-way
};

// ----------------------------------------------------------------------------

class BnOverlay : public BnInterface<IOverlay>
{
public:
    virtual status_t    onTransact( uint32_t code,
                                    const Parcel& data,
                                    Parcel* reply,
                                    uint32_t flags = 0);
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_IOVERLAY_H
