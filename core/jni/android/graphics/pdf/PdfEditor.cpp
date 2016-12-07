/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "jni.h"
#include "JNIHelp.h"

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdelete-non-virtual-dtor"
#include "fpdfview.h"
#include "fpdf_edit.h"
#include "fpdf_save.h"
#include "fsdk_rendercontext.h"
#include "fpdf_transformpage.h"
#pragma GCC diagnostic pop

#include "SkMatrix.h"

#include <core_jni_helpers.h>
#include <vector>
#include <utils/Log.h>
#include <unistd.h>
#include <sys/types.h>
#include <unistd.h>

namespace android {

enum PageBox {PAGE_BOX_MEDIA, PAGE_BOX_CROP};

static struct {
    jfieldID x;
    jfieldID y;
} gPointClassInfo;

static struct {
    jfieldID left;
    jfieldID top;
    jfieldID right;
    jfieldID bottom;
} gRectClassInfo;

// Also used in PdfRenderer.cpp
int sUnmatchedPdfiumInitRequestCount = 0;

static void initializeLibraryIfNeeded() {
    if (sUnmatchedPdfiumInitRequestCount == 0) {
        FPDF_InitLibrary();
    }
    sUnmatchedPdfiumInitRequestCount++;
}

static void destroyLibraryIfNeeded() {
    sUnmatchedPdfiumInitRequestCount--;
    if (sUnmatchedPdfiumInitRequestCount == 0) {
       FPDF_DestroyLibrary();
    }
}

static int getBlock(void* param, unsigned long position, unsigned char* outBuffer,
        unsigned long size) {
    const int fd = reinterpret_cast<intptr_t>(param);
    const int readCount = pread(fd, outBuffer, size, position);
    if (readCount < 0) {
        ALOGE("Cannot read from file descriptor. Error:%d", errno);
        return 0;
    }
    return 1;
}

static jlong nativeOpen(JNIEnv* env, jclass thiz, jint fd, jlong size) {
    initializeLibraryIfNeeded();

    FPDF_FILEACCESS loader;
    loader.m_FileLen = size;
    loader.m_Param = reinterpret_cast<void*>(intptr_t(fd));
    loader.m_GetBlock = &getBlock;

    FPDF_DOCUMENT document = FPDF_LoadCustomDocument(&loader, NULL);

    if (!document) {
        const long error = FPDF_GetLastError();
        switch (error) {
            case FPDF_ERR_PASSWORD:
            case FPDF_ERR_SECURITY: {
                jniThrowExceptionFmt(env, "java/lang/SecurityException",
                        "cannot create document. Error: %ld", error);
            } break;
            default: {
                jniThrowExceptionFmt(env, "java/io/IOException",
                        "cannot create document. Error: %ld", error);
            } break;
        }
        destroyLibraryIfNeeded();
        return -1;
    }

    return reinterpret_cast<jlong>(document);
}

static void nativeClose(JNIEnv* env, jclass thiz, jlong documentPtr) {
    FPDF_DOCUMENT document = reinterpret_cast<FPDF_DOCUMENT>(documentPtr);
    FPDF_CloseDocument(document);
    destroyLibraryIfNeeded();
}

static jint nativeGetPageCount(JNIEnv* env, jclass thiz, jlong documentPtr) {
    FPDF_DOCUMENT document = reinterpret_cast<FPDF_DOCUMENT>(documentPtr);
    return FPDF_GetPageCount(document);
}

static jint nativeRemovePage(JNIEnv* env, jclass thiz, jlong documentPtr, jint pageIndex) {
    FPDF_DOCUMENT document = reinterpret_cast<FPDF_DOCUMENT>(documentPtr);
    FPDFPage_Delete(document, pageIndex);
    return FPDF_GetPageCount(document);
}

struct PdfToFdWriter : FPDF_FILEWRITE {
    int dstFd;
};

static bool writeAllBytes(const int fd, const void* buffer, const size_t byteCount) {
    char* writeBuffer = static_cast<char*>(const_cast<void*>(buffer));
    size_t remainingBytes = byteCount;
    while (remainingBytes > 0) {
        ssize_t writtenByteCount = write(fd, writeBuffer, remainingBytes);
        if (writtenByteCount == -1) {
            if (errno == EINTR) {
                continue;
            }
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                    "Error writing to buffer: %d", errno);
            return false;
        }
        remainingBytes -= writtenByteCount;
        writeBuffer += writtenByteCount;
    }
    return true;
}

static int writeBlock(FPDF_FILEWRITE* owner, const void* buffer, unsigned long size) {
    const PdfToFdWriter* writer = reinterpret_cast<PdfToFdWriter*>(owner);
    const bool success = writeAllBytes(writer->dstFd, buffer, size);
    if (!success) {
        ALOGE("Cannot write to file descriptor. Error:%d", errno);
        return 0;
    }
    return 1;
}

