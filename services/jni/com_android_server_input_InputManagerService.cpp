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

#define LOG_TAG "InputManager-JNI"

//#define LOG_NDEBUG 0

// Log debug messages about InputReaderPolicy
#define DEBUG_INPUT_READER_POLICY 0

// Log debug messages about InputDispatcherPolicy
#define DEBUG_INPUT_DISPATCHER_POLICY 0


#include "JNIHelp.h"
#include "jni.h"
#include <limits.h>
#include <android_runtime/AndroidRuntime.h>

#include <utils/Log.h>
#include <utils/Looper.h>
#include <utils/threads.h>

#include <input/InputManager.h>
#include <input/PointerController.h>
#include <input/SpriteController.h>

#include <android_os_MessageQueue.h>
#include <android_view_InputDevice.h>
#include <android_view_KeyEvent.h>
#include <android_view_MotionEvent.h>
#include <android_view_InputChannel.h>
#include <android_view_PointerIcon.h>
#include <android/graphics/GraphicsJNI.h>

#include <ScopedLocalRef.h>
#include <ScopedUtfChars.h>

#include "com_android_server_PowerManagerService.h"
#include "com_android_server_input_InputApplicationHandle.h"
#include "com_android_server_input_InputWindowHandle.h"

namespace android {

// The exponent used to calculate the pointer speed scaling factor.
// The scaling factor is calculated as 2 ^ (speed * exponent),
// where the speed ranges from -7 to + 7 and is supplied by the user.
static const float POINTER_SPEED_EXPONENT = 1.0f / 4;

static struct {
    jmethodID notifyConfigurationChanged;
    jmethodID notifyInputDevicesChanged;
    jmethodID notifyLidSwitchChanged;
    jmethodID notifyInputChannelBroken;
    jmethodID notifyANR;
    jmethodID filterInputEvent;
    jmethodID interceptKeyBeforeQueueing;
    jmethodID interceptMotionBeforeQueueingWhenScreenOff;
    jmethodID interceptKeyBeforeDispatching;
    jmethodID dispatchUnhandledKey;
    jmethodID checkInjectEventsPermission;
    jmethodID getVirtualKeyQuietTimeMillis;
    jmethodID getExcludedDeviceNames;
    jmethodID getKeyRepeatTimeout;
    jmethodID getKeyRepeatDelay;
    jmethodID getHoverTapTimeout;
    jmethodID getHoverTapSlop;
    jmethodID getDoubleTapTimeout;
    jmethodID getLongPressTimeout;
    jmethodID getPointerLayer;
    jmethodID getPointerIcon;
    jmethodID getKeyboardLayoutOverlay;
    jmethodID getDeviceAlias;
} gServiceClassInfo;

static struct {
    jclass clazz;
} gInputDeviceClassInfo;

static struct {
    jclass clazz;
} gKeyEventClassInfo;

static struct {
    jclass clazz;
} gMotionEventClassInfo;


// --- Global functions ---

template<typename T>
inline static T min(const T& a, const T& b) {
    return a < b ? a : b;
}

template<typename T>
inline static T max(const T& a, const T& b) {
    return a > b ? a : b;
}

static jobject getInputApplicationHandleObjLocalRef(JNIEnv* env,
        const sp<InputApplicationHandle>& inputApplicationHandle) {
    if (inputApplicationHandle == NULL) {
        return NULL;
    }
    return static_cast<NativeInputApplicationHandle*>(inputApplicationHandle.get())->
            getInputApplicationHandleObjLocalRef(env);
}

static jobject getInputWindowHandleObjLocalRef(JNIEnv* env,
        const sp<InputWindowHandle>& inputWindowHandle) {
    if (inputWindowHandle == NULL) {
        return NULL;
    }
    return static_cast<NativeInputWindowHandle*>(inputWindowHandle.get())->
            getInputWindowHandleObjLocalRef(env);
}

static void loadSystemIconAsSprite(JNIEnv* env, jobject contextObj, int32_t style,
        SpriteIcon* outSpriteIcon) {
    PointerIcon pointerIcon;
    status_t status = android_view_PointerIcon_loadSystemIcon(env,
            contextObj, style, &pointerIcon);
    if (!status) {
        pointerIcon.bitmap.copyTo(&outSpriteIcon->bitmap, SkBitmap::kARGB_8888_Config);
        outSpriteIcon->hotSpotX = pointerIcon.hotSpotX;
        outSpriteIcon->hotSpotY = pointerIcon.hotSpotY;
    }
}

enum {
    WM_ACTION_PASS_TO_USER = 1,
    WM_ACTION_POKE_USER_ACTIVITY = 2,
    WM_ACTION_GO_TO_SLEEP = 4,
};


// --- NativeInputManager ---

class NativeInputManager : public virtual RefBase,
    public virtual InputReaderPolicyInterface,
    public virtual InputDispatcherPolicyInterface,
    public virtual PointerControllerPolicyInterface {
protected:
    virtual ~NativeInputManager();

public:
    NativeInputManager(jobject contextObj, jobject serviceObj, const sp<Looper>& looper);

    inline sp<InputManager> getInputManager() const { return mInputManager; }

    void dump(String8& dump);

    void setDisplaySize(int32_t displayId, int32_t width, int32_t height,
            int32_t externalWidth, int32_t externalHeight);
    void setDisplayOrientation(int32_t displayId, int32_t orientation, int32_t externalOrientation);

    status_t registerInputChannel(JNIEnv* env, const sp<InputChannel>& inputChannel,
            const sp<InputWindowHandle>& inputWindowHandle, bool monitor);
    status_t unregisterInputChannel(JNIEnv* env, const sp<InputChannel>& inputChannel);

    void setInputWindows(JNIEnv* env, jobjectArray windowHandleObjArray);
    void setFocusedApplication(JNIEnv* env, jobject applicationHandleObj);
    void setInputDispatchMode(bool enabled, bool frozen);
    void setSystemUiVisibility(int32_t visibility);
    void setPointerSpeed(int32_t speed);
    void setShowTouches(bool enabled);
    void setStylusIconEnabled(bool enabled);

    /* --- InputReaderPolicyInterface implementation --- */

    virtual void getReaderConfiguration(InputReaderConfiguration* outConfig);
    virtual sp<PointerControllerInterface> obtainPointerController(int32_t deviceId);
    virtual void notifyInputDevicesChanged(const Vector<InputDeviceInfo>& inputDevices);
    virtual sp<KeyCharacterMap> getKeyboardLayoutOverlay(const String8& inputDeviceDescriptor);
    virtual String8 getDeviceAlias(const InputDeviceIdentifier& identifier);

    /* --- InputDispatcherPolicyInterface implementation --- */

    virtual void notifySwitch(nsecs_t when, int32_t switchCode, int32_t switchValue,
            uint32_t policyFlags);
    virtual void notifyConfigurationChanged(nsecs_t when);
    virtual nsecs_t notifyANR(const sp<InputApplicationHandle>& inputApplicationHandle,
            const sp<InputWindowHandle>& inputWindowHandle);
    virtual void notifyInputChannelBroken(const sp<InputWindowHandle>& inputWindowHandle);
    virtual bool filterInputEvent(const InputEvent* inputEvent, uint32_t policyFlags);
    virtual void getDispatcherConfiguration(InputDispatcherConfiguration* outConfig);
    virtual bool isKeyRepeatEnabled();
    virtual void interceptKeyBeforeQueueing(const KeyEvent* keyEvent, uint32_t& policyFlags);
    virtual void interceptMotionBeforeQueueing(nsecs_t when, uint32_t& policyFlags);
    virtual nsecs_t interceptKeyBeforeDispatching(
            const sp<InputWindowHandle>& inputWindowHandle,
            const KeyEvent* keyEvent, uint32_t policyFlags);
    virtual bool dispatchUnhandledKey(const sp<InputWindowHandle>& inputWindowHandle,
            const KeyEvent* keyEvent, uint32_t policyFlags, KeyEvent* outFallbackKeyEvent);
    virtual void pokeUserActivity(nsecs_t eventTime, int32_t eventType);
    virtual bool checkInjectEventsPermissionNonReentrant(
            int32_t injectorPid, int32_t injectorUid);

    /* --- PointerControllerPolicyInterface implementation --- */

    virtual void loadPointerResources(PointerResources* outResources);

private:
    sp<InputManager> mInputManager;

    jobject mContextObj;
    jobject mServiceObj;
    sp<Looper> mLooper;

    Mutex mLock;
    struct Locked {
        // Display size information.
        int32_t displayWidth, displayHeight; // -1 when not initialized
        int32_t displayOrientation;
        int32_t displayExternalWidth, displayExternalHeight; // -1 when not initialized
        int32_t displayExternalOrientation;

        // System UI visibility.
        int32_t systemUiVisibility;

        // Pointer speed.
        int32_t pointerSpeed;

        // True if pointer gestures are enabled.
        bool pointerGesturesEnabled;

        // Show touches feature enable/disable.
        bool showTouches;

        // Show icon when stylus is used
        bool stylusIconEnabled;

        // Sprite controller singleton, created on first use.
        sp<SpriteController> spriteController;

        // Pointer controller singleton, created and destroyed as needed.
        wp<PointerController> pointerController;
    } mLocked;

    void updateInactivityTimeoutLocked(const sp<PointerController>& controller);
    void handleInterceptActions(jint wmActions, nsecs_t when, uint32_t& policyFlags);
    void ensureSpriteControllerLocked();

    // Power manager interactions.
    bool isScreenOn();
    bool isScreenBright();

    static bool checkAndClearExceptionFromCallback(JNIEnv* env, const char* methodName);

    static inline JNIEnv* jniEnv() {
        return AndroidRuntime::getJNIEnv();
    }
};



NativeInputManager::NativeInputManager(jobject contextObj,
        jobject serviceObj, const sp<Looper>& looper) :
        mLooper(looper) {
    JNIEnv* env = jniEnv();

    mContextObj = env->NewGlobalRef(contextObj);
    mServiceObj = env->NewGlobalRef(serviceObj);

    {
        AutoMutex _l(mLock);
        mLocked.displayWidth = -1;
        mLocked.displayHeight = -1;
        mLocked.displayOrientation = DISPLAY_ORIENTATION_0;
        mLocked.displayExternalWidth = -1;
        mLocked.displayExternalHeight = -1;
        mLocked.displayExternalOrientation = DISPLAY_ORIENTATION_0;

        mLocked.systemUiVisibility = ASYSTEM_UI_VISIBILITY_STATUS_BAR_VISIBLE;
        mLocked.pointerSpeed = 0;
        mLocked.pointerGesturesEnabled = true;
        mLocked.showTouches = false;
        mLocked.stylusIconEnabled = false;
    }

    sp<EventHub> eventHub = new EventHub();
    mInputManager = new InputManager(eventHub, this, this);
}

NativeInputManager::~NativeInputManager() {
    JNIEnv* env = jniEnv();

    env->DeleteGlobalRef(mContextObj);
    env->DeleteGlobalRef(mServiceObj);
}

void NativeInputManager::dump(String8& dump) {
    mInputManager->getReader()->dump(dump);
    dump.append("\n");

    mInputManager->getDispatcher()->dump(dump);
    dump.append("\n");
}

bool NativeInputManager::checkAndClearExceptionFromCallback(JNIEnv* env, const char* methodName) {
    if (env->ExceptionCheck()) {
        ALOGE("An exception was thrown by callback '%s'.", methodName);
        LOGE_EX(env);
        env->ExceptionClear();
        return true;
    }
    return false;
}

void NativeInputManager::setDisplaySize(int32_t displayId, int32_t width, int32_t height,
        int32_t externalWidth, int32_t externalHeight) {
    bool changed = false;
    if (displayId == 0) {
        AutoMutex _l(mLock);

        if (mLocked.displayWidth != width || mLocked.displayHeight != height) {
            changed = true;
            mLocked.displayWidth = width;
            mLocked.displayHeight = height;

            sp<PointerController> controller = mLocked.pointerController.promote();
            if (controller != NULL) {
                controller->setDisplaySize(width, height);
            }
        }

        if (mLocked.displayExternalWidth != externalWidth
                || mLocked.displayExternalHeight != externalHeight) {
            changed = true;
            mLocked.displayExternalWidth = externalWidth;
            mLocked.displayExternalHeight = externalHeight;
        }
    }

    if (changed) {
        mInputManager->getReader()->requestRefreshConfiguration(
                InputReaderConfiguration::CHANGE_DISPLAY_INFO);
    }
}

void NativeInputManager::setDisplayOrientation(int32_t displayId, int32_t orientation,
        int32_t externalOrientation) {
    bool changed = false;
    if (displayId == 0) {
        AutoMutex _l(mLock);

        if (mLocked.displayOrientation != orientation) {
            changed = true;
            mLocked.displayOrientation = orientation;

            sp<PointerController> controller = mLocked.pointerController.promote();
            if (controller != NULL) {
                controller->setDisplayOrientation(orientation);
            }
        }

        if (mLocked.displayExternalOrientation != externalOrientation) {
            changed = true;
            mLocked.displayExternalOrientation = externalOrientation;
        }
    }

    if (changed) {
        mInputManager->getReader()->requestRefreshConfiguration(
                InputReaderConfiguration::CHANGE_DISPLAY_INFO);
    }
}

status_t NativeInputManager::registerInputChannel(JNIEnv* env,
        const sp<InputChannel>& inputChannel,
        const sp<InputWindowHandle>& inputWindowHandle, bool monitor) {
    return mInputManager->getDispatcher()->registerInputChannel(
            inputChannel, inputWindowHandle, monitor);
}

status_t NativeInputManager::unregisterInputChannel(JNIEnv* env,
        const sp<InputChannel>& inputChannel) {
    return mInputManager->getDispatcher()->unregisterInputChannel(inputChannel);
}

void NativeInputManager::getReaderConfiguration(InputReaderConfiguration* outConfig) {
    JNIEnv* env = jniEnv();

    jint virtualKeyQuietTime = env->CallIntMethod(mServiceObj,
            gServiceClassInfo.getVirtualKeyQuietTimeMillis);
    if (!checkAndClearExceptionFromCallback(env, "getVirtualKeyQuietTimeMillis")) {
        outConfig->virtualKeyQuietTime = milliseconds_to_nanoseconds(virtualKeyQuietTime);
    }

    outConfig->excludedDeviceNames.clear();
    jobjectArray excludedDeviceNames = jobjectArray(env->CallObjectMethod(mServiceObj,
            gServiceClassInfo.getExcludedDeviceNames));
    if (!checkAndClearExceptionFromCallback(env, "getExcludedDeviceNames") && excludedDeviceNames) {
        jsize length = env->GetArrayLength(excludedDeviceNames);
        for (jsize i = 0; i < length; i++) {
            jstring item = jstring(env->GetObjectArrayElement(excludedDeviceNames, i));
            const char* deviceNameChars = env->GetStringUTFChars(item, NULL);
            outConfig->excludedDeviceNames.add(String8(deviceNameChars));
            env->ReleaseStringUTFChars(item, deviceNameChars);
            env->DeleteLocalRef(item);
        }
        env->DeleteLocalRef(excludedDeviceNames);
    }

    jint hoverTapTimeout = env->CallIntMethod(mServiceObj,
            gServiceClassInfo.getHoverTapTimeout);
    if (!checkAndClearExceptionFromCallback(env, "getHoverTapTimeout")) {
        jint doubleTapTimeout = env->CallIntMethod(mServiceObj,
                gServiceClassInfo.getDoubleTapTimeout);
        if (!checkAndClearExceptionFromCallback(env, "getDoubleTapTimeout")) {
            jint longPressTimeout = env->CallIntMethod(mServiceObj,
                    gServiceClassInfo.getLongPressTimeout);
            if (!checkAndClearExceptionFromCallback(env, "getLongPressTimeout")) {
                outConfig->pointerGestureTapInterval = milliseconds_to_nanoseconds(hoverTapTimeout);

                // We must ensure that the tap-drag interval is significantly shorter than
                // the long-press timeout because the tap is held down for the entire duration
                // of the double-tap timeout.
                jint tapDragInterval = max(min(longPressTimeout - 100,
                        doubleTapTimeout), hoverTapTimeout);
                outConfig->pointerGestureTapDragInterval =
                        milliseconds_to_nanoseconds(tapDragInterval);
            }
        }
    }

    jint hoverTapSlop = env->CallIntMethod(mServiceObj,
            gServiceClassInfo.getHoverTapSlop);
    if (!checkAndClearExceptionFromCallback(env, "getHoverTapSlop")) {
        outConfig->pointerGestureTapSlop = hoverTapSlop;
    }

    { // acquire lock
        AutoMutex _l(mLock);

        outConfig->pointerVelocityControlParameters.scale = exp2f(mLocked.pointerSpeed
                * POINTER_SPEED_EXPONENT);
        outConfig->pointerGesturesEnabled = mLocked.pointerGesturesEnabled;

        outConfig->showTouches = mLocked.showTouches;

        outConfig->stylusIconEnabled = mLocked.stylusIconEnabled;

        outConfig->setDisplayInfo(0, false /*external*/,
                mLocked.displayWidth, mLocked.displayHeight, mLocked.displayOrientation);
        outConfig->setDisplayInfo(0, true /*external*/,
                mLocked.displayExternalWidth, mLocked.displayExternalHeight,
                mLocked.displayExternalOrientation);
    } // release lock
}

sp<PointerControllerInterface> NativeInputManager::obtainPointerController(int32_t deviceId) {
    AutoMutex _l(mLock);

    sp<PointerController> controller = mLocked.pointerController.promote();
    if (controller == NULL) {
        ensureSpriteControllerLocked();

        controller = new PointerController(this, mLooper, mLocked.spriteController);
        mLocked.pointerController = controller;

        controller->setDisplaySize(mLocked.displayWidth, mLocked.displayHeight);
        controller->setDisplayOrientation(mLocked.displayOrientation);

        JNIEnv* env = jniEnv();
        jobject pointerIconObj = env->CallObjectMethod(mServiceObj,
                gServiceClassInfo.getPointerIcon);
        if (!checkAndClearExceptionFromCallback(env, "getPointerIcon")) {
            PointerIcon pointerIcon;
            status_t status = android_view_PointerIcon_load(env, pointerIconObj,
                    mContextObj, &pointerIcon);
            if (!status && !pointerIcon.isNullIcon()) {
                controller->setPointerIcon(SpriteIcon(pointerIcon.bitmap,
                        pointerIcon.hotSpotX, pointerIcon.hotSpotY));
            } else {
                controller->setPointerIcon(SpriteIcon());
            }
            env->DeleteLocalRef(pointerIconObj);
        }

        updateInactivityTimeoutLocked(controller);
    }
    return controller;
}

void NativeInputManager::ensureSpriteControllerLocked() {
    if (mLocked.spriteController == NULL) {
        JNIEnv* env = jniEnv();
        jint layer = env->CallIntMethod(mServiceObj, gServiceClassInfo.getPointerLayer);
        if (checkAndClearExceptionFromCallback(env, "getPointerLayer")) {
            layer = -1;
        }
        mLocked.spriteController = new SpriteController(mLooper, layer);
    }
}

void NativeInputManager::notifyInputDevicesChanged(const Vector<InputDeviceInfo>& inputDevices) {
    JNIEnv* env = jniEnv();

    size_t count = inputDevices.size();
    jobjectArray inputDevicesObjArray = env->NewObjectArray(
            count, gInputDeviceClassInfo.clazz, NULL);
    if (inputDevicesObjArray) {
        bool error = false;
        for (size_t i = 0; i < count; i++) {
            jobject inputDeviceObj = android_view_InputDevice_create(env, inputDevices.itemAt(i));
            if (!inputDeviceObj) {
                error = true;
                break;
            }

            env->SetObjectArrayElement(inputDevicesObjArray, i, inputDeviceObj);
            env->DeleteLocalRef(inputDeviceObj);
        }

        if (!error) {
            env->CallVoidMethod(mServiceObj, gServiceClassInfo.notifyInputDevicesChanged,
                    inputDevicesObjArray);
        }

        env->DeleteLocalRef(inputDevicesObjArray);
    }

    checkAndClearExceptionFromCallback(env, "notifyInputDevicesChanged");
}

sp<KeyCharacterMap> NativeInputManager::getKeyboardLayoutOverlay(
        const String8& inputDeviceDescriptor) {
    JNIEnv* env = jniEnv();

    sp<KeyCharacterMap> result;
    ScopedLocalRef<jstring> descriptorObj(env, env->NewStringUTF(inputDeviceDescriptor.string()));
    ScopedLocalRef<jobjectArray> arrayObj(env, jobjectArray(env->CallObjectMethod(mServiceObj,
                gServiceClassInfo.getKeyboardLayoutOverlay, descriptorObj.get())));
    if (arrayObj.get()) {
        ScopedLocalRef<jstring> filenameObj(env,
                jstring(env->GetObjectArrayElement(arrayObj.get(), 0)));
        ScopedLocalRef<jstring> contentsObj(env,
                jstring(env->GetObjectArrayElement(arrayObj.get(), 1)));
        ScopedUtfChars filenameChars(env, filenameObj.get());
        ScopedUtfChars contentsChars(env, contentsObj.get());

        KeyCharacterMap::loadContents(String8(filenameChars.c_str()),
                String8(contentsChars.c_str()), KeyCharacterMap::FORMAT_OVERLAY, &result);
    }
    checkAndClearExceptionFromCallback(env, "getKeyboardLayoutOverlay");
    return result;
}

String8 NativeInputManager::getDeviceAlias(const InputDeviceIdentifier& identifier) {
    JNIEnv* env = jniEnv();

    ScopedLocalRef<jstring> uniqueIdObj(env, env->NewStringUTF(identifier.uniqueId.string()));
    ScopedLocalRef<jstring> aliasObj(env, jstring(env->CallObjectMethod(mServiceObj,
            gServiceClassInfo.getDeviceAlias, uniqueIdObj.get())));
    String8 result;
    if (aliasObj.get()) {
        ScopedUtfChars aliasChars(env, aliasObj.get());
        result.setTo(aliasChars.c_str());
    }
    checkAndClearExceptionFromCallback(env, "getDeviceAlias");
    return result;
}

void NativeInputManager::notifySwitch(nsecs_t when, int32_t switchCode,
        int32_t switchValue, uint32_t policyFlags) {
#if DEBUG_INPUT_DISPATCHER_POLICY
    ALOGD("notifySwitch - when=%lld, switchCode=%d, switchValue=%d, policyFlags=0x%x",
            when, switchCode, switchValue, policyFlags);
#endif

    JNIEnv* env = jniEnv();

    switch (switchCode) {
    case SW_LID:
        // When switch value is set indicates lid is closed.
        env->CallVoidMethod(mServiceObj, gServiceClassInfo.notifyLidSwitchChanged,
                when, switchValue == 0 /*lidOpen*/);
        checkAndClearExceptionFromCallback(env, "notifyLidSwitchChanged");
        break;
    }
}

void NativeInputManager::notifyConfigurationChanged(nsecs_t when) {
#if DEBUG_INPUT_DISPATCHER_POLICY
    ALOGD("notifyConfigurationChanged - when=%lld", when);
#endif

    JNIEnv* env = jniEnv();

    env->CallVoidMethod(mServiceObj, gServiceClassInfo.notifyConfigurationChanged, when);
    checkAndClearExceptionFromCallback(env, "notifyConfigurationChanged");
}

nsecs_t NativeInputManager::notifyANR(const sp<InputApplicationHandle>& inputApplicationHandle,
        const sp<InputWindowHandle>& inputWindowHandle) {
#if DEBUG_INPUT_DISPATCHER_POLICY
    ALOGD("notifyANR");
#endif

    JNIEnv* env = jniEnv();

    jobject inputApplicationHandleObj =
            getInputApplicationHandleObjLocalRef(env, inputApplicationHandle);
    jobject inputWindowHandleObj =
            getInputWindowHandleObjLocalRef(env, inputWindowHandle);

    jlong newTimeout = env->CallLongMethod(mServiceObj,
                gServiceClassInfo.notifyANR, inputApplicationHandleObj, inputWindowHandleObj);
    if (checkAndClearExceptionFromCallback(env, "notifyANR")) {
        newTimeout = 0; // abort dispatch
    } else {
        assert(newTimeout >= 0);
    }

    env->DeleteLocalRef(inputWindowHandleObj);
    env->DeleteLocalRef(inputApplicationHandleObj);
    return newTimeout;
}

void NativeInputManager::notifyInputChannelBroken(const sp<InputWindowHandle>& inputWindowHandle) {
#if DEBUG_INPUT_DISPATCHER_POLICY
    ALOGD("notifyInputChannelBroken");
#endif

    JNIEnv* env = jniEnv();

    jobject inputWindowHandleObj =
            getInputWindowHandleObjLocalRef(env, inputWindowHandle);
    if (inputWindowHandleObj) {
        env->CallVoidMethod(mServiceObj, gServiceClassInfo.notifyInputChannelBroken,
                inputWindowHandleObj);
        checkAndClearExceptionFromCallback(env, "notifyInputChannelBroken");

        env->DeleteLocalRef(inputWindowHandleObj);
    }
}

void NativeInputManager::getDispatcherConfiguration(InputDispatcherConfiguration* outConfig) {
    JNIEnv* env = jniEnv();

    jint keyRepeatTimeout = env->CallIntMethod(mServiceObj,
            gServiceClassInfo.getKeyRepeatTimeout);
    if (!checkAndClearExceptionFromCallback(env, "getKeyRepeatTimeout")) {
        outConfig->keyRepeatTimeout = milliseconds_to_nanoseconds(keyRepeatTimeout);
    }

    jint keyRepeatDelay = env->CallIntMethod(mServiceObj,
            gServiceClassInfo.getKeyRepeatDelay);
    if (!checkAndClearExceptionFromCallback(env, "getKeyRepeatDelay")) {
        outConfig->keyRepeatDelay = milliseconds_to_nanoseconds(keyRepeatDelay);
    }
}

bool NativeInputManager::isKeyRepeatEnabled() {
    // Only enable automatic key repeating when the screen is on.
    return isScreenOn();
}

void NativeInputManager::setInputWindows(JNIEnv* env, jobjectArray windowHandleObjArray) {
    Vector<sp<InputWindowHandle> > windowHandles;

    if (windowHandleObjArray) {
        jsize length = env->GetArrayLength(windowHandleObjArray);
        for (jsize i = 0; i < length; i++) {
            jobject windowHandleObj = env->GetObjectArrayElement(windowHandleObjArray, i);
            if (! windowHandleObj) {
                break; // found null element indicating end of used portion of the array
            }

            sp<InputWindowHandle> windowHandle =
                    android_server_InputWindowHandle_getHandle(env, windowHandleObj);
            if (windowHandle != NULL) {
                windowHandles.push(windowHandle);
            }
            env->DeleteLocalRef(windowHandleObj);
        }
    }

    mInputManager->getDispatcher()->setInputWindows(windowHandles);

    // Do this after the dispatcher has updated the window handle state.
    bool newPointerGesturesEnabled = true;
    size_t numWindows = windowHandles.size();
    for (size_t i = 0; i < numWindows; i++) {
        const sp<InputWindowHandle>& windowHandle = windowHandles.itemAt(i);
        const InputWindowInfo* windowInfo = windowHandle->getInfo();
        if (windowInfo && windowInfo->hasFocus && (windowInfo->inputFeatures
                & InputWindowInfo::INPUT_FEATURE_DISABLE_TOUCH_PAD_GESTURES)) {
            newPointerGesturesEnabled = false;
        }
    }

    uint32_t changes = 0;
    { // acquire lock
        AutoMutex _l(mLock);

        if (mLocked.pointerGesturesEnabled != newPointerGesturesEnabled) {
            mLocked.pointerGesturesEnabled = newPointerGesturesEnabled;
            changes |= InputReaderConfiguration::CHANGE_POINTER_GESTURE_ENABLEMENT;
        }
    } // release lock

    if (changes) {
        mInputManager->getReader()->requestRefreshConfiguration(changes);
    }
}

void NativeInputManager::setFocusedApplication(JNIEnv* env, jobject applicationHandleObj) {
    sp<InputApplicationHandle> applicationHandle =
            android_server_InputApplicationHandle_getHandle(env, applicationHandleObj);
    mInputManager->getDispatcher()->setFocusedApplication(applicationHandle);
}

void NativeInputManager::setInputDispatchMode(bool enabled, bool frozen) {
    mInputManager->getDispatcher()->setInputDispatchMode(enabled, frozen);
}

void NativeInputManager::setSystemUiVisibility(int32_t visibility) {
    AutoMutex _l(mLock);

    if (mLocked.systemUiVisibility != visibility) {
        mLocked.systemUiVisibility = visibility;

        sp<PointerController> controller = mLocked.pointerController.promote();
        if (controller != NULL) {
            updateInactivityTimeoutLocked(controller);
        }
    }
}

void NativeInputManager::updateInactivityTimeoutLocked(const sp<PointerController>& controller) {
    bool lightsOut = mLocked.systemUiVisibility & ASYSTEM_UI_VISIBILITY_STATUS_BAR_HIDDEN;
    controller->setInactivityTimeout(lightsOut
            ? PointerController::INACTIVITY_TIMEOUT_SHORT
            : PointerController::INACTIVITY_TIMEOUT_NORMAL);
}

void NativeInputManager::setPointerSpeed(int32_t speed) {
    { // acquire lock
        AutoMutex _l(mLock);

        if (mLocked.pointerSpeed == speed) {
            return;
        }

        ALOGI("Setting pointer speed to %d.", speed);
        mLocked.pointerSpeed = speed;
    } // release lock

    mInputManager->getReader()->requestRefreshConfiguration(
            InputReaderConfiguration::CHANGE_POINTER_SPEED);
}

void NativeInputManager::setShowTouches(bool enabled) {
    { // acquire lock
        AutoMutex _l(mLock);

        if (mLocked.showTouches == enabled) {
            return;
        }

        ALOGI("Setting show touches feature to %s.", enabled ? "enabled" : "disabled");
        mLocked.showTouches = enabled;
    } // release lock

    mInputManager->getReader()->requestRefreshConfiguration(
            InputReaderConfiguration::CHANGE_SHOW_TOUCHES);
}

void NativeInputManager::setStylusIconEnabled(bool enabled) {
    { // acquire lock
        AutoMutex _l(mLock);

        if (mLocked.stylusIconEnabled == enabled) {
            return;
        }

        ALOGI("Setting stylus icon enabled to %s.", enabled ? "enabled" : "disabled");
        mLocked.stylusIconEnabled = enabled;
    } // release lock

    mInputManager->getReader()->requestRefreshConfiguration(
            InputReaderConfiguration::CHANGE_STYLUS_ICON_ENABLED);
}

bool NativeInputManager::isScreenOn() {
    return android_server_PowerManagerService_isScreenOn();
}

bool NativeInputManager::isScreenBright() {
    return android_server_PowerManagerService_isScreenBright();
}

bool NativeInputManager::filterInputEvent(const InputEvent* inputEvent, uint32_t policyFlags) {
    jobject inputEventObj;

    JNIEnv* env = jniEnv();
    switch (inputEvent->getType()) {
    case AINPUT_EVENT_TYPE_KEY:
        inputEventObj = android_view_KeyEvent_fromNative(env,
                static_cast<const KeyEvent*>(inputEvent));
        break;
    case AINPUT_EVENT_TYPE_MOTION:
        inputEventObj = android_view_MotionEvent_obtainAsCopy(env,
                static_cast<const MotionEvent*>(inputEvent));
        break;
    default:
        return true; // dispatch the event normally
    }

    if (!inputEventObj) {
        ALOGE("Failed to obtain input event object for filterInputEvent.");
        return true; // dispatch the event normally
    }

    // The callee is responsible for recycling the event.
    jboolean pass = env->CallBooleanMethod(mServiceObj, gServiceClassInfo.filterInputEvent,
            inputEventObj, policyFlags);
    if (checkAndClearExceptionFromCallback(env, "filterInputEvent")) {
        pass = true;
    }
    env->DeleteLocalRef(inputEventObj);
    return pass;
}

void NativeInputManager::interceptKeyBeforeQueueing(const KeyEvent* keyEvent,
        uint32_t& policyFlags) {
    // Policy:
    // - Ignore untrusted events and pass them along.
    // - Ask the window manager what to do with normal events and trusted injected events.
    // - For normal events wake and brighten the screen if currently off or dim.
    if ((policyFlags & POLICY_FLAG_TRUSTED)) {
        nsecs_t when = keyEvent->getEventTime();
        bool isScreenOn = this->isScreenOn();
        bool isScreenBright = this->isScreenBright();

        JNIEnv* env = jniEnv();
        jobject keyEventObj = android_view_KeyEvent_fromNative(env, keyEvent);
        jint wmActions;
        if (keyEventObj) {
            wmActions = env->CallIntMethod(mServiceObj,
                    gServiceClassInfo.interceptKeyBeforeQueueing,
                    keyEventObj, policyFlags, isScreenOn);
            if (checkAndClearExceptionFromCallback(env, "interceptKeyBeforeQueueing")) {
                wmActions = 0;
            }
            android_view_KeyEvent_recycle(env, keyEventObj);
            env->DeleteLocalRef(keyEventObj);
        } else {
            ALOGE("Failed to obtain key event object for interceptKeyBeforeQueueing.");
            wmActions = 0;
        }

        if (!(policyFlags & POLICY_FLAG_INJECTED)) {
            if (!isScreenOn) {
                policyFlags |= POLICY_FLAG_WOKE_HERE;
            }

            if (!isScreenBright) {
                policyFlags |= POLICY_FLAG_BRIGHT_HERE;
            }
        }

        handleInterceptActions(wmActions, when, /*byref*/ policyFlags);
    } else {
        policyFlags |= POLICY_FLAG_PASS_TO_USER;
    }
}

void NativeInputManager::interceptMotionBeforeQueueing(nsecs_t when, uint32_t& policyFlags) {
    // Policy:
    // - Ignore untrusted events and pass them along.
    // - No special filtering for injected events required at this time.
    // - Filter normal events based on screen state.
    // - For normal events brighten (but do not wake) the screen if currently dim.
    if ((policyFlags & POLICY_FLAG_TRUSTED) && !(policyFlags & POLICY_FLAG_INJECTED)) {
        if (isScreenOn()) {
            policyFlags |= POLICY_FLAG_PASS_TO_USER;

            if (!isScreenBright()) {
                policyFlags |= POLICY_FLAG_BRIGHT_HERE;
            }
        } else {
            JNIEnv* env = jniEnv();
            jint wmActions = env->CallIntMethod(mServiceObj,
                        gServiceClassInfo.interceptMotionBeforeQueueingWhenScreenOff,
                        policyFlags);
            if (checkAndClearExceptionFromCallback(env,
                    "interceptMotionBeforeQueueingWhenScreenOff")) {
                wmActions = 0;
            }

            policyFlags |= POLICY_FLAG_WOKE_HERE | POLICY_FLAG_BRIGHT_HERE;
            handleInterceptActions(wmActions, when, /*byref*/ policyFlags);
        }
    } else {
        policyFlags |= POLICY_FLAG_PASS_TO_USER;
    }
}

void NativeInputManager::handleInterceptActions(jint wmActions, nsecs_t when,
        uint32_t& policyFlags) {
    if (wmActions & WM_ACTION_GO_TO_SLEEP) {
#if DEBUG_INPUT_DISPATCHER_POLICY
        ALOGD("handleInterceptActions: Going to sleep.");
#endif
        android_server_PowerManagerService_goToSleep(when);
    }

    if (wmActions & WM_ACTION_POKE_USER_ACTIVITY) {
#if DEBUG_INPUT_DISPATCHER_POLICY
        ALOGD("handleInterceptActions: Poking user activity.");
#endif
        android_server_PowerManagerService_userActivity(when, POWER_MANAGER_BUTTON_EVENT);
    }

    if (wmActions & WM_ACTION_PASS_TO_USER) {
        policyFlags |= POLICY_FLAG_PASS_TO_USER;
    } else {
#if DEBUG_INPUT_DISPATCHER_POLICY
        ALOGD("handleInterceptActions: Not passing key to user.");
#endif
    }
}

nsecs_t NativeInputManager::interceptKeyBeforeDispatching(
        const sp<InputWindowHandle>& inputWindowHandle,
        const KeyEvent* keyEvent, uint32_t policyFlags) {
    // Policy:
    // - Ignore untrusted events and pass them along.
    // - Filter normal events and trusted injected events through the window manager policy to
    //   handle the HOME key and the like.
    nsecs_t result = 0;
    if (policyFlags & POLICY_FLAG_TRUSTED) {
        JNIEnv* env = jniEnv();

        // Note: inputWindowHandle may be null.
        jobject inputWindowHandleObj = getInputWindowHandleObjLocalRef(env, inputWindowHandle);
        jobject keyEventObj = android_view_KeyEvent_fromNative(env, keyEvent);
        if (keyEventObj) {
            jlong delayMillis = env->CallLongMethod(mServiceObj,
                    gServiceClassInfo.interceptKeyBeforeDispatching,
                    inputWindowHandleObj, keyEventObj, policyFlags);
            bool error = checkAndClearExceptionFromCallback(env, "interceptKeyBeforeDispatching");
            android_view_KeyEvent_recycle(env, keyEventObj);
            env->DeleteLocalRef(keyEventObj);
            if (!error) {
                if (delayMillis < 0) {
                    result = -1;
                } else if (delayMillis > 0) {
                    result = milliseconds_to_nanoseconds(delayMillis);
                }
            }
        } else {
            ALOGE("Failed to obtain key event object for interceptKeyBeforeDispatching.");
        }
        env->DeleteLocalRef(inputWindowHandleObj);
    }
    return result;
}

bool NativeInputManager::dispatchUnhandledKey(const sp<InputWindowHandle>& inputWindowHandle,
        const KeyEvent* keyEvent, uint32_t policyFlags, KeyEvent* outFallbackKeyEvent) {
    // Policy:
    // - Ignore untrusted events and do not perform default handling.
    bool result = false;
    if (policyFlags & POLICY_FLAG_TRUSTED) {
        JNIEnv* env = jniEnv();

        // Note: inputWindowHandle may be null.
        jobject inputWindowHandleObj = getInputWindowHandleObjLocalRef(env, inputWindowHandle);
        jobject keyEventObj = android_view_KeyEvent_fromNative(env, keyEvent);
        if (keyEventObj) {
            jobject fallbackKeyEventObj = env->CallObjectMethod(mServiceObj,
                    gServiceClassInfo.dispatchUnhandledKey,
                    inputWindowHandleObj, keyEventObj, policyFlags);
            if (checkAndClearExceptionFromCallback(env, "dispatchUnhandledKey")) {
                fallbackKeyEventObj = NULL;
            }
            android_view_KeyEvent_recycle(env, keyEventObj);
            env->DeleteLocalRef(keyEventObj);

            if (fallbackKeyEventObj) {
                // Note: outFallbackKeyEvent may be the same object as keyEvent.
                if (!android_view_KeyEvent_toNative(env, fallbackKeyEventObj,
                        outFallbackKeyEvent)) {
                    result = true;
                }
                android_view_KeyEvent_recycle(env, fallbackKeyEventObj);
                env->DeleteLocalRef(fallbackKeyEventObj);
            }
        } else {
            ALOGE("Failed to obtain key event object for dispatchUnhandledKey.");
        }
        env->DeleteLocalRef(inputWindowHandleObj);
    }
    return result;
}

void NativeInputManager::pokeUserActivity(nsecs_t eventTime, int32_t eventType) {
    android_server_PowerManagerService_userActivity(eventTime, eventType);
}


bool NativeInputManager::checkInjectEventsPermissionNonReentrant(
        int32_t injectorPid, int32_t injectorUid) {
    JNIEnv* env = jniEnv();
    jboolean result = env->CallBooleanMethod(mServiceObj,
            gServiceClassInfo.checkInjectEventsPermission, injectorPid, injectorUid);
    if (checkAndClearExceptionFromCallback(env, "checkInjectEventsPermission")) {
        result = false;
    }
    return result;
}

void NativeInputManager::loadPointerResources(PointerResources* outResources) {
    JNIEnv* env = jniEnv();

    loadSystemIconAsSprite(env, mContextObj, POINTER_ICON_STYLE_SPOT_HOVER,
            &outResources->spotHover);
    loadSystemIconAsSprite(env, mContextObj, POINTER_ICON_STYLE_SPOT_TOUCH,
            &outResources->spotTouch);
    loadSystemIconAsSprite(env, mContextObj, POINTER_ICON_STYLE_SPOT_ANCHOR,
            &outResources->spotAnchor);
}


// ----------------------------------------------------------------------------

static jint nativeInit(JNIEnv* env, jclass clazz,
        jobject serviceObj, jobject contextObj, jobject messageQueueObj) {
    sp<MessageQueue> messageQueue = android_os_MessageQueue_getMessageQueue(env, messageQueueObj);
    NativeInputManager* im = new NativeInputManager(contextObj, serviceObj,
            messageQueue->getLooper());
    im->incStrong(serviceObj);
    return reinterpret_cast<jint>(im);
}

static void nativeStart(JNIEnv* env, jclass clazz, jint ptr) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    status_t result = im->getInputManager()->start();
    if (result) {
        jniThrowRuntimeException(env, "Input manager could not be started.");
    }
}

