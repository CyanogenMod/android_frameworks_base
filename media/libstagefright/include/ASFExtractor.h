/*
 *****************************************************************************
 *
 *                                Android
 *                  ITTIAM SYSTEMS PVT LTD, BANGALORE
 *                           COPYRIGHT(C) 2010-20
 *
 *  This program  is  proprietary to  Ittiam  Systems  Private  Limited  and
 *  is protected under Indian  Copyright Law as an unpublished work. Its use
 *  and  disclosure  is  limited by  the terms  and  conditions of a license
 *  agreement. It may not be copied or otherwise  reproduced or disclosed to
 *  persons outside the licensee's organization except in accordance with the
 *  terms  and  conditions   of  such  an  agreement.  All  copies  and
 *  reproductions shall be the property of Ittiam Systems Private Limited and
 *  must bear this notice in its entirety.
 *
 *****************************************************************************
 */
/**
 *****************************************************************************
 *
 *  @file     ASFExtractor.h
 *
 *  @brief    This file contains definition of ASFExtractor class
 *
 *****************************************************************************
 */

#ifndef ITTIAM_ASF_EXTRACTOR_H_

#define ITTIAM_ASF_EXTRACTOR_H_

#include <media/stagefright/MediaExtractor.h>
#include <utils/Vector.h>

namespace android {

struct AMessage;
class String8;
class ASFExtractorImpl;

typedef struct ASF_WRAPER
{
    ASFExtractorImpl* (*ASFExtractor)(
        const sp<DataSource> &source);

    void (*destructorASFExtractor)(ASFExtractorImpl *mHandle);

    size_t (*countTracks)(ASFExtractorImpl *mHandle);

    sp<MediaSource> (*getTrack)(
        size_t index, ASFExtractorImpl *mHandle);

    sp<MetaData> (*getTrackMetaData)(
        size_t index,
        uint32_t flags,
        ASFExtractorImpl *mHandle);

    sp<MetaData> (*getMetaData)(ASFExtractorImpl *mHandle);

}ASF_WRAPER;

class ASFExtractor : public MediaExtractor {
public:
    // Extractor assumes ownership of "source".
    ASFExtractor(const sp<DataSource> &source);

    virtual size_t countTracks();
    virtual sp<MediaSource> getTrack(size_t index);
    virtual sp<MetaData> getTrackMetaData(size_t index, uint32_t flags);

    virtual sp<MetaData> getMetaData();

protected:
    virtual ~ASFExtractor();

private:
    ASF_WRAPER *pASFParser;
    ASFExtractorImpl *mHandle;
};

bool SniffASF(const sp<DataSource> &source,
              String8 *mimeType,
              float *confidence,
              sp<AMessage> *meta);

bool isASFParserAvailable();
void closeASFLib();

}  // namespace android

#endif  // ITTIAM_ASF_EXTRACTOR_H_

