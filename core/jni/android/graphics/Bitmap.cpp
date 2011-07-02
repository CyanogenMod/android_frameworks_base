#include "SkBitmap.h"
#include "SkImageEncoder.h"
#include "SkColorPriv.h"
#include "GraphicsJNI.h"
#include "SkDither.h"
#include "SkUnPreMultiply.h"

#include <binder/Parcel.h>
#include "android_util_Binder.h"
#include "android_nio_utils.h"
#include "CreateJavaOutputStreamAdaptor.h"

#include <jni.h>

#if 0
    #define TRACE_BITMAP(code)  code
#else
    #define TRACE_BITMAP(code)
#endif

///////////////////////////////////////////////////////////////////////////////
// Conversions to/from SkColor, for get/setPixels, and the create method, which
// is basically like setPixels

typedef void (*FromColorProc)(void* dst, const SkColor src[], int width,
                              int x, int y);

static void FromColor_D32(void* dst, const SkColor src[], int width,
                          int, int) {
    SkPMColor* d = (SkPMColor*)dst;

    for (int i = 0; i < width; i++) {
        *d++ = SkPreMultiplyColor(*src++);
    }
}

static void FromColor_D565(void* dst, const SkColor src[], int width,
                           int x, int y) {
    uint16_t* d = (uint16_t*)dst;

    DITHER_565_SCAN(y);
    for (int stop = x + width; x < stop; x++) {
        SkColor c = *src++;
        *d++ = SkDitherRGBTo565(SkColorGetR(c), SkColorGetG(c), SkColorGetB(c),
                                DITHER_VALUE(x));
    }
}

static void FromColor_D4444(void* dst, const SkColor src[], int width,
                            int x, int y) {
    SkPMColor16* d = (SkPMColor16*)dst;

    DITHER_4444_SCAN(y);
    for (int stop = x + width; x < stop; x++) {
        SkPMColor c = SkPreMultiplyColor(*src++);
        *d++ = SkDitherARGB32To4444(c, DITHER_VALUE(x));
//        *d++ = SkPixel32ToPixel4444(c);
    }
}

// can return NULL
static FromColorProc ChooseFromColorProc(SkBitmap::Config config) {
    switch (config) {
        case SkBitmap::kARGB_8888_Config:
            return FromColor_D32;
        case SkBitmap::kARGB_4444_Config:
            return FromColor_D4444;
        case SkBitmap::kRGB_565_Config:
            return FromColor_D565;
        default:
            break;
    }
    return NULL;
}

bool GraphicsJNI::SetPixels(JNIEnv* env, jintArray srcColors,
                            int srcOffset, int srcStride,
                            int x, int y, int width, int height,
                            const SkBitmap& dstBitmap) {
    SkAutoLockPixels alp(dstBitmap);
    void* dst = dstBitmap.getPixels();
    FromColorProc proc = ChooseFromColorProc(dstBitmap.config());

    if (NULL == dst || NULL == proc) {
        return false;
    }

    const jint* array = env->GetIntArrayElements(srcColors, NULL);
    const SkColor* src = (const SkColor*)array + srcOffset;

    // reset to to actual choice from caller
    dst = dstBitmap.getAddr(x, y);
    // now copy/convert each scanline
    for (int y = 0; y < height; y++) {
        proc(dst, src, width, x, y);
        src += srcStride;
        dst = (char*)dst + dstBitmap.rowBytes();
    }

    env->ReleaseIntArrayElements(srcColors, const_cast<jint*>(array),
                                 JNI_ABORT);
    return true;
}

//////////////////// ToColor procs

typedef void (*ToColorProc)(SkColor dst[], const void* src, int width,
                            SkColorTable*);

static void ToColor_S32_Alpha(SkColor dst[], const void* src, int width,
                              SkColorTable*) {
    SkASSERT(width > 0);
    const SkPMColor* s = (const SkPMColor*)src;
    do {
        *dst++ = SkUnPreMultiply::PMColorToColor(*s++);
    } while (--width != 0);
}

