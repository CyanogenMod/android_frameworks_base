/* //device/libs/android_runtime/android_util_AssetManager.cpp
**
** Copyright 2006, The Android Open Source Project
** This code has been modified.  Portions copyright (C) 2010, T-Mobile USA, Inc.
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

#define LOG_TAG "asset"

#define DEBUG_STYLES(x) //x
#define THROW_ON_BAD_ID 0

#include <android_runtime/android_util_AssetManager.h>

#include "jni.h"
#include "JNIHelp.h"
#include "ScopedStringChars.h"
#include "ScopedUtfChars.h"
#include "android_util_Binder.h"
#include <utils/misc.h>
#include <android_runtime/AndroidRuntime.h>
#include <utils/Log.h>

#include <utils/Asset.h>
#include <utils/AssetManager.h>
#include <utils/ResourceTypes.h>
#include <utils/PackageRedirectionMap.h>
#include <utils/ZipFile.h>

#include <stdio.h>

#define REDIRECT_NOISY(x) //x

namespace android {

// ----------------------------------------------------------------------------

static struct typedvalue_offsets_t
{
    jfieldID mType;
    jfieldID mData;
    jfieldID mString;
    jfieldID mAssetCookie;
    jfieldID mResourceId;
    jfieldID mChangingConfigurations;
    jfieldID mDensity;
} gTypedValueOffsets;

static struct assetfiledescriptor_offsets_t
{
    jfieldID mFd;
    jfieldID mStartOffset;
    jfieldID mLength;
} gAssetFileDescriptorOffsets;

static struct assetmanager_offsets_t
{
    jfieldID mObject;
} gAssetManagerOffsets;

jclass g_stringClass = NULL;

// ----------------------------------------------------------------------------

enum {
    STYLE_NUM_ENTRIES = 6,
    STYLE_TYPE = 0,
    STYLE_DATA = 1,
    STYLE_ASSET_COOKIE = 2,
    STYLE_RESOURCE_ID = 3,
    STYLE_CHANGING_CONFIGURATIONS = 4,
    STYLE_DENSITY = 5
};

static jint copyValue(JNIEnv* env, jobject outValue, const ResTable* table,
                      const Res_value& value, uint32_t ref, ssize_t block,
                      uint32_t typeSpecFlags, ResTable_config* config = NULL);

jint copyValue(JNIEnv* env, jobject outValue, const ResTable* table,
               const Res_value& value, uint32_t ref, ssize_t block,
               uint32_t typeSpecFlags, ResTable_config* config)
{
    env->SetIntField(outValue, gTypedValueOffsets.mType, value.dataType);
    env->SetIntField(outValue, gTypedValueOffsets.mAssetCookie,
                        (jint)table->getTableCookie(block));
    env->SetIntField(outValue, gTypedValueOffsets.mData, value.data);
    env->SetObjectField(outValue, gTypedValueOffsets.mString, NULL);
    env->SetIntField(outValue, gTypedValueOffsets.mResourceId, ref);
    env->SetIntField(outValue, gTypedValueOffsets.mChangingConfigurations,
            typeSpecFlags);
    if (config != NULL) {
        env->SetIntField(outValue, gTypedValueOffsets.mDensity, config->density);
    }
    return block;
}

// ----------------------------------------------------------------------------

// this guy is exported to other jni routines
AssetManager* assetManagerForJavaObject(JNIEnv* env, jobject obj)
{
    AssetManager* am = (AssetManager*)env->GetIntField(obj, gAssetManagerOffsets.mObject);
    if (am != NULL) {
        return am;
    }
    jniThrowException(env, "java/lang/IllegalStateException", "AssetManager has been finalized!");
    return NULL;
}

static jint android_content_AssetManager_openAsset(JNIEnv* env, jobject clazz,
                                                jstring fileName, jint mode)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }

    LOGV("openAsset in %p (Java object %p)\n", am, clazz);

    ScopedUtfChars fileName8(env, fileName);
    if (fileName8.c_str() == NULL) {
        return -1;
    }

    if (mode != Asset::ACCESS_UNKNOWN && mode != Asset::ACCESS_RANDOM
        && mode != Asset::ACCESS_STREAMING && mode != Asset::ACCESS_BUFFER) {
        jniThrowException(env, "java/lang/IllegalArgumentException", "Bad access mode");
        return -1;
    }

    Asset* a = am->open(fileName8.c_str(), (Asset::AccessMode)mode);

    if (a == NULL) {
        jniThrowException(env, "java/io/FileNotFoundException", fileName8.c_str());
        return -1;
    }

    //printf("Created Asset Stream: %p\n", a);

    return (jint)a;
}

static jobject returnParcelFileDescriptor(JNIEnv* env, Asset* a, jlongArray outOffsets)
{
    off64_t startOffset, length;
    int fd = a->openFileDescriptor(&startOffset, &length);
    delete a;

    if (fd < 0) {
        jniThrowException(env, "java/io/FileNotFoundException",
                "This file can not be opened as a file descriptor; it is probably compressed");
        return NULL;
    }

    jlong* offsets = (jlong*)env->GetPrimitiveArrayCritical(outOffsets, 0);
    if (offsets == NULL) {
        close(fd);
        return NULL;
    }

    offsets[0] = startOffset;
    offsets[1] = length;

    env->ReleasePrimitiveArrayCritical(outOffsets, offsets, 0);

    jobject fileDesc = jniCreateFileDescriptor(env, fd);
    if (fileDesc == NULL) {
        close(fd);
        return NULL;
    }

    return newParcelFileDescriptor(env, fileDesc);
}

static jobject android_content_AssetManager_openAssetFd(JNIEnv* env, jobject clazz,
                                                jstring fileName, jlongArray outOffsets)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    LOGV("openAssetFd in %p (Java object %p)\n", am, clazz);

    ScopedUtfChars fileName8(env, fileName);
    if (fileName8.c_str() == NULL) {
        return NULL;
    }

    Asset* a = am->open(fileName8.c_str(), Asset::ACCESS_RANDOM);

    if (a == NULL) {
        jniThrowException(env, "java/io/FileNotFoundException", fileName8.c_str());
        return NULL;
    }

    //printf("Created Asset Stream: %p\n", a);

    return returnParcelFileDescriptor(env, a, outOffsets);
}

static jint android_content_AssetManager_openNonAssetNative(JNIEnv* env, jobject clazz,
                                                         jint cookie,
                                                         jstring fileName,
                                                         jint mode)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }

    LOGV("openNonAssetNative in %p (Java object %p)\n", am, clazz);

    ScopedUtfChars fileName8(env, fileName);
    if (fileName8.c_str() == NULL) {
        return -1;
    }

    if (mode != Asset::ACCESS_UNKNOWN && mode != Asset::ACCESS_RANDOM
        && mode != Asset::ACCESS_STREAMING && mode != Asset::ACCESS_BUFFER) {
        jniThrowException(env, "java/lang/IllegalArgumentException", "Bad access mode");
        return -1;
    }

    Asset* a = cookie
        ? am->openNonAsset((void*)cookie, fileName8.c_str(), (Asset::AccessMode)mode)
        : am->openNonAsset(fileName8.c_str(), (Asset::AccessMode)mode);

    if (a == NULL) {
        jniThrowException(env, "java/io/FileNotFoundException", fileName8.c_str());
        return -1;
    }

    //printf("Created Asset Stream: %p\n", a);

    return (jint)a;
}

static jobject android_content_AssetManager_openNonAssetFdNative(JNIEnv* env, jobject clazz,
                                                         jint cookie,
                                                         jstring fileName,
                                                         jlongArray outOffsets)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    LOGV("openNonAssetFd in %p (Java object %p)\n", am, clazz);

    ScopedUtfChars fileName8(env, fileName);
    if (fileName8.c_str() == NULL) {
        return NULL;
    }

    Asset* a = cookie
        ? am->openNonAsset((void*)cookie, fileName8.c_str(), Asset::ACCESS_RANDOM)
        : am->openNonAsset(fileName8.c_str(), Asset::ACCESS_RANDOM);

    if (a == NULL) {
        jniThrowException(env, "java/io/FileNotFoundException", fileName8.c_str());
        return NULL;
    }

    //printf("Created Asset Stream: %p\n", a);

    return returnParcelFileDescriptor(env, a, outOffsets);
}

static jobjectArray android_content_AssetManager_list(JNIEnv* env, jobject clazz,
                                                   jstring fileName)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    ScopedUtfChars fileName8(env, fileName);
    if (fileName8.c_str() == NULL) {
        return NULL;
    }

    AssetDir* dir = am->openDir(fileName8.c_str());

    if (dir == NULL) {
        jniThrowException(env, "java/io/FileNotFoundException", fileName8.c_str());
        return NULL;
    }

    jclass cls = env->FindClass("java/lang/String");
    LOG_FATAL_IF(cls == NULL, "No string class?!?");
    if (cls == NULL) {
        delete dir;
        return NULL;
    }

    size_t N = dir->getFileCount();

    jobjectArray array = env->NewObjectArray(dir->getFileCount(),
                                                cls, NULL);
    if (array == NULL) {
        delete dir;
        return NULL;
    }

    for (size_t i=0; i<N; i++) {
        const String8& name = dir->getFileName(i);
        jstring str = env->NewStringUTF(name.string());
        if (str == NULL) {
            delete dir;
            return NULL;
        }
        env->SetObjectArrayElement(array, i, str);
        env->DeleteLocalRef(str);
    }

    delete dir;

    return array;
}

static void android_content_AssetManager_destroyAsset(JNIEnv* env, jobject clazz,
                                                   jint asset)
{
    Asset* a = (Asset*)asset;

    //printf("Destroying Asset Stream: %p\n", a);

    if (a == NULL) {
        jniThrowNullPointerException(env, "asset");
        return;
    }

    delete a;
}

static jint android_content_AssetManager_readAssetChar(JNIEnv* env, jobject clazz,
                                                    jint asset)
{
    Asset* a = (Asset*)asset;

    if (a == NULL) {
        jniThrowNullPointerException(env, "asset");
        return -1;
    }

    uint8_t b;
    ssize_t res = a->read(&b, 1);
    return res == 1 ? b : -1;
}

static jint android_content_AssetManager_readAsset(JNIEnv* env, jobject clazz,
                                                jint asset, jbyteArray bArray,
                                                jint off, jint len)
{
    Asset* a = (Asset*)asset;

    if (a == NULL || bArray == NULL) {
        jniThrowNullPointerException(env, "asset");
        return -1;
    }

    if (len == 0) {
        return 0;
    }

    jsize bLen = env->GetArrayLength(bArray);
    if (off < 0 || off >= bLen || len < 0 || len > bLen || (off+len) > bLen) {
        jniThrowException(env, "java/lang/IndexOutOfBoundsException", "");
        return -1;
    }

    jbyte* b = env->GetByteArrayElements(bArray, NULL);
    ssize_t res = a->read(b+off, len);
    env->ReleaseByteArrayElements(bArray, b, 0);

    if (res > 0) return res;

    if (res < 0) {
        jniThrowException(env, "java/io/IOException", "");
    }
    return -1;
}

static jlong android_content_AssetManager_seekAsset(JNIEnv* env, jobject clazz,
                                                 jint asset,
                                                 jlong offset, jint whence)
{
    Asset* a = (Asset*)asset;

    if (a == NULL) {
        jniThrowNullPointerException(env, "asset");
        return -1;
    }

    return a->seek(
        offset, (whence > 0) ? SEEK_END : (whence < 0 ? SEEK_SET : SEEK_CUR));
}

static jlong android_content_AssetManager_getAssetLength(JNIEnv* env, jobject clazz,
                                                      jint asset)
{
    Asset* a = (Asset*)asset;

    if (a == NULL) {
        jniThrowNullPointerException(env, "asset");
        return -1;
    }

    return a->getLength();
}

static jlong android_content_AssetManager_getAssetRemainingLength(JNIEnv* env, jobject clazz,
                                                               jint asset)
{
    Asset* a = (Asset*)asset;

    if (a == NULL) {
        jniThrowNullPointerException(env, "asset");
        return -1;
    }

    return a->getRemainingLength();
}

static jint android_content_AssetManager_addAssetPath(JNIEnv* env, jobject clazz,
                                                       jstring path)
{
    ScopedUtfChars path8(env, path);
    if (path8.c_str() == NULL) {
        return JNI_FALSE;
    }

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }

    void* cookie;
    bool res = am->addAssetPath(String8(path8.c_str()), &cookie);

    return (res) ? (jint)cookie : 0;
}

static jboolean android_content_AssetManager_isUpToDate(JNIEnv* env, jobject clazz)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_TRUE;
    }
    return am->isUpToDate() ? JNI_TRUE : JNI_FALSE;
}

static void android_content_AssetManager_setLocale(JNIEnv* env, jobject clazz,
                                                jstring locale)
{
    ScopedUtfChars locale8(env, locale);
    if (locale8.c_str() == NULL) {
        return;
    }

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return;
    }

    am->setLocale(locale8.c_str());
}

static jobjectArray android_content_AssetManager_getLocales(JNIEnv* env, jobject clazz)
{
    Vector<String8> locales;

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    am->getLocales(&locales);

    const int N = locales.size();

    jobjectArray result = env->NewObjectArray(N, g_stringClass, NULL);
    if (result == NULL) {
        return NULL;
    }

    for (int i=0; i<N; i++) {
        jstring str = env->NewStringUTF(locales[i].string());
        if (str == NULL) {
            return NULL;
        }
        env->SetObjectArrayElement(result, i, str);
        env->DeleteLocalRef(str);
    }

    return result;
}

static void android_content_AssetManager_setConfiguration(JNIEnv* env, jobject clazz,
                                                          jint mcc, jint mnc,
                                                          jstring locale, jint orientation,
                                                          jint touchscreen, jint density,
                                                          jint keyboard, jint keyboardHidden,
                                                          jint navigation,
                                                          jint screenWidth, jint screenHeight,
                                                          jint smallestScreenWidthDp,
                                                          jint screenWidthDp, jint screenHeightDp,
                                                          jint screenLayout, jint uiMode,
                                                          jint sdkVersion)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return;
    }

    ResTable_config config;
    memset(&config, 0, sizeof(config));

    const char* locale8 = locale != NULL ? env->GetStringUTFChars(locale, NULL) : NULL;

    config.mcc = (uint16_t)mcc;
    config.mnc = (uint16_t)mnc;
    config.orientation = (uint8_t)orientation;
    config.touchscreen = (uint8_t)touchscreen;
    config.density = (uint16_t)density;
    config.keyboard = (uint8_t)keyboard;
    config.inputFlags = (uint8_t)keyboardHidden;
    config.navigation = (uint8_t)navigation;
    config.screenWidth = (uint16_t)screenWidth;
    config.screenHeight = (uint16_t)screenHeight;
    config.smallestScreenWidthDp = (uint16_t)smallestScreenWidthDp;
    config.screenWidthDp = (uint16_t)screenWidthDp;
    config.screenHeightDp = (uint16_t)screenHeightDp;
    config.screenLayout = (uint8_t)screenLayout;
    config.uiMode = (uint8_t)uiMode;
    config.sdkVersion = (uint16_t)sdkVersion;
    config.minorVersion = 0;
    am->setConfiguration(config, locale8);

    if (locale != NULL) env->ReleaseStringUTFChars(locale, locale8);
}

static jint android_content_AssetManager_getResourceIdentifier(JNIEnv* env, jobject clazz,
                                                            jstring name,
                                                            jstring defType,
                                                            jstring defPackage)
{
    ScopedStringChars name16(env, name);
    if (name16.get() == NULL) {
        return 0;
    }

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }

    const char16_t* defType16 = defType
        ? (char16_t*)env->GetStringChars(defType, NULL) : NULL;
    jsize defTypeLen = defType
        ? env->GetStringLength(defType) : 0;
    const char16_t* defPackage16 = defPackage
        ? (char16_t*)env->GetStringChars(defPackage, NULL) : NULL;
    jsize defPackageLen = defPackage
        ? env->GetStringLength(defPackage) : 0;

    jint ident = am->getResources().identifierForName(
        (const char16_t*)name16.get(), name16.size(), defType16, defTypeLen, defPackage16, defPackageLen);

    if (defPackage16) {
        env->ReleaseStringChars(defPackage, (jchar*)defPackage16);
    }
    if (defType16) {
        env->ReleaseStringChars(defType, (jchar*)defType16);
    }

    return ident;
}

static jstring android_content_AssetManager_getResourceName(JNIEnv* env, jobject clazz,
                                                            jint resid)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    ResTable::resource_name name;
    if (!am->getResources().getResourceName(resid, &name)) {
        return NULL;
    }

    String16 str;
    if (name.package != NULL) {
        str.setTo(name.package, name.packageLen);
    }
    if (name.type != NULL) {
        if (str.size() > 0) {
            char16_t div = ':';
            str.append(&div, 1);
        }
        str.append(name.type, name.typeLen);
    }
    if (name.name != NULL) {
        if (str.size() > 0) {
            char16_t div = '/';
            str.append(&div, 1);
        }
        str.append(name.name, name.nameLen);
    }

    return env->NewString((const jchar*)str.string(), str.size());
}

static jstring android_content_AssetManager_getResourcePackageName(JNIEnv* env, jobject clazz,
                                                                   jint resid)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    ResTable::resource_name name;
    if (!am->getResources().getResourceName(resid, &name)) {
        return NULL;
    }

    if (name.package != NULL) {
        return env->NewString((const jchar*)name.package, name.packageLen);
    }

    return NULL;
}

static jstring android_content_AssetManager_getResourceTypeName(JNIEnv* env, jobject clazz,
                                                                jint resid)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    ResTable::resource_name name;
    if (!am->getResources().getResourceName(resid, &name)) {
        return NULL;
    }

    if (name.type != NULL) {
        return env->NewString((const jchar*)name.type, name.typeLen);
    }

    return NULL;
}

static jstring android_content_AssetManager_getResourceEntryName(JNIEnv* env, jobject clazz,
                                                                 jint resid)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    ResTable::resource_name name;
    if (!am->getResources().getResourceName(resid, &name)) {
        return NULL;
    }

    if (name.name != NULL) {
        return env->NewString((const jchar*)name.name, name.nameLen);
    }

    return NULL;
}

static jint android_content_AssetManager_loadResourceValue(JNIEnv* env, jobject clazz,
                                                           jint ident,
                                                           jshort density,
                                                           jobject outValue,
                                                           jboolean resolve)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    const ResTable& res(am->getResources());

    uint32_t ref = res.lookupRedirectionMap(ident);
    if (ref == 0) {
        ref = ident;
    } else {
        REDIRECT_NOISY(LOGW("PERFORMED REDIRECT OF ident=0x%08x FOR ref=0x%08x\n", ident, ref));
    }

    Res_value value;
    ResTable_config config;
    uint32_t typeSpecFlags;
    ssize_t block = res.getResource(ref, &value, false, density, &typeSpecFlags, &config);
#if THROW_ON_BAD_ID
    if (block == BAD_INDEX) {
        jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
        return 0;
    }
#endif
    if (resolve) {
        block = res.resolveReference(&value, block, &ref);
#if THROW_ON_BAD_ID
        if (block == BAD_INDEX) {
            jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
            return 0;
        }
#endif
    }
    return block >= 0 ? copyValue(env, outValue, &res, value, ref, block, typeSpecFlags, &config) : block;
}

static jint android_content_AssetManager_loadResourceBagValue(JNIEnv* env, jobject clazz,
                                                           jint ident, jint bagEntryId,
                                                           jobject outValue, jboolean resolve)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    const ResTable& res(am->getResources());

    // Now lock down the resource object and start pulling stuff from it.
    res.lock();

    ssize_t block = -1;
    Res_value value;

    const ResTable::bag_entry* entry = NULL;
    uint32_t typeSpecFlags;
    ssize_t entryCount = res.getBagLocked(ident, &entry, &typeSpecFlags);

    for (ssize_t i=0; i<entryCount; i++) {
        if (((uint32_t)bagEntryId) == entry->map.name.ident) {
            block = entry->stringBlock;
            value = entry->map.value;
        }
        entry++;
    }

    res.unlock();

    if (block < 0) {
        return block;
    }

    uint32_t ref = ident;
    if (resolve) {
        block = res.resolveReference(&value, block, &ref, &typeSpecFlags);
#if THROW_ON_BAD_ID
        if (block == BAD_INDEX) {
            jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
            return 0;
        }
#endif
    }
    return block >= 0 ? copyValue(env, outValue, &res, value, ref, block, typeSpecFlags) : block;
}

static jint android_content_AssetManager_getStringBlockCount(JNIEnv* env, jobject clazz)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    return am->getResources().getTableCount();
}

static jint android_content_AssetManager_getNativeStringBlock(JNIEnv* env, jobject clazz,
                                                           jint block)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    return (jint)am->getResources().getTableStringBlock(block);
}

static jstring android_content_AssetManager_getCookieName(JNIEnv* env, jobject clazz,
                                                       jint cookie)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    String8 name(am->getAssetPath((void*)cookie));
    if (name.length() == 0) {
        jniThrowException(env, "java/lang/IndexOutOfBoundsException", "Empty cookie name");
        return NULL;
    }
    jstring str = env->NewStringUTF(name.string());
    return str;
}

static jint android_content_AssetManager_newTheme(JNIEnv* env, jobject clazz)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    return (jint)(new ResTable::Theme(am->getResources()));
}

static void android_content_AssetManager_deleteTheme(JNIEnv* env, jobject clazz,
                                                     jint themeInt)
{
    ResTable::Theme* theme = (ResTable::Theme*)themeInt;
    delete theme;
}

static void android_content_AssetManager_applyThemeStyle(JNIEnv* env, jobject clazz,
                                                         jint themeInt,
                                                         jint styleRes,
                                                         jboolean force)
{
    ResTable::Theme* theme = (ResTable::Theme*)themeInt;
    theme->applyStyle(styleRes, force ? true : false);
}

static void android_content_AssetManager_copyTheme(JNIEnv* env, jobject clazz,
                                                   jint destInt, jint srcInt)
{
    ResTable::Theme* dest = (ResTable::Theme*)destInt;
    ResTable::Theme* src = (ResTable::Theme*)srcInt;
    dest->setTo(*src);
}

static jint android_content_AssetManager_loadThemeAttributeValue(
    JNIEnv* env, jobject clazz, jint themeInt, jint ident, jobject outValue, jboolean resolve)
{
    ResTable::Theme* theme = (ResTable::Theme*)themeInt;
    const ResTable& res(theme->getResTable());

    Res_value value;
    // XXX value could be different in different configs!
    uint32_t typeSpecFlags = 0;
    ssize_t block = theme->getAttribute(ident, &value, &typeSpecFlags);
    uint32_t ref = 0;
    if (resolve) {
        block = res.resolveReference(&value, block, &ref, &typeSpecFlags);
#if THROW_ON_BAD_ID
        if (block == BAD_INDEX) {
            jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
            return 0;
        }
#endif
    }
    return block >= 0 ? copyValue(env, outValue, &res, value, ref, block, typeSpecFlags) : block;
}

static void android_content_AssetManager_dumpTheme(JNIEnv* env, jobject clazz,
                                                   jint themeInt, jint pri,
                                                   jstring tag, jstring prefix)
{
    ResTable::Theme* theme = (ResTable::Theme*)themeInt;
    const ResTable& res(theme->getResTable());

    // XXX Need to use params.
    theme->dumpToLog();
}

static jboolean android_content_AssetManager_applyStyle(JNIEnv* env, jobject clazz,
                                                        jint themeToken,
                                                        jint defStyleAttr,
                                                        jint defStyleRes,
                                                        jint xmlParserToken,
                                                        jintArray attrs,
                                                        jintArray outValues,
                                                        jintArray outIndices)
{
    if (themeToken == 0) {
        jniThrowNullPointerException(env, "theme token");
        return JNI_FALSE;
    }
    if (attrs == NULL) {
        jniThrowNullPointerException(env, "attrs");
        return JNI_FALSE;
    }
    if (outValues == NULL) {
        jniThrowNullPointerException(env, "out values");
        return JNI_FALSE;
    }

    DEBUG_STYLES(LOGI("APPLY STYLE: theme=0x%x defStyleAttr=0x%x defStyleRes=0x%x xml=0x%x",
        themeToken, defStyleAttr, defStyleRes, xmlParserToken));

    ResTable::Theme* theme = (ResTable::Theme*)themeToken;
    const ResTable& res = theme->getResTable();
    ResXMLParser* xmlParser = (ResXMLParser*)xmlParserToken;
    ResTable_config config;
    Res_value value;

    const jsize NI = env->GetArrayLength(attrs);
    const jsize NV = env->GetArrayLength(outValues);
    if (NV < (NI*STYLE_NUM_ENTRIES)) {
        jniThrowException(env, "java/lang/IndexOutOfBoundsException", "out values too small");
        return JNI_FALSE;
    }

    jint* src = (jint*)env->GetPrimitiveArrayCritical(attrs, 0);
    if (src == NULL) {
        return JNI_FALSE;
    }

    jint* baseDest = (jint*)env->GetPrimitiveArrayCritical(outValues, 0);
    jint* dest = baseDest;
    if (dest == NULL) {
        env->ReleasePrimitiveArrayCritical(attrs, src, 0);
        return JNI_FALSE;
    }

    jint* indices = NULL;
    int indicesIdx = 0;
    if (outIndices != NULL) {
        if (env->GetArrayLength(outIndices) > NI) {
            indices = (jint*)env->GetPrimitiveArrayCritical(outIndices, 0);
        }
    }

    // Load default style from attribute, if specified...
    uint32_t defStyleBagTypeSetFlags = 0;
    if (defStyleAttr != 0) {
        Res_value value;
        if (theme->getAttribute(defStyleAttr, &value, &defStyleBagTypeSetFlags) >= 0) {
            if (value.dataType == Res_value::TYPE_REFERENCE) {
                defStyleRes = value.data;
            }
        }
    }

    // Retrieve the style class associated with the current XML tag.
    int style = 0;
    uint32_t styleBagTypeSetFlags = 0;
    if (xmlParser != NULL) {
        ssize_t idx = xmlParser->indexOfStyle();
        if (idx >= 0 && xmlParser->getAttributeValue(idx, &value) >= 0) {
            if (value.dataType == value.TYPE_ATTRIBUTE) {
                if (theme->getAttribute(value.data, &value, &styleBagTypeSetFlags) < 0) {
                    value.dataType = Res_value::TYPE_NULL;
                }
            }
            if (value.dataType == value.TYPE_REFERENCE) {
                style = value.data;
            }
        }
    }

    // Now lock down the resource object and start pulling stuff from it.
    res.lock();

    // Apply theme redirections to the referenced styles.
    if (defStyleRes != 0) {
        uint32_t ref = res.lookupRedirectionMap(defStyleRes);
        if (ref != 0) {
            defStyleRes = ref;
        }
    }
    if (style != 0) {
        uint32_t ref = res.lookupRedirectionMap(style);
        if (ref != 0) {
            style = ref;
        }
    }

    // Retrieve the default style bag, if requested.
    const ResTable::bag_entry* defStyleEnt = NULL;
    uint32_t defStyleTypeSetFlags = 0;
    ssize_t bagOff = defStyleRes != 0
            ? res.getBagLocked(defStyleRes, &defStyleEnt, &defStyleTypeSetFlags) : -1;
    defStyleTypeSetFlags |= defStyleBagTypeSetFlags;
    const ResTable::bag_entry* endDefStyleEnt = defStyleEnt +
        (bagOff >= 0 ? bagOff : 0);

    // Retrieve the style class bag, if requested.
    const ResTable::bag_entry* styleEnt = NULL;
    uint32_t styleTypeSetFlags = 0;
    bagOff = style != 0 ? res.getBagLocked(style, &styleEnt, &styleTypeSetFlags) : -1;
    styleTypeSetFlags |= styleBagTypeSetFlags;
    const ResTable::bag_entry* endStyleEnt = styleEnt +
        (bagOff >= 0 ? bagOff : 0);

    // Retrieve the XML attributes, if requested.
    const jsize NX = xmlParser ? xmlParser->getAttributeCount() : 0;
    jsize ix=0;
    uint32_t curXmlAttr = xmlParser ? xmlParser->getAttributeNameResID(ix) : 0;

    static const ssize_t kXmlBlock = 0x10000000;

    // Now iterate through all of the attributes that the client has requested,
    // filling in each with whatever data we can find.
    ssize_t block = 0;
    uint32_t typeSetFlags;
    for (jsize ii=0; ii<NI; ii++) {
        const uint32_t curIdent = (uint32_t)src[ii];

        DEBUG_STYLES(LOGI("RETRIEVING ATTR 0x%08x...", curIdent));

        // Try to find a value for this attribute...  we prioritize values
        // coming from, first XML attributes, then XML style, then default
        // style, and finally the theme.
        value.dataType = Res_value::TYPE_NULL;
        value.data = 0;
        typeSetFlags = 0;
        config.density = 0;

        // Skip through XML attributes until the end or the next possible match.
        while (ix < NX && curIdent > curXmlAttr) {
            ix++;
            curXmlAttr = xmlParser->getAttributeNameResID(ix);
        }
        // Retrieve the current XML attribute if it matches, and step to next.
        if (ix < NX && curIdent == curXmlAttr) {
            block = kXmlBlock;
            xmlParser->getAttributeValue(ix, &value);
            ix++;
            curXmlAttr = xmlParser->getAttributeNameResID(ix);
            DEBUG_STYLES(LOGI("-> From XML: type=0x%x, data=0x%08x",
                    value.dataType, value.data));
        }

        // Skip through the style values until the end or the next possible match.
        while (styleEnt < endStyleEnt && curIdent > styleEnt->map.name.ident) {
            styleEnt++;
        }
        // Retrieve the current style attribute if it matches, and step to next.
        if (styleEnt < endStyleEnt && curIdent == styleEnt->map.name.ident) {
            if (value.dataType == Res_value::TYPE_NULL) {
                block = styleEnt->stringBlock;
                typeSetFlags = styleTypeSetFlags;
                value = styleEnt->map.value;
                DEBUG_STYLES(LOGI("-> From style: type=0x%x, data=0x%08x",
                        value.dataType, value.data));
            }
            styleEnt++;
        }

        // Skip through the default style values until the end or the next possible match.
        while (defStyleEnt < endDefStyleEnt && curIdent > defStyleEnt->map.name.ident) {
            defStyleEnt++;
        }
        // Retrieve the current default style attribute if it matches, and step to next.
        if (defStyleEnt < endDefStyleEnt && curIdent == defStyleEnt->map.name.ident) {
            if (value.dataType == Res_value::TYPE_NULL) {
                block = defStyleEnt->stringBlock;
                typeSetFlags = defStyleTypeSetFlags;
                value = defStyleEnt->map.value;
                DEBUG_STYLES(LOGI("-> From def style: type=0x%x, data=0x%08x",
                        value.dataType, value.data));
            }
            defStyleEnt++;
        }

        uint32_t resid = 0;
        if (value.dataType != Res_value::TYPE_NULL) {
            // Take care of resolving the found resource to its final value.
            ssize_t newBlock = theme->resolveAttributeReference(&value, block,
                    &resid, &typeSetFlags, &config);
            if (newBlock >= 0) block = newBlock;
            DEBUG_STYLES(LOGI("-> Resolved attr: type=0x%x, data=0x%08x",
                    value.dataType, value.data));
        } else {
            // If we still don't have a value for this attribute, try to find
            // it in the theme!
            ssize_t newBlock = theme->getAttribute(curIdent, &value, &typeSetFlags);
            if (newBlock >= 0) {
                DEBUG_STYLES(LOGI("-> From theme: type=0x%x, data=0x%08x",
                        value.dataType, value.data));
                newBlock = res.resolveReference(&value, block, &resid,
                        &typeSetFlags, &config);
#if THROW_ON_BAD_ID
                if (newBlock == BAD_INDEX) {
                    jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
                    return JNI_FALSE;
                }
#endif
                if (newBlock >= 0) block = newBlock;
                DEBUG_STYLES(LOGI("-> Resolved theme: type=0x%x, data=0x%08x",
                        value.dataType, value.data));
            }
        }

        // Deal with the special @null value -- it turns back to TYPE_NULL.
        if (value.dataType == Res_value::TYPE_REFERENCE && value.data == 0) {
            DEBUG_STYLES(LOGI("-> Setting to @null!"));
            value.dataType = Res_value::TYPE_NULL;
            block = kXmlBlock;
        }

        // One final test for a resource redirection from the applied theme.
        if (resid != 0) {
            uint32_t redirect = res.lookupRedirectionMap(resid);
            if (redirect != 0) {
                REDIRECT_NOISY(LOGW("deep REDIRECT 0x%08x => 0x%08x\n", resid, redirect));
                ssize_t newBlock = res.getResource(redirect, &value, true, config.density, &typeSetFlags, &config);
                if (newBlock >= 0) {
                    newBlock = res.resolveReference(&value, newBlock, &redirect, &typeSetFlags, &config);
#if THROW_ON_BAD_ID
                    if (newBlock == BAD_INDEX) {
                        jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
                        return JNI_FALSE;
                    }
#endif
                    if (newBlock >= 0) {
                        block = newBlock;
                        resid = redirect;
                    }
                }
                if (resid != redirect) {
                    LOGW("deep redirect failure from 0x%08x => 0x%08x, defStyleAttr=0x%08x, defStyleRes=0x%08x, style=0x%08x\n", resid, redirect, defStyleAttr, defStyleRes, style);
                }
            }
        }

        DEBUG_STYLES(LOGI("Attribute 0x%08x: type=0x%x, data=0x%08x",
                curIdent, value.dataType, value.data));

        // Write the final value back to Java.
        dest[STYLE_TYPE] = value.dataType;
        dest[STYLE_DATA] = value.data;
        dest[STYLE_ASSET_COOKIE] =
            block != kXmlBlock ? (jint)res.getTableCookie(block) : (jint)-1;
        dest[STYLE_RESOURCE_ID] = resid;
        dest[STYLE_CHANGING_CONFIGURATIONS] = typeSetFlags;
        dest[STYLE_DENSITY] = config.density;

        if (indices != NULL && value.dataType != Res_value::TYPE_NULL) {
            indicesIdx++;
            indices[indicesIdx] = ii;
        }

        dest += STYLE_NUM_ENTRIES;
    }

    res.unlock();

    if (indices != NULL) {
        indices[0] = indicesIdx;
        env->ReleasePrimitiveArrayCritical(outIndices, indices, 0);
    }
    env->ReleasePrimitiveArrayCritical(outValues, baseDest, 0);
    env->ReleasePrimitiveArrayCritical(attrs, src, 0);

    return JNI_TRUE;
}

static jboolean android_content_AssetManager_retrieveAttributes(JNIEnv* env, jobject clazz,
                                                        jint xmlParserToken,
                                                        jintArray attrs,
                                                        jintArray outValues,
                                                        jintArray outIndices)
{
    if (xmlParserToken == 0) {
        jniThrowNullPointerException(env, "xmlParserToken");
        return JNI_FALSE;
    }
    if (attrs == NULL) {
        jniThrowNullPointerException(env, "attrs");
        return JNI_FALSE;
    }
    if (outValues == NULL) {
        jniThrowNullPointerException(env, "out values");
        return JNI_FALSE;
    }

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }
    const ResTable& res(am->getResources());
    ResXMLParser* xmlParser = (ResXMLParser*)xmlParserToken;
    ResTable_config config;
    Res_value value;

    const jsize NI = env->GetArrayLength(attrs);
    const jsize NV = env->GetArrayLength(outValues);
    if (NV < (NI*STYLE_NUM_ENTRIES)) {
        jniThrowException(env, "java/lang/IndexOutOfBoundsException", "out values too small");
        return JNI_FALSE;
    }

    jint* src = (jint*)env->GetPrimitiveArrayCritical(attrs, 0);
    if (src == NULL) {
        return JNI_FALSE;
    }

    jint* baseDest = (jint*)env->GetPrimitiveArrayCritical(outValues, 0);
    jint* dest = baseDest;
    if (dest == NULL) {
        env->ReleasePrimitiveArrayCritical(attrs, src, 0);
        return JNI_FALSE;
    }

    jint* indices = NULL;
    int indicesIdx = 0;
    if (outIndices != NULL) {
        if (env->GetArrayLength(outIndices) > NI) {
            indices = (jint*)env->GetPrimitiveArrayCritical(outIndices, 0);
        }
    }

    // Now lock down the resource object and start pulling stuff from it.
    res.lock();

    // Retrieve the XML attributes, if requested.
    const jsize NX = xmlParser->getAttributeCount();
    jsize ix=0;
    uint32_t curXmlAttr = xmlParser->getAttributeNameResID(ix);

    static const ssize_t kXmlBlock = 0x10000000;

    // Now iterate through all of the attributes that the client has requested,
    // filling in each with whatever data we can find.
    ssize_t block = 0;
    uint32_t typeSetFlags;
    for (jsize ii=0; ii<NI; ii++) {
        const uint32_t curIdent = (uint32_t)src[ii];

        // Try to find a value for this attribute...
        value.dataType = Res_value::TYPE_NULL;
        value.data = 0;
        typeSetFlags = 0;
        config.density = 0;

        // Skip through XML attributes until the end or the next possible match.
        while (ix < NX && curIdent > curXmlAttr) {
            ix++;
            curXmlAttr = xmlParser->getAttributeNameResID(ix);
        }
        // Retrieve the current XML attribute if it matches, and step to next.
        if (ix < NX && curIdent == curXmlAttr) {
            block = kXmlBlock;
            xmlParser->getAttributeValue(ix, &value);
            ix++;
            curXmlAttr = xmlParser->getAttributeNameResID(ix);
        }

        //printf("Attribute 0x%08x: type=0x%x, data=0x%08x\n", curIdent, value.dataType, value.data);
        uint32_t resid = 0;
        if (value.dataType != Res_value::TYPE_NULL) {
            // Take care of resolving the found resource to its final value.
            //printf("Resolving attribute reference\n");
            ssize_t newBlock = res.resolveReference(&value, block, &resid,
                    &typeSetFlags, &config);
#if THROW_ON_BAD_ID
            if (newBlock == BAD_INDEX) {
                jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
                return JNI_FALSE;
            }
#endif
            if (newBlock >= 0) block = newBlock;
        }

        // Deal with the special @null value -- it turns back to TYPE_NULL.
        if (value.dataType == Res_value::TYPE_REFERENCE && value.data == 0) {
            value.dataType = Res_value::TYPE_NULL;
        }

        //printf("Attribute 0x%08x: final type=0x%x, data=0x%08x\n", curIdent, value.dataType, value.data);

        // Write the final value back to Java.
        dest[STYLE_TYPE] = value.dataType;
        dest[STYLE_DATA] = value.data;
        dest[STYLE_ASSET_COOKIE] =
            block != kXmlBlock ? (jint)res.getTableCookie(block) : (jint)-1;
        dest[STYLE_RESOURCE_ID] = resid;
        dest[STYLE_CHANGING_CONFIGURATIONS] = typeSetFlags;
        dest[STYLE_DENSITY] = config.density;

        if (indices != NULL && value.dataType != Res_value::TYPE_NULL) {
            indicesIdx++;
            indices[indicesIdx] = ii;
        }

        dest += STYLE_NUM_ENTRIES;
    }

    res.unlock();

    if (indices != NULL) {
        indices[0] = indicesIdx;
        env->ReleasePrimitiveArrayCritical(outIndices, indices, 0);
    }

    env->ReleasePrimitiveArrayCritical(outValues, baseDest, 0);
    env->ReleasePrimitiveArrayCritical(attrs, src, 0);

    return JNI_TRUE;
}

static jint android_content_AssetManager_getArraySize(JNIEnv* env, jobject clazz,
                                                       jint id)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    const ResTable& res(am->getResources());

    res.lock();
    const ResTable::bag_entry* defStyleEnt = NULL;
    ssize_t bagOff = res.getBagLocked(id, &defStyleEnt);
    res.unlock();

    return bagOff;
}

static jint android_content_AssetManager_retrieveArray(JNIEnv* env, jobject clazz,
                                                        jint id,
                                                        jintArray outValues)
{
    if (outValues == NULL) {
        jniThrowNullPointerException(env, "out values");
        return JNI_FALSE;
    }

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }
    const ResTable& res(am->getResources());
    ResTable_config config;
    Res_value value;
    ssize_t block;

    const jsize NV = env->GetArrayLength(outValues);

    jint* baseDest = (jint*)env->GetPrimitiveArrayCritical(outValues, 0);
    jint* dest = baseDest;
    if (dest == NULL) {
        jniThrowException(env, "java/lang/OutOfMemoryError", "");
        return JNI_FALSE;
    }

    // Now lock down the resource object and start pulling stuff from it.
    res.lock();

    const ResTable::bag_entry* arrayEnt = NULL;
    uint32_t arrayTypeSetFlags = 0;
    ssize_t bagOff = res.getBagLocked(id, &arrayEnt, &arrayTypeSetFlags);
    const ResTable::bag_entry* endArrayEnt = arrayEnt +
        (bagOff >= 0 ? bagOff : 0);

    int i = 0;
    uint32_t typeSetFlags;
    while (i < NV && arrayEnt < endArrayEnt) {
        block = arrayEnt->stringBlock;
        typeSetFlags = arrayTypeSetFlags;
        config.density = 0;
        value = arrayEnt->map.value;

        uint32_t resid = 0;
        if (value.dataType != Res_value::TYPE_NULL) {
            // Take care of resolving the found resource to its final value.
            //printf("Resolving attribute reference\n");
            ssize_t newBlock = res.resolveReference(&value, block, &resid,
                    &typeSetFlags, &config);
#if THROW_ON_BAD_ID
            if (newBlock == BAD_INDEX) {
                jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
                return JNI_FALSE;
            }
#endif
            if (newBlock >= 0) block = newBlock;
        }

        // Deal with the special @null value -- it turns back to TYPE_NULL.
        if (value.dataType == Res_value::TYPE_REFERENCE && value.data == 0) {
            value.dataType = Res_value::TYPE_NULL;
        }

        // One final test for a resource redirection from the applied theme.
        if (resid != 0) {
            uint32_t redirect = res.lookupRedirectionMap(resid);
            if (redirect != 0) {
                REDIRECT_NOISY(LOGW("array REDIRECT 0x%08x => 0x%08x\n", resid, redirect));
                ssize_t newBlock = res.getResource(redirect, &value, true, config.density, &typeSetFlags, &config);
                if (newBlock >= 0) {
                    newBlock = res.resolveReference(&value, newBlock, &redirect, &typeSetFlags, &config);
#if THROW_ON_BAD_ID
                    if (newBlock == BAD_INDEX) {
                        jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
                        return JNI_FALSE;
                    }
#endif
                    if (newBlock >= 0) {
                        block = newBlock;
                        resid = redirect;
                    }
                }
                if (resid != redirect) {
                    LOGW("array redirect failure from 0x%08x => 0x%08x, array id=0x%08x", resid, redirect, id);
                }
            }
        }

        //printf("Attribute 0x%08x: final type=0x%x, data=0x%08x\n", curIdent, value.dataType, value.data);

        // Write the final value back to Java.
        dest[STYLE_TYPE] = value.dataType;
        dest[STYLE_DATA] = value.data;
        dest[STYLE_ASSET_COOKIE] = (jint)res.getTableCookie(block);
        dest[STYLE_RESOURCE_ID] = resid;
        dest[STYLE_CHANGING_CONFIGURATIONS] = typeSetFlags;
        dest[STYLE_DENSITY] = config.density;
        dest += STYLE_NUM_ENTRIES;
        i+= STYLE_NUM_ENTRIES;
        arrayEnt++;
    }

    i /= STYLE_NUM_ENTRIES;

    res.unlock();

    env->ReleasePrimitiveArrayCritical(outValues, baseDest, 0);

    return i;
}

static jint android_content_AssetManager_openXmlAssetNative(JNIEnv* env, jobject clazz,
                                                         jint cookie,
                                                         jstring fileName)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }

    LOGV("openXmlAsset in %p (Java object %p)\n", am, clazz);

    ScopedUtfChars fileName8(env, fileName);
    if (fileName8.c_str() == NULL) {
        return 0;
    }

    Asset* a = cookie
        ? am->openNonAsset((void*)cookie, fileName8.c_str(), Asset::ACCESS_BUFFER)
        : am->openNonAsset(fileName8.c_str(), Asset::ACCESS_BUFFER);

    if (a == NULL) {
        jniThrowException(env, "java/io/FileNotFoundException", fileName8.c_str());
        return 0;
    }

    ResXMLTree* block = new ResXMLTree();
    status_t err = block->setTo(a->getBuffer(true), a->getLength(), true);
    a->close();
    delete a;

    if (err != NO_ERROR) {
        jniThrowException(env, "java/io/FileNotFoundException", "Corrupt XML binary file");
        return 0;
    }

    return (jint)block;
}

static jintArray android_content_AssetManager_getArrayStringInfo(JNIEnv* env, jobject clazz,
                                                                 jint arrayResId)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    const ResTable& res(am->getResources());

    const ResTable::bag_entry* startOfBag;
    const ssize_t N = res.lockBag(arrayResId, &startOfBag);
    if (N < 0) {
        return NULL;
    }

    jintArray array = env->NewIntArray(N * 2);
    if (array == NULL) {
        res.unlockBag(startOfBag);
        return NULL;
    }

    Res_value value;
    const ResTable::bag_entry* bag = startOfBag;
    for (size_t i = 0, j = 0; ((ssize_t)i)<N; i++, bag++) {
        jint stringIndex = -1;
        jint stringBlock = 0;
        value = bag->map.value;

        // Take care of resolving the found resource to its final value.
        stringBlock = res.resolveReference(&value, bag->stringBlock, NULL);
        if (value.dataType == Res_value::TYPE_STRING) {
            stringIndex = value.data;
        }

#if THROW_ON_BAD_ID
        if (stringBlock == BAD_INDEX) {
            jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
            return array;
        }
#endif

        //todo: It might be faster to allocate a C array to contain
        //      the blocknums and indices, put them in there and then
        //      do just one SetIntArrayRegion()
        env->SetIntArrayRegion(array, j, 1, &stringBlock);
        env->SetIntArrayRegion(array, j + 1, 1, &stringIndex);
        j = j + 2;
    }
    res.unlockBag(startOfBag);
    return array;
}

static jobjectArray android_content_AssetManager_getArrayStringResource(JNIEnv* env, jobject clazz,
                                                                        jint arrayResId)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    const ResTable& res(am->getResources());

    jclass cls = env->FindClass("java/lang/String");
    LOG_FATAL_IF(cls == NULL, "No string class?!?");
    if (cls == NULL) {
        return NULL;
    }

    const ResTable::bag_entry* startOfBag;
    const ssize_t N = res.lockBag(arrayResId, &startOfBag);
    if (N < 0) {
        return NULL;
    }

    jobjectArray array = env->NewObjectArray(N, cls, NULL);
    if (env->ExceptionCheck()) {
        res.unlockBag(startOfBag);
        return NULL;
    }

    Res_value value;
    const ResTable::bag_entry* bag = startOfBag;
    size_t strLen = 0;
    for (size_t i=0; ((ssize_t)i)<N; i++, bag++) {
        value = bag->map.value;
        jstring str = NULL;

        // Take care of resolving the found resource to its final value.
        ssize_t block = res.resolveReference(&value, bag->stringBlock, NULL);
#if THROW_ON_BAD_ID
        if (block == BAD_INDEX) {
            jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
            return array;
        }
#endif
        if (value.dataType == Res_value::TYPE_STRING) {
            const ResStringPool* pool = res.getTableStringBlock(block);
            const char* str8 = pool->string8At(value.data, &strLen);
            if (str8 != NULL) {
                str = env->NewStringUTF(str8);
            } else {
                const char16_t* str16 = pool->stringAt(value.data, &strLen);
                str = env->NewString((jchar*)str16, strLen);
            }

            // If one of our NewString{UTF} calls failed due to memory, an
            // exception will be pending.
            if (env->ExceptionCheck()) {
                res.unlockBag(startOfBag);
                return NULL;
            }

            env->SetObjectArrayElement(array, i, str);

            // str is not NULL at that point, otherwise ExceptionCheck would have been true.
            // If we have a large amount of strings in our array, we might
            // overflow the local reference table of the VM.
            env->DeleteLocalRef(str);
        }
    }
    res.unlockBag(startOfBag);
    return array;
}

static jintArray android_content_AssetManager_getArrayIntResource(JNIEnv* env, jobject clazz,
                                                                        jint arrayResId)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    const ResTable& res(am->getResources());

    const ResTable::bag_entry* startOfBag;
    const ssize_t N = res.lockBag(arrayResId, &startOfBag);
    if (N < 0) {
        return NULL;
    }

    jintArray array = env->NewIntArray(N);
    if (array == NULL) {
        res.unlockBag(startOfBag);
        return NULL;
    }

    Res_value value;
    const ResTable::bag_entry* bag = startOfBag;
    for (size_t i=0; ((ssize_t)i)<N; i++, bag++) {
        value = bag->map.value;

        // Take care of resolving the found resource to its final value.
        ssize_t block = res.resolveReference(&value, bag->stringBlock, NULL);
#if THROW_ON_BAD_ID
        if (block == BAD_INDEX) {
            jniThrowException(env, "java/lang/IllegalStateException", "Bad resource!");
            return array;
        }
#endif
        if (value.dataType >= Res_value::TYPE_FIRST_INT
                && value.dataType <= Res_value::TYPE_LAST_INT) {
            int intVal = value.data;
            env->SetIntArrayRegion(array, i, 1, &intVal);
        }
    }
    res.unlockBag(startOfBag);
    return array;
}

static jint android_content_AssetManager_splitThemePackage(JNIEnv* env, jobject clazz,
		jstring srcFileName, jstring dstFileName, jobjectArray drmProtectedAssetNames)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return -1;
    }

    LOGV("splitThemePackage in %p (Java object %p)\n", am, clazz);

    if (srcFileName == NULL || dstFileName == NULL) {
        jniThrowException(env, "java/lang/NullPointerException", srcFileName == NULL ? "srcFileName" : "dstFileName");
        return -2;
    }

    jsize size = env->GetArrayLength(drmProtectedAssetNames);
    if (size == 0) {
        jniThrowException(env, "java/lang/IllegalArgumentException", "drmProtectedAssetNames");
        return -3;
    }

    const char* srcFileName8 = env->GetStringUTFChars(srcFileName, NULL);
    ZipFile* srcZip = new ZipFile;
    status_t err = srcZip->open(srcFileName8, ZipFile::kOpenReadWrite);
    if (err != NO_ERROR) {
        LOGV("error opening zip file %s\n", srcFileName8);
        delete srcZip;
        env->ReleaseStringUTFChars(srcFileName, srcFileName8);
        return -4;
    }

    const char* dstFileName8 = env->GetStringUTFChars(dstFileName, NULL);
    ZipFile* dstZip = new ZipFile;
    err = dstZip->open(dstFileName8, ZipFile::kOpenReadWrite | ZipFile::kOpenTruncate | ZipFile::kOpenCreate);

    if (err != NO_ERROR) {
        LOGV("error opening zip file %s\n", dstFileName8);
        delete srcZip;
        delete dstZip;
        env->ReleaseStringUTFChars(srcFileName, srcFileName8);
        env->ReleaseStringUTFChars(dstFileName, dstFileName8);
        return -5;
    }

    int result = 0;
    for (int i = 0; i < size; i++) {
        jstring javaString = (jstring)env->GetObjectArrayElement(drmProtectedAssetNames, i);
        const char* drmProtectedAssetFileName8 = env->GetStringUTFChars(javaString, NULL);
        ZipEntry *assetEntry = srcZip->getEntryByName(drmProtectedAssetFileName8);
        if (assetEntry == NULL) {
            result = 1;
            LOGV("Invalid asset entry %s\n", drmProtectedAssetFileName8);
        } else {
            status_t loc_result = dstZip->add(srcZip, assetEntry, 0, NULL);
            if (loc_result != NO_ERROR) {
                LOGV("error copying zip entry %s\n", drmProtectedAssetFileName8);
                result = result | 2;
            } else {
                loc_result = srcZip->remove(assetEntry);
                if (loc_result != NO_ERROR) {
                    LOGV("error removing zip entry %s\n", drmProtectedAssetFileName8);
                    result = result | 4;
                }
            }
        }
        env->ReleaseStringUTFChars(javaString, drmProtectedAssetFileName8);
    }
    srcZip->flush();
    dstZip->flush();

    delete srcZip;
    delete dstZip;
    env->ReleaseStringUTFChars(srcFileName, srcFileName8);
    env->ReleaseStringUTFChars(dstFileName, dstFileName8);

    return (jint)result;
}

static void android_content_AssetManager_init(JNIEnv* env, jobject clazz)
{
    AssetManager* am = new AssetManager();
    if (am == NULL) {
        jniThrowException(env, "java/lang/OutOfMemoryError", "");
        return;
    }

    am->addDefaultAssets();

    LOGV("Created AssetManager %p for Java object %p\n", am, clazz);
    env->SetIntField(clazz, gAssetManagerOffsets.mObject, (jint)am);
}

static void android_content_AssetManager_destroy(JNIEnv* env, jobject clazz)
{
    AssetManager* am = (AssetManager*)
        (env->GetIntField(clazz, gAssetManagerOffsets.mObject));
    LOGV("Destroying AssetManager %p for Java object %p\n", am, clazz);
    if (am != NULL) {
        delete am;
        env->SetIntField(clazz, gAssetManagerOffsets.mObject, 0);
    }
}

static jint android_content_AssetManager_getGlobalAssetCount(JNIEnv* env, jobject clazz)
{
    return Asset::getGlobalCount();
}

static jobject android_content_AssetManager_getAssetAllocations(JNIEnv* env, jobject clazz)
{
    String8 alloc = Asset::getAssetAllocations();
    if (alloc.length() <= 0) {
        return NULL;
    }

    jstring str = env->NewStringUTF(alloc.string());
    return str;
}

static jint android_content_AssetManager_getGlobalAssetManagerCount(JNIEnv* env, jobject clazz)
{
    return AssetManager::getGlobalCount();
}

static jint android_content_AssetManager_getBasePackageCount(JNIEnv* env, jobject clazz)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }

    return am->getResources().getBasePackageCount();
}

static jstring android_content_AssetManager_getBasePackageName(JNIEnv* env, jobject clazz, jint index)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }

    String16 packageName(am->getResources().getBasePackageName(index));
    return env->NewString((const jchar*)packageName.string(), packageName.size());
}

static jint android_content_AssetManager_getBasePackageId(JNIEnv* env, jobject clazz, jint index)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }

    return am->getResources().getBasePackageId(index);
}

static void android_content_AssetManager_addRedirectionsNative(JNIEnv* env, jobject clazz,
            PackageRedirectionMap* resMap)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return;
    }

    am->addRedirections(resMap);
}

static void android_content_AssetManager_clearRedirectionsNative(JNIEnv* env, jobject clazz)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return;
    }

    am->clearRedirections();
}

static jboolean android_content_AssetManager_generateStyleRedirections(JNIEnv* env, jobject clazz,
        PackageRedirectionMap* resMap, jint sourceStyle, jint destStyle)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }

    const ResTable& res(am->getResources());

    res.lock();

    // Load up a bag for the user-supplied theme.
    const ResTable::bag_entry* themeEnt = NULL;
    ssize_t N = res.getBagLocked(destStyle, &themeEnt);
    const ResTable::bag_entry* endThemeEnt = themeEnt + (N >= 0 ? N : 0);

    // ...and a bag for the framework default.
    const ResTable::bag_entry* frameworkEnt = NULL;
    N = res.getBagLocked(sourceStyle, &frameworkEnt);
    const ResTable::bag_entry* endFrameworkEnt = frameworkEnt + (N >= 0 ? N : 0);

    // Add the source => dest style redirection first.
    jboolean ret = JNI_FALSE;
    if (themeEnt < endThemeEnt && frameworkEnt < endFrameworkEnt) {
        resMap->addRedirection(sourceStyle, destStyle);
        ret = JNI_TRUE;
    }

    // Now compare them and infer resource redirections for attributes that
    // remap to different styles.  This works by essentially lining up all the
    // sorted attributes from each theme and detected TYPE_REFERENCE entries
    // that point to different resources.  When we find such a mismatch, we'll
    // create a resource redirection from the original framework resource ID to
    // the one in the theme.  This lets us do things like automatically find
    // redirections for @android:style/Widget.Button by looking at how the
    // theme overrides the android:attr/buttonStyle attribute.
    REDIRECT_NOISY(LOGW("delta between 0x%08x and 0x%08x:\n", sourceStyle, destStyle));
    for (; frameworkEnt < endFrameworkEnt; frameworkEnt++) {
        if (frameworkEnt->map.value.dataType != Res_value::TYPE_REFERENCE) {
            continue;
        }

        uint32_t curIdent = frameworkEnt->map.name.ident;

        // Walk along the theme entry looking for a match.
        while (themeEnt < endThemeEnt && curIdent > themeEnt->map.name.ident) {
            themeEnt++;
        }
        // Match found, compare the references.
        if (themeEnt < endThemeEnt && curIdent == themeEnt->map.name.ident) {
            if (themeEnt->map.value.data != frameworkEnt->map.value.data) {
                uint32_t fromIdent = frameworkEnt->map.value.data;
                uint32_t toIdent = themeEnt->map.value.data;
                REDIRECT_NOISY(LOGW("   generated mapping from 0x%08x => 0x%08x (by attr 0x%08x)\n",
                        fromIdent, toIdent, curIdent));
                resMap->addRedirection(fromIdent, toIdent);
            }
            themeEnt++;
        }

        // Exhausted the theme, bail early.
        if (themeEnt >= endThemeEnt) {
            break;
        }
    }

    res.unlock();

    return ret;
}

static jboolean android_content_AssetManager_detachThemePath(JNIEnv* env, jobject clazz,
            jstring packageName, jint cookie)
{
    if (packageName == NULL) {
        jniThrowException(env, "java/lang/NullPointerException", "packageName");
        return JNI_FALSE;
    }

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }

    const char* name8 = env->GetStringUTFChars(packageName, NULL);
    bool res = am->detachThemePath(String8(name8), (void *)cookie);
    env->ReleaseStringUTFChars(packageName, name8);

    return res;
}

static jint android_content_AssetManager_attachThemePath(
            JNIEnv* env, jobject clazz, jstring path)
{
    if (path == NULL) {
        jniThrowException(env, "java/lang/NullPointerException", "path");
        return JNI_FALSE;
    }

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }

    const char* path8 = env->GetStringUTFChars(path, NULL);

    void* cookie;
    bool res = am->attachThemePath(String8(path8), &cookie);

    env->ReleaseStringUTFChars(path, path8);

    return (res) ? (jint)cookie : 0;
}

// ----------------------------------------------------------------------------

/*
 * JNI registration.
 */