static void nativeSetDisplaySize(JNIEnv* env, jclass clazz, jint ptr,
        jint displayId, jint width, jint height, jint externalWidth, jint externalHeight) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    // XXX we could get this from the SurfaceFlinger directly instead of requiring it
    // to be passed in like this, not sure which is better but leaving it like this
    // keeps the window manager in direct control of when display transitions propagate down
    // to the input dispatcher
    im->setDisplaySize(displayId, width, height, externalWidth, externalHeight);
}

static void nativeSetDisplayOrientation(JNIEnv* env, jclass clazz,
        jint ptr, jint displayId, jint orientation, jint externalOrientation) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->setDisplayOrientation(displayId, orientation, externalOrientation);
}

static jint nativeGetScanCodeState(JNIEnv* env, jclass clazz,
        jint ptr, jint deviceId, jint sourceMask, jint scanCode) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    return im->getInputManager()->getReader()->getScanCodeState(
            deviceId, uint32_t(sourceMask), scanCode);
}

static jint nativeGetKeyCodeState(JNIEnv* env, jclass clazz,
        jint ptr, jint deviceId, jint sourceMask, jint keyCode) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    return im->getInputManager()->getReader()->getKeyCodeState(
            deviceId, uint32_t(sourceMask), keyCode);
}

static jint nativeGetSwitchState(JNIEnv* env, jclass clazz,
        jint ptr, jint deviceId, jint sourceMask, jint sw) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    return im->getInputManager()->getReader()->getSwitchState(
            deviceId, uint32_t(sourceMask), sw);
}