static void ToColor_S32_Opaque(SkColor dst[], const void* src, int width,
                               SkColorTable*) {
    SkASSERT(width > 0);
    const SkPMColor* s = (const SkPMColor*)src;
    do {
        SkPMColor c = *s++;
        *dst++ = SkColorSetRGB(SkGetPackedR32(c), SkGetPackedG32(c),
                               SkGetPackedB32(c));
    } while (--width != 0);
}

static void ToColor_S4444_Alpha(SkColor dst[], const void* src, int width,
                                SkColorTable*) {
    SkASSERT(width > 0);
    const SkPMColor16* s = (const SkPMColor16*)src;
    do {
        *dst++ = SkUnPreMultiply::PMColorToColor(SkPixel4444ToPixel32(*s++));
    } while (--width != 0);
}

static void ToColor_S4444_Opaque(SkColor dst[], const void* src, int width,
                                 SkColorTable*) {
    SkASSERT(width > 0);
    const SkPMColor* s = (const SkPMColor*)src;
    do {
        SkPMColor c = SkPixel4444ToPixel32(*s++);
        *dst++ = SkColorSetRGB(SkGetPackedR32(c), SkGetPackedG32(c),
                               SkGetPackedB32(c));
    } while (--width != 0);
}

static void ToColor_S565(SkColor dst[], const void* src, int width,
                         SkColorTable*) {
    SkASSERT(width > 0);
    const uint16_t* s = (const uint16_t*)src;
    do {
        uint16_t c = *s++;
        *dst++ =  SkColorSetRGB(SkPacked16ToR32(c), SkPacked16ToG32(c),
                                SkPacked16ToB32(c));
    } while (--width != 0);
}

static void ToColor_SI8_Alpha(SkColor dst[], const void* src, int width,
                              SkColorTable* ctable) {
    SkASSERT(width > 0);
    const uint8_t* s = (const uint8_t*)src;
    const SkPMColor* colors = ctable->lockColors();
    do {
        *dst++ = SkUnPreMultiply::PMColorToColor(colors[*s++]);
    } while (--width != 0);
    ctable->unlockColors(false);
}

static void ToColor_SI8_Opaque(SkColor dst[], const void* src, int width,
                               SkColorTable* ctable) {
    SkASSERT(width > 0);
    const uint8_t* s = (const uint8_t*)src;
    const SkPMColor* colors = ctable->lockColors();
    do {
        SkPMColor c = colors[*s++];
        *dst++ = SkColorSetRGB(SkGetPackedR32(c), SkGetPackedG32(c),
                               SkGetPackedB32(c));
    } while (--width != 0);
    ctable->unlockColors(false);
}

// can return NULL
static ToColorProc ChooseToColorProc(const SkBitmap& src) {
    switch (src.config()) {
        case SkBitmap::kARGB_8888_Config:
            return src.isOpaque() ? ToColor_S32_Opaque : ToColor_S32_Alpha;
        case SkBitmap::kARGB_4444_Config:
            return src.isOpaque() ? ToColor_S4444_Opaque : ToColor_S4444_Alpha;
        case SkBitmap::kRGB_565_Config:
            return ToColor_S565;
        case SkBitmap::kIndex8_Config:
            if (src.getColorTable() == NULL) {
                return NULL;
            }
            return src.isOpaque() ? ToColor_SI8_Opaque : ToColor_SI8_Alpha;
        default:
            break;
    }
    return NULL;
}

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////

