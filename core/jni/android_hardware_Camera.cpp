/*
**
** Copyright 2008, The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "Camera-JNI"
#include <utils/Log.h>

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <utils/Vector.h>

#include <surfaceflinger/Surface.h>
#include <camera/Camera.h>
#include <binder/IMemory.h>

using namespace android;

struct fields_t {
    jfieldID    context;
    jfieldID    surface;
    jfieldID    facing;
    jfieldID    orientation;
    jmethodID   post_event;
};

static fields_t fields;
static Mutex sLock;

// provides persistent context for calls from native code to Java
class JNICameraContext: public CameraListener
{
public:
    JNICameraContext(JNIEnv* env, jobject weak_this, jclass clazz, const sp<Camera>& camera);
    ~JNICameraContext() { release(); }
    virtual void notify(int32_t msgType, int32_t ext1, int32_t ext2);
    virtual void postData(int32_t msgType, const sp<IMemory>& dataPtr);
#ifdef OMAP_ENHANCEMENT
    virtual void postDataTimestamp(nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr, uint32_t offset=0, uint32_t stride=0);
#else
    virtual void postDataTimestamp(nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr);
#endif
    void addCallbackBuffer(JNIEnv *env, jbyteArray cbb);
    void setCallbackMode(JNIEnv *env, bool installed, bool manualMode);
    sp<Camera> getCamera() { Mutex::Autolock _l(mLock); return mCamera; }
    void release();

private:
    void copyAndPost(JNIEnv* env, const sp<IMemory>& dataPtr, int msgType);
    void clearCallbackBuffers_l(JNIEnv *env);

    jobject     mCameraJObjectWeak;     // weak reference to java object
    jclass      mCameraJClass;          // strong reference to java class
    sp<Camera>  mCamera;                // strong reference to native object
    Mutex       mLock;

    Vector<jbyteArray> mCallbackBuffers; // Global reference application managed byte[]
    bool mManualBufferMode;              // Whether to use application managed buffers.
    bool mManualCameraCallbackSet;       // Whether the callback has been set, used to reduce unnecessary calls to set the callback.
};

sp<Camera> get_native_camera(JNIEnv *env, jobject thiz, JNICameraContext** pContext)
{
    sp<Camera> camera;
    Mutex::Autolock _l(sLock);
    JNICameraContext* context = reinterpret_cast<JNICameraContext*>(env->GetIntField(thiz, fields.context));
    if (context != NULL) {
        camera = context->getCamera();
    }
    LOGV("get_native_camera: context=%p, camera=%p", context, camera.get());
    if (camera == 0) {
        jniThrowException(env, "java/lang/RuntimeException", "Method called after release()");
    }

    if (pContext != NULL) *pContext = context;
    return camera;
}

JNICameraContext::JNICameraContext(JNIEnv* env, jobject weak_this, jclass clazz, const sp<Camera>& camera)
{
    mCameraJObjectWeak = env->NewGlobalRef(weak_this);
    mCameraJClass = (jclass)env->NewGlobalRef(clazz);
    mCamera = camera;

    mManualBufferMode = false;
    mManualCameraCallbackSet = false;
}

void JNICameraContext::release()
{
    LOGV("release");
    Mutex::Autolock _l(mLock);
    JNIEnv *env = AndroidRuntime::getJNIEnv();

    if (mCameraJObjectWeak != NULL) {
        env->DeleteGlobalRef(mCameraJObjectWeak);
        mCameraJObjectWeak = NULL;
    }
    if (mCameraJClass != NULL) {
        env->DeleteGlobalRef(mCameraJClass);
        mCameraJClass = NULL;
    }
    clearCallbackBuffers_l(env);
    mCamera.clear();
}

void JNICameraContext::notify(int32_t msgType, int32_t ext1, int32_t ext2)
{
    LOGV("notify");

    // VM pointer will be NULL if object is released
    Mutex::Autolock _l(mLock);
    if (mCameraJObjectWeak == NULL) {
        LOGW("callback on dead camera object");
        return;
    }
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    env->CallStaticVoidMethod(mCameraJClass, fields.post_event,
            mCameraJObjectWeak, msgType, ext1, ext2, NULL);
}

void JNICameraContext::copyAndPost(JNIEnv* env, const sp<IMemory>& dataPtr, int msgType)
{
    jbyteArray obj = NULL;

    // allocate Java byte array and copy data
    if (dataPtr != NULL) {
        ssize_t offset;
        size_t size;
        sp<IMemoryHeap> heap = dataPtr->getMemory(&offset, &size);
        LOGV("postData: off=%d, size=%d", offset, size);
        uint8_t *heapBase = (uint8_t*)heap->base();

        if (heapBase != NULL) {
            const jbyte* data = reinterpret_cast<const jbyte*>(heapBase + offset);

            if (!mManualBufferMode) {
                LOGV("Allocating callback buffer");
                obj = env->NewByteArray(size);
            } else {
                // Vector access should be protected by lock in postData()
                if(!mCallbackBuffers.isEmpty()) {
                    LOGV("Using callback buffer from queue of length %d", mCallbackBuffers.size());
                    jbyteArray globalBuffer = mCallbackBuffers.itemAt(0);
                    mCallbackBuffers.removeAt(0);

                    obj = (jbyteArray)env->NewLocalRef(globalBuffer);
                    env->DeleteGlobalRef(globalBuffer);

                    if (obj != NULL) {
                        jsize bufferLength = env->GetArrayLength(obj);
                        if ((int)bufferLength < (int)size) {
                            LOGE("Manually set buffer was too small! Expected %d bytes, but got %d!",
                                 size, bufferLength);
                            env->DeleteLocalRef(obj);
                            return;
                        }
                    }
                }

                if(mCallbackBuffers.isEmpty()) {
                    LOGV("Out of buffers, clearing callback!");
                    mCamera->setPreviewCallbackFlags(FRAME_CALLBACK_FLAG_NOOP);
                    mManualCameraCallbackSet = false;

                    if (obj == NULL) {
                        return;
                    }
                }
            }

            if (obj == NULL) {
                LOGE("Couldn't allocate byte array for JPEG data");
                env->ExceptionClear();
            } else {
                env->SetByteArrayRegion(obj, 0, size, data);
            }
        } else {
            LOGE("image heap is NULL");
        }
    }

    // post image data to Java
    env->CallStaticVoidMethod(mCameraJClass, fields.post_event,
            mCameraJObjectWeak, msgType, 0, 0, obj);
    if (obj) {
        env->DeleteLocalRef(obj);
    }
}

void JNICameraContext::postData(int32_t msgType, const sp<IMemory>& dataPtr)
{
    // VM pointer will be NULL if object is released
    Mutex::Autolock _l(mLock);
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    if (mCameraJObjectWeak == NULL) {
        LOGW("callback on dead camera object");
        return;
    }

    // return data based on callback type
    switch(msgType) {
    case CAMERA_MSG_VIDEO_FRAME:
        // should never happen
        break;
    // don't return raw data to Java
    case CAMERA_MSG_RAW_IMAGE:
        LOGV("rawCallback");
        env->CallStaticVoidMethod(mCameraJClass, fields.post_event,
                mCameraJObjectWeak, msgType, 0, 0, NULL);
        break;
    default:
        // TODO: Change to LOGV
        LOGV("dataCallback(%d, %p)", msgType, dataPtr.get());
        copyAndPost(env, dataPtr, msgType);
        break;
    }
}
#ifdef OMAP_ENHANCEMENT
void JNICameraContext::postDataTimestamp(nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr,
        uint32_t offset, uint32_t stride)
#else
void JNICameraContext::postDataTimestamp(nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr)
#endif
{
    // TODO: plumb up to Java. For now, just drop the timestamp
    postData(msgType, dataPtr);
}

void JNICameraContext::setCallbackMode(JNIEnv *env, bool installed, bool manualMode)
{
    Mutex::Autolock _l(mLock);
    mManualBufferMode = manualMode;
    mManualCameraCallbackSet = false;

    // In order to limit the over usage of binder threads, all non-manual buffer
    // callbacks use FRAME_CALLBACK_FLAG_BARCODE_SCANNER mode now.
    //
    // Continuous callbacks will have the callback re-registered from handleMessage.
    // Manual buffer mode will operate as fast as possible, relying on the finite supply
    // of buffers for throttling.

    if (!installed) {
        mCamera->setPreviewCallbackFlags(FRAME_CALLBACK_FLAG_NOOP);
        clearCallbackBuffers_l(env);
    } else if (mManualBufferMode) {
        if (!mCallbackBuffers.isEmpty()) {
            mCamera->setPreviewCallbackFlags(FRAME_CALLBACK_FLAG_CAMERA);
            mManualCameraCallbackSet = true;
        }
    } else {
        mCamera->setPreviewCallbackFlags(FRAME_CALLBACK_FLAG_BARCODE_SCANNER);
        clearCallbackBuffers_l(env);
    }
}

void JNICameraContext::addCallbackBuffer(JNIEnv *env, jbyteArray cbb)
{
    if (cbb != NULL) {
        Mutex::Autolock _l(mLock);
        jbyteArray callbackBuffer = (jbyteArray)env->NewGlobalRef(cbb);
        mCallbackBuffers.push(cbb);

        LOGV("Adding callback buffer to queue, %d total", mCallbackBuffers.size());

        // We want to make sure the camera knows we're ready for the next frame.
        // This may have come unset had we not had a callbackbuffer ready for it last time.
        if (mManualBufferMode && !mManualCameraCallbackSet) {
            mCamera->setPreviewCallbackFlags(FRAME_CALLBACK_FLAG_CAMERA);
            mManualCameraCallbackSet = true;
        }
    } else {
       LOGE("Null byte array!");
    }
}

void JNICameraContext::clearCallbackBuffers_l(JNIEnv *env)
{
    LOGV("Clearing callback buffers, %d remained", mCallbackBuffers.size());
    while(!mCallbackBuffers.isEmpty()) {
        env->DeleteGlobalRef(mCallbackBuffers.top());
        mCallbackBuffers.pop();
    }
}

static jint android_hardware_Camera_getNumberOfCameras(JNIEnv *env, jobject thiz)
{
    return Camera::getNumberOfCameras();
}

static void android_hardware_Camera_getCameraInfo(JNIEnv *env, jobject thiz,
    jint cameraId, jobject info_obj)
{
    CameraInfo cameraInfo;
    status_t rc = Camera::getCameraInfo(cameraId, &cameraInfo);
    if (rc != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Fail to get camera info");
        return;
    }
    env->SetIntField(info_obj, fields.facing, cameraInfo.facing);
    env->SetIntField(info_obj, fields.orientation, cameraInfo.orientation);
}

// connect to camera service
static void android_hardware_Camera_native_setup(JNIEnv *env, jobject thiz,
    jobject weak_this, jint cameraId)
{
    sp<Camera> camera = Camera::connect(cameraId);

    if (camera == NULL) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Fail to connect to camera service");
        return;
    }

    // make sure camera hardware is alive
    if (camera->getStatus() != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "Camera initialization failed");
        return;
    }

    jclass clazz = env->GetObjectClass(thiz);
    if (clazz == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find android/hardware/Camera");
        return;
    }

    // We use a weak reference so the Camera object can be garbage collected.
    // The reference is only used as a proxy for callbacks.
    sp<JNICameraContext> context = new JNICameraContext(env, weak_this, clazz, camera);
    context->incStrong(thiz);
    camera->setListener(context);

    // save context in opaque field
    env->SetIntField(thiz, fields.context, (int)context.get());
}

// disconnect from camera service
// It's okay to call this when the native camera context is already null.
// This handles the case where the user has called release() and the
// finalizer is invoked later.
static void android_hardware_Camera_release(JNIEnv *env, jobject thiz)
{
    // TODO: Change to LOGV
    LOGV("release camera");
    JNICameraContext* context = NULL;
    sp<Camera> camera;
    {
        Mutex::Autolock _l(sLock);
        context = reinterpret_cast<JNICameraContext*>(env->GetIntField(thiz, fields.context));

        // Make sure we do not attempt to callback on a deleted Java object.
        env->SetIntField(thiz, fields.context, 0);
    }

    // clean up if release has not been called before
    if (context != NULL) {
        camera = context->getCamera();
        context->release();
        LOGV("native_release: context=%p camera=%p", context, camera.get());

        // clear callbacks
        if (camera != NULL) {
            camera->setPreviewCallbackFlags(FRAME_CALLBACK_FLAG_NOOP);
            camera->disconnect();
        }

        // remove context to prevent further Java access
        context->decStrong(thiz);
    }
}

static void android_hardware_Camera_setPreviewDisplay(JNIEnv *env, jobject thiz, jobject jSurface)
{
    LOGV("setPreviewDisplay");
    sp<Camera> camera = get_native_camera(env, thiz, NULL);
    if (camera == 0) return;

    sp<Surface> surface = NULL;
    if (jSurface != NULL) {
        surface = reinterpret_cast<Surface*>(env->GetIntField(jSurface, fields.surface));
    }
    if (camera->setPreviewDisplay(surface) != NO_ERROR) {
        jniThrowException(env, "java/io/IOException", "setPreviewDisplay failed");
    }
}

static void android_hardware_Camera_startPreview(JNIEnv *env, jobject thiz)
{
    LOGV("startPreview");
    sp<Camera> camera = get_native_camera(env, thiz, NULL);
    if (camera == 0) return;

    if (camera->startPreview() != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "startPreview failed");
        return;
    }
}

static void android_hardware_Camera_stopPreview(JNIEnv *env, jobject thiz)
{
    LOGV("stopPreview");
    sp<Camera> c = get_native_camera(env, thiz, NULL);
    if (c == 0) return;

    c->stopPreview();
}

static bool android_hardware_Camera_previewEnabled(JNIEnv *env, jobject thiz)
{
    LOGV("previewEnabled");
    sp<Camera> c = get_native_camera(env, thiz, NULL);
    if (c == 0) return false;

    return c->previewEnabled();
}

static void android_hardware_Camera_setHasPreviewCallback(JNIEnv *env, jobject thiz, jboolean installed, jboolean manualBuffer)
{
    LOGV("setHasPreviewCallback: installed:%d, manualBuffer:%d", (int)installed, (int)manualBuffer);
    // Important: Only install preview_callback if the Java code has called
    // setPreviewCallback() with a non-null value, otherwise we'd pay to memcpy
    // each preview frame for nothing.
    JNICameraContext* context;
    sp<Camera> camera = get_native_camera(env, thiz, &context);
    if (camera == 0) return;

    // setCallbackMode will take care of setting the context flags and calling
    // camera->setPreviewCallbackFlags within a mutex for us.
    context->setCallbackMode(env, installed, manualBuffer);
}

static void android_hardware_Camera_addCallbackBuffer(JNIEnv *env, jobject thiz, jbyteArray bytes) {
    LOGV("addCallbackBuffer");

    JNICameraContext* context = reinterpret_cast<JNICameraContext*>(env->GetIntField(thiz, fields.context));

    if (context != NULL) {
        context->addCallbackBuffer(env, bytes);
    }
}

static void android_hardware_Camera_autoFocus(JNIEnv *env, jobject thiz)
{
    LOGV("autoFocus");
    JNICameraContext* context;
    sp<Camera> c = get_native_camera(env, thiz, &context);
    if (c == 0) return;

    if (c->autoFocus() != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "autoFocus failed");
    }
}

static void android_hardware_Camera_cancelAutoFocus(JNIEnv *env, jobject thiz)
{
    LOGV("cancelAutoFocus");
    JNICameraContext* context;
    sp<Camera> c = get_native_camera(env, thiz, &context);
    if (c == 0) return;

    if (c->cancelAutoFocus() != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "cancelAutoFocus failed");
    }
}

static void android_hardware_Camera_takePicture(JNIEnv *env, jobject thiz)
{
    LOGV("takePicture");
    JNICameraContext* context;
    sp<Camera> camera = get_native_camera(env, thiz, &context);
    if (camera == 0) return;

    if (camera->takePicture() != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "takePicture failed");
        return;
    }
}

static void android_hardware_Camera_setParameters(JNIEnv *env, jobject thiz, jstring params)
{
    LOGV("setParameters");
    sp<Camera> camera = get_native_camera(env, thiz, NULL);
    if (camera == 0) return;

    const jchar* str = env->GetStringCritical(params, 0);
    String8 params8;
    if (params) {
        params8 = String8(str, env->GetStringLength(params));
        env->ReleaseStringCritical(params, str);
    }
    if (camera->setParameters(params8) != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "setParameters failed");
        return;
    }
}

static jstring android_hardware_Camera_getParameters(JNIEnv *env, jobject thiz)
{
    LOGV("getParameters");
    sp<Camera> camera = get_native_camera(env, thiz, NULL);
    if (camera == 0) return 0;

    return env->NewStringUTF(camera->getParameters().string());
}

static void android_hardware_Camera_reconnect(JNIEnv *env, jobject thiz)
{
    LOGV("reconnect");
    sp<Camera> camera = get_native_camera(env, thiz, NULL);
    if (camera == 0) return;

    if (camera->reconnect() != NO_ERROR) {
        jniThrowException(env, "java/io/IOException", "reconnect failed");
        return;
    }
}

static void android_hardware_Camera_lock(JNIEnv *env, jobject thiz)
{
    LOGV("lock");
    sp<Camera> camera = get_native_camera(env, thiz, NULL);
    if (camera == 0) return;

    if (camera->lock() != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "lock failed");
    }
}

static void android_hardware_Camera_unlock(JNIEnv *env, jobject thiz)
{
    LOGV("unlock");
    sp<Camera> camera = get_native_camera(env, thiz, NULL);
    if (camera == 0) return;

    if (camera->unlock() != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "unlock failed");
    }
}

static void android_hardware_Camera_startSmoothZoom(JNIEnv *env, jobject thiz, jint value)
{
    LOGV("startSmoothZoom");
    sp<Camera> camera = get_native_camera(env, thiz, NULL);
    if (camera == 0) return;

    status_t rc = camera->sendCommand(CAMERA_CMD_START_SMOOTH_ZOOM, value, 0);
    if (rc == BAD_VALUE) {
        char msg[64];
        sprintf(msg, "invalid zoom value=%d", value);
        jniThrowException(env, "java/lang/IllegalArgumentException", msg);
    } else if (rc != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "start smooth zoom failed");
    }
}

static void android_hardware_Camera_stopSmoothZoom(JNIEnv *env, jobject thiz)
{
    LOGV("stopSmoothZoom");
    sp<Camera> camera = get_native_camera(env, thiz, NULL);
    if (camera == 0) return;

    if (camera->sendCommand(CAMERA_CMD_STOP_SMOOTH_ZOOM, 0, 0) != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "stop smooth zoom failed");
    }
}

static void android_hardware_Camera_setDisplayOrientation(JNIEnv *env, jobject thiz,
        jint value)
{
    LOGV("setDisplayOrientation");
    sp<Camera> camera = get_native_camera(env, thiz, NULL);
    if (camera == 0) return;

    if (camera->sendCommand(CAMERA_CMD_SET_DISPLAY_ORIENTATION, value, 0) != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "set display orientation failed");
    }
}

//-------------------------------------------------

static JNINativeMethod camMethods[] = {
  { "getNumberOfCameras",
    "()I",
    (void *)android_hardware_Camera_getNumberOfCameras },
  { "getCameraInfo",
    "(ILandroid/hardware/Camera$CameraInfo;)V",
    (void*)android_hardware_Camera_getCameraInfo },
  { "native_setup",
    "(Ljava/lang/Object;I)V",
    (void*)android_hardware_Camera_native_setup },
  { "native_release",
    "()V",
    (void*)android_hardware_Camera_release },
  { "setPreviewDisplay",
    "(Landroid/view/Surface;)V",
    (void *)android_hardware_Camera_setPreviewDisplay },
  { "startPreview",
    "()V",
    (void *)android_hardware_Camera_startPreview },
  { "stopPreview",
    "()V",
    (void *)android_hardware_Camera_stopPreview },
  { "previewEnabled",
    "()Z",
    (void *)android_hardware_Camera_previewEnabled },
  { "setHasPreviewCallback",
    "(ZZ)V",
    (void *)android_hardware_Camera_setHasPreviewCallback },
  { "addCallbackBuffer",
    "([B)V",
    (void *)android_hardware_Camera_addCallbackBuffer },
  { "native_autoFocus",
    "()V",
    (void *)android_hardware_Camera_autoFocus },
  { "native_cancelAutoFocus",
    "()V",
    (void *)android_hardware_Camera_cancelAutoFocus },
  { "native_takePicture",
    "()V",
    (void *)android_hardware_Camera_takePicture },
  { "native_setParameters",
    "(Ljava/lang/String;)V",
    (void *)android_hardware_Camera_setParameters },
  { "native_getParameters",
    "()Ljava/lang/String;",
    (void *)android_hardware_Camera_getParameters },
  { "reconnect",
    "()V",
    (void*)android_hardware_Camera_reconnect },
  { "lock",
    "()V",
    (void*)android_hardware_Camera_lock },
  { "unlock",
    "()V",
    (void*)android_hardware_Camera_unlock },
  { "startSmoothZoom",
    "(I)V",
    (void *)android_hardware_Camera_startSmoothZoom },
  { "stopSmoothZoom",
    "()V",
    (void *)android_hardware_Camera_stopSmoothZoom },
  { "setDisplayOrientation",
    "(I)V",
    (void *)android_hardware_Camera_setDisplayOrientation },
};

struct field {
    const char *class_name;
    const char *field_name;
    const char *field_type;
    jfieldID   *jfield;
};

static int find_fields(JNIEnv *env, field *fields, int count)
{
    for (int i = 0; i < count; i++) {
        field *f = &fields[i];
        jclass clazz = env->FindClass(f->class_name);
        if (clazz == NULL) {
            LOGE("Can't find %s", f->class_name);
            return -1;
        }

        jfieldID field = env->GetFieldID(clazz, f->field_name, f->field_type);
        if (field == NULL) {
            LOGE("Can't find %s.%s", f->class_name, f->field_name);
            return -1;
        }

        *(f->jfield) = field;
    }

    return 0;
}

// Get all the required offsets in java class and register native functions
int register_android_hardware_Camera(JNIEnv *env)
{
    field fields_to_find[] = {
        { "android/hardware/Camera", "mNativeContext",   "I", &fields.context },
        { "android/view/Surface",    ANDROID_VIEW_SURFACE_JNI_ID, "I", &fields.surface },
        { "android/hardware/Camera$CameraInfo", "facing",   "I", &fields.facing },
        { "android/hardware/Camera$CameraInfo", "orientation",   "I", &fields.orientation },
    };

    if (find_fields(env, fields_to_find, NELEM(fields_to_find)) < 0)
        return -1;

    jclass clazz = env->FindClass("android/hardware/Camera");
    fields.post_event = env->GetStaticMethodID(clazz, "postEventFromNative",
                                               "(Ljava/lang/Object;IIILjava/lang/Object;)V");
    if (fields.post_event == NULL) {
        LOGE("Can't find android/hardware/Camera.postEventFromNative");
        return -1;
    }


    // Register native functions
    return AndroidRuntime::registerNativeMethods(env, "android/hardware/Camera",
                                              camMethods, NELEM(camMethods));
}