static jboolean nativeHasKeys(JNIEnv* env, jclass clazz,
        jint ptr, jint deviceId, jint sourceMask, jintArray keyCodes, jbooleanArray outFlags) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    int32_t* codes = env->GetIntArrayElements(keyCodes, NULL);
    uint8_t* flags = env->GetBooleanArrayElements(outFlags, NULL);
    jsize numCodes = env->GetArrayLength(keyCodes);
    jboolean result;
    if (numCodes == env->GetArrayLength(keyCodes)) {
        result = im->getInputManager()->getReader()->hasKeys(
                deviceId, uint32_t(sourceMask), numCodes, codes, flags);
    } else {
        result = JNI_FALSE;
    }

    env->ReleaseBooleanArrayElements(outFlags, flags, 0);
    env->ReleaseIntArrayElements(keyCodes, codes, 0);
    return result;
}

static void throwInputChannelNotInitialized(JNIEnv* env) {
    jniThrowException(env, "java/lang/IllegalStateException",
             "inputChannel is not initialized");
}

static void handleInputChannelDisposed(JNIEnv* env,
        jobject inputChannelObj, const sp<InputChannel>& inputChannel, void* data) {
    NativeInputManager* im = static_cast<NativeInputManager*>(data);

    ALOGW("Input channel object '%s' was disposed without first being unregistered with "
            "the input manager!", inputChannel->getName().string());
    im->unregisterInputChannel(env, inputChannel);
}

