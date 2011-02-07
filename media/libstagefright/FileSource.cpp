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

#include <media/stagefright/FileSource.h>
#include <media/stagefright/MediaDebug.h>

namespace android {

FileSource::FileSource(const char *filename)
    : mFile(fopen(filename, "rb")),
      mOffset(0),
      mLength(-1) {
}

FileSource::FileSource(int fd, int64_t offset, int64_t length)
    : mFile(fdopen(fd, "rb")),
      mOffset(offset),
      mLength(length) {
    CHECK(offset >= 0);
    CHECK(length >= 0);
}

FileSource::~FileSource() {
    if (mFile != NULL) {
        fclose(mFile);
        mFile = NULL;
    }
}

status_t FileSource::initCheck() const {
    return mFile != NULL ? OK : NO_INIT;
}

ssize_t FileSource::readAt(off64_t offset, void *data, size_t size) {
    if (mFile == NULL) {
        return NO_INIT;
    }

    Mutex::Autolock autoLock(mLock);

    if (mLength >= 0) {
        if (offset >= mLength) {
            return 0;  // read beyond EOF.
        }
        int64_t numAvailable = mLength - offset;
        if ((int64_t)size > numAvailable) {
            size = numAvailable;
        }
    }

    off64_t err = lseek64(mFile->_file, offset + mOffset, SEEK_SET);
    if (err < 0) {
        LOGE("seek to %lld failed", offset + mOffset);
        return UNKNOWN_ERROR;
    }

    return read(mFile->_file, data, size);
}

status_t FileSource::getSize(off64_t *size) {
    if (mFile == NULL) {
        return NO_INIT;
    }

    if (mLength >= 0) {
        *size = mLength;

        return OK;
    }

    fseek(mFile, 0, SEEK_END);
    *size = ftello(mFile);

    return OK;
}

}  // namespace android
