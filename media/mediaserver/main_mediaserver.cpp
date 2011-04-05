/*
**
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

// System headers required for setgroups, etc.
#include <sys/types.h>
#include <unistd.h>
#include <grp.h>

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <utils/Log.h>

#include <AudioFlinger.h>
#include <CameraService.h>
#include <MediaPlayerService.h>
#include <AudioPolicyService.h>
#include <private/android_filesystem_config.h>

using namespace android;

int waitBeforeAdding( const String16& serviceName ) {
    sp<IServiceManager> sm = defaultServiceManager();
    for ( int i = 0 ; i < 5; i++ ) {
        if ( sm->checkService ( serviceName ) != NULL ) {
            sleep(1);
        }
        else {
            //good to go;
            return 0;
        }
    }
    LOGE("waitBeforeAdding (%s) timed out",
         String8(serviceName.string()).string());
    return -1;
}

int main(int argc, char** argv)
{
    sp<ProcessState> proc(ProcessState::self());
    sp<IServiceManager> sm = defaultServiceManager();
    LOGI("ServiceManager: %p", sm.get());
    waitBeforeAdding( String16("media.audio_flinger") );
    AudioFlinger::instantiate();
    waitBeforeAdding( String16("media.player") );
    MediaPlayerService::instantiate();
    waitBeforeAdding( String16("media.camera") );
    CameraService::instantiate();
    waitBeforeAdding( String16("media.audio_policy") );
    AudioPolicyService::instantiate();
    ProcessState::self()->startThreadPool();
    IPCThreadState::self()->joinThreadPool();
}