static void nativeRegisterInputChannel(JNIEnv* env, jclass clazz,
        jint ptr, jobject inputChannelObj, jobject inputWindowHandleObj, jboolean monitor) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    sp<InputChannel> inputChannel = android_view_InputChannel_getInputChannel(env,
            inputChannelObj);
    if (inputChannel == NULL) {
        throwInputChannelNotInitialized(env);
        return;
    }

    sp<InputWindowHandle> inputWindowHandle =
            android_server_InputWindowHandle_getHandle(env, inputWindowHandleObj);

    status_t status = im->registerInputChannel(
            env, inputChannel, inputWindowHandle, monitor);
    if (status) {
        String8 message;
        message.appendFormat("Failed to register input channel.  status=%d", status);
        jniThrowRuntimeException(env, message.string());
        return;
    }

    if (! monitor) {
        android_view_InputChannel_setDisposeCallback(env, inputChannelObj,
                handleInputChannelDisposed, im);
    }
}

static void nativeUnregisterInputChannel(JNIEnv* env, jclass clazz,
        jint ptr, jobject inputChannelObj) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    sp<InputChannel> inputChannel = android_view_InputChannel_getInputChannel(env,
            inputChannelObj);
    if (inputChannel == NULL) {
        throwInputChannelNotInitialized(env);
        return;
    }

    android_view_InputChannel_setDisposeCallback(env, inputChannelObj, NULL, NULL);

    status_t status = im->unregisterInputChannel(env, inputChannel);
    if (status && status != BAD_VALUE) { // ignore already unregistered channel
        String8 message;
        message.appendFormat("Failed to unregister input channel.  status=%d", status);
        jniThrowRuntimeException(env, message.string());
    }
}

