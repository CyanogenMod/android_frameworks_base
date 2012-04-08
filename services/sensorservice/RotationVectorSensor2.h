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

#ifndef ANDROID_ROTATION_VECTOR_SENSOR2_H
#define ANDROID_ROTATION_VECTOR_SENSOR2_H

#include <stdint.h>
#include <sys/types.h>

#include <gui/Sensor.h>

#include "SensorDevice.h"
#include "SensorInterface.h"

#include "quat.h"

// ---------------------------------------------------------------------------
namespace android {
// ---------------------------------------------------------------------------

class RotationVectorSensor2 : public SensorInterface,
                              public Singleton<RotationVectorSensor2> {
    friend class Singleton<RotationVectorSensor2>;

    SensorDevice& mSensorDevice;

    Sensor mOrientation;
    bool mEnabled;
    bool mHasData;
    quat_t mData;

public:
    RotationVectorSensor2();
    virtual bool process(sensors_event_t* outEvent,
            const sensors_event_t& event);
    virtual status_t activate(void* ident, bool enabled);
    virtual status_t setDelay(void* ident, int handle, int64_t ns);
    virtual Sensor getSensor() const;
    virtual bool isVirtual() const { return true; }
    bool isEnabled() const { return mEnabled; }

    // Incoming data
    void process(const sensors_event_t& event);
};

// ---------------------------------------------------------------------------
}; // namespace android

#endif // ANDROID_ROTATION_VECTOR2_SENSOR_H
