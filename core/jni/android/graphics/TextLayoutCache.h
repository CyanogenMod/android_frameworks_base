/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef ANDROID_TEXT_LAYOUT_CACHE_H
#define ANDROID_TEXT_LAYOUT_CACHE_H

#include "RtlProperties.h"

#include <stddef.h>
#include <utils/threads.h>
#include <utils/String16.h>
#include <utils/GenerationCache.h>
#include <utils/Compare.h>
#include <utils/RefBase.h>
#include <utils/Singleton.h>

#include <SkPaint.h>
#include <SkTemplates.h>
#include <SkUtils.h>
#include <SkScalerContext.h>
#include <SkAutoKern.h>

#include <unicode/ubidi.h>
#include <unicode/ushape.h>
#include "HarfbuzzSkia.h"
#include "harfbuzz-shaper.h"

#include <android_runtime/AndroidRuntime.h>

#define UNICODE_NOT_A_CHAR              0xffff
#define UNICODE_ZWSP                    0x200b
#define UNICODE_FIRST_LOW_SURROGATE     0xdc00
#define UNICODE_FIRST_HIGH_SURROGATE    0xd800
#define UNICODE_FIRST_PRIVATE_USE       0xe000
#define UNICODE_FIRST_RTL_CHAR          0x0590

// Temporary buffer size
#define CHAR_BUFFER_SIZE 80

// Converts a number of mega-bytes into bytes
#define MB(s) s * 1024 * 1024

// Define the default cache size in Mb
#define DEFAULT_TEXT_LAYOUT_CACHE_SIZE_IN_MB 0.250f

// Define the interval in number of cache hits between two statistics dump
#define DEFAULT_DUMP_STATS_CACHE_HIT_INTERVAL 100

namespace android {

/**
 * TextLayoutCacheKey is the Cache key
 */
class TextLayoutCacheKey {
public:
    TextLayoutCacheKey();

    TextLayoutCacheKey(const SkPaint* paint, const UChar* text, size_t start, size_t count,
            size_t contextCount, int dirFlags);

    TextLayoutCacheKey(const TextLayoutCacheKey& other);

    /**
     * We need to copy the text when we insert the key into the cache itself.
     * We don't need to copy the text when we are only comparing keys.
     */
    void internalTextCopy();

    /**
     * Get the size of the Cache key.
     */
    size_t getSize();

    static int compare(const TextLayoutCacheKey& lhs, const TextLayoutCacheKey& rhs);

private:
    const UChar* text; // if text is NULL, use textCopy
    String16 textCopy;
    size_t start;
    size_t count;
    size_t contextCount;
    int dirFlags;
    SkTypeface* typeface;
    SkScalar textSize;
    SkScalar textSkewX;
    SkScalar textScaleX;
    uint32_t flags;
    SkPaint::Hinting hinting;

    inline const UChar* getText() const { return text ? text : (UChar*)textCopy.string(); }

}; // TextLayoutCacheKey

inline int strictly_order_type(const TextLayoutCacheKey& lhs, const TextLayoutCacheKey& rhs) {
    return TextLayoutCacheKey::compare(lhs, rhs) < 0;
}

inline int compare_type(const TextLayoutCacheKey& lhs, const TextLayoutCacheKey& rhs) {
    return TextLayoutCacheKey::compare(lhs, rhs);
}

/*
 * TextLayoutCacheValue is the Cache value
 */
class TextLayoutCacheValue : public RefBase {
public:
    TextLayoutCacheValue();

    void setElapsedTime(uint32_t time);
    uint32_t getElapsedTime();

    void computeValues(SkPaint* paint, const UChar* chars, size_t start, size_t count,
            size_t contextCount, int dirFlags);

    inline const jfloat* getAdvances() const { return mAdvances.array(); }
    inline size_t getAdvancesCount() const { return mAdvances.size(); }
    inline jfloat getTotalAdvance() const { return mTotalAdvance; }
    inline const jchar* getGlyphs() const { return mGlyphs.array(); }
    inline size_t getGlyphsCount() const { return mGlyphs.size(); }

    /**
     * Get the size of the Cache entry
     */
    size_t getSize();

private:
    /**
     * Advances vector
     */
    Vector<jfloat> mAdvances;

    /**
     * Total number of advances
     */
    jfloat mTotalAdvance;

    /**
     * Glyphs vector
     */
    Vector<jchar> mGlyphs;

    /**
     * Time for computing the values (in milliseconds)
     */
    uint32_t mElapsedTime;

    static void computeValuesWithHarfbuzz(SkPaint* paint, const UChar* chars,
            size_t start, size_t count, size_t contextCount, int dirFlags,
            Vector<jfloat>* const outAdvances, jfloat* outTotalAdvance,
            Vector<jchar>* const outGlyphs);

    static void computeRunValuesWithHarfbuzz(HB_ShaperItem& shaperItem, SkPaint* paint,
            size_t start, size_t count, bool isRTL,
            Vector<jfloat>* const outAdvances, jfloat* outTotalAdvance,
            Vector<jchar>* const outGlyphs);

    static void initShaperItem(HB_ShaperItem& shaperItem, HB_FontRec* font, FontData* fontData,
            SkPaint* paint, const UChar* chars, size_t contextCount);

    static void freeShaperItem(HB_ShaperItem& shaperItem);

    static void shapeRun(HB_ShaperItem& shaperItem, size_t start, size_t count, bool isRTL);

    static void deleteGlyphArrays(HB_ShaperItem& shaperItem);

    static void createGlyphArrays(HB_ShaperItem& shaperItem, int size);

}; // TextLayoutCacheValue

/**
 * Cache of text layout information.
 */
class TextLayoutCache : public OnEntryRemoved<TextLayoutCacheKey, sp<TextLayoutCacheValue> >,
        public Singleton<TextLayoutCache>
{
public:
    TextLayoutCache();

    virtual ~TextLayoutCache();

    bool isInitialized() {
        return mInitialized;
    }

    /**
     * Used as a callback when an entry is removed from the cache
     * Do not invoke directly
     */
    void operator()(TextLayoutCacheKey& text, sp<TextLayoutCacheValue>& desc);

    sp<TextLayoutCacheValue> getValue(SkPaint* paint, const jchar* text, jint start, jint count,
            jint contextCount, jint dirFlags);

    /**
     * Clear the cache
     */
    void clear();

    /**
     * Sets the maximum size of the cache in bytes
     */
    void setMaxSize(uint32_t maxSize);

    /**
     * Returns the maximum size of the cache in bytes
     */
    uint32_t getMaxSize();

    /**
     * Returns the current size of the cache in bytes
     */
    uint32_t getSize();

private:
    Mutex mLock;
    bool mInitialized;

    GenerationCache<TextLayoutCacheKey, sp<TextLayoutCacheValue> > mCache;

    uint32_t mSize;
    uint32_t mMaxSize;

    uint32_t mCacheHitCount;
    uint64_t mNanosecondsSaved;

    uint64_t mCacheStartTime;

    RtlDebugLevel mDebugLevel;
    bool mDebugEnabled;

    /*
     * Class initialization
     */
    void init();

    /**
     * Remove oldest entries until we are having enough space
     */
    void removeOldests();

    /**
     * Dump Cache statistics
     */
    void dumpCacheStats();

}; // TextLayoutCache

} // namespace android
#endif /* ANDROID_TEXT_LAYOUT_CACHE_H */