static void nativeSetInputFilterEnabled(JNIEnv* env, jclass clazz,
        jint ptr, jboolean enabled) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->getInputManager()->getDispatcher()->setInputFilterEnabled(enabled);
}

static jint nativeInjectInputEvent(JNIEnv* env, jclass clazz,
        jint ptr, jobject inputEventObj, jint injectorPid, jint injectorUid,
        jint syncMode, jint timeoutMillis, jint policyFlags) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    if (env->IsInstanceOf(inputEventObj, gKeyEventClassInfo.clazz)) {
        KeyEvent keyEvent;
        status_t status = android_view_KeyEvent_toNative(env, inputEventObj, & keyEvent);
        if (status) {
            jniThrowRuntimeException(env, "Could not read contents of KeyEvent object.");
            return INPUT_EVENT_INJECTION_FAILED;
        }

        return im->getInputManager()->getDispatcher()->injectInputEvent(
                & keyEvent, injectorPid, injectorUid, syncMode, timeoutMillis,
                uint32_t(policyFlags));
    } else if (env->IsInstanceOf(inputEventObj, gMotionEventClassInfo.clazz)) {
        const MotionEvent* motionEvent = android_view_MotionEvent_getNativePtr(env, inputEventObj);
        if (!motionEvent) {
            jniThrowRuntimeException(env, "Could not read contents of MotionEvent object.");
            return INPUT_EVENT_INJECTION_FAILED;
        }

        return im->getInputManager()->getDispatcher()->injectInputEvent(
                motionEvent, injectorPid, injectorUid, syncMode, timeoutMillis,
                uint32_t(policyFlags));
    } else {
        jniThrowRuntimeException(env, "Invalid input event type.");
        return INPUT_EVENT_INJECTION_FAILED;
    }
}

