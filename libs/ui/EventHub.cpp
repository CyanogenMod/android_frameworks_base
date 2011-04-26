//
// Copyright 2005 The Android Open Source Project
//
// Handle events, like key input and vsync.
//
// The goal is to provide an optimized solution for Linux, not an
// implementation that works well across all platforms.  We expect
// events to arrive on file descriptors, so that we can use a select()
// select() call to sleep.
//
// We can't select() on anything but network sockets in Windows, so we
// provide an alternative implementation of waitEvent for that platform.
//
#define LOG_TAG "EventHub"

//#define LOG_NDEBUG 0

#include <ui/EventHub.h>
#include <ui/KeycodeLabels.h>
#include <hardware_legacy/power.h>

#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/Timers.h>
#include <utils/threads.h>
#include <utils/Errors.h>

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <memory.h>
#include <errno.h>
#include <assert.h>

#include "KeyLayoutMap.h"

#include <string.h>
#include <stdint.h>
#include <dirent.h>
#ifdef HAVE_INOTIFY
# include <sys/inotify.h>
#endif
#ifdef HAVE_ANDROID_OS
# include <sys/limits.h>        /* not part of Linux */
#endif
#include <sys/poll.h>
#include <sys/ioctl.h>

/* this macro is used to tell if "bit" is set in "array"
 * it selects a byte from the array, and does a boolean AND
 * operation with a byte that only has the relevant bit set.
 * eg. to check for the 12th bit, we do (array[1] & 1<<4)
 */
#define test_bit(bit, array)    (array[bit/8] & (1<<(bit%8)))

/* this macro computes the number of bytes needed to represent a bit array of the specified size */
#define sizeof_bit_array(bits)  ((bits + 7) / 8)

#define ID_MASK  0x0000ffff
#define SEQ_MASK 0x7fff0000
#define SEQ_SHIFT 16

#ifndef ABS_MT_TOUCH_MAJOR
#define ABS_MT_TOUCH_MAJOR      0x30    /* Major axis of touching ellipse */
#endif

#ifndef ABS_MT_POSITION_X
#define ABS_MT_POSITION_X       0x35    /* Center X ellipse position */
#endif

#ifndef ABS_MT_POSITION_Y
#define ABS_MT_POSITION_Y       0x36    /* Center Y ellipse position */
#endif

#define INDENT "  "
#define INDENT2 "    "
#define INDENT3 "      "