static JNINativeMethod gAssetManagerMethods[] = {
    /* name, signature, funcPtr */

    // Basic asset stuff.
    { "openAsset",      "(Ljava/lang/String;I)I",
        (void*) android_content_AssetManager_openAsset },
    { "openAssetFd",      "(Ljava/lang/String;[J)Landroid/os/ParcelFileDescriptor;",
        (void*) android_content_AssetManager_openAssetFd },
    { "openNonAssetNative", "(ILjava/lang/String;I)I",
        (void*) android_content_AssetManager_openNonAssetNative },
    { "openNonAssetFdNative", "(ILjava/lang/String;[J)Landroid/os/ParcelFileDescriptor;",
        (void*) android_content_AssetManager_openNonAssetFdNative },
    { "list",           "(Ljava/lang/String;)[Ljava/lang/String;",
        (void*) android_content_AssetManager_list },
    { "destroyAsset",   "(I)V",
        (void*) android_content_AssetManager_destroyAsset },
    { "readAssetChar",  "(I)I",
        (void*) android_content_AssetManager_readAssetChar },
    { "readAsset",      "(I[BII)I",
        (void*) android_content_AssetManager_readAsset },
    { "seekAsset",      "(IJI)J",
        (void*) android_content_AssetManager_seekAsset },
    { "getAssetLength", "(I)J",
        (void*) android_content_AssetManager_getAssetLength },
    { "getAssetRemainingLength", "(I)J",
        (void*) android_content_AssetManager_getAssetRemainingLength },
    { "addAssetPath",   "(Ljava/lang/String;)I",
        (void*) android_content_AssetManager_addAssetPath },
    { "isUpToDate",     "()Z",
        (void*) android_content_AssetManager_isUpToDate },

    // Resources.
    { "setLocale",      "(Ljava/lang/String;)V",
        (void*) android_content_AssetManager_setLocale },
    { "getLocales",      "()[Ljava/lang/String;",
        (void*) android_content_AssetManager_getLocales },
    { "setConfiguration", "(IILjava/lang/String;IIIIIIIIIIIIII)V",
        (void*) android_content_AssetManager_setConfiguration },
    { "getResourceIdentifier","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I",
        (void*) android_content_AssetManager_getResourceIdentifier },
    { "getResourceName","(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getResourceName },
    { "getResourcePackageName","(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getResourcePackageName },
    { "getResourceTypeName","(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getResourceTypeName },
    { "getResourceEntryName","(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getResourceEntryName },
    { "loadResourceValue","(ISLandroid/util/TypedValue;Z)I",
        (void*) android_content_AssetManager_loadResourceValue },
    { "loadResourceBagValue","(IILandroid/util/TypedValue;Z)I",
        (void*) android_content_AssetManager_loadResourceBagValue },
    { "getStringBlockCount","()I",
        (void*) android_content_AssetManager_getStringBlockCount },
    { "getNativeStringBlock","(I)I",
        (void*) android_content_AssetManager_getNativeStringBlock },
    { "getCookieName","(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getCookieName },

    // Themes.
    { "newTheme", "()I",
        (void*) android_content_AssetManager_newTheme },
    { "deleteTheme", "(I)V",
        (void*) android_content_AssetManager_deleteTheme },
    { "applyThemeStyle", "(IIZ)V",
        (void*) android_content_AssetManager_applyThemeStyle },
    { "copyTheme", "(II)V",
        (void*) android_content_AssetManager_copyTheme },
    { "loadThemeAttributeValue", "(IILandroid/util/TypedValue;Z)I",
        (void*) android_content_AssetManager_loadThemeAttributeValue },
    { "dumpTheme", "(IILjava/lang/String;Ljava/lang/String;)V",
        (void*) android_content_AssetManager_dumpTheme },
    { "applyStyle","(IIII[I[I[I)Z",
        (void*) android_content_AssetManager_applyStyle },
    { "retrieveAttributes","(I[I[I[I)Z",
        (void*) android_content_AssetManager_retrieveAttributes },
    { "getArraySize","(I)I",
        (void*) android_content_AssetManager_getArraySize },
    { "retrieveArray","(I[I)I",
        (void*) android_content_AssetManager_retrieveArray },

    // XML files.
    { "openXmlAssetNative", "(ILjava/lang/String;)I",
        (void*) android_content_AssetManager_openXmlAssetNative },

    // Arrays.
    { "getArrayStringResource","(I)[Ljava/lang/String;",
        (void*) android_content_AssetManager_getArrayStringResource },
    { "getArrayStringInfo","(I)[I",
        (void*) android_content_AssetManager_getArrayStringInfo },
    { "getArrayIntResource","(I)[I",
        (void*) android_content_AssetManager_getArrayIntResource },

    // Bookkeeping.
    { "init",           "()V",
        (void*) android_content_AssetManager_init },
    { "destroy",        "()V",
        (void*) android_content_AssetManager_destroy },
    { "getGlobalAssetCount", "()I",
        (void*) android_content_AssetManager_getGlobalAssetCount },
    { "getAssetAllocations", "()Ljava/lang/String;",
        (void*) android_content_AssetManager_getAssetAllocations },
    { "getGlobalAssetManagerCount", "()I",
        (void*) android_content_AssetManager_getGlobalAssetCount },

    // Split theme package apk into two.
    { "splitThemePackage","(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)I",
        (void*) android_content_AssetManager_splitThemePackage },

    // Dynamic theme package support.
    { "detachThemePath", "(Ljava/lang/String;I)Z",
        (void*) android_content_AssetManager_detachThemePath },
    { "attachThemePath",   "(Ljava/lang/String;)I",
        (void*) android_content_AssetManager_attachThemePath },
    { "getBasePackageCount", "()I",
        (void*) android_content_AssetManager_getBasePackageCount },
    { "getBasePackageName", "(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getBasePackageName },
    { "getBasePackageId", "(I)I",
        (void*) android_content_AssetManager_getBasePackageId },
    { "addRedirectionsNative", "(I)V",
        (void*) android_content_AssetManager_addRedirectionsNative },
    { "clearRedirectionsNative", "()V",
        (void*) android_content_AssetManager_clearRedirectionsNative },
    { "generateStyleRedirections", "(III)Z",
        (void*) android_content_AssetManager_generateStyleRedirections },
};

int register_android_content_AssetManager(JNIEnv* env)
{
    jclass typedValue = env->FindClass("android/util/TypedValue");
    LOG_FATAL_IF(typedValue == NULL, "Unable to find class android/util/TypedValue");
    gTypedValueOffsets.mType
        = env->GetFieldID(typedValue, "type", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mType == NULL, "Unable to find TypedValue.type");
    gTypedValueOffsets.mData
        = env->GetFieldID(typedValue, "data", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mData == NULL, "Unable to find TypedValue.data");
    gTypedValueOffsets.mString
        = env->GetFieldID(typedValue, "string", "Ljava/lang/CharSequence;");
    LOG_FATAL_IF(gTypedValueOffsets.mString == NULL, "Unable to find TypedValue.string");
    gTypedValueOffsets.mAssetCookie
        = env->GetFieldID(typedValue, "assetCookie", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mAssetCookie == NULL, "Unable to find TypedValue.assetCookie");
    gTypedValueOffsets.mResourceId
        = env->GetFieldID(typedValue, "resourceId", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mResourceId == NULL, "Unable to find TypedValue.resourceId");
    gTypedValueOffsets.mChangingConfigurations
        = env->GetFieldID(typedValue, "changingConfigurations", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mChangingConfigurations == NULL, "Unable to find TypedValue.changingConfigurations");
    gTypedValueOffsets.mDensity = env->GetFieldID(typedValue, "density", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mDensity == NULL, "Unable to find TypedValue.density");

    jclass assetFd = env->FindClass("android/content/res/AssetFileDescriptor");
    LOG_FATAL_IF(assetFd == NULL, "Unable to find class android/content/res/AssetFileDescriptor");
    gAssetFileDescriptorOffsets.mFd
        = env->GetFieldID(assetFd, "mFd", "Landroid/os/ParcelFileDescriptor;");
    LOG_FATAL_IF(gAssetFileDescriptorOffsets.mFd == NULL, "Unable to find AssetFileDescriptor.mFd");
    gAssetFileDescriptorOffsets.mStartOffset
        = env->GetFieldID(assetFd, "mStartOffset", "J");
    LOG_FATAL_IF(gAssetFileDescriptorOffsets.mStartOffset == NULL, "Unable to find AssetFileDescriptor.mStartOffset");
    gAssetFileDescriptorOffsets.mLength
        = env->GetFieldID(assetFd, "mLength", "J");
    LOG_FATAL_IF(gAssetFileDescriptorOffsets.mLength == NULL, "Unable to find AssetFileDescriptor.mLength");

    jclass assetManager = env->FindClass("android/content/res/AssetManager");
    LOG_FATAL_IF(assetManager == NULL, "Unable to find class android/content/res/AssetManager");
    gAssetManagerOffsets.mObject
        = env->GetFieldID(assetManager, "mObject", "I");
    LOG_FATAL_IF(gAssetManagerOffsets.mObject == NULL, "Unable to find AssetManager.mObject");

    jclass stringClass = env->FindClass("java/lang/String");
    LOG_FATAL_IF(stringClass == NULL, "Unable to find class java/lang/String");
    g_stringClass = (jclass)env->NewGlobalRef(stringClass);

    return AndroidRuntime::registerNativeMethods(env,
            "android/content/res/AssetManager", gAssetManagerMethods, NELEM(gAssetManagerMethods));
}

}; // namespace android
