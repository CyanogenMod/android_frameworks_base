/*
**
** Copyright 2012, ParanoidAndroid Project
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

#include <android/log.h>
#include <android_runtime/AndroidRuntime.h>
#include <jni.h>
#include <JNIHelp.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <utils/misc.h>
#include <wctype.h>

namespace android {

/*
 *  In class android.util.ExtendedPropertiesUtils:
 *  public static native String readFile(String msg)
 */
static jstring android_util_ExtendedPropertiesUtils_readFile(JNIEnv* env, jobject clazz, jstring msgObj)
{
    const char* msgString = env->GetStringUTFChars(msgObj, NULL);
    FILE* file = fopen(msgString, "r");
    if(file == NULL)
        return NULL;

    fseek(file, 0, SEEK_END);
    long int size = ftell(file);
    rewind(file);

    char* content = (char*) calloc(size + 1, 1);

    fread(content,1,size,file);

    return env->NewStringUTF(content);
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "readFile",      "(Ljava/lang/String;)Ljava/lang/String;", (void*) android_util_ExtendedPropertiesUtils_readFile },
};

int register_android_util_ExtendedPropertiesUtils(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/util/ExtendedPropertiesUtils");

    if (clazz == NULL) {
        return -1;
    }

    return AndroidRuntime::registerNativeMethods(env, "android/util/ExtendedPropertiesUtils", gMethods, NELEM(gMethods));
}

}; // namespace android