namespace android {

static const char *WAKE_LOCK_ID = "KeyEvents";
static const char *device_path = "/dev/input";

/* return the larger integer */
static inline int max(int v1, int v2)
{
    return (v1 > v2) ? v1 : v2;
}

static inline const char* toString(bool value) {
    return value ? "true" : "false";
}

EventHub::device_t::device_t(int32_t _id, const char* _path, const char* name)
    : id(_id), path(_path), name(name), classes(0)
    , keyBitmask(NULL), layoutMap(new KeyLayoutMap()), fd(-1), next(NULL) {
}

EventHub::device_t::~device_t() {
    delete [] keyBitmask;
    delete layoutMap;
}

EventHub::EventHub(void)
    : mError(NO_INIT), mHaveFirstKeyboard(false), mFirstKeyboardId(0)
    , mDevicesById(0), mNumDevicesById(0)
    , mOpeningDevices(0), mClosingDevices(0)
    , mDevices(0), mFDs(0), mFDCount(0), mOpened(false), mNeedToSendFinishedDeviceScan(false)
    , mInputBufferIndex(0), mInputBufferCount(0), mInputDeviceIndex(0)
{
    acquire_wake_lock(PARTIAL_WAKE_LOCK, WAKE_LOCK_ID);
#ifdef EV_SW
    memset(mSwitches, 0, sizeof(mSwitches));
#endif
}

/*
 * Clean up.
 */
EventHub::~EventHub(void)
{
    release_wake_lock(WAKE_LOCK_ID);
    // we should free stuff here...
}

status_t EventHub::errorCheck() const
{
    return mError;
}

String8 EventHub::getDeviceName(int32_t deviceId) const
{
    AutoMutex _l(mLock);
    device_t* device = getDeviceLocked(deviceId);
    if (device == NULL) return String8();
    return device->name;
}

uint32_t EventHub::getDeviceClasses(int32_t deviceId) const
{
    AutoMutex _l(mLock);
    device_t* device = getDeviceLocked(deviceId);
    if (device == NULL) return 0;
    return device->classes;
}

status_t EventHub::getAbsoluteAxisInfo(int32_t deviceId, int axis,
        RawAbsoluteAxisInfo* outAxisInfo) const {
    outAxisInfo->clear();

    AutoMutex _l(mLock);
    device_t* device = getDeviceLocked(deviceId);
    if (device == NULL) return -1;

    struct input_absinfo info;

    if(ioctl(device->fd, EVIOCGABS(axis), &info)) {
        LOGW("Error reading absolute controller %d for device %s fd %d\n",
             axis, device->name.string(), device->fd);
        return -errno;
    }

    if (info.minimum != info.maximum) {
        outAxisInfo->valid = true;
        outAxisInfo->minValue = info.minimum;
        outAxisInfo->maxValue = info.maximum;
        outAxisInfo->flat = info.flat;
        outAxisInfo->fuzz = info.fuzz;
    }
    return OK;
}

int32_t EventHub::getScanCodeState(int32_t deviceId, int32_t scanCode) const {
    if (scanCode >= 0 && scanCode <= KEY_MAX) {
        AutoMutex _l(mLock);

        device_t* device = getDeviceLocked(deviceId);
        if (device != NULL) {
            return getScanCodeStateLocked(device, scanCode);
        }
    }
    return AKEY_STATE_UNKNOWN;
}

int32_t EventHub::getScanCodeStateLocked(device_t* device, int32_t scanCode) const {
    uint8_t key_bitmask[sizeof_bit_array(KEY_MAX + 1)];
    memset(key_bitmask, 0, sizeof(key_bitmask));
    if (ioctl(device->fd,
               EVIOCGKEY(sizeof(key_bitmask)), key_bitmask) >= 0) {
        return test_bit(scanCode, key_bitmask) ? AKEY_STATE_DOWN : AKEY_STATE_UP;
    }
    return AKEY_STATE_UNKNOWN;
}

int32_t EventHub::getKeyCodeState(int32_t deviceId, int32_t keyCode) const {
    AutoMutex _l(mLock);

    device_t* device = getDeviceLocked(deviceId);
    if (device != NULL) {
        return getKeyCodeStateLocked(device, keyCode);
    }
    return AKEY_STATE_UNKNOWN;
}

int32_t EventHub::getKeyCodeStateLocked(device_t* device, int32_t keyCode) const {
    Vector<int32_t> scanCodes;
    device->layoutMap->findScancodes(keyCode, &scanCodes);

    uint8_t key_bitmask[sizeof_bit_array(KEY_MAX + 1)];
    memset(key_bitmask, 0, sizeof(key_bitmask));
    if (ioctl(device->fd, EVIOCGKEY(sizeof(key_bitmask)), key_bitmask) >= 0) {
        #if 0
        for (size_t i=0; i<=KEY_MAX; i++) {
            LOGI("(Scan code %d: down=%d)", i, test_bit(i, key_bitmask));
        }
        #endif
        const size_t N = scanCodes.size();
        for (size_t i=0; i<N && i<=KEY_MAX; i++) {
            int32_t sc = scanCodes.itemAt(i);
            //LOGI("Code %d: down=%d", sc, test_bit(sc, key_bitmask));
            if (sc >= 0 && sc <= KEY_MAX && test_bit(sc, key_bitmask)) {
                return AKEY_STATE_DOWN;
            }
        }
        return AKEY_STATE_UP;
    }
    return AKEY_STATE_UNKNOWN;
}

int32_t EventHub::getSwitchState(int32_t deviceId, int32_t sw) const {
#ifdef EV_SW
    if (sw >= 0 && sw <= SW_MAX) {
        AutoMutex _l(mLock);

        device_t* device = getDeviceLocked(deviceId);
        if (device != NULL) {
            return getSwitchStateLocked(device, sw);
        }
    }
#endif
    return AKEY_STATE_UNKNOWN;
}

int32_t EventHub::getSwitchStateLocked(device_t* device, int32_t sw) const {
    uint8_t sw_bitmask[sizeof_bit_array(SW_MAX + 1)];
    memset(sw_bitmask, 0, sizeof(sw_bitmask));
    if (ioctl(device->fd,
               EVIOCGSW(sizeof(sw_bitmask)), sw_bitmask) >= 0) {
        return test_bit(sw, sw_bitmask) ? AKEY_STATE_DOWN : AKEY_STATE_UP;
    }
    return AKEY_STATE_UNKNOWN;
}

bool EventHub::markSupportedKeyCodes(int32_t deviceId, size_t numCodes,
        const int32_t* keyCodes, uint8_t* outFlags) const {
    AutoMutex _l(mLock);

    device_t* device = getDeviceLocked(deviceId);
    if (device != NULL) {
        return markSupportedKeyCodesLocked(device, numCodes, keyCodes, outFlags);
    }
    return false;
}

bool EventHub::markSupportedKeyCodesLocked(device_t* device, size_t numCodes,
        const int32_t* keyCodes, uint8_t* outFlags) const {
    if (device->layoutMap == NULL || device->keyBitmask == NULL) {
        return false;
    }

    Vector<int32_t> scanCodes;
    for (size_t codeIndex = 0; codeIndex < numCodes; codeIndex++) {
        scanCodes.clear();

        status_t err = device->layoutMap->findScancodes(keyCodes[codeIndex], &scanCodes);
        if (! err) {
            // check the possible scan codes identified by the layout map against the
            // map of codes actually emitted by the driver
            for (size_t sc = 0; sc < scanCodes.size(); sc++) {
                if (test_bit(scanCodes[sc], device->keyBitmask)) {
                    outFlags[codeIndex] = 1;
                    break;
                }
            }
        }
    }
    return true;
}

status_t EventHub::scancodeToKeycode(int32_t deviceId, int scancode,
        int32_t* outKeycode, uint32_t* outFlags) const
{
    AutoMutex _l(mLock);
    device_t* device = getDeviceLocked(deviceId);
    
    if (device != NULL && device->layoutMap != NULL) {
        status_t err = device->layoutMap->map(scancode, outKeycode, outFlags);
        if (err == NO_ERROR) {
            return NO_ERROR;
        }
    }
    
    if (mHaveFirstKeyboard) {
        device = getDeviceLocked(mFirstKeyboardId);
        
        if (device != NULL && device->layoutMap != NULL) {
            status_t err = device->layoutMap->map(scancode, outKeycode, outFlags);
            if (err == NO_ERROR) {
                return NO_ERROR;
            }
        }
    }
    
    *outKeycode = 0;
    *outFlags = 0;
    return NAME_NOT_FOUND;
}

void EventHub::addExcludedDevice(const char* deviceName)
{
    AutoMutex _l(mLock);

    String8 name(deviceName);
    mExcludedDevices.push_back(name);
}

EventHub::device_t* EventHub::getDeviceLocked(int32_t deviceId) const
{
    if (deviceId == 0) deviceId = mFirstKeyboardId;
    int32_t id = deviceId & ID_MASK;
    if (id >= mNumDevicesById || id < 0) return NULL;
    device_t* dev = mDevicesById[id].device;
    if (dev == NULL) return NULL;
    if (dev->id == deviceId) {
        return dev;
    }
    return NULL;
}

bool EventHub::getEvent(RawEvent* outEvent)
{
    outEvent->deviceId = 0;
    outEvent->type = 0;
    outEvent->scanCode = 0;
    outEvent->keyCode = 0;
    outEvent->flags = 0;
    outEvent->value = 0;
    outEvent->when = 0;

    // Note that we only allow one caller to getEvent(), so don't need
    // to do locking here...  only when adding/removing devices.

    if (!mOpened) {
        mError = openPlatformInput() ? NO_ERROR : UNKNOWN_ERROR;
        mOpened = true;
        mNeedToSendFinishedDeviceScan = true;
    }

    for (;;) {
        // Report any devices that had last been added/removed.
        if (mClosingDevices != NULL) {
            device_t* device = mClosingDevices;
            LOGV("Reporting device closed: id=0x%x, name=%s\n",
                 device->id, device->path.string());
            mClosingDevices = device->next;
            if (device->id == mFirstKeyboardId) {
                outEvent->deviceId = 0;
            } else {
                outEvent->deviceId = device->id;
            }
            outEvent->type = DEVICE_REMOVED;
            outEvent->when = systemTime(SYSTEM_TIME_MONOTONIC);
            delete device;
            mNeedToSendFinishedDeviceScan = true;
            return true;
        }

        if (mOpeningDevices != NULL) {
            device_t* device = mOpeningDevices;
            LOGV("Reporting device opened: id=0x%x, name=%s\n",
                 device->id, device->path.string());
            mOpeningDevices = device->next;
            if (device->id == mFirstKeyboardId) {
                outEvent->deviceId = 0;
            } else {
                outEvent->deviceId = device->id;
            }
            outEvent->type = DEVICE_ADDED;
            outEvent->when = systemTime(SYSTEM_TIME_MONOTONIC);
            mNeedToSendFinishedDeviceScan = true;
            return true;
        }

        if (mNeedToSendFinishedDeviceScan) {
            mNeedToSendFinishedDeviceScan = false;
            outEvent->type = FINISHED_DEVICE_SCAN;
            outEvent->when = systemTime(SYSTEM_TIME_MONOTONIC);
            return true;
        }

        // Grab the next input event.
        for (;;) {
            // Consume buffered input events, if any.
            if (mInputBufferIndex < mInputBufferCount) {
                const struct input_event& iev = mInputBufferData[mInputBufferIndex++];
                const device_t* device = mDevices[mInputDeviceIndex];

                LOGV("%s got: t0=%d, t1=%d, type=%d, code=%d, v=%d", device->path.string(),
                     (int) iev.time.tv_sec, (int) iev.time.tv_usec, iev.type, iev.code, iev.value);
                if (device->id == mFirstKeyboardId) {
                    outEvent->deviceId = 0;
                } else {
                    outEvent->deviceId = device->id;
                }
                outEvent->type = iev.type;
                outEvent->scanCode = iev.code;
                if (iev.type == EV_KEY) {
                    status_t err = device->layoutMap->map(iev.code,
                            & outEvent->keyCode, & outEvent->flags);
                    LOGV("iev.code=%d keyCode=%d flags=0x%08x err=%d\n",
                        iev.code, outEvent->keyCode, outEvent->flags, err);
                    if (err != 0) {
                        outEvent->keyCode = AKEYCODE_UNKNOWN;
                        outEvent->flags = 0;
                    }
                } else {
                    outEvent->keyCode = iev.code;
                }
                outEvent->value = iev.value;

                // Use an event timestamp in the same timebase as
                // java.lang.System.nanoTime() and android.os.SystemClock.uptimeMillis()
                // as expected by the rest of the system.
                outEvent->when = systemTime(SYSTEM_TIME_MONOTONIC);
                return true;
            }

            // Finish reading all events from devices identified in previous poll().
            // This code assumes that mInputDeviceIndex is initially 0 and that the
            // revents member of pollfd is initialized to 0 when the device is first added.
            // Since mFDs[0] is used for inotify, we process regular events starting at index 1.
            mInputDeviceIndex += 1;
            if (mInputDeviceIndex >= mFDCount) {
                break;
            }

            const struct pollfd& pfd = mFDs[mInputDeviceIndex];
            if (pfd.revents & POLLIN) {
                int32_t readSize = read(pfd.fd, mInputBufferData,
                        sizeof(struct input_event) * INPUT_BUFFER_SIZE);
                if (readSize < 0) {
                    if (errno != EAGAIN && errno != EINTR) {
                        LOGW("could not get event (errno=%d)", errno);
                    }
                } else if ((readSize % sizeof(struct input_event)) != 0) {
                    LOGE("could not get event (wrong size: %d)", readSize);
                } else {
                    mInputBufferCount = readSize / sizeof(struct input_event);
                    mInputBufferIndex = 0;
                }
            }
        }

#if HAVE_INOTIFY
        // readNotify() will modify mFDs and mFDCount, so this must be done after
        // processing all other events.
        if(mFDs[0].revents & POLLIN) {
            readNotify(mFDs[0].fd);
            mFDs[0].revents = 0;
            continue; // report added or removed devices immediately
        }
#endif

        mInputDeviceIndex = 0;

        // Poll for events.  Mind the wake lock dance!
        // We hold a wake lock at all times except during poll().  This works due to some
        // subtle choreography.  When a device driver has pending (unread) events, it acquires
        // a kernel wake lock.  However, once the last pending event has been read, the device
        // driver will release the kernel wake lock.  To prevent the system from going to sleep
        // when this happens, the EventHub holds onto its own user wake lock while the client
        // is processing events.  Thus the system can only sleep if there are no events
        // pending or currently being processed.
        release_wake_lock(WAKE_LOCK_ID);

        int pollResult = poll(mFDs, mFDCount, -1);

        acquire_wake_lock(PARTIAL_WAKE_LOCK, WAKE_LOCK_ID);

        if (pollResult <= 0) {
            if (errno != EINTR) {
                LOGW("poll failed (errno=%d)\n", errno);
                usleep(100000);
            }
        }
    }
}

/*
 * Open the platform-specific input device.
 */
bool EventHub::openPlatformInput(void)
{
    /*
     * Open platform-specific input device(s).
     */
    int res;

    mFDCount = 1;
    mFDs = (pollfd *)calloc(1, sizeof(mFDs[0]));
    mDevices = (device_t **)calloc(1, sizeof(mDevices[0]));
    mFDs[0].events = POLLIN;
    mFDs[0].revents = 0;
    mDevices[0] = NULL;
#ifdef HAVE_INOTIFY
    mFDs[0].fd = inotify_init();
    res = inotify_add_watch(mFDs[0].fd, device_path, IN_DELETE | IN_CREATE);
    if(res < 0) {
        LOGE("could not add watch for %s, %s\n", device_path, strerror(errno));
    }
#else
    /*
     * The code in EventHub::getEvent assumes that mFDs[0] is an inotify fd.
     * We allocate space for it and set it to something invalid.
     */
    mFDs[0].fd = -1;
#endif

    res = scanDir(device_path);
    if(res < 0) {
        LOGE("scan dir failed for %s\n", device_path);
    }

    return true;
}

// ----------------------------------------------------------------------------

static bool containsNonZeroByte(const uint8_t* array, uint32_t startIndex, uint32_t endIndex) {
    const uint8_t* end = array + endIndex;
    array += startIndex;
    while (array != end) {
        if (*(array++) != 0) {
            return true;
        }
    }
    return false;
}

static const int32_t GAMEPAD_KEYCODES[] = {
        AKEYCODE_BUTTON_A, AKEYCODE_BUTTON_B, AKEYCODE_BUTTON_C,
        AKEYCODE_BUTTON_X, AKEYCODE_BUTTON_Y, AKEYCODE_BUTTON_Z,
        AKEYCODE_BUTTON_L1, AKEYCODE_BUTTON_R1,
        AKEYCODE_BUTTON_L2, AKEYCODE_BUTTON_R2,
        AKEYCODE_BUTTON_THUMBL, AKEYCODE_BUTTON_THUMBR,
        AKEYCODE_BUTTON_START, AKEYCODE_BUTTON_SELECT, AKEYCODE_BUTTON_MODE
};

int EventHub::openDevice(const char *deviceName) {
    int version;
    int fd;
    struct pollfd *new_mFDs;
    device_t **new_devices;
    char **new_device_names;
    char name[80];
    char location[80];
    char idstr[80];
    struct input_id id;

    LOGV("Opening device: %s", deviceName);

    AutoMutex _l(mLock);

    fd = open(deviceName, O_RDWR);
    if(fd < 0) {
        LOGE("could not open %s, %s\n", deviceName, strerror(errno));
        return -1;
    }

    if(ioctl(fd, EVIOCGVERSION, &version)) {
        LOGE("could not get driver version for %s, %s\n", deviceName, strerror(errno));
        return -1;
    }
    if(ioctl(fd, EVIOCGID, &id)) {
        LOGE("could not get driver id for %s, %s\n", deviceName, strerror(errno));
        return -1;
    }
    name[sizeof(name) - 1] = '\0';
    location[sizeof(location) - 1] = '\0';
    idstr[sizeof(idstr) - 1] = '\0';
    if(ioctl(fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
        //fprintf(stderr, "could not get device name for %s, %s\n", deviceName, strerror(errno));
        name[0] = '\0';
    }

    // check to see if the device is on our excluded list
    List<String8>::iterator iter = mExcludedDevices.begin();
    List<String8>::iterator end = mExcludedDevices.end();
    for ( ; iter != end; iter++) {
        const char* test = *iter;
        if (strcmp(name, test) == 0) {
            LOGI("ignoring event id %s driver %s\n", deviceName, test);
            close(fd);
            return -1;
        }
    }

    if(ioctl(fd, EVIOCGPHYS(sizeof(location) - 1), &location) < 1) {
        //fprintf(stderr, "could not get location for %s, %s\n", deviceName, strerror(errno));
        location[0] = '\0';
    }
    if(ioctl(fd, EVIOCGUNIQ(sizeof(idstr) - 1), &idstr) < 1) {
        //fprintf(stderr, "could not get idstring for %s, %s\n", deviceName, strerror(errno));
        idstr[0] = '\0';
    }

    if (fcntl(fd, F_SETFL, O_NONBLOCK)) {
        LOGE("Error %d making device file descriptor non-blocking.", errno);
        close(fd);
        return -1;
    }

    int devid = 0;
    while (devid < mNumDevicesById) {
        if (mDevicesById[devid].device == NULL) {
            break;
        }
        devid++;
    }
    if (devid >= mNumDevicesById) {
        device_ent* new_devids = (device_ent*)realloc(mDevicesById,
                sizeof(mDevicesById[0]) * (devid + 1));
        if (new_devids == NULL) {
            LOGE("out of memory");
            return -1;
        }
        mDevicesById = new_devids;
        mNumDevicesById = devid+1;
        mDevicesById[devid].device = NULL;
        mDevicesById[devid].seq = 0;
    }

    mDevicesById[devid].seq = (mDevicesById[devid].seq+(1<<SEQ_SHIFT))&SEQ_MASK;
    if (mDevicesById[devid].seq == 0) {
        mDevicesById[devid].seq = 1<<SEQ_SHIFT;
    }

    new_mFDs = (pollfd*)realloc(mFDs, sizeof(mFDs[0]) * (mFDCount + 1));
    new_devices = (device_t**)realloc(mDevices, sizeof(mDevices[0]) * (mFDCount + 1));
    if (new_mFDs == NULL || new_devices == NULL) {
        LOGE("out of memory");
        return -1;
    }
    mFDs = new_mFDs;
    mDevices = new_devices;

#if 0
    LOGI("add device %d: %s\n", mFDCount, deviceName);
    LOGI("  bus:      %04x\n"
         "  vendor    %04x\n"
         "  product   %04x\n"
         "  version   %04x\n",
        id.bustype, id.vendor, id.product, id.version);
    LOGI("  name:     \"%s\"\n", name);
    LOGI("  location: \"%s\"\n"
         "  id:       \"%s\"\n", location, idstr);
    LOGI("  version:  %d.%d.%d\n",
        version >> 16, (version >> 8) & 0xff, version & 0xff);
#endif

    device_t* device = new device_t(devid|mDevicesById[devid].seq, deviceName, name);
    if (device == NULL) {
        LOGE("out of memory");
        return -1;
    }

    device->fd = fd;
    mFDs[mFDCount].fd = fd;
    mFDs[mFDCount].events = POLLIN;
    mFDs[mFDCount].revents = 0;

    // Figure out the kinds of events the device reports.
    
    uint8_t key_bitmask[sizeof_bit_array(KEY_MAX + 1)];
    memset(key_bitmask, 0, sizeof(key_bitmask));

    LOGV("Getting keys...");
    if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(key_bitmask)), key_bitmask) >= 0) {
        //LOGI("MAP\n");
        //for (int i = 0; i < sizeof(key_bitmask); i++) {
        //    LOGI("%d: 0x%02x\n", i, key_bitmask[i]);
        //}

        // See if this is a keyboard.  Ignore everything in the button range except for
        // gamepads which are also considered keyboards.
        if (containsNonZeroByte(key_bitmask, 0, sizeof_bit_array(BTN_MISC))
                || containsNonZeroByte(key_bitmask, sizeof_bit_array(BTN_GAMEPAD),
                        sizeof_bit_array(BTN_DIGI))
                || containsNonZeroByte(key_bitmask, sizeof_bit_array(KEY_OK),
                        sizeof_bit_array(KEY_MAX + 1))) {
            device->classes |= INPUT_DEVICE_CLASS_KEYBOARD;

            device->keyBitmask = new uint8_t[sizeof(key_bitmask)];
            if (device->keyBitmask != NULL) {
                memcpy(device->keyBitmask, key_bitmask, sizeof(key_bitmask));
            } else {
                delete device;
                LOGE("out of memory allocating key bitmask");
                return -1;
            }
        }
    }
    
    // See if this is a trackball (or mouse).
    if (test_bit(BTN_MOUSE, key_bitmask)) {
        uint8_t rel_bitmask[sizeof_bit_array(REL_MAX + 1)];
        memset(rel_bitmask, 0, sizeof(rel_bitmask));
        LOGV("Getting relative controllers...");
        if (ioctl(fd, EVIOCGBIT(EV_REL, sizeof(rel_bitmask)), rel_bitmask) >= 0) {
            if (test_bit(REL_X, rel_bitmask) && test_bit(REL_Y, rel_bitmask)) {
                if (test_bit(BTN_LEFT, key_bitmask) && test_bit(BTN_RIGHT, key_bitmask))
                    device->classes |= INPUT_DEVICE_CLASS_MOUSE;
                else
                    device->classes |= INPUT_DEVICE_CLASS_TRACKBALL;
            }
        }
    }

    // See if this is a touch pad.
    uint8_t abs_bitmask[sizeof_bit_array(ABS_MAX + 1)];
    memset(abs_bitmask, 0, sizeof(abs_bitmask));
    LOGV("Getting absolute controllers...");
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(abs_bitmask)), abs_bitmask) >= 0) {
        // Is this a new modern multi-touch driver?
        if (test_bit(ABS_MT_POSITION_X, abs_bitmask)
                && test_bit(ABS_MT_POSITION_Y, abs_bitmask)) {
            device->classes |= INPUT_DEVICE_CLASS_TOUCHSCREEN | INPUT_DEVICE_CLASS_TOUCHSCREEN_MT;

        // Is this an old style single-touch driver?
        } else if (test_bit(BTN_TOUCH, key_bitmask)
                && test_bit(ABS_X, abs_bitmask) && test_bit(ABS_Y, abs_bitmask)) {
            device->classes |= INPUT_DEVICE_CLASS_TOUCHSCREEN;
        }
    }

