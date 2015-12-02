    /*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.hardware;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import dalvik.system.CloseGuard;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Sensor manager implementation that communicates with the built-in
 * system sensors.
 *
 * @hide
 */
public class SystemSensorManager extends SensorManager {
    private static native void nativeClassInit();
    private static native long nativeCreate(String opPackageName);
    private static native boolean nativeGetSensorAtIndex(long nativeInstance,
            Sensor sensor, int index);
    private static native boolean nativeIsDataInjectionEnabled(long nativeInstance);

    private static boolean sSensorModuleInitialized = false;
    private static InjectEventQueue mInjectEventQueue = null;

    private final Object mLock = new Object();

    private final ArrayList<Sensor> mFullSensorsList = new ArrayList<>();
    private final SparseArray<Sensor> mHandleToSensor = new SparseArray<>();

    // Listener list
    private final HashMap<SensorEventListener, SensorEventQueue> mSensorListeners =
            new HashMap<SensorEventListener, SensorEventQueue>();
    private final HashMap<TriggerEventListener, TriggerEventQueue> mTriggerListeners =
            new HashMap<TriggerEventListener, TriggerEventQueue>();

    // Looper associated with the context in which this instance was created.
    private final Looper mMainLooper;
    private final int mTargetSdkLevel;
    private final Context mContext;
    private final long mNativeInstance;

    /** {@hide} */
    public SystemSensorManager(Context context, Looper mainLooper) {
        mMainLooper = mainLooper;
        mTargetSdkLevel = context.getApplicationInfo().targetSdkVersion;
        mContext = context;
        mNativeInstance = nativeCreate(context.getOpPackageName());

        synchronized(mLock) {
            if (!sSensorModuleInitialized) {
                sSensorModuleInitialized = true;
                nativeClassInit();
            }
        }

        // initialize the sensor list
        for (int index = 0;;++index) {
            Sensor sensor = new Sensor();
            if (!nativeGetSensorAtIndex(mNativeInstance, sensor, index)) break;
            mFullSensorsList.add(sensor);
            mHandleToSensor.append(sensor.getHandle(), sensor);
        }
    }


    /** @hide */
    @Override
    protected List<Sensor> getFullSensorList() {
        return mFullSensorsList;
    }


    /** @hide */
    @Override
    protected boolean registerListenerImpl(SensorEventListener listener, Sensor sensor,
            int delayUs, Handler handler, int maxBatchReportLatencyUs, int reservedFlags) {
        android.util.SeempLog.record_sensor_rate(381, sensor, delayUs);
        if (listener == null || sensor == null) {
            Log.e(TAG, "sensor or listener is null");
            return false;
        }
        // Trigger Sensors should use the requestTriggerSensor call.
        if (sensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT) {
            Log.e(TAG, "Trigger Sensors should use the requestTriggerSensor.");
            return false;
        }
        if (maxBatchReportLatencyUs < 0 || delayUs < 0) {
            Log.e(TAG, "maxBatchReportLatencyUs and delayUs should be non-negative");
            return false;
        }

        // Invariants to preserve:
        // - one Looper per SensorEventListener
        // - one Looper per SensorEventQueue
        // We map SensorEventListener to a SensorEventQueue, which holds the looper
        synchronized (mSensorListeners) {
            SensorEventQueue queue = mSensorListeners.get(listener);
            if (queue == null) {
                Looper looper = (handler != null) ? handler.getLooper() : mMainLooper;
                final String fullClassName = listener.getClass().getEnclosingClass() != null ?
                    listener.getClass().getEnclosingClass().getName() :
                    listener.getClass().getName();
                queue = new SensorEventQueue(listener, looper, this, fullClassName);
                if (!queue.addSensor(sensor, delayUs, maxBatchReportLatencyUs)) {
                    queue.dispose();
                    return false;
                }
                mSensorListeners.put(listener, queue);
                return true;
            } else {
                return queue.addSensor(sensor, delayUs, maxBatchReportLatencyUs);
            }
        }
    }

    /** @hide */
    @Override
    protected void unregisterListenerImpl(SensorEventListener listener, Sensor sensor) {
        android.util.SeempLog.record_sensor(382, sensor);
        // Trigger Sensors should use the cancelTriggerSensor call.
        if (sensor != null && sensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT) {
            return;
        }

        synchronized (mSensorListeners) {
            SensorEventQueue queue = mSensorListeners.get(listener);
            if (queue != null) {
                boolean result;
                if (sensor == null) {
                    result = queue.removeAllSensors();
                } else {
                    result = queue.removeSensor(sensor, true);
                }
                if (result && !queue.hasSensors()) {
                    mSensorListeners.remove(listener);
                    queue.dispose();
                }
            }
        }
    }

