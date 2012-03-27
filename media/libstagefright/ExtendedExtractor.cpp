/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
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

#define LOG_NDEBUG 0
#define LOG_TAG "ExtendedExtractor"
#include <utils/Log.h>
//#define DUMP_TO_FILE


#include <media/stagefright/ExtendedExtractorFuncs.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaDefs.h>
#include <utils/String8.h>
#include <dlfcn.h>  // for dlopen/dlclose

#include "include/ExtendedExtractor.h"
#define LOGV LOGE

static const char* MM_PARSER_LIB = "libmmparser.so";
static const char* MM_PARSER_LITE_LIB = "libmmparser_lite.so";

namespace android {

void* MmParserLib() {
    static void* mmParserLib = NULL;
    static bool alreadyTriedToOpenMmParsers = false;

    if(alreadyTriedToOpenMmParsers) {
        return mmParserLib;
    }

    alreadyTriedToOpenMmParsers = true;

    mmParserLib = ::dlopen(MM_PARSER_LIB, RTLD_LAZY);

    if(mmParserLib != NULL) {
        return mmParserLib;
    }

    LOGV("Failed to open MM_PARSER_LIB, dlerror = %s \n", dlerror());

    mmParserLib = ::dlopen(MM_PARSER_LITE_LIB, RTLD_LAZY);

    if(mmParserLib == NULL) {
        LOGV("Failed to open MM_PARSER_LITE_LIB, dlerror = %s \n", dlerror());
    }

    return mmParserLib;
}

MediaExtractorFactory MediaExtractorFactoryFunction() {
    static MediaExtractorFactory mediaFactoryFunction = NULL;
    static bool alreadyTriedToFindFactoryFunction = false;

    if(alreadyTriedToFindFactoryFunction) {
        return mediaFactoryFunction;
    }

    void *mmParserLib = MmParserLib();
    if (mmParserLib == NULL) {
        return NULL;
    }

    mediaFactoryFunction = (MediaExtractorFactory) dlsym(mmParserLib, MEDIA_CREATE_EXTRACTOR);
    alreadyTriedToFindFactoryFunction = true;

    if(mediaFactoryFunction==NULL) {
        LOGE(" dlsym for ExtendedExtractor factory function failed, dlerror = %s \n", dlerror());
    }

    return mediaFactoryFunction;
}

sp<MediaExtractor> ExtendedExtractor::CreateExtractor(const sp<DataSource> &source, const char *mime) {
    MediaExtractorFactory f = MediaExtractorFactoryFunction();
    if(f==NULL) {
        return NULL;
    }

    sp<MediaExtractor> extractor = f(source, mime);
    if(extractor==NULL) {
        LOGE(" ExtendedExtractor failed to instantiate extractor \n");
    }

    return extractor;
}

void ExtendedExtractor::RegisterSniffers() {
    void *mmParserLib = MmParserLib();
    if (mmParserLib == NULL) {
        return;
    }

    SnifferArrayFunc snifferArrayFunc = (SnifferArrayFunc) dlsym(mmParserLib, MEDIA_SNIFFER_ARRAY);
    if(snifferArrayFunc==NULL) {
        LOGE(" Unable to init Extended Sniffers, dlerror = %s \n", dlerror());
        return;
    }

    const DataSource::SnifferFunc *snifferArray = NULL;
    int snifferCount = 0;

    //Invoke function in libmmparser to return its array of sniffers.
    snifferArrayFunc(&snifferArray, &snifferCount);

    if(snifferArray==NULL) {
        LOGE(" snifferArray is NULL \n");
        return;
    }

    //Register the remote sniffers with the DataSource.
    for(int i=0; i<snifferCount; i++) {
        DataSource::RegisterSniffer(snifferArray[i]);
    }
}

}  // namespace android