#ifdef EV_SW
    // figure out the switches this device reports
    uint8_t sw_bitmask[sizeof_bit_array(SW_MAX + 1)];
    memset(sw_bitmask, 0, sizeof(sw_bitmask));
    bool hasSwitches = false;
    if (ioctl(fd, EVIOCGBIT(EV_SW, sizeof(sw_bitmask)), sw_bitmask) >= 0) {
        for (int i=0; i<EV_SW; i++) {
            //LOGI("Device 0x%x sw %d: has=%d", device->id, i, test_bit(i, sw_bitmask));
            if (test_bit(i, sw_bitmask)) {
                hasSwitches = true;
                if (mSwitches[i] == 0) {
                    mSwitches[i] = device->id;
                }
            }
        }
    }
    if (hasSwitches) {
        device->classes |= INPUT_DEVICE_CLASS_SWITCH;
    }
#endif

    if ((device->classes & INPUT_DEVICE_CLASS_KEYBOARD) != 0) {
        char tmpfn[sizeof(name)];
        char keylayoutFilename[300];

        // a more descriptive name
        device->name = name;

        // replace all the spaces with underscores
        strcpy(tmpfn, name);
        for (char *p = strchr(tmpfn, ' '); p && *p; p = strchr(tmpfn, ' '))
            *p = '_';

        // find the .kl file we need for this device
        const char* root = getenv("ANDROID_ROOT");
        snprintf(keylayoutFilename, sizeof(keylayoutFilename),
                 "%s/usr/keylayout/%s.kl", root, tmpfn);
        bool defaultKeymap = false;
        if (access(keylayoutFilename, R_OK)) {
            snprintf(keylayoutFilename, sizeof(keylayoutFilename),
                     "%s/usr/keylayout/%s", root, "qwerty.kl");
            defaultKeymap = true;
        }
        status_t status = device->layoutMap->load(keylayoutFilename);
        if (status) {
            LOGE("Error %d loading key layout.", status);
        }

        // tell the world about the devname (the descriptive name)
        if (!mHaveFirstKeyboard && !defaultKeymap && strstr(name, "-keypad")) {
            // the built-in keyboard has a well-known device ID of 0,
            // this device better not go away.
            mHaveFirstKeyboard = true;
            mFirstKeyboardId = device->id;
            property_set("hw.keyboards.0.devname", name);
        } else {
            // ensure mFirstKeyboardId is set to -something-.
            if (mFirstKeyboardId == 0) {
                mFirstKeyboardId = device->id;
            }
        }
        char propName[100];
        sprintf(propName, "hw.keyboards.%u.devname", device->id);
        property_set(propName, name);

        // 'Q' key support = cheap test of whether this is an alpha-capable kbd
        if (hasKeycodeLocked(device, AKEYCODE_Q)) {
            device->classes |= INPUT_DEVICE_CLASS_ALPHAKEY;
        }
        
        // See if this device has a DPAD.
        if (hasKeycodeLocked(device, AKEYCODE_DPAD_UP) &&
                hasKeycodeLocked(device, AKEYCODE_DPAD_DOWN) &&
                hasKeycodeLocked(device, AKEYCODE_DPAD_LEFT) &&
                hasKeycodeLocked(device, AKEYCODE_DPAD_RIGHT) &&
                hasKeycodeLocked(device, AKEYCODE_DPAD_CENTER)) {
            device->classes |= INPUT_DEVICE_CLASS_DPAD;
        }
        
        // See if this device has a gamepad.
        for (size_t i = 0; i < sizeof(GAMEPAD_KEYCODES)/sizeof(GAMEPAD_KEYCODES[0]); i++) {
            if (hasKeycodeLocked(device, GAMEPAD_KEYCODES[i])) {
                device->classes |= INPUT_DEVICE_CLASS_GAMEPAD;
                break;
            }
        }

        LOGI("New keyboard: device->id=0x%x devname='%s' propName='%s' keylayout='%s'\n",
                device->id, name, propName, keylayoutFilename);
    }

    // If the device isn't recognized as something we handle, don't monitor it.
    if (device->classes == 0) {
        LOGV("Dropping device %s %p, id = %d\n", deviceName, device, devid);
        close(fd);
        delete device;
        return -1;
    }

    LOGI("New device: path=%s name=%s id=0x%x (of 0x%x) index=%d fd=%d classes=0x%x\n",
         deviceName, name, device->id, mNumDevicesById, mFDCount, fd, device->classes);
         
    LOGV("Adding device %s %p at %d, id = %d, classes = 0x%x\n",
         deviceName, device, mFDCount, devid, device->classes);

    mDevicesById[devid].device = device;
    device->next = mOpeningDevices;
    mOpeningDevices = device;
    mDevices[mFDCount] = device;

    mFDCount++;
    return 0;
}

