/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2012 The CyanogenMod Project
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

#include <utils/Errors.h>

#include <hardware/sensors.h>

#include "RotationVectorSensor2.h"
#include "vec.h"

namespace android {
// ---------------------------------------------------------------------------

ANDROID_SINGLETON_STATIC_INSTANCE(RotationVectorSensor2)

RotationVectorSensor2::RotationVectorSensor2()
    : mSensorDevice(SensorDevice::getInstance()),
      mEnabled(false), mHasData(false)
{
    sensor_t const* list;
    ssize_t count = mSensorDevice.getSensorList(&list);
    if (count > 0) {
        for (size_t i=0 ; i<size_t(count) ; i++) {
            if (list[i].type == SENSOR_TYPE_ORIENTATION) {
                mOrientation = Sensor(list + i);
            }
        }
    }
}

bool RotationVectorSensor2::process(sensors_event_t* outEvent,
        const sensors_event_t& event)
{
    if (mHasData && event.type == SENSOR_TYPE_ACCELEROMETER) {
        *outEvent = event;
        outEvent->data[0] = mData[1];
        outEvent->data[1] = mData[2];
        outEvent->data[2] = mData[3];
        outEvent->data[3] = mData[0];
        outEvent->sensor = '_rv2';
        outEvent->type = SENSOR_TYPE_ROTATION_VECTOR;

        mHasData = false;
        return true;
    }
    return false;
}

status_t RotationVectorSensor2::activate(void* ident, bool enabled) {
    mEnabled = enabled;
    return mSensorDevice.activate(this, mOrientation.getHandle(), enabled);
}

status_t RotationVectorSensor2::setDelay(void* ident, int handle, int64_t ns) {
    return mSensorDevice.setDelay(this, mOrientation.getHandle(), ns);
}

Sensor RotationVectorSensor2::getSensor() const {
    sensor_t hwSensor;
    hwSensor.name       = "Rotation Vector Sensor 2";
    hwSensor.vendor     = "CyanogenMod Project";
    hwSensor.version    = 1;
    hwSensor.handle     = '_rv2';
    hwSensor.type       = SENSOR_TYPE_ROTATION_VECTOR;
    hwSensor.maxRange   = 1;
    hwSensor.resolution = 1.0f / (1<<24);
    hwSensor.power      = mOrientation.getPowerUsage();
    hwSensor.minDelay   = mOrientation.getMinDelay();
    Sensor sensor(&hwSensor);
    return sensor;
}

void RotationVectorSensor2::process(const sensors_event_t& event) {
    if (event.type == SENSOR_TYPE_ORIENTATION) {
        const vec3_t v(event.data);

        // Convert euler angle to quarternion
        const float deg2rad = M_PI / 180;
        float halfAzi = (v[0] / 2) * deg2rad;
        float halfPitch = (v[1] / 2) * deg2rad;
        float halfRoll = (-v[2] / 2) * deg2rad; // roll is reverse

        float c1 = cosf(halfAzi);
        float s1 = sinf(halfAzi);
        float c2 = cosf(halfPitch);
        float s2 = sinf(halfPitch);
        float c3 = cosf(halfRoll);
        float s3 = sinf(halfRoll);
        mData[0] = c1*c2*c3 - s1*s2*s3;
        mData[1] = c1*s2*c3 - s1*c2*s3;
        mData[2] = c1*c2*s3 + s1*s2*c3;
        mData[3] = s1*c2*c3 + c1*s2*s3;

        // Misc fixes (a.k.a. "magic")
        if (v[0] < 180) {
            mData[1] = -mData[1];
            mData[3] = -mData[3];
        } else {
            mData[2] = -mData[2];
        }

        mHasData = true;
    }
}

// ---------------------------------------------------------------------------
}; // namespace android

