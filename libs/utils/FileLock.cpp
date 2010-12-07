/*
 * Copyright (C) 2010, T-Mobile USA, Inc.
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

//
// File locking utility
//

#define LOG_TAG "filelock"

#include <utils/FileLock.h>
#include <utils/Log.h>

#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/file.h>
#include <errno.h>
#include <assert.h>

using namespace android;

/*
 * Constructor.  Create an unlocked object.
 */
FileLock::FileLock(const char* fileName)
    : mRefCount(0), mFd(-1), mFileName(strdup(fileName))
{
    assert(mFileName != NULL);
}

/*
 * Destructor.
 */
FileLock::~FileLock(void)
{
    assert(mRefCount == 0);

    if (mFileName != NULL) {
        free(mFileName);
    }
    if (mFd >= 0) {
        if (flock(mFd, LOCK_UN) != 0) {
            LOGE("flock(%s,LOCK_UN) failed: %s\n", mFileName, strerror(errno));
        }
        if (close(mFd) != 0) {
            LOGE("close(%s) failed: %s\n", mFileName, strerror(errno));
        }
    }
}

bool FileLock::doLock(int openFlags, mode_t fileCreateMode)
{
    int fd = open(mFileName, openFlags | O_CREAT, fileCreateMode);
    if (fd == -1) {
        return false;
    }

    if (flock(fd, LOCK_EX) != 0) {
        LOGE("flock(%s,LOCK_EX) failed: %s\n", mFileName, strerror(errno));
        close(fd);
        return false;
    }

    mFd = fd;
    return true;
}
