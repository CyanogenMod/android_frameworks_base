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

#include <media/stagefright/MediaSource.h>

namespace android {

MediaSource::MediaSource() {}

MediaSource::~MediaSource() {}

////////////////////////////////////////////////////////////////////////////////

MediaSource::ReadOptions::ReadOptions() {
    reset();
}

void MediaSource::ReadOptions::reset() {
    mOptions = 0;
    mSeekTimeUs = 0;
    mLatenessUs = 0;
    mSkipFrameUntilTimeUs = 0;
}

void MediaSource::ReadOptions::setSeekTo(int64_t time_us, SeekMode mode) {
    mOptions |= kSeekTo_Option;
#ifdef OMAP_ENHANCEMENT
    //Incase the app layer tries to seek to negative offset,
    //resetting the value to Zero.
    if (time_us < 0)
    {
        time_us = 0;
    }
#endif
    mSeekTimeUs = time_us;
    mSeekMode = mode;
}

extern "C" void _ZN7android11MediaSource11ReadOptions9setSeekToExNS1_8SeekModeE(int64_t time_us, void *mode);
extern "C" void _ZN7android11MediaSource11ReadOptions9setSeekToEx(int64_t time_us) {
    _ZN7android11MediaSource11ReadOptions9setSeekToExNS1_8SeekModeE(time_us, NULL);
}

void MediaSource::ReadOptions::clearSeekTo() {
    mOptions &= ~kSeekTo_Option;
    mSeekTimeUs = 0;
    mSeekMode = SEEK_CLOSEST_SYNC;
}

bool MediaSource::ReadOptions::getSeekTo(
        int64_t *time_us, SeekMode *mode) const {
    *time_us = mSeekTimeUs;
    *mode = mSeekMode;
    return (mOptions & kSeekTo_Option) != 0;
}

void MediaSource::ReadOptions::clearSkipFrame() {
    mOptions &= ~kSkipFrame_Option;
    mSkipFrameUntilTimeUs = 0;
}

void MediaSource::ReadOptions::setSkipFrame(int64_t timeUs) {
    mOptions |= kSkipFrame_Option;
    mSkipFrameUntilTimeUs = timeUs;
}

bool MediaSource::ReadOptions::getSkipFrame(int64_t *timeUs) const {
    *timeUs = mSkipFrameUntilTimeUs;
    return (mOptions & kSkipFrame_Option) != 0;
}

void MediaSource::ReadOptions::setLateBy(int64_t lateness_us) {
    mLatenessUs = lateness_us;
}

int64_t MediaSource::ReadOptions::getLateBy() const {
    return mLatenessUs;
}

#ifdef USE_GETBUFFERINFO
status_t MediaSource::getBufferInfo(sp<IMemory>& Frame, size_t *alignedSize) {
    //do nothing, since it is virtual, need dummy implementation
    return OK;
}
#endif

}  // namespace android
