/* //device/libs/android_runtime/android_util_XmlBlock.cpp
**
** Copyright 2006, The Android Open Source Project
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

#define LOG_TAG "XmlBlock"

#include "jni.h"
#include "JNIHelp.h"
#include <android_runtime/AndroidRuntime.h>
#include <androidfw/AssetManager.h>
#include <androidfw/ResourceTypes.h>
#include <utils/Log.h>
#include <utils/misc.h>

#include <stdio.h>

namespace android {

// ----------------------------------------------------------------------------

static jint android_content_XmlBlock_nativeCreate(JNIEnv* env, jobject clazz,
                                               jbyteArray bArray,
                                               jint off, jint len)
{
    if (bArray == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    jsize bLen = env->GetArrayLength(bArray);
    if (off < 0 || off >= bLen || len < 0 || len > bLen || (off+len) > bLen) {
        jniThrowException(env, "java/lang/IndexOutOfBoundsException", NULL);
        return 0;
    }

    jbyte* b = env->GetByteArrayElements(bArray, NULL);
    ResXMLTree* osb = new ResXMLTree(b+off, len, true);
    env->ReleaseByteArrayElements(bArray, b, 0);

    if (osb == NULL || osb->getError() != NO_ERROR) {
        jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return 0;
    }

    return (jint)osb;
}

static jint android_content_XmlBlock_nativeGetStringBlock(JNIEnv* env, jobject clazz,
                                                       jint token)
{
    ResXMLTree* osb = (ResXMLTree*)token;
    if (osb == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    return (jint)&osb->getStrings();
}

static jint android_content_XmlBlock_nativeCreateParseState(JNIEnv* env, jobject clazz,
                                                          jint token)
{
    ResXMLTree* osb = (ResXMLTree*)token;
    if (osb == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    ResXMLParser* st = new ResXMLParser(*osb);
    if (st == NULL) {
        jniThrowException(env, "java/lang/OutOfMemoryError", NULL);
        return 0;
    }

    st->restart();

    return (jint)st;
}

static jint android_content_XmlBlock_nativeNext(JNIEnv* env, jobject clazz,
                                             jint token)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        return ResXMLParser::END_DOCUMENT;
    }

    do {
        jint code = (jint)st->next();
        switch (code) {
            case ResXMLParser::START_TAG:
                return 2;
            case ResXMLParser::END_TAG:
                return 3;
            case ResXMLParser::TEXT:
                return 4;
            case ResXMLParser::START_DOCUMENT:
                return 0;
            case ResXMLParser::END_DOCUMENT:
                return 1;
            case ResXMLParser::BAD_DOCUMENT:
                goto bad;
        }
    } while (true);

bad:
    jniThrowException(env, "org/xmlpull/v1/XmlPullParserException",
            "Corrupt XML binary file");
    return ResXMLParser::BAD_DOCUMENT;
}

static jint android_content_XmlBlock_nativeGetNamespace(JNIEnv* env, jobject clazz,
                                                   jint token)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        return -1;
    }

    return (jint)st->getElementNamespaceID();
}

static jint android_content_XmlBlock_nativeGetName(JNIEnv* env, jobject clazz,
                                                jint token)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        return -1;
    }

    return (jint)st->getElementNameID();
}

static jint android_content_XmlBlock_nativeGetText(JNIEnv* env, jobject clazz,
                                                jint token)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        return -1;
    }

    return (jint)st->getTextID();
}

static jint android_content_XmlBlock_nativeGetLineNumber(JNIEnv* env, jobject clazz,
                                                         jint token)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    return (jint)st->getLineNumber();
}

static jint android_content_XmlBlock_nativeGetAttributeCount(JNIEnv* env, jobject clazz,
                                                          jint token)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    return (jint)st->getAttributeCount();
}

static jint android_content_XmlBlock_nativeGetAttributeNamespace(JNIEnv* env, jobject clazz,
                                                                 jint token, jint idx)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    return (jint)st->getAttributeNamespaceID(idx);
}

static jint android_content_XmlBlock_nativeGetAttributeName(JNIEnv* env, jobject clazz,
                                                         jint token, jint idx)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    return (jint)st->getAttributeNameID(idx);
}

static jint android_content_XmlBlock_nativeGetAttributeResource(JNIEnv* env, jobject clazz,
                                                             jint token, jint idx)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    return (jint)st->getAttributeNameResID(idx);
}

static jint android_content_XmlBlock_nativeGetAttributeDataType(JNIEnv* env, jobject clazz,
                                                                jint token, jint idx)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    return (jint)st->getAttributeDataType(idx);
}

static jint android_content_XmlBlock_nativeGetAttributeData(JNIEnv* env, jobject clazz,
                                                            jint token, jint idx)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    return (jint)st->getAttributeData(idx);
}

static jint android_content_XmlBlock_nativeGetAttributeStringValue(JNIEnv* env, jobject clazz,
                                                                   jint token, jint idx)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    return (jint)st->getAttributeValueStringID(idx);
}

static jint android_content_XmlBlock_nativeGetAttributeIndex(JNIEnv* env, jobject clazz,
                                                             jint token,
                                                             jstring ns, jstring name)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL || name == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    const char16_t* ns16 = NULL;
    jsize nsLen = 0;
    if (ns) {
        ns16 = (char16_t*)env->GetStringChars(ns, NULL);
        nsLen = env->GetStringLength(ns);
    }

    const char16_t* name16 = (char16_t*)env->GetStringChars(name, NULL);
    jsize nameLen = env->GetStringLength(name);

    jint idx = (jint)st->indexOfAttribute(ns16, nsLen, name16, nameLen);

    if (ns) {
        env->ReleaseStringChars(ns, (jchar*)ns16);
    }
    env->ReleaseStringChars(name, (jchar*)name16);

    return idx;
}

static jint android_content_XmlBlock_nativeGetIdAttribute(JNIEnv* env, jobject clazz,
                                                          jint token)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    ssize_t idx = st->indexOfID();
    return idx >= 0 ? (jint)st->getAttributeValueStringID(idx) : -1;
}

static jint android_content_XmlBlock_nativeGetClassAttribute(JNIEnv* env, jobject clazz,
                                                             jint token)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    ssize_t idx = st->indexOfClass();
    return idx >= 0 ? (jint)st->getAttributeValueStringID(idx) : -1;
}

static jint android_content_XmlBlock_nativeGetStyleAttribute(JNIEnv* env, jobject clazz,
                                                             jint token)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return 0;
    }

    ssize_t idx = st->indexOfStyle();
    if (idx < 0) {
        return 0;
    }

    Res_value value;
    if (st->getAttributeValue(idx, &value) < 0) {
        return 0;
    }

    return value.dataType == value.TYPE_REFERENCE
        || value.dataType == value.TYPE_ATTRIBUTE
        ? value.data : 0;
}

static void android_content_XmlBlock_nativeDestroyParseState(JNIEnv* env, jobject clazz,
                                                          jint token)
{
    ResXMLParser* st = (ResXMLParser*)token;
    if (st == NULL) {
        jniThrowNullPointerException(env, NULL);
        return;
    }

    delete st;
}

static void android_content_XmlBlock_nativeDestroy(JNIEnv* env, jobject clazz,
                                                   jint token)
{
    ResXMLTree* osb = (ResXMLTree*)token;
    if (osb == NULL) {
        jniThrowNullPointerException(env, NULL);
        return;
    }

    delete osb;
}

// ----------------------------------------------------------------------------

/*
 * JNI registration.
 */
