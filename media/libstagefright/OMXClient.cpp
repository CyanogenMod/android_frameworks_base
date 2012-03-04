/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifdef DEBUG_OMX
#define LOG_NDEBUG 0
#endif

#define LOG_TAG "OMXClient"
#include <utils/Log.h>

#include <binder/IServiceManager.h>
#include <media/IMediaPlayerService.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/OMXClient.h>

namespace android {

OMXClient::OMXClient() {
}

status_t OMXClient::connect() {
    sp<IServiceManager> sm = defaultServiceManager();
    sp<IBinder> binder = sm->getService(String16("media.player"));
    sp<IMediaPlayerService> service = interface_cast<IMediaPlayerService>(binder);

    CHECK(service.get() != NULL);

    mOMX = service->getOMX();
    CHECK(mOMX.get() != NULL);

    return OK;
}

void OMXClient::disconnect() {
}

}  // namespace android