static void nativeSetInputWindows(JNIEnv* env, jclass clazz,
        jint ptr, jobjectArray windowHandleObjArray) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->setInputWindows(env, windowHandleObjArray);
}

static void nativeSetFocusedApplication(JNIEnv* env, jclass clazz,
        jint ptr, jobject applicationHandleObj) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->setFocusedApplication(env, applicationHandleObj);
}

static void nativeSetInputDispatchMode(JNIEnv* env,
        jclass clazz, jint ptr, jboolean enabled, jboolean frozen) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->setInputDispatchMode(enabled, frozen);
}

static void nativeSetSystemUiVisibility(JNIEnv* env,
        jclass clazz, jint ptr, jint visibility) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->setSystemUiVisibility(visibility);
}

static jboolean nativeTransferTouchFocus(JNIEnv* env,
        jclass clazz, jint ptr, jobject fromChannelObj, jobject toChannelObj) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    sp<InputChannel> fromChannel =
            android_view_InputChannel_getInputChannel(env, fromChannelObj);
    sp<InputChannel> toChannel =
            android_view_InputChannel_getInputChannel(env, toChannelObj);

    if (fromChannel == NULL || toChannel == NULL) {
        return false;
    }

    return im->getInputManager()->getDispatcher()->
            transferTouchFocus(fromChannel, toChannel);
}

