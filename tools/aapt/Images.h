//
// Copyright 2006 The Android Open Source Project
//
// Build resource files from raw assets.
//

#ifndef IMAGES_H
#define IMAGES_H

#include "ResourceTable.h"
#include "Bundle.h"

#include <png.h>

#include <utils/String8.h>
#include <utils/RefBase.h>

using android::String8;

status_t preProcessImage(const Bundle* bundle, const sp<AaptAssets>& assets,
                         const sp<AaptFile>& file, String8* outNewLeafName);

status_t preProcessImage(const Bundle* bundle, const sp<AaptFile>& file);

status_t preProcessImageToCache(const Bundle* bundle, const String8& source, const String8& dest);

status_t postProcessImage(const sp<AaptAssets>& assets,
                          ResourceTable* table, const sp<AaptFile>& file);

class PngMemoryFile {
public:
    PngMemoryFile(void)
      : mData(NULL), mDataSize(0), mIndex(0)
      {}
    void setDataSource(const char* data, uint32_t size) { mData = data; mDataSize = size; mIndex = 0; }
    status_t read(png_bytep data, png_size_t length);

private:
    const char* mData;
    uint32_t mDataSize;
    uint32_t mIndex;
};

#endif
