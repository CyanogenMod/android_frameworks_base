/*
 * Copyright (C) ST-Ericsson SA 2010
 * Copyright (C) 2010 The Android Open Source Project
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
 *
 * Author: Andreas Gustafsson (andreas.a.gustafsson@stericsson.com)
 *         for ST-Ericsson
 */

#ifndef FMRADIO_DATA_SOURCE_H_

#define FMRADIO_DATA_SOURCE_H_

#include <stdio.h>

#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaErrors.h>
#include <utils/threads.h>
#include <hardware_legacy/AudioHardwareInterface.h>
#include <media/AudioSystem.h>
#include <system/audio.h>
#include <hardware_legacy/AudioSystemLegacy.h>

namespace android {

class FMRadioDataSource : public DataSource {
public:
    FMRadioDataSource();

    virtual status_t initCheck() const;

    virtual ssize_t readAt(off64_t offset, void *data, size_t size);

    virtual status_t getSize(off64_t *size);

    virtual uint32_t getBufferSize();

    virtual uint32_t getNumChannels();

    virtual uint32_t getSampleRate();

    virtual uint32_t getFormat();

protected:
    virtual ~FMRadioDataSource();

private:
    android_audio_legacy::AudioStreamIn *mStream;
    sp<IAudioFlinger> mAudioFlinger;
    FMRadioDataSource(const FMRadioDataSource &);
    FMRadioDataSource &operator=(const FMRadioDataSource &);
    audio_input_clients mInputClientId;
    uint32_t mFormat;
    uint32_t mChannels;
    uint32_t mSampleRate;
    uint32_t mOverwrittenBytes;
    uint32_t mFlags;
};

}  // namespace android

#endif  // FMRADIO_DATA_SOURCE_H_