static jobject Bitmap_creator(JNIEnv* env, jobject, jintArray jColors,
                              int offset, int stride, int width, int height,
                              SkBitmap::Config config, jboolean isMutable) {
    if (width <= 0 || height <= 0) {
        doThrowIAE(env, "width and height must be > 0");
        return NULL;
    }

    if (NULL != jColors) {
        size_t n = env->GetArrayLength(jColors);
        if (n < SkAbs32(stride) * (size_t)height) {
            doThrowAIOOBE(env);
            return NULL;
        }
    }

    SkBitmap bitmap;

    bitmap.setConfig(config, width, height);
    if (!GraphicsJNI::setJavaPixelRef(env, &bitmap, NULL, true)) {
        return NULL;
    }

    if (jColors != NULL) {
        GraphicsJNI::SetPixels(env, jColors, offset, stride,
                               0, 0, width, height, bitmap);
    }

    return GraphicsJNI::createBitmap(env, new SkBitmap(bitmap), isMutable,
                                     NULL);
}

static jobject Bitmap_copy(JNIEnv* env, jobject, const SkBitmap* src,
                           SkBitmap::Config dstConfig, jboolean isMutable) {
    SkBitmap            result;
    JavaPixelAllocator  allocator(env, true);

    if (!src->copyTo(&result, dstConfig, &allocator)) {
        return NULL;
    }

    return GraphicsJNI::createBitmap(env, new SkBitmap(result), isMutable,
                                     NULL);
}

static void Bitmap_destructor(JNIEnv* env, jobject, SkBitmap* bitmap) {
    delete bitmap;
}

static void Bitmap_recycle(JNIEnv* env, jobject, SkBitmap* bitmap) {
    bitmap->setPixels(NULL, NULL);
}

// These must match the int values in Bitmap.java
enum JavaEncodeFormat {
    kJPEG_JavaEncodeFormat = 0,
    kPNG_JavaEncodeFormat = 1,
    kWEBP_JavaEncodeFormat = 2
};

static bool Bitmap_compress(JNIEnv* env, jobject clazz, SkBitmap* bitmap,
                            int format, int quality,
                            jobject jstream, jbyteArray jstorage) {
    SkImageEncoder::Type fm;

    switch (format) {
    case kJPEG_JavaEncodeFormat:
        fm = SkImageEncoder::kJPEG_Type;
        break;
    case kPNG_JavaEncodeFormat:
        fm = SkImageEncoder::kPNG_Type;
        break;
    case kWEBP_JavaEncodeFormat:
        fm = SkImageEncoder::kWEBP_Type;
        break;
    default:
        return false;
    }

    bool success = false;
    SkWStream* strm = CreateJavaOutputStreamAdaptor(env, jstream, jstorage);
    if (NULL != strm) {
        SkImageEncoder* encoder = SkImageEncoder::Create(fm);
        if (NULL != encoder) {
            success = encoder->encodeStream(strm, *bitmap, quality);
            delete encoder;
        }
        delete strm;
    }
    return success;
}

static void Bitmap_erase(JNIEnv* env, jobject, SkBitmap* bitmap, jint color) {
    bitmap->eraseColor(color);
}

static int Bitmap_width(JNIEnv* env, jobject, SkBitmap* bitmap) {
    return bitmap->width();
}

static int Bitmap_height(JNIEnv* env, jobject, SkBitmap* bitmap) {
    return bitmap->height();
}

static int Bitmap_rowBytes(JNIEnv* env, jobject, SkBitmap* bitmap) {
    return bitmap->rowBytes();
}

static int Bitmap_config(JNIEnv* env, jobject, SkBitmap* bitmap) {
    return bitmap->config();
}

static jboolean Bitmap_hasAlpha(JNIEnv* env, jobject, SkBitmap* bitmap) {
    return !bitmap->isOpaque();
}

static void Bitmap_setHasAlpha(JNIEnv* env, jobject, SkBitmap* bitmap,
                               jboolean hasAlpha) {
    bitmap->setIsOpaque(!hasAlpha);
}

///////////////////////////////////////////////////////////////////////////////