    /** @hide */
    @Override
    protected boolean requestTriggerSensorImpl(TriggerEventListener listener, Sensor sensor) {
        if (sensor == null) throw new IllegalArgumentException("sensor cannot be null");

        if (listener == null) throw new IllegalArgumentException("listener cannot be null");

        if (sensor.getReportingMode() != Sensor.REPORTING_MODE_ONE_SHOT) return false;

        synchronized (mTriggerListeners) {
            TriggerEventQueue queue = mTriggerListeners.get(listener);
            if (queue == null) {
                final String fullClassName = listener.getClass().getEnclosingClass() != null ?
                    listener.getClass().getEnclosingClass().getName() :
                    listener.getClass().getName();
                queue = new TriggerEventQueue(listener, mMainLooper, this, fullClassName);
                if (!queue.addSensor(sensor, 0, 0)) {
                    queue.dispose();
                    return false;
                }
                mTriggerListeners.put(listener, queue);
                return true;
            } else {
                return queue.addSensor(sensor, 0, 0);
            }
        }
    }

    /** @hide */
    @Override
    protected boolean cancelTriggerSensorImpl(TriggerEventListener listener, Sensor sensor,
            boolean disable) {
        if (sensor != null && sensor.getReportingMode() != Sensor.REPORTING_MODE_ONE_SHOT) {
            return false;
        }
        synchronized (mTriggerListeners) {
            TriggerEventQueue queue = mTriggerListeners.get(listener);
            if (queue != null) {
                boolean result;
                if (sensor == null) {
                    result = queue.removeAllSensors();
                } else {
                    result = queue.removeSensor(sensor, disable);
                }
                if (result && !queue.hasSensors()) {
                    mTriggerListeners.remove(listener);
                    queue.dispose();
                }
                return result;
            }
            return false;
        }
    }

    protected boolean flushImpl(SensorEventListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener cannot be null");

        synchronized (mSensorListeners) {
            SensorEventQueue queue = mSensorListeners.get(listener);
            if (queue == null) {
                return false;
            } else {
                return (queue.flush() == 0);
            }
        }
    }

    protected boolean initDataInjectionImpl(boolean enable) {
        synchronized (mLock) {
            if (enable) {
                boolean isDataInjectionModeEnabled = nativeIsDataInjectionEnabled(mNativeInstance);
                // The HAL does not support injection OR SensorService hasn't been set in DI mode.
                if (!isDataInjectionModeEnabled) {
                    Log.e(TAG, "Data Injection mode not enabled");
                    return false;
                }
                // Initialize a client for data_injection.
                if (mInjectEventQueue == null) {
                    mInjectEventQueue = new InjectEventQueue(mMainLooper, this,
                            mContext.getPackageName());
                }
            } else {
                // If data injection is being disabled clean up the native resources.
                if (mInjectEventQueue != null) {
                    mInjectEventQueue.dispose();
                    mInjectEventQueue = null;
                }
            }
            return true;
        }
    }

    protected boolean injectSensorDataImpl(Sensor sensor, float[] values, int accuracy,
            long timestamp) {
        synchronized (mLock) {
            if (mInjectEventQueue == null) {
                Log.e(TAG, "Data injection mode not activated before calling injectSensorData");
                return false;
            }
            int ret = mInjectEventQueue.injectSensorData(sensor.getHandle(), values, accuracy,
                                                         timestamp);
            // If there are any errors in data injection clean up the native resources.
            if (ret != 0) {
                mInjectEventQueue.dispose();
                mInjectEventQueue = null;
            }
            return ret == 0;
        }
    }

    /*
     * BaseEventQueue is the communication channel with the sensor service,
     * SensorEventQueue, TriggerEventQueue are subclases and there is one-to-one mapping between
     * the queues and the listeners. InjectEventQueue is also a sub-class which is a special case
     * where data is being injected into the sensor HAL through the sensor service. It is not
     * associated with any listener and there is one InjectEventQueue associated with a
     * SensorManager instance.
     */
    private static abstract class BaseEventQueue {
        private static native long nativeInitBaseEventQueue(long nativeManager,
                WeakReference<BaseEventQueue> eventQWeak, MessageQueue msgQ, float[] scratch,
                String packageName, int mode, String opPackageName);
        private static native int nativeEnableSensor(long eventQ, int handle, int rateUs,
                int maxBatchReportLatencyUs);
        private static native int nativeDisableSensor(long eventQ, int handle);
        private static native void nativeDestroySensorEventQueue(long eventQ);
        private static native int nativeFlushSensor(long eventQ);
        private static native int nativeInjectSensorData(long eventQ, int handle,
                float[] values,int accuracy, long timestamp);