static void nativeSetPointerSpeed(JNIEnv* env,
        jclass clazz, jint ptr, jint speed) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->setPointerSpeed(speed);
}

static void nativeSetShowTouches(JNIEnv* env,
        jclass clazz, jint ptr, jboolean enabled) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->setShowTouches(enabled);
}

static void nativeSetStylusIconEnabled(JNIEnv* env,
        jclass clazz, jint ptr, jboolean enabled) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->setStylusIconEnabled(enabled);
}

static void nativeVibrate(JNIEnv* env,
        jclass clazz, jint ptr, jint deviceId, jlongArray patternObj,
        jint repeat, jint token) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    size_t patternSize = env->GetArrayLength(patternObj);
    if (patternSize > MAX_VIBRATE_PATTERN_SIZE) {
        ALOGI("Skipped requested vibration because the pattern size is %d "
                "which is more than the maximum supported size of %d.",
                patternSize, MAX_VIBRATE_PATTERN_SIZE);
        return; // limit to reasonable size
    }

    jlong* patternMillis = static_cast<jlong*>(env->GetPrimitiveArrayCritical(
            patternObj, NULL));
    nsecs_t pattern[patternSize];
    for (size_t i = 0; i < patternSize; i++) {
        pattern[i] = max(jlong(0), min(patternMillis[i],
                MAX_VIBRATE_PATTERN_DELAY_NSECS / 1000000LL)) * 1000000LL;
    }
    env->ReleasePrimitiveArrayCritical(patternObj, patternMillis, JNI_ABORT);

    im->getInputManager()->getReader()->vibrate(deviceId, pattern, patternSize, repeat, token);
}

static void nativeCancelVibrate(JNIEnv* env,
        jclass clazz, jint ptr, jint deviceId, jint token) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->getInputManager()->getReader()->cancelVibrate(deviceId, token);
}

static void nativeReloadKeyboardLayouts(JNIEnv* env,
        jclass clazz, jint ptr) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->getInputManager()->getReader()->requestRefreshConfiguration(
            InputReaderConfiguration::CHANGE_KEYBOARD_LAYOUTS);
}

static void nativeReloadDeviceAliases(JNIEnv* env,
        jclass clazz, jint ptr) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->getInputManager()->getReader()->requestRefreshConfiguration(
            InputReaderConfiguration::CHANGE_DEVICE_ALIAS);
}

static jstring nativeDump(JNIEnv* env, jclass clazz, jint ptr) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    String8 dump;
    im->dump(dump);
    return env->NewStringUTF(dump.string());
}

static void nativeMonitor(JNIEnv* env, jclass clazz, jint ptr) {
    NativeInputManager* im = reinterpret_cast<NativeInputManager*>(ptr);

    im->getInputManager()->getReader()->monitor();
    im->getInputManager()->getDispatcher()->monitor();
}

// ----------------------------------------------------------------------------

