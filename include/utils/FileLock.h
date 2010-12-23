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

#ifndef __LIBS_FILE_LOCK_H
#define __LIBS_FILE_LOCK_H

#include <fcntl.h>

namespace android {

/*
 * Object oriented interface for flock.  Implements reference counting so that
 * multiple levels of locks on the same object instance is possible.
 */
class FileLock {
public:
    FileLock(const char* fileName);

    /*
     * Lock the file.  A balanced call to unlock is required even if the lock
     * fails.
     */
    bool lock(int openFlags=O_RDWR, mode_t fileCreateMode=0755) {
        mRefCount++;
        if (mFd == -1) {
            return doLock(openFlags, fileCreateMode);
        } else {
            return true;
        }
    }

    /*
     * Call this when mapping is no longer needed.
     */
    void unlock(void) {
        if (--mRefCount <= 0) {
            delete this;
        }
    }

    /*
     * Return the name of the file this map came from, if known.
     */
    const char* getFileName(void) const { return mFileName; }

    /*
     * Return the open file descriptor, if locked; -1 otherwise.
     */
    int getFileDescriptor(void) const { return mFd; }

protected:
    // don't delete objects; call unlock()
    ~FileLock(void);

    bool doLock(int openFlags, mode_t fileCreateMode);

private:

    int         mRefCount;      // reference count
    int         mFd;            // file descriptor, if locked
    char*       mFileName;      // original file name, if known
};

}; // namespace android

#endif // __LIBS_FILE_LOCK_H