bool EventHub::hasKeycodeLocked(device_t* device, int keycode) const
{
    if (device->keyBitmask == NULL || device->layoutMap == NULL) {
        return false;
    }
    
    Vector<int32_t> scanCodes;
    device->layoutMap->findScancodes(keycode, &scanCodes);
    const size_t N = scanCodes.size();
    for (size_t i=0; i<N && i<=KEY_MAX; i++) {
        int32_t sc = scanCodes.itemAt(i);
        if (sc >= 0 && sc <= KEY_MAX && test_bit(sc, device->keyBitmask)) {
            return true;
        }
    }
    
    return false;
}

int EventHub::closeDevice(const char *deviceName) {
    AutoMutex _l(mLock);

    int i;
    for(i = 1; i < mFDCount; i++) {
        if(strcmp(mDevices[i]->path.string(), deviceName) == 0) {
            //LOGD("remove device %d: %s\n", i, deviceName);
            device_t* device = mDevices[i];
            
            LOGI("Removed device: path=%s name=%s id=0x%x (of 0x%x) index=%d fd=%d classes=0x%x\n",
                 device->path.string(), device->name.string(), device->id,
                 mNumDevicesById, mFDCount, mFDs[i].fd, device->classes);
         
            // Clear this device's entry.
            int index = (device->id&ID_MASK);
            mDevicesById[index].device = NULL;
            
            // Close the file descriptor and compact the fd array.
            close(mFDs[i].fd);
            int count = mFDCount - i - 1;
            memmove(mDevices + i, mDevices + i + 1, sizeof(mDevices[0]) * count);
            memmove(mFDs + i, mFDs + i + 1, sizeof(mFDs[0]) * count);
            mFDCount--;

#ifdef EV_SW
            for (int j=0; j<EV_SW; j++) {
                if (mSwitches[j] == device->id) {
                    mSwitches[j] = 0;
                }
            }
#endif
            
            device->next = mClosingDevices;
            mClosingDevices = device;

            if (device->id == mFirstKeyboardId) {
                LOGW("built-in keyboard device %s (id=%d) is closing! the apps will not like this",
                        device->path.string(), mFirstKeyboardId);
                mFirstKeyboardId = 0;
                property_set("hw.keyboards.0.devname", NULL);
            }
            // clear the property
            char propName[100];
            sprintf(propName, "hw.keyboards.%u.devname", device->id);
            property_set(propName, NULL);
            return 0;
        }
    }
    LOGE("remove device: %s not found\n", deviceName);
    return -1;
}