        private long nSensorEventQueue;
        private final SparseBooleanArray mActiveSensors = new SparseBooleanArray();
        protected final SparseIntArray mSensorAccuracies = new SparseIntArray();
        protected final SparseBooleanArray mFirstEvent = new SparseBooleanArray();
        private final CloseGuard mCloseGuard = CloseGuard.get();
        private final float[] mScratch = new float[16];
        protected final SystemSensorManager mManager;

        protected static final int OPERATING_MODE_NORMAL = 0;
        protected static final int OPERATING_MODE_DATA_INJECTION = 1;

        BaseEventQueue(Looper looper, SystemSensorManager manager, int mode, String packageName) {
            if (packageName == null) packageName = "";
            nSensorEventQueue = nativeInitBaseEventQueue(manager.mNativeInstance,
                    new WeakReference<>(this), looper.getQueue(), mScratch,
                    packageName, mode, manager.mContext.getOpPackageName());
            mCloseGuard.open("dispose");
            mManager = manager;
        }

        public void dispose() {
            dispose(false);
        }

        public boolean addSensor(
                Sensor sensor, int delayUs, int maxBatchReportLatencyUs) {
            // Check if already present.
            int handle = sensor.getHandle();
            if (mActiveSensors.get(handle)) return false;

            // Get ready to receive events before calling enable.
            mActiveSensors.put(handle, true);
            addSensorEvent(sensor);
            if (enableSensor(sensor, delayUs, maxBatchReportLatencyUs) != 0) {
                // Try continuous mode if batching fails.
                if (maxBatchReportLatencyUs == 0 ||
                    maxBatchReportLatencyUs > 0 && enableSensor(sensor, delayUs, 0) != 0) {
                  removeSensor(sensor, false);
                  return false;
                }
            }
            return true;
        }

        public boolean removeAllSensors() {
            for (int i=0 ; i<mActiveSensors.size(); i++) {
                if (mActiveSensors.valueAt(i) == true) {
                    int handle = mActiveSensors.keyAt(i);
                    Sensor sensor = mManager.mHandleToSensor.get(handle);
                    if (sensor != null) {
                        disableSensor(sensor);
                        mActiveSensors.put(handle, false);
                        removeSensorEvent(sensor);
                    } else {
                        // it should never happen -- just ignore.
                    }
                }
            }
            return true;
        }

        public boolean removeSensor(Sensor sensor, boolean disable) {
            final int handle = sensor.getHandle();
            if (mActiveSensors.get(handle)) {
                if (disable) disableSensor(sensor);
                mActiveSensors.put(sensor.getHandle(), false);
                removeSensorEvent(sensor);
                return true;
            }
            return false;
        }

        public int flush() {
            if (nSensorEventQueue == 0) throw new NullPointerException();
            return nativeFlushSensor(nSensorEventQueue);
        }

        public boolean hasSensors() {
            // no more sensors are set
            return mActiveSensors.indexOfValue(true) >= 0;
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                dispose(true);
            } finally {
                super.finalize();
            }
        }

        private void dispose(boolean finalized) {
            if (mCloseGuard != null) {
                if (finalized) {
                    mCloseGuard.warnIfOpen();
                }
                mCloseGuard.close();
            }
            if (nSensorEventQueue != 0) {
                nativeDestroySensorEventQueue(nSensorEventQueue);
                nSensorEventQueue = 0;
            }
        }

        private int enableSensor(
                Sensor sensor, int rateUs, int maxBatchReportLatencyUs) {
            if (nSensorEventQueue == 0) throw new NullPointerException();
            if (sensor == null) throw new NullPointerException();
            return nativeEnableSensor(nSensorEventQueue, sensor.getHandle(), rateUs,
                    maxBatchReportLatencyUs);
        }

        protected int injectSensorDataBase(int handle, float[] values, int accuracy,
                                           long timestamp) {
            return nativeInjectSensorData(nSensorEventQueue, handle, values, accuracy, timestamp);
        }

        private int disableSensor(Sensor sensor) {
            if (nSensorEventQueue == 0) throw new NullPointerException();
            if (sensor == null) throw new NullPointerException();
            return nativeDisableSensor(nSensorEventQueue, sensor.getHandle());
        }
        protected abstract void dispatchSensorEvent(int handle, float[] values, int accuracy,
                long timestamp);
        protected abstract void dispatchFlushCompleteEvent(int handle);