static jobject Bitmap_createFromParcel(JNIEnv* env, jobject, jobject parcel) {
    if (parcel == NULL) {
        SkDebugf("-------- unparcel parcel is NULL\n");
        return NULL;
    }

    android::Parcel* p = android::parcelForJavaObject(env, parcel);

    const bool              isMutable = p->readInt32() != 0;
    const SkBitmap::Config  config = (SkBitmap::Config)p->readInt32();
    const int               width = p->readInt32();
    const int               height = p->readInt32();
    const int               rowBytes = p->readInt32();
    const int               density = p->readInt32();

    if (SkBitmap::kARGB_8888_Config != config &&
            SkBitmap::kRGB_565_Config != config &&
            SkBitmap::kARGB_4444_Config != config &&
            SkBitmap::kIndex8_Config != config &&
            SkBitmap::kA8_Config != config) {
        SkDebugf("Bitmap_createFromParcel unknown config: %d\n", config);
        return NULL;
    }

    SkBitmap* bitmap = new SkBitmap;

    bitmap->setConfig(config, width, height, rowBytes);

    SkColorTable* ctable = NULL;
    if (config == SkBitmap::kIndex8_Config) {
        int count = p->readInt32();
        if (count > 0) {
            size_t size = count * sizeof(SkPMColor);
            const SkPMColor* src = (const SkPMColor*)p->readInplace(size);
            ctable = new SkColorTable(src, count);
        }
    }

    if (!GraphicsJNI::setJavaPixelRef(env, bitmap, ctable, true)) {
        ctable->safeUnref();
        delete bitmap;
        return NULL;
    }

    ctable->safeUnref();

    size_t size = bitmap->getSize();
    bitmap->lockPixels();
    memcpy(bitmap->getPixels(), p->readInplace(size), size);
    bitmap->unlockPixels();

    return GraphicsJNI::createBitmap(env, bitmap, isMutable, NULL, density);
}

static jboolean Bitmap_writeToParcel(JNIEnv* env, jobject,
                                     const SkBitmap* bitmap,
                                     jboolean isMutable, jint density,
                                     jobject parcel) {
    if (parcel == NULL) {
        SkDebugf("------- writeToParcel null parcel\n");
        return false;
    }

    android::Parcel* p = android::parcelForJavaObject(env, parcel);

    p->writeInt32(isMutable);
    p->writeInt32(bitmap->config());
    p->writeInt32(bitmap->width());
    p->writeInt32(bitmap->height());
    p->writeInt32(bitmap->rowBytes());
    p->writeInt32(density);

    if (bitmap->getConfig() == SkBitmap::kIndex8_Config) {
        SkColorTable* ctable = bitmap->getColorTable();
        if (ctable != NULL) {
            int count = ctable->count();
            p->writeInt32(count);
            memcpy(p->writeInplace(count * sizeof(SkPMColor)),
                   ctable->lockColors(), count * sizeof(SkPMColor));
            ctable->unlockColors(false);
        } else {
            p->writeInt32(0);   // indicate no ctable
        }
    }

    size_t size = bitmap->getSize();
    bitmap->lockPixels();
    memcpy(p->writeInplace(size), bitmap->getPixels(), size);
    bitmap->unlockPixels();
    return true;
}

static jobject Bitmap_extractAlpha(JNIEnv* env, jobject clazz,
                                   const SkBitmap* src, const SkPaint* paint,
                                   jintArray offsetXY) {
    SkIPoint  offset;
    SkBitmap* dst = new SkBitmap;

    src->extractAlpha(dst, paint, &offset);
    if (offsetXY != 0 && env->GetArrayLength(offsetXY) >= 2) {
        int* array = env->GetIntArrayElements(offsetXY, NULL);
        array[0] = offset.fX;
        array[1] = offset.fY;
        env->ReleaseIntArrayElements(offsetXY, array, 0);
    }

    return GraphicsJNI::createBitmap(env, dst, true, NULL);
}

///////////////////////////////////////////////////////////////////////////////

