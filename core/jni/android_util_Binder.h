/* //device/libs/android_runtime/android_util_Binder.h
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

#include <binder/IBinder.h>

#include "jni.h"

namespace android {

// Converstion to/from Java IBinder Object and C++ IBinder instance.
extern jobject javaObjectForIBinder(JNIEnv* env, const sp<IBinder>& val);
extern sp<IBinder> ibinderForJavaObject(JNIEnv* env, jobject obj);

// Conversion from Java Parcel Object to C++ Parcel instance.
// Note: does not type checking; must guarantee jobject is a Java Parcel
extern Parcel* parcelForJavaObject(JNIEnv* env, jobject obj);

extern jobject newFileDescriptor(JNIEnv* env, int fd);
extern jobject newParcelFileDescriptor(JNIEnv* env, jobject fileDesc);

}
