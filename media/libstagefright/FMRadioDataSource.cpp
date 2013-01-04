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

#define LOG_TAG "FMRadioDataSource"
#include <utils/Log.h>

#include <media/stagefright/FMRadioDataSource.h>
#include <media/stagefright/MediaDebug.h>
#include <media/AudioSystem.h>
#include <media/IAudioPolicyService.h>
#include <binder/IServiceManager.h>
#include <media/AudioSystem.h>
#include <media/mediarecorder.h>
#include <media/IAudioFlinger.h>
#include <system/audio.h>


namespace android {

FMRadioDataSource::FMRadioDataSource() {

    mFormat = android_audio_legacy::AudioSystem::PCM_16_BIT;
    mChannels = android_audio_legacy::AudioSystem::CHANNEL_IN_STEREO;
    mSampleRate = 48000;
    mFlags = 0;
    mOverwrittenBytes = 0;
    mInputClientId = AUDIO_INPUT_CLIENT_PLAYBACK;
    int inputSource = AUDIO_SOURCE_FM_RADIO_RX;

    mStream = (android_audio_legacy::AudioStreamIn*) AudioSystem::getInput(inputSource,mSampleRate, mFormat, mChannels,
                                              (audio_in_acoustics_t)mFlags,0,&mInputClientId);
    if (mStream != NULL) {
        AudioSystem::startInput((audio_io_handle_t) mStream);
    }
}

FMRadioDataSource::~FMRadioDataSource() {

    if (mStream != NULL) {
        AudioSystem::stopInput((audio_io_handle_t) mStream);
        AudioSystem::releaseInput((audio_io_handle_t) mStream);
    }
}

status_t FMRadioDataSource::initCheck() const {
    return mStream != NULL ? OK : NO_INIT;
}

ssize_t FMRadioDataSource::readAt(off64_t offset, void *data, size_t size) {
    if(mStream != NULL) {
       const sp<IAudioFlinger>& af = AudioSystem::get_audio_flinger();
       return af->readInput((uint32_t*)mStream, mInputClientId, data, size, &mOverwrittenBytes);
    }
    return 0;
}

status_t FMRadioDataSource::getSize(off64_t *size) {
    *size = 0;
    return OK;
}

uint32_t FMRadioDataSource::getBufferSize() {
    return mStream->bufferSize();
}

uint32_t FMRadioDataSource::getNumChannels() {
    return android_audio_legacy::AudioSystem::popCount(mChannels);
}

uint32_t FMRadioDataSource::getSampleRate() {
    return mSampleRate;
}

uint32_t FMRadioDataSource::getFormat() {
    if (mFormat == android_audio_legacy::AudioSystem::PCM_16_BIT) {
        return 16;
    } else if (mFormat == android_audio_legacy::AudioSystem::PCM_8_BIT) {
        return 8;
    }
    return 0;
}

}  // namespace android
