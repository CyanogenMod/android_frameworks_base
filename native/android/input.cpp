/*
 * Copyright (C) 2009 The Android Open Source Project
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

#define LOG_TAG "input"
#include <utils/Log.h>

#include <android/input.h>
#include <androidfw/Input.h>
#include <androidfw/InputTransport.h>
#include <utils/Looper.h>
#include <utils/RefBase.h>
#include <utils/Vector.h>

#include <android_runtime/android_app_NativeActivity.h>

#include <poll.h>
#include <errno.h>

using android::InputEvent;
using android::KeyEvent;
using android::MotionEvent;
using android::sp;
using android::Vector;

int32_t AInputEvent_getType(const AInputEvent* event) {
    return static_cast<const InputEvent*>(event)->getType();
}

int32_t AInputEvent_getDeviceId(const AInputEvent* event) {
    return static_cast<const InputEvent*>(event)->getDeviceId();
}

int32_t AInputEvent_getSource(const AInputEvent* event) {
    return static_cast<const InputEvent*>(event)->getSource();
}

int32_t AKeyEvent_getAction(const AInputEvent* key_event) {
    return static_cast<const KeyEvent*>(key_event)->getAction();
}

int32_t AKeyEvent_getFlags(const AInputEvent* key_event) {
    return static_cast<const KeyEvent*>(key_event)->getFlags();
}

int32_t AKeyEvent_getKeyCode(const AInputEvent* key_event) {
    return static_cast<const KeyEvent*>(key_event)->getKeyCode();
}

int32_t AKeyEvent_getScanCode(const AInputEvent* key_event) {
    return static_cast<const KeyEvent*>(key_event)->getScanCode();
}

int32_t AKeyEvent_getMetaState(const AInputEvent* key_event) {
    return static_cast<const KeyEvent*>(key_event)->getMetaState();
}
int32_t AKeyEvent_getRepeatCount(const AInputEvent* key_event) {
    return static_cast<const KeyEvent*>(key_event)->getRepeatCount();
}

int64_t AKeyEvent_getDownTime(const AInputEvent* key_event) {
    return static_cast<const KeyEvent*>(key_event)->getDownTime();
}


int64_t AKeyEvent_getEventTime(const AInputEvent* key_event) {
    return static_cast<const KeyEvent*>(key_event)->getEventTime();
}

int32_t AMotionEvent_getAction(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getAction();
}

int32_t AMotionEvent_getFlags(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getFlags();
}

int32_t AMotionEvent_getMetaState(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getMetaState();
}

int32_t AMotionEvent_getButtonState(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getButtonState();
}

int32_t AMotionEvent_getEdgeFlags(const AInputEvent* motion_event) {
    return reinterpret_cast<const MotionEvent*>(motion_event)->getEdgeFlags();
}

int64_t AMotionEvent_getDownTime(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getDownTime();
}

int64_t AMotionEvent_getEventTime(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getEventTime();
}

float AMotionEvent_getXOffset(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getXOffset();
}

float AMotionEvent_getYOffset(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getYOffset();
}

float AMotionEvent_getXPrecision(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getXPrecision();
}

float AMotionEvent_getYPrecision(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getYPrecision();
}

size_t AMotionEvent_getPointerCount(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getPointerCount();
}

int32_t AMotionEvent_getPointerId(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getPointerId(pointer_index);
}

int32_t AMotionEvent_getToolType(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getToolType(pointer_index);
}

float AMotionEvent_getRawX(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getRawX(pointer_index);
}

float AMotionEvent_getRawY(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getRawY(pointer_index);
}

float AMotionEvent_getX(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getX(pointer_index);
}

float AMotionEvent_getY(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getY(pointer_index);
}

float AMotionEvent_getPressure(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getPressure(pointer_index);
}

float AMotionEvent_getSize(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getSize(pointer_index);
}

float AMotionEvent_getTouchMajor(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getTouchMajor(pointer_index);
}

float AMotionEvent_getTouchMinor(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getTouchMinor(pointer_index);
}

float AMotionEvent_getToolMajor(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getToolMajor(pointer_index);
}

float AMotionEvent_getToolMinor(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getToolMinor(pointer_index);
}

float AMotionEvent_getOrientation(const AInputEvent* motion_event, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getOrientation(pointer_index);
}

float AMotionEvent_getAxisValue(const AInputEvent* motion_event,
        int32_t axis, size_t pointer_index) {
    return static_cast<const MotionEvent*>(motion_event)->getAxisValue(axis, pointer_index);
}

size_t AMotionEvent_getHistorySize(const AInputEvent* motion_event) {
    return static_cast<const MotionEvent*>(motion_event)->getHistorySize();
}

int64_t AMotionEvent_getHistoricalEventTime(const AInputEvent* motion_event,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalEventTime(
            history_index);
}

float AMotionEvent_getHistoricalRawX(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalRawX(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalRawY(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalRawY(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalX(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalX(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalY(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalY(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalPressure(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalPressure(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalSize(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalSize(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalTouchMajor(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalTouchMajor(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalTouchMinor(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalTouchMinor(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalToolMajor(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalToolMajor(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalToolMinor(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalToolMinor(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalOrientation(const AInputEvent* motion_event, size_t pointer_index,
        size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalOrientation(
            pointer_index, history_index);
}

float AMotionEvent_getHistoricalAxisValue(const AInputEvent* motion_event,
        int32_t axis, size_t pointer_index, size_t history_index) {
    return static_cast<const MotionEvent*>(motion_event)->getHistoricalAxisValue(
            axis, pointer_index, history_index);
}


void AInputQueue_attachLooper(AInputQueue* queue, ALooper* looper,
        int ident, ALooper_callbackFunc callback, void* data) {
    queue->attachLooper(looper, ident, callback, data);
}

void AInputQueue_detachLooper(AInputQueue* queue) {
    queue->detachLooper();
}

int32_t AInputQueue_hasEvents(AInputQueue* queue) {
    return queue->hasEvents();
}

int32_t AInputQueue_getEvent(AInputQueue* queue, AInputEvent** outEvent) {
    return queue->getEvent(outEvent);
}

int32_t AInputQueue_preDispatchEvent(AInputQueue* queue, AInputEvent* event) {
    return queue->preDispatchEvent(event) ? 1 : 0;
}

void AInputQueue_finishEvent(AInputQueue* queue, AInputEvent* event, int handled) {
    queue->finishEvent(event, handled != 0, false);
}