static void nativeWrite(JNIEnv* env, jclass thiz, jlong documentPtr, jint fd) {
    FPDF_DOCUMENT document = reinterpret_cast<FPDF_DOCUMENT>(documentPtr);
    PdfToFdWriter writer;
    writer.dstFd = fd;
    writer.WriteBlock = &writeBlock;
    const bool success = FPDF_SaveAsCopy(document, &writer, FPDF_NO_INCREMENTAL);
    if (!success) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                "cannot write to fd. Error: %d", errno);
        destroyLibraryIfNeeded();
    }
}

static void nativeSetTransformAndClip(JNIEnv* env, jclass thiz, jlong documentPtr, jint pageIndex,
        jlong transformPtr, jint clipLeft, jint clipTop, jint clipRight, jint clipBottom) {
    FPDF_DOCUMENT document = reinterpret_cast<FPDF_DOCUMENT>(documentPtr);

    CPDF_Page* page = (CPDF_Page*) FPDF_LoadPage(document, pageIndex);
    if (!page) {
        jniThrowException(env, "java/lang/IllegalStateException",
                "cannot open page");
        return;
    }

    double width = 0;
    double height = 0;

    const int result = FPDF_GetPageSizeByIndex(document, pageIndex, &width, &height);
    if (!result) {
        jniThrowException(env, "java/lang/IllegalStateException",
                    "cannot get page size");
        return;
    }

    CFX_Matrix matrix;

    SkMatrix* skTransform = reinterpret_cast<SkMatrix*>(transformPtr);

    SkScalar transformValues[6];
    if (!skTransform->asAffine(transformValues)) {
        jniThrowException(env, "java/lang/IllegalArgumentException",
                "transform matrix has perspective. Only affine matrices are allowed.");
        return;
    }

    // PDF's coordinate system origin is left-bottom while in graphics it
    // is the top-left. So, translate the PDF coordinates to ours.
    matrix.Set(1, 0, 0, -1, 0, page->GetPageHeight());

    // Apply the transformation what was created in our coordinates.
    matrix.Concat(transformValues[SkMatrix::kAScaleX], transformValues[SkMatrix::kASkewY],
            transformValues[SkMatrix::kASkewX], transformValues[SkMatrix::kAScaleY],
            transformValues[SkMatrix::kATransX], transformValues[SkMatrix::kATransY]);

    // Translate the result back to PDF coordinates.
    matrix.Concat(1, 0, 0, -1, 0, page->GetPageHeight());

    FS_MATRIX transform = {matrix.a, matrix.b, matrix.c, matrix.d, matrix.e, matrix.f};
    FS_RECTF clip = {(float) clipLeft, (float) clipTop, (float) clipRight, (float) clipBottom};

    FPDFPage_TransFormWithClip(page, &transform, &clip);

    FPDF_ClosePage(page);
}

static void nativeGetPageSize(JNIEnv* env, jclass thiz, jlong documentPtr,
        jint pageIndex, jobject outSize) {
    FPDF_DOCUMENT document = reinterpret_cast<FPDF_DOCUMENT>(documentPtr);

    FPDF_PAGE page = FPDF_LoadPage(document, pageIndex);
    if (!page) {
        jniThrowException(env, "java/lang/IllegalStateException",
                "cannot open page");
        return;
    }

    double width = 0;
    double height = 0;

    const int result = FPDF_GetPageSizeByIndex(document, pageIndex, &width, &height);
    if (!result) {
        jniThrowException(env, "java/lang/IllegalStateException",
                    "cannot get page size");
        return;
    }

    env->SetIntField(outSize, gPointClassInfo.x, width);
    env->SetIntField(outSize, gPointClassInfo.y, height);

    FPDF_ClosePage(page);
}

static jboolean nativeScaleForPrinting(JNIEnv* env, jclass thiz, jlong documentPtr) {
    FPDF_DOCUMENT document = reinterpret_cast<FPDF_DOCUMENT>(documentPtr);
    FPDF_BOOL success = FPDF_VIEWERREF_GetPrintScaling(document);
    return success ? JNI_TRUE : JNI_FALSE;
}

static bool nativeGetPageBox(JNIEnv* env, jclass thiz, jlong documentPtr, jint pageIndex,
        PageBox pageBox, jobject outBox) {
    FPDF_DOCUMENT document = reinterpret_cast<FPDF_DOCUMENT>(documentPtr);

    FPDF_PAGE page = FPDF_LoadPage(document, pageIndex);
    if (!page) {
        jniThrowException(env, "java/lang/IllegalStateException",
                "cannot open page");
        return false;
    }

    float left;
    float top;
    float right;
    float bottom;

    const FPDF_BOOL success = (pageBox == PAGE_BOX_MEDIA)
        ? FPDFPage_GetMediaBox(page, &left, &top, &right, &bottom)
        : FPDFPage_GetCropBox(page, &left, &top, &right, &bottom);

    FPDF_ClosePage(page);

    if (!success) {
        return false;
    }

    env->SetIntField(outBox, gRectClassInfo.left, (int) left);
    env->SetIntField(outBox, gRectClassInfo.top, (int) top);
    env->SetIntField(outBox, gRectClassInfo.right, (int) right);
    env->SetIntField(outBox, gRectClassInfo.bottom, (int) bottom);

    return true;
}