static int Bitmap_getPixel(JNIEnv* env, jobject, const SkBitmap* bitmap,
                           int x, int y) {
    SkAutoLockPixels alp(*bitmap);

    ToColorProc proc = ChooseToColorProc(*bitmap);
    if (NULL == proc) {
        return 0;
    }
    const void* src = bitmap->getAddr(x, y);
    if (NULL == src) {
        return 0;
    }

    SkColor dst[1];
    proc(dst, src, 1, bitmap->getColorTable());
    return dst[0];
}

static void Bitmap_getPixels(JNIEnv* env, jobject, const SkBitmap* bitmap,
                             jintArray pixelArray, int offset, int stride,
                             int x, int y, int width, int height) {
    SkAutoLockPixels alp(*bitmap);

    ToColorProc proc = ChooseToColorProc(*bitmap);
    if (NULL == proc) {
        return;
    }
    const void* src = bitmap->getAddr(x, y);
    if (NULL == src) {
        return;
    }

    SkColorTable* ctable = bitmap->getColorTable();
    jint* dst = env->GetIntArrayElements(pixelArray, NULL);
    SkColor* d = (SkColor*)dst + offset;
    while (--height >= 0) {
        proc(d, src, width, ctable);
        d += stride;
        src = (void*)((const char*)src + bitmap->rowBytes());
    }
    env->ReleaseIntArrayElements(pixelArray, dst, 0);
}

///////////////////////////////////////////////////////////////////////////////

static void Bitmap_setPixel(JNIEnv* env, jobject, const SkBitmap* bitmap,
                            int x, int y, SkColor color) {
    SkAutoLockPixels alp(*bitmap);
    if (NULL == bitmap->getPixels()) {
        return;
    }

    FromColorProc proc = ChooseFromColorProc(bitmap->config());
    if (NULL == proc) {
        return;
    }

    proc(bitmap->getAddr(x, y), &color, 1, x, y);
}

static void Bitmap_setPixels(JNIEnv* env, jobject, const SkBitmap* bitmap,
                             jintArray pixelArray, int offset, int stride,
                             int x, int y, int width, int height) {
    GraphicsJNI::SetPixels(env, pixelArray, offset, stride,
                           x, y, width, height, *bitmap);
}

static void Bitmap_copyPixelsToBuffer(JNIEnv* env, jobject,
                                      const SkBitmap* bitmap, jobject jbuffer) {
    SkAutoLockPixels alp(*bitmap);
    const void* src = bitmap->getPixels();

    if (NULL != src) {
        android::AutoBufferPointer abp(env, jbuffer, JNI_TRUE);

        // the java side has already checked that buffer is large enough
        memcpy(abp.pointer(), src, bitmap->getSize());
    }
}

static void Bitmap_copyPixelsFromBuffer(JNIEnv* env, jobject,
                                    const SkBitmap* bitmap, jobject jbuffer) {
    SkAutoLockPixels alp(*bitmap);
    void* dst = bitmap->getPixels();

    if (NULL != dst) {
        android::AutoBufferPointer abp(env, jbuffer, JNI_FALSE);
        // the java side has already checked that buffer is large enough
        memcpy(dst, abp.pointer(), bitmap->getSize());
    }
}

static bool Bitmap_sameAs(JNIEnv* env, jobject, const SkBitmap* bm0,
                             const SkBitmap* bm1) {
    if (bm0->width() != bm1->width() ||
        bm0->height() != bm1->height() ||
        bm0->config() != bm1->config()) {
        return false;
    }

    SkAutoLockPixels alp0(*bm0);
    SkAutoLockPixels alp1(*bm1);

    // if we can't load the pixels, return false
    if (NULL == bm0->getPixels() || NULL == bm1->getPixels()) {
        return false;
    }

    if (bm0->config() == SkBitmap::kIndex8_Config) {
        SkColorTable* ct0 = bm0->getColorTable();
        SkColorTable* ct1 = bm1->getColorTable();
        if (NULL == ct0 || NULL == ct1) {
            return false;
        }
        if (ct0->count() != ct1->count()) {
            return false;
        }

        SkAutoLockColors alc0(ct0);
        SkAutoLockColors alc1(ct1);
        const size_t size = ct0->count() * sizeof(SkPMColor);
        if (memcmp(alc0.colors(), alc1.colors(), size) != 0) {
            return false;
        }
    }

    // now compare each scanline. We can't do the entire buffer at once,
    // since we don't care about the pixel values that might extend beyond
    // the width (since the scanline might be larger than the logical width)
    const int h = bm0->height();
    const size_t size = bm0->width() * bm0->bytesPerPixel();
    for (int y = 0; y < h; y++) {
        if (memcmp(bm0->getAddr(0, y), bm1->getAddr(0, y), size) != 0) {
            return false;
        }
    }
    return true;
}

