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

#include <stdint.h>
#include <math.h>
#include <sys/types.h>

#include <utils/Atomic.h>
#include <utils/Errors.h>
#include <utils/Singleton.h>

#include <binder/BinderService.h>
#include <binder/Parcel.h>
#include <binder/IServiceManager.h>

#include <hardware/sensors.h>

#include "SensorDevice.h"

#ifdef USE_LGE_ALS_DUMMY
#include <fcntl.h>
#endif

namespace android {
// ---------------------------------------------------------------------------
class BatteryService : public Singleton<BatteryService> {
    static const int TRANSACTION_noteStartSensor = IBinder::FIRST_CALL_TRANSACTION + 3;
    static const int TRANSACTION_noteStopSensor = IBinder::FIRST_CALL_TRANSACTION + 4;
    static const String16 DESCRIPTOR;

    friend class Singleton<BatteryService>;
    sp<IBinder> mBatteryStatService;

    BatteryService() {
        const sp<IServiceManager> sm(defaultServiceManager());
        if (sm != NULL) {
            const String16 name("batteryinfo");
            mBatteryStatService = sm->getService(name);
        }
    }

    status_t noteStartSensor(int uid, int handle) {
        Parcel data, reply;
        data.writeInterfaceToken(DESCRIPTOR);
        data.writeInt32(uid);
        data.writeInt32(handle);
        status_t err = mBatteryStatService->transact(
                TRANSACTION_noteStartSensor, data, &reply, 0);
        err = reply.readExceptionCode();
        return err;
    }

