/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef ANDROID_REGIONALIZATION_ENVIRONMENT_H
#define ANDROID_REGIONALIZATION_ENVIRONMENT_H

namespace android {

 /**
  * Class used by Regionalization Carrier switching in order to get
  * the resource path of switched packages for Carrier.
  */
class Environment {
public:
     /** For boot and shutdown animation and music
      *  The value which will get different type Animation and boot
      *  audio file path from BOOT_SHUTDOWN_FILE Array.
      *  (STATUS:TYPE)
      *  (0:0) Boot Animation,(0:1) Boot Audio
      *  (1:0) Shutdown Animation,(1,1) Shutdown Audio
      */
     const static int BOOT_STATUS = 0;
     const static int SHUTDOWN_STATUS = 1;
     const static int ANIMATION_TYPE = 0;
     const static int MUSIC_TYPE = 1;

     Environment(void);

     ~Environment(void);

     static bool isSupported(void);

     bool loadPackagesFromSpecFile(void);

     const char* getMediaFile(int type, int state);

     const char* getOverlayDir(void);

private:
    char* mStoragePos;
    int mPackagesCount;
    char** mPackages;
    char* mMediaFile;
    char* mOverlayDir;
};

}; // namespace android

#endif // ANDROID_REGIONALIZATION_ENVIRONMENT_H
