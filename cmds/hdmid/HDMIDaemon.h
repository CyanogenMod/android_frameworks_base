/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (c) 2010-2011, Code Aurora Forum. All rights reserved.
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

#ifndef ANDROID_HDMISERVICE_H
#define ANDROID_HDMISERVICE_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/threads.h>
#include <utils/AssetManager.h>

#include <surfaceflinger/ISurfaceComposer.h>
#include <surfaceflinger/SurfaceComposerClient.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <pthread.h>

#include <sys/socket.h>
#include <sys/select.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/un.h>

#include <cutils/config_utils.h>
#include <cutils/cpu_info.h>
#include <cutils/properties.h>
#include <cutils/sockets.h>

#include <linux/netlink.h>

#include <private/android_filesystem_config.h>


namespace android {

enum uevent_action { action_add, action_remove, action_change,
                                  action_online, action_offline, action_audio_on, action_audio_off, action_no_broadcast_online };
const int ueventParamMax = 32;
struct uevent {
    char *path;
    enum uevent_action action;
    char *subsystem;
    char *param[ueventParamMax];
    unsigned int seqnum;
    uevent() : path(NULL), subsystem(NULL) {
	for (int i = 0; i < ueventParamMax; i++)
	    param[i] = NULL;
    }
};

struct HDMIUeventQueue {
    HDMIUeventQueue* next;
    uevent mEvent;
    ~HDMIUeventQueue() {
        delete[] mEvent.path;
        delete[] mEvent.subsystem;
        for (int i = 0; i < ueventParamMax; i++) {
            if (!mEvent.param[i])
                break;
            delete[] mEvent.param[i];
        }
    }
};

class HDMIDaemon : public Thread, public IBinder::DeathRecipient
{
    virtual bool        threadLoop();
    virtual status_t    readyToRun();
    virtual void        onFirstRef();
    virtual void        binderDied(const wp<IBinder>& who);
    bool processUeventMessage(uevent& event);
    void queueUevent();
    void processUeventQueue();
    void processUevent();
    int processFrameworkCommand();
    bool sendCommandToFramework(uevent_action action = action_offline);
    bool cableConnected(bool defaultValue = true) const;
    bool readResolution();
    void setResolution(int ID);
    bool openFramebuffer();
    bool writeHPDOption(int userOption) const;
    inline bool isValidMode(int ID);
    bool checkHDCPPresent();

    int mFrameworkSock;
    int mAcceptedConnection;
    int mUeventSock;
    HDMIUeventQueue* mHDMIUeventQueueHead;
    sp<SurfaceComposerClient> mSession;
    int fd1;
    bool mDriverOnline;
    int mCurrentID;
    int mNxtMode;
    char mEDIDs[128];

public:
                HDMIDaemon();
    virtual     ~HDMIDaemon();

    sp<SurfaceComposerClient> session() const;
};

// ---------------------------------------------------------------------------

}; // namespace android

#endif
