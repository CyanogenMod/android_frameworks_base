/**
 *****************************************************************************
 *
 *  @file     ASFExtractor.cpp
 *
 *  @brief    This file works as a wrapper to the ASFExtractor class
 *
 *****************************************************************************
 */

#define LOG_TAG "ASFDummyExtractor"
#include <utils/Log.h>

#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/MediaExtractor.h>
#include <utils/Vector.h>
#include <dlfcn.h>   /* For dynamic loading */
#include "include/ASFExtractor.h"


namespace android {
    static void * pASFHandle = NULL;

ASFExtractor::ASFExtractor(const sp<DataSource> &source) {
    const char *errstr;
    LOGD("Dummy ASFExtractor contructor");

    pASFParser = new ASF_WRAPER;

    pASFParser->ASFExtractor =     ( ASFExtractorImpl* (*)(const android::sp<android::DataSource>&))dlsym(pASFHandle, "ASFExtractor");
    if((errstr = dlerror()) != NULL){
        LOGE("dlsym(), err: %s", errstr);
        dlclose(pASFHandle);
        delete pASFParser;
        return;
    }
    pASFParser->destructorASFExtractor =     (void (*)(ASFExtractorImpl *))dlsym(pASFHandle, "destructorASFExtractor");
    if((errstr = dlerror()) != NULL){
        LOGE("dlsym(), err: %s", errstr);
        dlclose(pASFHandle);
        delete pASFParser;
        return;
    }
    pASFParser->countTracks =       (size_t (*)(ASFExtractorImpl *))dlsym(pASFHandle, "countTracks");
    if((errstr = dlerror()) != NULL){
        LOGE("dlsym(), err: %s", errstr);
        dlclose(pASFHandle);
        delete pASFParser;
        return;
    }
    pASFParser->getTrack =          (android::sp<android::MediaSource> (*)(size_t, ASFExtractorImpl *))dlsym(pASFHandle, "getTrack");
    if((errstr = dlerror()) != NULL){
        LOGE("dlsym(), err: %s", errstr);
        dlclose(pASFHandle);
        delete pASFParser;
        return;
    }
    pASFParser->getTrackMetaData =  (android::sp<android::MetaData> (*)(size_t, uint32_t, ASFExtractorImpl *))dlsym(pASFHandle, "getTrackMetaData");
    if((errstr = dlerror()) != NULL){
        LOGE("dlsym(), err: %s", errstr);
        dlclose(pASFHandle);
        delete pASFParser;
        return;
    }
    pASFParser->getMetaData =       (android::sp<android::MetaData> (*)(ASFExtractorImpl *))dlsym(pASFHandle, "getMetaData");
    if((errstr = dlerror()) != NULL){
        LOGE("dlsym(), err: %s", errstr);
        dlclose(pASFHandle);
        delete pASFParser;
        return;
    }


    mHandle = (*pASFParser->ASFExtractor)(source);
}

ASFExtractor::~ASFExtractor() {
    LOGD("Dummy ASFExtractor destructor");
    if(!pASFParser) {
        return;
    }

    (pASFParser->destructorASFExtractor)(mHandle);

	// Commented to handle the MediaSource::getFormat() issue.
    delete pASFParser;
    pASFParser = NULL;
}

size_t ASFExtractor::countTracks() {
    LOGV("Dummy ASFExtractor::countTracks()");
    if(!pASFParser) {
        return 0;
    }

    return (*pASFParser->countTracks)(mHandle);
}

sp<MediaSource> ASFExtractor::getTrack(size_t index) {
    LOGV("Dummy ASFExtractor::getTrack()");
    if(!pASFParser) {
        return NULL;
    }

    return (*pASFParser->getTrack)(index, mHandle);
}

sp<MetaData> ASFExtractor::getTrackMetaData(
        size_t index, uint32_t flags) {
    if(!pASFParser) {
        return NULL;
    }

    return (*pASFParser->getTrackMetaData)(index, flags, mHandle);
}

sp<MetaData> ASFExtractor::getMetaData() {
    LOGV("Dummy ASFExtractor::getMetaData()");
    if(!pASFParser) {
        return NULL;
    }

    return (*pASFParser->getMetaData)(mHandle);
}

bool SniffASF(const sp<DataSource> &source,
              String8 *mimeType,
              float *confidence,
              sp<AMessage> *meta)
{
    const char *errstr;

    static bool (*pSniffASF)(
        const sp<DataSource> &source,
        String8 *mimeType,
        float *confidence,
        sp<AMessage> *meta);

    LOGV("Dummy SniffASF function");

    if(!pASFHandle) {
        pASFHandle = dlopen("/system/lib/libittiam_asfextractor.so", RTLD_LAZY | RTLD_GLOBAL);
        if((errstr = dlerror()) != NULL){
            LOGE("dlopen() err: %s", errstr);
            return false;
        }
    }

    pSniffASF =(bool (*)(const android::sp<android::DataSource>&, android::String8*, float*, android::sp<android::AMessage>*)) dlsym(pASFHandle, "SniffASF");
    if((errstr = dlerror()) != NULL) {
        LOGE("Error dlsym(pSniffASF), err: %s", errstr);
        return false;
    }
    return (*pSniffASF)(source, mimeType, confidence, meta);
}

bool isASFParserAvailable()
{
    FILE *pF;

    pF = fopen("/system/lib/libittiam_asfextractor.so", "r");
    if(!pF) {
        LOGW("ASF parser is not available");
        return false;
    }
    fclose(pF);

    return true;
}

void closeASFLib()
{
    if (pASFHandle) {
        dlclose(pASFHandle);
        pASFHandle = NULL;
     }
}
}  // namespace android