static JNINativeMethod gInputManagerMethods[] = {
    /* name, signature, funcPtr */
    { "nativeInit",
            "(Lcom/android/server/input/InputManagerService;Landroid/content/Context;Landroid/os/MessageQueue;)I",
            (void*) nativeInit },
    { "nativeStart", "(I)V",
            (void*) nativeStart },
    { "nativeSetDisplaySize", "(IIIIII)V",
            (void*) nativeSetDisplaySize },
    { "nativeSetDisplayOrientation", "(IIII)V",
            (void*) nativeSetDisplayOrientation },
    { "nativeGetScanCodeState", "(IIII)I",
            (void*) nativeGetScanCodeState },
    { "nativeGetKeyCodeState", "(IIII)I",
            (void*) nativeGetKeyCodeState },
    { "nativeGetSwitchState", "(IIII)I",
            (void*) nativeGetSwitchState },
    { "nativeHasKeys", "(III[I[Z)Z",
            (void*) nativeHasKeys },
    { "nativeRegisterInputChannel",
            "(ILandroid/view/InputChannel;Lcom/android/server/input/InputWindowHandle;Z)V",
            (void*) nativeRegisterInputChannel },
    { "nativeUnregisterInputChannel", "(ILandroid/view/InputChannel;)V",
            (void*) nativeUnregisterInputChannel },
    { "nativeSetInputFilterEnabled", "(IZ)V",
            (void*) nativeSetInputFilterEnabled },
    { "nativeInjectInputEvent", "(ILandroid/view/InputEvent;IIIII)I",
            (void*) nativeInjectInputEvent },
    { "nativeSetInputWindows", "(I[Lcom/android/server/input/InputWindowHandle;)V",
            (void*) nativeSetInputWindows },
    { "nativeSetFocusedApplication", "(ILcom/android/server/input/InputApplicationHandle;)V",
            (void*) nativeSetFocusedApplication },
    { "nativeSetInputDispatchMode", "(IZZ)V",
            (void*) nativeSetInputDispatchMode },
    { "nativeSetSystemUiVisibility", "(II)V",
            (void*) nativeSetSystemUiVisibility },
    { "nativeTransferTouchFocus", "(ILandroid/view/InputChannel;Landroid/view/InputChannel;)Z",
            (void*) nativeTransferTouchFocus },
    { "nativeSetPointerSpeed", "(II)V",
            (void*) nativeSetPointerSpeed },
    { "nativeSetShowTouches", "(IZ)V",
            (void*) nativeSetShowTouches },
    { "nativeSetStylusIconEnabled", "(IZ)V",
            (void*) nativeSetStylusIconEnabled },
    { "nativeVibrate", "(II[JII)V",
            (void*) nativeVibrate },
    { "nativeCancelVibrate", "(III)V",
            (void*) nativeCancelVibrate },
    { "nativeReloadKeyboardLayouts", "(I)V",
            (void*) nativeReloadKeyboardLayouts },
    { "nativeReloadDeviceAliases", "(I)V",
            (void*) nativeReloadDeviceAliases },
    { "nativeDump", "(I)Ljava/lang/String;",
            (void*) nativeDump },
    { "nativeMonitor", "(I)V",
            (void*) nativeMonitor },
};

#define FIND_CLASS(var, className) \
        var = env->FindClass(className); \
        LOG_FATAL_IF(! var, "Unable to find class " className);

#define GET_METHOD_ID(var, clazz, methodName, methodDescriptor) \
        var = env->GetMethodID(clazz, methodName, methodDescriptor); \
        LOG_FATAL_IF(! var, "Unable to find method " methodName);

#define GET_FIELD_ID(var, clazz, fieldName, fieldDescriptor) \
        var = env->GetFieldID(clazz, fieldName, fieldDescriptor); \
        LOG_FATAL_IF(! var, "Unable to find field " fieldName);

int register_android_server_InputManager(JNIEnv* env) {
    int res = jniRegisterNativeMethods(env, "com/android/server/input/InputManagerService",
            gInputManagerMethods, NELEM(gInputManagerMethods));
    LOG_FATAL_IF(res < 0, "Unable to register native methods.");

    // Callbacks

    jclass clazz;
    FIND_CLASS(clazz, "com/android/server/input/InputManagerService");

    GET_METHOD_ID(gServiceClassInfo.notifyConfigurationChanged, clazz,
            "notifyConfigurationChanged", "(J)V");

    GET_METHOD_ID(gServiceClassInfo.notifyInputDevicesChanged, clazz,
            "notifyInputDevicesChanged", "([Landroid/view/InputDevice;)V");

    GET_METHOD_ID(gServiceClassInfo.notifyLidSwitchChanged, clazz,
            "notifyLidSwitchChanged", "(JZ)V");

    GET_METHOD_ID(gServiceClassInfo.notifyInputChannelBroken, clazz,
            "notifyInputChannelBroken", "(Lcom/android/server/input/InputWindowHandle;)V");

    GET_METHOD_ID(gServiceClassInfo.notifyANR, clazz,
            "notifyANR",
            "(Lcom/android/server/input/InputApplicationHandle;Lcom/android/server/input/InputWindowHandle;)J");

    GET_METHOD_ID(gServiceClassInfo.filterInputEvent, clazz,
            "filterInputEvent", "(Landroid/view/InputEvent;I)Z");

    GET_METHOD_ID(gServiceClassInfo.interceptKeyBeforeQueueing, clazz,
            "interceptKeyBeforeQueueing", "(Landroid/view/KeyEvent;IZ)I");

    GET_METHOD_ID(gServiceClassInfo.interceptMotionBeforeQueueingWhenScreenOff,
            clazz,
            "interceptMotionBeforeQueueingWhenScreenOff", "(I)I");

    GET_METHOD_ID(gServiceClassInfo.interceptKeyBeforeDispatching, clazz,
            "interceptKeyBeforeDispatching",
            "(Lcom/android/server/input/InputWindowHandle;Landroid/view/KeyEvent;I)J");

    GET_METHOD_ID(gServiceClassInfo.dispatchUnhandledKey, clazz,
            "dispatchUnhandledKey",
            "(Lcom/android/server/input/InputWindowHandle;Landroid/view/KeyEvent;I)Landroid/view/KeyEvent;");

    GET_METHOD_ID(gServiceClassInfo.checkInjectEventsPermission, clazz,
            "checkInjectEventsPermission", "(II)Z");

    GET_METHOD_ID(gServiceClassInfo.getVirtualKeyQuietTimeMillis, clazz,
            "getVirtualKeyQuietTimeMillis", "()I");

    GET_METHOD_ID(gServiceClassInfo.getExcludedDeviceNames, clazz,
            "getExcludedDeviceNames", "()[Ljava/lang/String;");

    GET_METHOD_ID(gServiceClassInfo.getKeyRepeatTimeout, clazz,
            "getKeyRepeatTimeout", "()I");

    GET_METHOD_ID(gServiceClassInfo.getKeyRepeatDelay, clazz,
            "getKeyRepeatDelay", "()I");

    GET_METHOD_ID(gServiceClassInfo.getHoverTapTimeout, clazz,
            "getHoverTapTimeout", "()I");

    GET_METHOD_ID(gServiceClassInfo.getHoverTapSlop, clazz,
            "getHoverTapSlop", "()I");

    GET_METHOD_ID(gServiceClassInfo.getDoubleTapTimeout, clazz,
            "getDoubleTapTimeout", "()I");

    GET_METHOD_ID(gServiceClassInfo.getLongPressTimeout, clazz,
            "getLongPressTimeout", "()I");

    GET_METHOD_ID(gServiceClassInfo.getPointerLayer, clazz,
            "getPointerLayer", "()I");

    GET_METHOD_ID(gServiceClassInfo.getPointerIcon, clazz,
            "getPointerIcon", "()Landroid/view/PointerIcon;");

    GET_METHOD_ID(gServiceClassInfo.getKeyboardLayoutOverlay, clazz,
            "getKeyboardLayoutOverlay", "(Ljava/lang/String;)[Ljava/lang/String;");

    GET_METHOD_ID(gServiceClassInfo.getDeviceAlias, clazz,
            "getDeviceAlias", "(Ljava/lang/String;)Ljava/lang/String;");

    // InputDevice

    FIND_CLASS(gInputDeviceClassInfo.clazz, "android/view/InputDevice");
    gInputDeviceClassInfo.clazz = jclass(env->NewGlobalRef(gInputDeviceClassInfo.clazz));

    // KeyEvent

    FIND_CLASS(gKeyEventClassInfo.clazz, "android/view/KeyEvent");
    gKeyEventClassInfo.clazz = jclass(env->NewGlobalRef(gKeyEventClassInfo.clazz));

    // MotionEvent

    FIND_CLASS(gMotionEventClassInfo.clazz, "android/view/MotionEvent");
    gMotionEventClassInfo.clazz = jclass(env->NewGlobalRef(gMotionEventClassInfo.clazz));

    return 0;
}

} /* namespace android */
