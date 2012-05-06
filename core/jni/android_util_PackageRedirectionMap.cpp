/*
 * Copyright (C) 2011, T-Mobile USA, Inc.
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

#include <utils/PackageRedirectionMap.h>

#include "jni.h"
#include "JNIHelp.h"
#include <utils/misc.h>
#include <android_runtime/AndroidRuntime.h>

#include "android_util_Binder.h"
#include <binder/Parcel.h>

#include <utils/ResourceTypes.h>

#include <stdio.h>

namespace android {

// ----------------------------------------------------------------------------

static PackageRedirectionMap* PackageRedirectionMap_constructor(JNIEnv* env, jobject clazz)
{
    return new PackageRedirectionMap;
}

static void PackageRedirectionMap_destructor(JNIEnv* env, jobject clazz,
        PackageRedirectionMap* resMap)
{
    delete resMap;
}

static PackageRedirectionMap* PackageRedirectionMap_createFromParcel(JNIEnv* env, jobject clazz,
        jobject parcel)
{
    if (parcel == NULL) {
        return NULL;
    }

    Parcel* p = parcelForJavaObject(env, parcel);
    PackageRedirectionMap* resMap = new PackageRedirectionMap;

    int32_t entryCount = p->readInt32();
    while (entryCount-- > 0) {
        uint32_t fromIdent = (uint32_t)p->readInt32();
        uint32_t toIdent = (uint32_t)p->readInt32();
        resMap->addRedirection(fromIdent, toIdent);
    }

    return resMap;
}

static jboolean PackageRedirectionMap_writeToParcel(JNIEnv* env, jobject clazz,
        PackageRedirectionMap* resMap, jobject parcel)
{
    if (parcel == NULL) {
        return JNI_FALSE;
    }

    Parcel* p = parcelForJavaObject(env, parcel);

    int package = resMap->getPackage();
    size_t nTypes = resMap->getNumberOfTypes();
    size_t entryCount = 0;
    for (size_t type=0; type<nTypes; type++) {
        entryCount += resMap->getNumberOfUsedEntries(type);
    }
    p->writeInt32(entryCount);
    for (size_t type=0; type<nTypes; type++) {
        size_t nEntries = resMap->getNumberOfEntries(type);
        for (size_t entry=0; entry<nEntries; entry++) {
            uint32_t toIdent = resMap->getEntry(type, entry);
            if (toIdent != 0) {
                uint32_t fromIdent = Res_MAKEID(package-1, type, entry);
                p->writeInt32(fromIdent);
                p->writeInt32(toIdent);
            }
        }
    }

    return JNI_TRUE;
}

static void PackageRedirectionMap_addRedirection(JNIEnv* env, jobject clazz,
        PackageRedirectionMap* resMap, jint fromIdent, jint toIdent)
{
    resMap->addRedirection(fromIdent, toIdent);
}

static jint PackageRedirectionMap_getPackageId(JNIEnv* env, jobject clazz,
        PackageRedirectionMap* resMap)
{
    return resMap->getPackage();
}

static jint PackageRedirectionMap_lookupRedirection(JNIEnv* env, jobject clazz,
        PackageRedirectionMap* resMap, jint fromIdent)
{
    return resMap->lookupRedirection(fromIdent);
}

static jintArray PackageRedirectionMap_getRedirectionKeys(JNIEnv* env, jobject clazz,
        PackageRedirectionMap* resMap)
{
    int package = resMap->getPackage();
    size_t nTypes = resMap->getNumberOfTypes();
    size_t entryCount = 0;
    for (size_t type=0; type<nTypes; type++) {
        size_t usedEntries = resMap->getNumberOfUsedEntries(type);
        entryCount += usedEntries;
    }
    jintArray array = env->NewIntArray(entryCount);
    if (array == NULL) {
        jniThrowException(env, "java/lang/OutOfMemoryError", "");
        return NULL;
    }
    jsize index = 0;
    for (size_t type=0; type<nTypes; type++) {
        size_t nEntries = resMap->getNumberOfEntries(type);
        for (size_t entry=0; entry<nEntries; entry++) {
            uint32_t toIdent = resMap->getEntry(type, entry);
            if (toIdent != 0) {
                jint fromIdent = (jint)Res_MAKEID(package-1, type, entry);
                env->SetIntArrayRegion(array, index++, 1, &fromIdent);
            }
        }
    }
    return array;
}

// ----------------------------------------------------------------------------

/*
 * JNI registration.
 */
static JNINativeMethod gPackageRedirectionMapMethods[] = {
    { "nativeConstructor",      "()I",
        (void*) PackageRedirectionMap_constructor },
    { "nativeDestructor",       "(I)V",
        (void*) PackageRedirectionMap_destructor },
    { "nativeCreateFromParcel", "(Landroid/os/Parcel;)I",
        (void*) PackageRedirectionMap_createFromParcel },
    { "nativeWriteToParcel", "(ILandroid/os/Parcel;)Z",
        (void*) PackageRedirectionMap_writeToParcel },
    { "nativeAddRedirection", "(III)V",
        (void*) PackageRedirectionMap_addRedirection },
    { "nativeGetPackageId", "(I)I",
        (void*) PackageRedirectionMap_getPackageId },
    { "nativeLookupRedirection", "(II)I",
        (void*) PackageRedirectionMap_lookupRedirection },
    { "nativeGetRedirectionKeys", "(I)[I",
        (void*) PackageRedirectionMap_getRedirectionKeys },
};

int register_android_content_res_PackageRedirectionMap(JNIEnv* env)
{
    return AndroidRuntime::registerNativeMethods(env,
            "android/content/res/PackageRedirectionMap",
            gPackageRedirectionMapMethods,
            NELEM(gPackageRedirectionMapMethods));
}

}; // namespace android
