/* //device/libs/android_runtime/android_text_AndroidBidi.cpp
**
** Copyright 2010, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#define LOG_TAG "AndroidUnicode"

#include "JNIHelp.h"
#include <android_runtime/AndroidRuntime.h>
#include "utils/misc.h"
#include "utils/Log.h"
#include "unicode/ubidi.h"
#include "unicode/uchar.h"
#include "unicode/ushape.h"


namespace android {
    
static jint runBidi(JNIEnv* env, jclass c, jint dir, jcharArray chsArray,
                    jbyteArray infoArray, int n, jboolean haveInfo)
{
    // Parameters are checked on java side
    // Failures from GetXXXArrayElements indicate a serious out-of-memory condition
    // that we don't bother to report, we're probably dead anyway.
    jint result = 0;
    jchar* chs = env->GetCharArrayElements(chsArray, NULL);
    if (chs != NULL) {
        jbyte* info = env->GetByteArrayElements(infoArray, NULL);
        if (info != NULL) {
            UErrorCode status = U_ZERO_ERROR;
            UBiDi* bidi = ubidi_openSized(n, 0, &status);
            ubidi_setPara(bidi, chs, n, dir, NULL, &status);
            if (U_SUCCESS(status)) {
                for (int i = 0; i < n; ++i) {
                  info[i] = ubidi_getLevelAt(bidi, i);
                }
                result = ubidi_getParaLevel(bidi);
            } else {
                jniThrowException(env, "java/lang/RuntimeException", NULL);
            }
            ubidi_close(bidi);

            env->ReleaseByteArrayElements(infoArray, info, 0);
        }
        env->ReleaseCharArrayElements(chsArray, chs, JNI_ABORT);
    }
    return result;
}

/*
Native Bidi text reordering and Arabic shaping
by: Eyad Aboulouz
*/
static jint reorderReshapeBidiText (JNIEnv* env, jclass c, jcharArray srcArray, jcharArray destArray, jint offset, jint n) {

    bool hasErrors = false;
    jint outputSize = 0;
    UChar *intermediate = new UChar[n];
    UChar *output = new UChar[n];
    UErrorCode status = U_ZERO_ERROR;

    UBiDi *para = ubidi_openSized(n, 0, &status);

    ubidi_setReorderingMode(para, UBIDI_REORDER_INVERSE_LIKE_DIRECT);

    jchar* src = env->GetCharArrayElements(srcArray, NULL);

    if (src != NULL && para != NULL && U_SUCCESS(status)) {

        ubidi_setPara(para, src+offset, n, UBIDI_DEFAULT_RTL, NULL, &status);

        if (U_SUCCESS(status)) {

            ubidi_writeReordered(para, intermediate, n, UBIDI_DO_MIRRORING | UBIDI_REMOVE_BIDI_CONTROLS, &status);

            if (U_SUCCESS(status)) {

                outputSize = u_shapeArabic(intermediate, n, output, n, U_SHAPE_TEXT_DIRECTION_VISUAL_LTR | U_SHAPE_LETTERS_SHAPE | U_SHAPE_LENGTH_FIXED_SPACES_AT_END, &status);

                if (U_SUCCESS(status))
                    env->SetCharArrayRegion(destArray, 0, outputSize, output);
                else
                    hasErrors = true;
            } else
                hasErrors = true;
        } else
            hasErrors = true;
    } else
        hasErrors = true;

    delete [] intermediate;
    delete [] output;

    if (para != NULL)
        ubidi_close(para);

    env->ReleaseCharArrayElements(srcArray, src, JNI_ABORT);

    if (hasErrors)
        jniThrowException(env, "java/lang/RuntimeException", NULL);

    return outputSize;
}

/*
Native Arabic text reshaping
by: Eyad Aboulouz
*/
static jint reshapeArabicText (JNIEnv* env, jclass c, jcharArray srcArray, jcharArray destArray, jint offset, jint n) {

    bool hasErrors = false;
    jint outputSize = 0;
    UChar *intermediate = new UChar[n];
    UChar *intermediate2 = new UChar[n];
    UChar *output = new UChar[n];
    UErrorCode status = U_ZERO_ERROR;

    jchar* src = env->GetCharArrayElements(srcArray, NULL);

    if (src != NULL) {

        ubidi_writeReverse (src+offset, n, intermediate, n, UBIDI_DO_MIRRORING | UBIDI_REMOVE_BIDI_CONTROLS, &status);

        if (U_SUCCESS(status)) {
            outputSize = u_shapeArabic(intermediate, n, intermediate2, n, U_SHAPE_TEXT_DIRECTION_VISUAL_LTR | U_SHAPE_LETTERS_SHAPE | U_SHAPE_LENGTH_FIXED_SPACES_AT_END, &status);

            if (U_SUCCESS(status)) {

                ubidi_writeReverse (intermediate2, n, output, n, UBIDI_REMOVE_BIDI_CONTROLS, &status);

                env->SetCharArrayRegion(destArray, 0, outputSize, output);
            } else
                hasErrors = true;
        } else
            hasErrors = true;
    } else
        hasErrors = true;

    delete [] intermediate;
    delete [] intermediate2;
    delete [] output;

    env->ReleaseCharArrayElements(srcArray, src, JNI_ABORT);

    if (hasErrors)
        jniThrowException(env, "java/lang/RuntimeException", NULL);

    return outputSize;
}

static JNINativeMethod gMethods[] = {
        { "runBidi", "(I[C[BIZ)I",
        (void*) runBidi },
        { "reorderReshapeBidiText", "([C[CII)I",
        (void*) reorderReshapeBidiText },
        { "reshapeArabicText", "([C[CII)I",
        (void*) reshapeArabicText }
};

int register_android_text_AndroidBidi(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/text/AndroidBidi");
    LOG_ASSERT(clazz, "Cannot find android/text/AndroidBidi");
    
    return AndroidRuntime::registerNativeMethods(env, "android/text/AndroidBidi",
            gMethods, NELEM(gMethods));
}

}
