/*
 * Copyright (C) 2011 Code Aurora Forum. All rights reserved
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

#ifndef EXTENDED_WRITER_H_

#define EXTENDED_WRITER_H_

#include <stdio.h>

#include <media/stagefright/MediaWriter.h>
#include <utils/threads.h>

namespace android {

struct MediaSource;
struct MetaData;

struct ExtendedWriter : public MediaWriter {
    ExtendedWriter(const char *filename);
    ExtendedWriter(int fd);

    status_t initCheck() const;

    virtual status_t addSource(const sp<MediaSource> &source);
    virtual bool reachedEOS();
    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual status_t pause();

protected:
    virtual ~ExtendedWriter();

private:
    FILE *mFile;
    status_t mInitCheck;
    sp<MediaSource> mSource;
    bool mStarted;
    volatile bool mPaused;
    volatile bool mResumed;
    volatile bool mDone;
    volatile bool mReachedEOS;
    pthread_t mThread;
    int64_t mEstimatedSizeBytes;
    int64_t mEstimatedDurationUs;

    int32_t mFormat;

    //QCP/EVRC header
    struct QCPEVRCHeader
    {
        /* RIFF Section */
        char riff[4];
        unsigned int s_riff;
        char qlcm[4];

        /* Format chunk */
        char fmt[4];
        unsigned int s_fmt;
        char mjr;
        char mnr;
        unsigned int data1;

        /* UNIQUE ID of the codec */
        unsigned short data2;
        unsigned short data3;
        char data4[8];
        unsigned short ver;

        /* Codec Info */
        char name[80];
        unsigned short abps;

        /* average bits per sec of the codec */
        unsigned short bytes_per_pkt;
        unsigned short samp_per_block;
        unsigned short samp_per_sec;
        unsigned short bits_per_samp;
        unsigned char vr_num_of_rates;

        /* Rate Header fmt info */
        unsigned char rvd1[3];
        unsigned short vr_bytes_per_pkt[8];
        unsigned int rvd2[5];

        /* Vrat chunk */
        unsigned char vrat[4];
        unsigned int s_vrat;
        unsigned int v_rate;
        unsigned int size_in_pkts;

        /* Data chunk */
        unsigned char data[4];
        unsigned int s_data;
    } __attribute__ ((packed));

    struct QCPEVRCHeader mHeader;
    off_t mOffset; //note off_t

    static void *ThreadWrapper(void *);
    status_t threadFunc();
    bool exceedsFileSizeLimit();
    bool exceedsFileDurationLimit();

    ExtendedWriter(const ExtendedWriter &);
    ExtendedWriter &operator=(const ExtendedWriter &);

    status_t writeQCPHeader( );
    status_t writeEVRCHeader( );
};

}  // namespace android

#endif  // AMR_WRITER_H_