static jboolean nativeGetPageMediaBox(JNIEnv* env, jclass thiz, jlong documentPtr, jint pageIndex,
        jobject outMediaBox) {
    const bool success = nativeGetPageBox(env, thiz, documentPtr, pageIndex, PAGE_BOX_MEDIA,
            outMediaBox);
    return success ? JNI_TRUE : JNI_FALSE;
}

static jboolean nativeGetPageCropBox(JNIEnv* env, jclass thiz, jlong documentPtr, jint pageIndex,
        jobject outMediaBox) {
    const bool success = nativeGetPageBox(env, thiz, documentPtr, pageIndex, PAGE_BOX_CROP,
         outMediaBox);
    return success ? JNI_TRUE : JNI_FALSE;
}

static void nativeSetPageBox(JNIEnv* env, jclass thiz, jlong documentPtr, jint pageIndex,
        PageBox pageBox, jobject box) {
    FPDF_DOCUMENT document = reinterpret_cast<FPDF_DOCUMENT>(documentPtr);

    FPDF_PAGE page = FPDF_LoadPage(document, pageIndex);
    if (!page) {
        jniThrowException(env, "java/lang/IllegalStateException",
                "cannot open page");
        return;
    }

    const int left = env->GetIntField(box, gRectClassInfo.left);
    const int top = env->GetIntField(box, gRectClassInfo.top);
    const int right = env->GetIntField(box, gRectClassInfo.right);
    const int bottom = env->GetIntField(box, gRectClassInfo.bottom);

    if (pageBox == PAGE_BOX_MEDIA) {
        FPDFPage_SetMediaBox(page, left, top, right, bottom);
    } else {
        FPDFPage_SetCropBox(page, left, top, right, bottom);
    }

    FPDF_ClosePage(page);
}

static void nativeSetPageMediaBox(JNIEnv* env, jclass thiz, jlong documentPtr, jint pageIndex,
        jobject mediaBox) {
    nativeSetPageBox(env, thiz, documentPtr, pageIndex, PAGE_BOX_MEDIA, mediaBox);
}

static void nativeSetPageCropBox(JNIEnv* env, jclass thiz, jlong documentPtr, jint pageIndex,
        jobject mediaBox) {
    nativeSetPageBox(env, thiz, documentPtr, pageIndex, PAGE_BOX_CROP, mediaBox);
}

static const JNINativeMethod gPdfEditor_Methods[] = {
    {"nativeOpen", "(IJ)J", (void*) nativeOpen},
    {"nativeClose", "(J)V", (void*) nativeClose},
    {"nativeGetPageCount", "(J)I", (void*) nativeGetPageCount},
    {"nativeRemovePage", "(JI)I", (void*) nativeRemovePage},
    {"nativeWrite", "(JI)V", (void*) nativeWrite},
    {"nativeSetTransformAndClip", "(JIJIIII)V", (void*) nativeSetTransformAndClip},
    {"nativeGetPageSize", "(JILandroid/graphics/Point;)V", (void*) nativeGetPageSize},
    {"nativeScaleForPrinting", "(J)Z", (void*) nativeScaleForPrinting},
    {"nativeGetPageMediaBox", "(JILandroid/graphics/Rect;)Z", (void*) nativeGetPageMediaBox},
    {"nativeSetPageMediaBox", "(JILandroid/graphics/Rect;)V", (void*) nativeSetPageMediaBox},
    {"nativeGetPageCropBox", "(JILandroid/graphics/Rect;)Z", (void*) nativeGetPageCropBox},
    {"nativeSetPageCropBox", "(JILandroid/graphics/Rect;)V", (void*) nativeSetPageCropBox}
};

int register_android_graphics_pdf_PdfEditor(JNIEnv* env) {
    const int result = RegisterMethodsOrDie(
            env, "android/graphics/pdf/PdfEditor", gPdfEditor_Methods,
            NELEM(gPdfEditor_Methods));

    jclass pointClass = FindClassOrDie(env, "android/graphics/Point");
    gPointClassInfo.x = GetFieldIDOrDie(env, pointClass, "x", "I");
    gPointClassInfo.y = GetFieldIDOrDie(env, pointClass, "y", "I");

    jclass rectClass = FindClassOrDie(env, "android/graphics/Rect");
    gRectClassInfo.left = GetFieldIDOrDie(env, rectClass, "left", "I");
    gRectClassInfo.top = GetFieldIDOrDie(env, rectClass, "top", "I");
    gRectClassInfo.right = GetFieldIDOrDie(env, rectClass, "right", "I");
    gRectClassInfo.bottom = GetFieldIDOrDie(env, rectClass, "bottom", "I");

    return result;
};

};