static JNINativeMethod gXmlBlockMethods[] = {
    /* name, signature, funcPtr */
    { "nativeCreate",               "([BII)I",
            (void*) android_content_XmlBlock_nativeCreate },
    { "nativeGetStringBlock",       "(I)I",
            (void*) android_content_XmlBlock_nativeGetStringBlock },
    { "nativeCreateParseState",     "(I)I",
            (void*) android_content_XmlBlock_nativeCreateParseState },
    { "nativeNext",                 "(I)I",
            (void*) android_content_XmlBlock_nativeNext },
    { "nativeGetNamespace",         "(I)I",
            (void*) android_content_XmlBlock_nativeGetNamespace },
    { "nativeGetName",              "(I)I",
            (void*) android_content_XmlBlock_nativeGetName },
    { "nativeGetText",              "(I)I",
            (void*) android_content_XmlBlock_nativeGetText },
    { "nativeGetLineNumber",        "(I)I",
            (void*) android_content_XmlBlock_nativeGetLineNumber },
    { "nativeGetAttributeCount",    "(I)I",
            (void*) android_content_XmlBlock_nativeGetAttributeCount },
    { "nativeGetAttributeNamespace","(II)I",
            (void*) android_content_XmlBlock_nativeGetAttributeNamespace },
    { "nativeGetAttributeName",     "(II)I",
            (void*) android_content_XmlBlock_nativeGetAttributeName },
    { "nativeGetAttributeResource", "(II)I",
            (void*) android_content_XmlBlock_nativeGetAttributeResource },
    { "nativeGetAttributeDataType", "(II)I",
            (void*) android_content_XmlBlock_nativeGetAttributeDataType },
    { "nativeGetAttributeData",    "(II)I",
            (void*) android_content_XmlBlock_nativeGetAttributeData },
    { "nativeGetAttributeStringValue", "(II)I",
            (void*) android_content_XmlBlock_nativeGetAttributeStringValue },
    { "nativeGetAttributeIndex",    "(ILjava/lang/String;Ljava/lang/String;)I",
            (void*) android_content_XmlBlock_nativeGetAttributeIndex },
    { "nativeGetIdAttribute",      "(I)I",
            (void*) android_content_XmlBlock_nativeGetIdAttribute },
    { "nativeGetClassAttribute",   "(I)I",
            (void*) android_content_XmlBlock_nativeGetClassAttribute },
    { "nativeGetStyleAttribute",   "(I)I",
            (void*) android_content_XmlBlock_nativeGetStyleAttribute },
    { "nativeDestroyParseState",    "(I)V",
            (void*) android_content_XmlBlock_nativeDestroyParseState },
    { "nativeDestroy",              "(I)V",
            (void*) android_content_XmlBlock_nativeDestroy },
};

int register_android_content_XmlBlock(JNIEnv* env)
{
    return AndroidRuntime::registerNativeMethods(env,
            "android/content/res/XmlBlock", gXmlBlockMethods, NELEM(gXmlBlockMethods));
}

}; // namespace android
