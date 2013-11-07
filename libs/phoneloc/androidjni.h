/* Copyright (C) 2013 The MoKee Open Source Project
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

#ifndef __ANDROID_JNI_H__
#define __ANDROID_JNI_H__

/*
 * Global header for android jni call
 *
 * Created by cytown martincz
 * Last edited 2013.3.24
 * 
 */

/**
 * This define the reg class for jni call
 */
#define JNIREG_CLASS "android/mokee/location/PhoneLocation"

JNIEXPORT jstring JNICALL
getPhoneLocationJni( JNIEnv* env, jclass thiz, jstring phone );

/**
 * Table of methods associated with a single class.
 */
static JNINativeMethod gMethods[] = {
    { "getPhoneLocationJni", "(Ljava/lang/String;)Ljava/lang/String;",
            (void*) getPhoneLocationJni },
    /* <<----Functions for sync end--------------------------------- */
};
#endif
