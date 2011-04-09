/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "InputChannel-JNI"

#include "JNIHelp.h"

#include <android_runtime/AndroidRuntime.h>
#include <binder/Parcel.h>
#include <utils/Log.h>
#include <ui/InputTransport.h>
#include "android_view_InputChannel.h"
#include "android_util_Binder.h"

namespace android {

// ----------------------------------------------------------------------------

static struct {
    jclass clazz;

    jfieldID mPtr;   // native object attached to the DVM InputChannel
    jmethodID ctor;
} gInputChannelClassInfo;

// ----------------------------------------------------------------------------

class NativeInputChannel {
public:
    NativeInputChannel(const sp<InputChannel>& inputChannel);
    ~NativeInputChannel();

    inline sp<InputChannel> getInputChannel() { return mInputChannel; }

    void setDisposeCallback(InputChannelObjDisposeCallback callback, void* data);
    void invokeAndRemoveDisposeCallback(JNIEnv* env, jobject obj);

private:
    sp<InputChannel> mInputChannel;
    InputChannelObjDisposeCallback mDisposeCallback;
    void* mDisposeData;
};

// ----------------------------------------------------------------------------

NativeInputChannel::NativeInputChannel(const sp<InputChannel>& inputChannel) :
    mInputChannel(inputChannel), mDisposeCallback(NULL) {
}

NativeInputChannel::~NativeInputChannel() {
}

void NativeInputChannel::setDisposeCallback(InputChannelObjDisposeCallback callback, void* data) {
    mDisposeCallback = callback;
    mDisposeData = data;
}

void NativeInputChannel::invokeAndRemoveDisposeCallback(JNIEnv* env, jobject obj) {
    if (mDisposeCallback) {
        mDisposeCallback(env, obj, mInputChannel, mDisposeData);
        mDisposeCallback = NULL;
        mDisposeData = NULL;
    }
}

// ----------------------------------------------------------------------------

static NativeInputChannel* android_view_InputChannel_getNativeInputChannel(JNIEnv* env,
        jobject inputChannelObj) {
    jint intPtr = env->GetIntField(inputChannelObj, gInputChannelClassInfo.mPtr);
    return reinterpret_cast<NativeInputChannel*>(intPtr);
}

static void android_view_InputChannel_setNativeInputChannel(JNIEnv* env, jobject inputChannelObj,
        NativeInputChannel* nativeInputChannel) {
    env->SetIntField(inputChannelObj, gInputChannelClassInfo.mPtr,
             reinterpret_cast<jint>(nativeInputChannel));
}

sp<InputChannel> android_view_InputChannel_getInputChannel(JNIEnv* env, jobject inputChannelObj) {
    NativeInputChannel* nativeInputChannel =
            android_view_InputChannel_getNativeInputChannel(env, inputChannelObj);
    return nativeInputChannel != NULL ? nativeInputChannel->getInputChannel() : NULL;
}

void android_view_InputChannel_setDisposeCallback(JNIEnv* env, jobject inputChannelObj,
        InputChannelObjDisposeCallback callback, void* data) {
    NativeInputChannel* nativeInputChannel =
            android_view_InputChannel_getNativeInputChannel(env, inputChannelObj);
    if (nativeInputChannel == NULL) {
        LOGW("Cannot set dispose callback because input channel object has not been initialized.");
    } else {
        nativeInputChannel->setDisposeCallback(callback, data);
    }
}

static jobject android_view_InputChannel_createInputChannel(JNIEnv* env,
        NativeInputChannel* nativeInputChannel) {
    jobject inputChannelObj = env->NewObject(gInputChannelClassInfo.clazz,
            gInputChannelClassInfo.ctor);
    android_view_InputChannel_setNativeInputChannel(env, inputChannelObj, nativeInputChannel);
    return inputChannelObj;
}

static jobjectArray android_view_InputChannel_nativeOpenInputChannelPair(JNIEnv* env,
        jclass clazz, jstring nameObj) {
    const char* nameChars = env->GetStringUTFChars(nameObj, NULL);
    String8 name(nameChars);
    env->ReleaseStringUTFChars(nameObj, nameChars);

    sp<InputChannel> serverChannel;
    sp<InputChannel> clientChannel;
    status_t result = InputChannel::openInputChannelPair(name, serverChannel, clientChannel);

    if (result) {
        LOGE("Could not open input channel pair.  status=%d", result);
        jniThrowRuntimeException(env, "Could not open input channel pair.");
        return NULL;
    }

    // TODO more robust error checking
    jobject serverChannelObj = android_view_InputChannel_createInputChannel(env,
            new NativeInputChannel(serverChannel));
    jobject clientChannelObj = android_view_InputChannel_createInputChannel(env,
            new NativeInputChannel(clientChannel));

    jobjectArray channelPair = env->NewObjectArray(2, gInputChannelClassInfo.clazz, NULL);
    env->SetObjectArrayElement(channelPair, 0, serverChannelObj);
    env->SetObjectArrayElement(channelPair, 1, clientChannelObj);
    return channelPair;
}

static void android_view_InputChannel_nativeDispose(JNIEnv* env, jobject obj, jboolean finalized) {
    NativeInputChannel* nativeInputChannel =
            android_view_InputChannel_getNativeInputChannel(env, obj);
    if (nativeInputChannel) {
        if (finalized) {
            LOGW("Input channel object '%s' was finalized without being disposed!",
                    nativeInputChannel->getInputChannel()->getName().string());
        }

        nativeInputChannel->invokeAndRemoveDisposeCallback(env, obj);

        android_view_InputChannel_setNativeInputChannel(env, obj, NULL);
        delete nativeInputChannel;
    }
}

static void android_view_InputChannel_nativeTransferTo(JNIEnv* env, jobject obj,
        jobject otherObj) {
    if (android_view_InputChannel_getInputChannel(env, otherObj) != NULL) {
        jniThrowException(env, "java/lang/IllegalStateException",
                "Other object already has a native input channel.");
        return;
    }

    NativeInputChannel* nativeInputChannel =
            android_view_InputChannel_getNativeInputChannel(env, obj);
    android_view_InputChannel_setNativeInputChannel(env, otherObj, nativeInputChannel);
    android_view_InputChannel_setNativeInputChannel(env, obj, NULL);
}

static void android_view_InputChannel_nativeReadFromParcel(JNIEnv* env, jobject obj,
        jobject parcelObj) {
    if (android_view_InputChannel_getInputChannel(env, obj) != NULL) {
        jniThrowException(env, "java/lang/IllegalStateException",
                "This object already has a native input channel.");
        return;
    }

    Parcel* parcel = parcelForJavaObject(env, parcelObj);
    if (parcel) {
        bool isInitialized = parcel->readInt32();
        if (isInitialized) {
            String8 name = parcel->readString8();
            int32_t ashmemFd = dup(parcel->readFileDescriptor());
            int32_t receivePipeFd = dup(parcel->readFileDescriptor());
            int32_t sendPipeFd = dup(parcel->readFileDescriptor());
            if (ashmemFd < 0 || receivePipeFd < 0 || sendPipeFd < 0) {
                if (ashmemFd >= 0) ::close(ashmemFd);
                if (receivePipeFd >= 0) ::close(receivePipeFd);
                if (sendPipeFd >= 0) ::close(sendPipeFd);
                jniThrowRuntimeException(env,
                        "Could not read input channel file descriptors from parcel.");
                return;
            }

            InputChannel* inputChannel = new InputChannel(name, ashmemFd,
                    receivePipeFd, sendPipeFd);
            NativeInputChannel* nativeInputChannel = new NativeInputChannel(inputChannel);

            android_view_InputChannel_setNativeInputChannel(env, obj, nativeInputChannel);
        }
    }
}

static void android_view_InputChannel_nativeWriteToParcel(JNIEnv* env, jobject obj,
        jobject parcelObj) {
    Parcel* parcel = parcelForJavaObject(env, parcelObj);
    if (parcel) {
        NativeInputChannel* nativeInputChannel =
                android_view_InputChannel_getNativeInputChannel(env, obj);
        if (nativeInputChannel) {
            sp<InputChannel> inputChannel = nativeInputChannel->getInputChannel();

            parcel->writeInt32(1);
            parcel->writeString8(inputChannel->getName());
            parcel->writeDupFileDescriptor(inputChannel->getAshmemFd());
            parcel->writeDupFileDescriptor(inputChannel->getReceivePipeFd());
            parcel->writeDupFileDescriptor(inputChannel->getSendPipeFd());
        } else {
            parcel->writeInt32(0);
        }
    }
}

static jstring android_view_InputChannel_nativeGetName(JNIEnv* env, jobject obj) {
    NativeInputChannel* nativeInputChannel =
            android_view_InputChannel_getNativeInputChannel(env, obj);
    if (! nativeInputChannel) {
        return NULL;
    }

    jstring name = env->NewStringUTF(nativeInputChannel->getInputChannel()->getName().string());
    return name;
}

// ----------------------------------------------------------------------------

static JNINativeMethod gInputChannelMethods[] = {
    /* name, signature, funcPtr */
    { "nativeOpenInputChannelPair", "(Ljava/lang/String;)[Landroid/view/InputChannel;",
            (void*)android_view_InputChannel_nativeOpenInputChannelPair },
    { "nativeDispose", "(Z)V",
            (void*)android_view_InputChannel_nativeDispose },
    { "nativeTransferTo", "(Landroid/view/InputChannel;)V",
            (void*)android_view_InputChannel_nativeTransferTo },
    { "nativeReadFromParcel", "(Landroid/os/Parcel;)V",
            (void*)android_view_InputChannel_nativeReadFromParcel },
    { "nativeWriteToParcel", "(Landroid/os/Parcel;)V",
            (void*)android_view_InputChannel_nativeWriteToParcel },
    { "nativeGetName", "()Ljava/lang/String;",
            (void*)android_view_InputChannel_nativeGetName },
};

#define FIND_CLASS(var, className) \
        var = env->FindClass(className); \
        LOG_FATAL_IF(! var, "Unable to find class " className); \
        var = jclass(env->NewGlobalRef(var));

#define GET_METHOD_ID(var, clazz, methodName, methodDescriptor) \
        var = env->GetMethodID(clazz, methodName, methodDescriptor); \
        LOG_FATAL_IF(! var, "Unable to find method " methodName);

#define GET_FIELD_ID(var, clazz, fieldName, fieldDescriptor) \
        var = env->GetFieldID(clazz, fieldName, fieldDescriptor); \
        LOG_FATAL_IF(! var, "Unable to find field " fieldName);

int register_android_view_InputChannel(JNIEnv* env) {
    int res = jniRegisterNativeMethods(env, "android/view/InputChannel",
            gInputChannelMethods, NELEM(gInputChannelMethods));
    LOG_FATAL_IF(res < 0, "Unable to register native methods.");

    FIND_CLASS(gInputChannelClassInfo.clazz, "android/view/InputChannel");

    GET_FIELD_ID(gInputChannelClassInfo.mPtr, gInputChannelClassInfo.clazz,
            "mPtr", "I");

    GET_METHOD_ID(gInputChannelClassInfo.ctor, gInputChannelClassInfo.clazz,
            "<init>", "()V");

    return 0;
}

} // namespace android