        protected abstract void addSensorEvent(Sensor sensor);
        protected abstract void removeSensorEvent(Sensor sensor);
    }

    static final class SensorEventQueue extends BaseEventQueue {
        private final SensorEventListener mListener;
        private final SparseArray<SensorEvent> mSensorsEvents = new SparseArray<SensorEvent>();

        public SensorEventQueue(SensorEventListener listener, Looper looper,
                SystemSensorManager manager, String packageName) {
            super(looper, manager, OPERATING_MODE_NORMAL, packageName);
            mListener = listener;
        }

        @Override
        public void addSensorEvent(Sensor sensor) {
            SensorEvent t = new SensorEvent(Sensor.getMaxLengthValuesArray(sensor,
                    mManager.mTargetSdkLevel));
            synchronized (mSensorsEvents) {
                mSensorsEvents.put(sensor.getHandle(), t);
            }
        }

        @Override
        public void removeSensorEvent(Sensor sensor) {
            synchronized (mSensorsEvents) {
                mSensorsEvents.delete(sensor.getHandle());
            }
        }

        // Called from native code.
        @SuppressWarnings("unused")
        @Override
        protected void dispatchSensorEvent(int handle, float[] values, int inAccuracy,
                long timestamp) {
            final Sensor sensor = mManager.mHandleToSensor.get(handle);
            SensorEvent t = null;
            synchronized (mSensorsEvents) {
                t = mSensorsEvents.get(handle);
            }

            if (t == null) {
                // This may happen if the client has unregistered and there are pending events in
                // the queue waiting to be delivered. Ignore.
                return;
            }
            // Copy from the values array.
            System.arraycopy(values, 0, t.values, 0, t.values.length);
            t.timestamp = timestamp;
            t.accuracy = inAccuracy;
            t.sensor = sensor;

            // call onAccuracyChanged() only if the value changes
            final int accuracy = mSensorAccuracies.get(handle);
            if ((t.accuracy >= 0) && (accuracy != t.accuracy)) {
                mSensorAccuracies.put(handle, t.accuracy);
                mListener.onAccuracyChanged(t.sensor, t.accuracy);
            }
            mListener.onSensorChanged(t);
        }

        @SuppressWarnings("unused")
        protected void dispatchFlushCompleteEvent(int handle) {
            if (mListener instanceof SensorEventListener2) {
                final Sensor sensor = mManager.mHandleToSensor.get(handle);
                ((SensorEventListener2)mListener).onFlushCompleted(sensor);
            }
            return;
        }
    }

    static final class TriggerEventQueue extends BaseEventQueue {
        private final TriggerEventListener mListener;
        private final SparseArray<TriggerEvent> mTriggerEvents = new SparseArray<TriggerEvent>();

        public TriggerEventQueue(TriggerEventListener listener, Looper looper,
                SystemSensorManager manager, String packageName) {
            super(looper, manager, OPERATING_MODE_NORMAL, packageName);
            mListener = listener;
        }

        @Override
        public void addSensorEvent(Sensor sensor) {
            TriggerEvent t = new TriggerEvent(Sensor.getMaxLengthValuesArray(sensor,
                    mManager.mTargetSdkLevel));
            synchronized (mTriggerEvents) {
                mTriggerEvents.put(sensor.getHandle(), t);
            }
        }

        @Override
        public void removeSensorEvent(Sensor sensor) {
            synchronized (mTriggerEvents) {
                mTriggerEvents.delete(sensor.getHandle());
            }
        }

        // Called from native code.
        @SuppressWarnings("unused")
        @Override
        protected void dispatchSensorEvent(int handle, float[] values, int accuracy,
                long timestamp) {
            final Sensor sensor = mManager.mHandleToSensor.get(handle);
            TriggerEvent t = null;
            synchronized (mTriggerEvents) {
                t = mTriggerEvents.get(handle);
            }
            if (t == null) {
                Log.e(TAG, "Error: Trigger Event is null for Sensor: " + sensor);
                return;
            }

            // Copy from the values array.
            System.arraycopy(values, 0, t.values, 0, t.values.length);
            t.timestamp = timestamp;
            t.sensor = sensor;

            // A trigger sensor is auto disabled. So just clean up and don't call native
            // disable.
            mManager.cancelTriggerSensorImpl(mListener, sensor, false);

            mListener.onTrigger(t);
        }

        @SuppressWarnings("unused")
        protected void dispatchFlushCompleteEvent(int handle) {
        }
    }

    final class InjectEventQueue extends BaseEventQueue {
        public InjectEventQueue(Looper looper, SystemSensorManager manager, String packageName) {
            super(looper, manager, OPERATING_MODE_DATA_INJECTION, packageName);
        }

        int injectSensorData(int handle, float[] values,int accuracy, long timestamp) {
             return injectSensorDataBase(handle, values, accuracy, timestamp);
        }

        @SuppressWarnings("unused")
        protected void dispatchSensorEvent(int handle, float[] values, int accuracy,
                long timestamp) {
        }

        @SuppressWarnings("unused")
        protected void dispatchFlushCompleteEvent(int handle) {

        }

        @SuppressWarnings("unused")
        protected void addSensorEvent(Sensor sensor) {

        }

        @SuppressWarnings("unused")
        protected void removeSensorEvent(Sensor sensor) {

        }
    }
}