static void Bitmap_prepareToDraw(JNIEnv* env, jobject, SkBitmap* bitmap) {
    bitmap->lockPixels();
    bitmap->unlockPixels();
}

///////////////////////////////////////////////////////////////////////////////

#include <android_runtime/AndroidRuntime.h>

static JNINativeMethod gBitmapMethods[] = {
    {   "nativeCreate",             "([IIIIIIZ)Landroid/graphics/Bitmap;",
        (void*)Bitmap_creator },
    {   "nativeCopy",               "(IIZ)Landroid/graphics/Bitmap;",
        (void*)Bitmap_copy },
    {   "nativeDestructor",         "(I)V", (void*)Bitmap_destructor },
    {   "nativeRecycle",            "(I)V", (void*)Bitmap_recycle },
    {   "nativeCompress",           "(IIILjava/io/OutputStream;[B)Z",
        (void*)Bitmap_compress },
    {   "nativeErase",              "(II)V", (void*)Bitmap_erase },
    {   "nativeWidth",              "(I)I", (void*)Bitmap_width },
    {   "nativeHeight",             "(I)I", (void*)Bitmap_height },
    {   "nativeRowBytes",           "(I)I", (void*)Bitmap_rowBytes },
    {   "nativeConfig",             "(I)I", (void*)Bitmap_config },
    {   "nativeHasAlpha",           "(I)Z", (void*)Bitmap_hasAlpha },
    {   "nativeSetHasAlpha",        "(IZ)V", (void*)Bitmap_setHasAlpha },
    {   "nativeCreateFromParcel",
        "(Landroid/os/Parcel;)Landroid/graphics/Bitmap;",
        (void*)Bitmap_createFromParcel },
    {   "nativeWriteToParcel",      "(IZILandroid/os/Parcel;)Z",
        (void*)Bitmap_writeToParcel },
    {   "nativeExtractAlpha",       "(II[I)Landroid/graphics/Bitmap;",
        (void*)Bitmap_extractAlpha },
    {   "nativeGetPixel",           "(III)I", (void*)Bitmap_getPixel },
    {   "nativeGetPixels",          "(I[IIIIIII)V", (void*)Bitmap_getPixels },
    {   "nativeSetPixel",           "(IIII)V", (void*)Bitmap_setPixel },
    {   "nativeSetPixels",          "(I[IIIIIII)V", (void*)Bitmap_setPixels },
    {   "nativeCopyPixelsToBuffer", "(ILjava/nio/Buffer;)V",
                                            (void*)Bitmap_copyPixelsToBuffer },
    {   "nativeCopyPixelsFromBuffer", "(ILjava/nio/Buffer;)V",
                                            (void*)Bitmap_copyPixelsFromBuffer },
    {   "nativeSameAs",             "(II)Z", (void*)Bitmap_sameAs },
    {   "nativePrepareToDraw",      "(I)V", (void*)Bitmap_prepareToDraw },
};

#define kClassPathName  "android/graphics/Bitmap"

int register_android_graphics_Bitmap(JNIEnv* env);
int register_android_graphics_Bitmap(JNIEnv* env)
{
    return android::AndroidRuntime::registerNativeMethods(env, kClassPathName,
                                gBitmapMethods, SK_ARRAY_COUNT(gBitmapMethods));
}