int EventHub::readNotify(int nfd) {
#ifdef HAVE_INOTIFY
    int res;
    char devname[PATH_MAX];
    char *filename;
    char event_buf[512];
    int event_size;
    int event_pos = 0;
    struct inotify_event *event;

    LOGV("EventHub::readNotify nfd: %d\n", nfd);
    res = read(nfd, event_buf, sizeof(event_buf));
    if(res < (int)sizeof(*event)) {
        if(errno == EINTR)
            return 0;
        LOGW("could not get event, %s\n", strerror(errno));
        return 1;
    }
    //printf("got %d bytes of event information\n", res);

    strcpy(devname, device_path);
    filename = devname + strlen(devname);
    *filename++ = '/';

    while(res >= (int)sizeof(*event)) {
        event = (struct inotify_event *)(event_buf + event_pos);
        //printf("%d: %08x \"%s\"\n", event->wd, event->mask, event->len ? event->name : "");
        if(event->len) {
            strcpy(filename, event->name);
            if(event->mask & IN_CREATE) {
                openDevice(devname);
            }
            else {
                closeDevice(devname);
            }
        }
        event_size = sizeof(*event) + event->len;
        res -= event_size;
        event_pos += event_size;
    }
#endif
    return 0;
}


int EventHub::scanDir(const char *dirname)
{
    char devname[PATH_MAX];
    char *filename;
    DIR *dir;
    struct dirent *de;
    dir = opendir(dirname);
    if(dir == NULL)
        return -1;
    strcpy(devname, dirname);
    filename = devname + strlen(devname);
    *filename++ = '/';
    while((de = readdir(dir))) {
        if(de->d_name[0] == '.' &&
           (de->d_name[1] == '\0' ||
            (de->d_name[1] == '.' && de->d_name[2] == '\0')))
            continue;
        strcpy(filename, de->d_name);
        openDevice(devname);
    }
    closedir(dir);
    return 0;
}

void EventHub::dump(String8& dump) {
    dump.append("Event Hub State:\n");

    { // acquire lock
        AutoMutex _l(mLock);

        dump.appendFormat(INDENT "HaveFirstKeyboard: %s\n", toString(mHaveFirstKeyboard));
        dump.appendFormat(INDENT "FirstKeyboardId: 0x%x\n", mFirstKeyboardId);

        dump.append(INDENT "Devices:\n");

        for (int i = 0; i < mNumDevicesById; i++) {
            const device_t* device = mDevicesById[i].device;
            if (device) {
                if (mFirstKeyboardId == device->id) {
                    dump.appendFormat(INDENT2 "0x%x: %s (aka device 0 - first keyboard)\n",
                            device->id, device->name.string());
                } else {
                    dump.appendFormat(INDENT2 "0x%x: %s\n", device->id, device->name.string());
                }
                dump.appendFormat(INDENT3 "Classes: 0x%08x\n", device->classes);
                dump.appendFormat(INDENT3 "Path: %s\n", device->path.string());
                dump.appendFormat(INDENT3 "KeyLayoutFile: %s\n", device->keylayoutFilename.string());
            }
        }
    } // release lock
}

}; // namespace android