    status_t noteStopSensor(int uid, int handle) {
        Parcel data, reply;
        data.writeInterfaceToken(DESCRIPTOR);
        data.writeInt32(uid);
        data.writeInt32(handle);
        status_t err = mBatteryStatService->transact(
                TRANSACTION_noteStopSensor, data, &reply, 0);
        err = reply.readExceptionCode();
        return err;
    }

public:
    void enableSensor(int handle) {
        if (mBatteryStatService != 0) {
            int uid = IPCThreadState::self()->getCallingUid();
            int64_t identity = IPCThreadState::self()->clearCallingIdentity();
            noteStartSensor(uid, handle);
            IPCThreadState::self()->restoreCallingIdentity(identity);
        }
    }
    void disableSensor(int handle) {
        if (mBatteryStatService != 0) {
            int uid = IPCThreadState::self()->getCallingUid();
            int64_t identity = IPCThreadState::self()->clearCallingIdentity();
            noteStopSensor(uid, handle);
            IPCThreadState::self()->restoreCallingIdentity(identity);
        }
    }
};

const String16 BatteryService::DESCRIPTOR("com.android.internal.app.IBatteryStats");

ANDROID_SINGLETON_STATIC_INSTANCE(BatteryService)

// ---------------------------------------------------------------------------

ANDROID_SINGLETON_STATIC_INSTANCE(SensorDevice)

#ifdef USE_LGE_ALS_DUMMY
static ssize_t addDummyLGESensor(sensor_t const **list, ssize_t count) {
    struct sensor_t dummy_light =     {
                  name            : "Dummy LGE-Star light sensor",
                  vendor          : "CyanogenMod",
                  version         : 1,
                  handle          : SENSOR_TYPE_LIGHT,
                  type            : SENSOR_TYPE_LIGHT,
                  maxRange        : 20,
                  resolution      : 0.1,
                  power           : 20,
    };
    void * new_list = malloc((count+1)*sizeof(sensor_t));
    new_list = memcpy(new_list, *list, count*sizeof(sensor_t));
    ((sensor_t *)new_list)[count] = dummy_light;
    *list = (sensor_t const *)new_list;
    count++;
    return count;
}
#endif

SensorDevice::SensorDevice()
    :  mSensorDevice(0),
       mOldSensorsEnabled(0),
       mOldSensorsCompatMode(false),
       mSensorModule(0)
{
    status_t err = hw_get_module(SENSORS_HARDWARE_MODULE_ID,
            (hw_module_t const**)&mSensorModule);

    LOGE_IF(err, "couldn't load %s module (%s)",
            SENSORS_HARDWARE_MODULE_ID, strerror(-err));

    if (mSensorModule) {
#ifdef ENABLE_SENSORS_COMPAT
#ifdef SENSORS_NO_OPEN_CHECK
        sensors_control_open(&mSensorModule->common, &mSensorControlDevice) ;
        sensors_data_open(&mSensorModule->common, &mSensorDataDevice) ;
        mOldSensorsCompatMode = true;
#else
        if (!sensors_control_open(&mSensorModule->common, &mSensorControlDevice)) {
            if (sensors_data_open(&mSensorModule->common, &mSensorDataDevice)) {
                LOGE("couldn't open data device in backwards-compat mode for module %s (%s)",
                        SENSORS_HARDWARE_MODULE_ID, strerror(-err));
            } else {
                LOGD("Opened sensors in backwards compat mode");
                mOldSensorsCompatMode = true;
            }
        } else {
            LOGE("couldn't open control device in backwards-compat mode for module %s (%s)",
                    SENSORS_HARDWARE_MODULE_ID, strerror(-err));
        }
#endif
#else
        err = sensors_open(&mSensorModule->common, &mSensorDevice);
        LOGE_IF(err, "couldn't open device for module %s (%s)",
                SENSORS_HARDWARE_MODULE_ID, strerror(-err));
#endif


        if (mSensorDevice || mOldSensorsCompatMode) {
            sensor_t const* list;
            ssize_t count = mSensorModule->get_sensors_list(mSensorModule, &list);

#ifdef USE_LGE_ALS_DUMMY
            count = addDummyLGESensor(&list, count);
#endif

            if (mOldSensorsCompatMode) {
                mOldSensorsList = list;
                mOldSensorsCount = count;
                mSensorDataDevice->data_open(mSensorDataDevice,
                            mSensorControlDevice->open_data_source(mSensorControlDevice));
            }

            mActivationCount.setCapacity(count);
            Info model;
            for (size_t i=0 ; i<size_t(count) ; i++) {
                mActivationCount.add(list[i].handle, model);
                if (mOldSensorsCompatMode) {
                    mSensorControlDevice->activate(mSensorControlDevice, list[i].handle, 0);
                } else {
                    mSensorDevice->activate(mSensorDevice, list[i].handle, 0);
                }
            }
        }
    }
}

void SensorDevice::dump(String8& result, char* buffer, size_t SIZE)
{
    if (!mSensorModule) return;
    sensor_t const* list;
    ssize_t count = mSensorModule->get_sensors_list(mSensorModule, &list);

    snprintf(buffer, SIZE, "%d h/w sensors:\n", int(count));
    result.append(buffer);

    Mutex::Autolock _l(mLock);
    for (size_t i=0 ; i<size_t(count) ; i++) {
        snprintf(buffer, SIZE, "handle=0x%08x, active-count=%d\n",
                list[i].handle,
                mActivationCount.valueFor(list[i].handle).rates.size());
        result.append(buffer);
    }
}

ssize_t SensorDevice::getSensorList(sensor_t const** list) {
    if (!mSensorModule) return NO_INIT;
    ssize_t count = mSensorModule->get_sensors_list(mSensorModule, list);

#ifdef USE_LGE_ALS_DUMMY
    return addDummyLGESensor(list, count);
#endif
    return count;
}

status_t SensorDevice::initCheck() const {
    return (mSensorDevice || mOldSensorsCompatMode) && mSensorModule ? NO_ERROR : NO_INIT;
}

ssize_t SensorDevice::poll(sensors_event_t* buffer, size_t count) {
    if (!mSensorDevice && !mOldSensorsCompatMode) return NO_INIT;
    if (mOldSensorsCompatMode) {
        size_t pollsDone = 0;
        //LOGV("%d buffers were requested",count);
        while (!mOldSensorsEnabled) {
            sleep(1);
            LOGV("Waiting...");
        }
        while (pollsDone < (size_t)mOldSensorsEnabled && pollsDone < count) {
            sensors_data_t oldBuffer;
            long result =  mSensorDataDevice->poll(mSensorDataDevice, &oldBuffer);
            int sensorType = -1;
            int maxRange = -1;
 
            if (result == 0x7FFFFFFF) {
                continue;
            } else {
                /* the old data_poll is supposed to return a handle,
                 * which has to be mapped to the type. */
                for (size_t i=0 ; i<size_t(mOldSensorsCount) && sensorType < 0 ; i++) {
                    if (mOldSensorsList[i].handle == result) {
                        sensorType = mOldSensorsList[i].type;
                        maxRange = mOldSensorsList[i].maxRange;
                        LOGV("mapped sensor type to %d",sensorType);
                    }
                }
            }
            if ( sensorType <= 0 ||
                 sensorType > SENSOR_TYPE_ROTATION_VECTOR) {
                LOGV("Useless output at round %u from %d",pollsDone, oldBuffer.sensor);
                count--;
                continue;
            }
            buffer[pollsDone].version = sizeof(struct sensors_event_t);
            buffer[pollsDone].timestamp = oldBuffer.time;
            buffer[pollsDone].type = sensorType;
            buffer[pollsDone].sensor = result;
            /* This part is a union. Regardless of the sensor type,
             * we only need to copy a sensors_vec_t and a float */
            buffer[pollsDone].acceleration = oldBuffer.vector;
            buffer[pollsDone].temperature = oldBuffer.temperature;
            LOGV("Adding results for sensor %d", buffer[pollsDone].sensor);
            /* The ALS and PS sensors only report values on change,
             * instead of a data "stream" like the others. So don't wait
             * for the number of requested samples to fill, and deliver
             * it immediately */
            if (sensorType == SENSOR_TYPE_PROXIMITY) {
#ifdef FOXCONN_SENSORS
            /* Fix ridiculous API breakages from FIH. */
            /* These idiots are returning -1 for FAR, and 1 for NEAR */
                if (buffer[pollsDone].distance > 0) {
                    buffer[pollsDone].distance = 0;
                } else {
                    buffer[pollsDone].distance = 1;
                }
#elif defined(PROXIMITY_LIES)
                if (buffer[pollsDone].distance >= PROXIMITY_LIES)
			buffer[pollsDone].distance = maxRange;
#endif
                return pollsDone+1;
            } else if (sensorType == SENSOR_TYPE_LIGHT) {
                return pollsDone+1;
            }
            pollsDone++;
        }
        return pollsDone;
    } else {
        return mSensorDevice->poll(mSensorDevice, buffer, count);
    }
}

status_t SensorDevice::activate(void* ident, int handle, int enabled)
{
    if (!mSensorDevice && !mOldSensorsCompatMode) return NO_INIT;
    status_t err(NO_ERROR);
    bool actuateHardware = false;

#ifdef USE_LGE_ALS_DUMMY

    if (handle == SENSOR_TYPE_LIGHT) {
        int nwr, ret, fd;
        char value[2];

#ifdef USE_LGE_ALS_OMAP3
        fd = open("/sys/class/leds/lcd-backlight/als", O_RDWR);
        if(fd < 0)
            return -ENODEV;

        nwr = sprintf(value, "%s\n", enabled ? "1" : "0");
        write(fd, value, nwr);
        close(fd);
#else
        fd = open("/sys/devices/platform/star_aat2870.0/lsensor_onoff", O_RDWR);
        if(fd < 0)
            return -ENODEV;

        nwr = sprintf(value, "%s\n", enabled ? "1" : "0");
        write(fd, value, nwr);
        close(fd);
        fd = open("/sys/devices/platform/star_aat2870.0/alc", O_RDWR);
        if(fd < 0)
            return -ENODEV;

        nwr = sprintf(value, "%s\n", enabled ? "2" : "0");
        write(fd, value, nwr);
        close(fd);
#endif

        return 0;

    }
#endif
    Info& info( mActivationCount.editValueFor(handle) );
    if (enabled) {
        Mutex::Autolock _l(mLock);
        if (info.rates.indexOfKey(ident) < 0) {
            info.rates.add(ident, DEFAULT_EVENTS_PERIOD);
            actuateHardware = true;
        } else {
            // sensor was already activated for this ident
        }
    } else {
        Mutex::Autolock _l(mLock);
        if (info.rates.removeItem(ident) >= 0) {
            if (info.rates.size() == 0) {
                actuateHardware = true;
            }
        } else {
            // sensor wasn't enabled for this ident
        }
    }

    if (actuateHardware) {
        if (mOldSensorsCompatMode) {
            if (enabled)
                mOldSensorsEnabled++;
            else if (mOldSensorsEnabled > 0)
                mOldSensorsEnabled--;
            LOGV("Activation for %d (%d)",handle,enabled);
            if (enabled) {
                mSensorControlDevice->wake(mSensorControlDevice);
            }
            err = mSensorControlDevice->activate(mSensorControlDevice, handle, enabled);
            err = 0;
        } else {
            err = mSensorDevice->activate(mSensorDevice, handle, enabled);
        }
        if (enabled) {
            LOGE_IF(err, "Error activating sensor %d (%s)", handle, strerror(-err));
            if (err == 0) {
                BatteryService::getInstance().enableSensor(handle);
            }
        } else {
            if (err == 0) {
                BatteryService::getInstance().disableSensor(handle);
            }
        }
    }

    if (!actuateHardware || enabled) {
        Mutex::Autolock _l(mLock);
        nsecs_t ns = info.rates.valueAt(0);
        for (size_t i=1 ; i<info.rates.size() ; i++) {
            if (info.rates.valueAt(i) < ns) {
                nsecs_t cur = info.rates.valueAt(i);
                if (cur < ns) {
                    ns = cur;
                }
            }
        }
        if (mOldSensorsCompatMode) {
            mSensorControlDevice->set_delay(mSensorControlDevice, (ns/(1000*1000)));
        } else {
            mSensorDevice->setDelay(mSensorDevice, handle, ns);
        }
    }

    return err;
}

status_t SensorDevice::setDelay(void* ident, int handle, int64_t ns)
{
    if (!mSensorDevice && !mOldSensorsCompatMode) return NO_INIT;
    Info& info( mActivationCount.editValueFor(handle) );
    { // scope for lock
        Mutex::Autolock _l(mLock);
        ssize_t index = info.rates.indexOfKey(ident);
        if (index < 0) return BAD_INDEX;
        info.rates.editValueAt(index) = ns;
        ns = info.rates.valueAt(0);
        for (size_t i=1 ; i<info.rates.size() ; i++) {
            nsecs_t cur = info.rates.valueAt(i);
            if (cur < ns) {
                ns = cur;
            }
        }
    }
	if (mOldSensorsCompatMode) {
		return mSensorControlDevice->set_delay(mSensorControlDevice, (ns/(1000*1000)));
	} else {
		return mSensorDevice->setDelay(mSensorDevice, handle, ns);
	}
}

// ---------------------------------------------------------------------------
}; // namespace android

