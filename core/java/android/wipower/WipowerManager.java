/*
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.wipower;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.IPackageManager;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import android.os.SystemProperties;

/**
 * Public APIs for wipower control
 * This class exposes APIs to control wipower module on the phone
 * These APIs would be used by wireless charging application in context
 * of Bluetooth A4WP profile
 *
 * {@hide}
 */

public final class WipowerManager {
    private static final String TAG = "WipowerManager";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;

    private static IWipower mService;
    private static ArrayList<WipowerManagerCallback> mCallbacks;
    private static WipowerDynamicParam mPruData;
    private static WipowerAlert mAlert;
    private static WipowerState mState;
    private static WipowerManager mWipowerManager;
   /**
    * various alerts that Wipower module
    * gives to application layer
    *
    * {@hide}
    */
    public enum WipowerAlert {
        ALERT_NONE,
        ALERT_OVER_VOLTAGE,
        ALERT_OVER_CURRENT,
        ALERT_OVER_TEMPERATURE,
        ALERT_SELF_PROTECTION,
        ALERT_CHARGE_COMPLETE,
        ALERT_WIRED_CHARGER_DETECTED,
        ALERT_CHARGE_PORT
    }

   /**
    * Power levels used to indicate the charging levels
    *
    * {@hide}
    */
    public enum PowerLevel {
        POWER_LEVEL_MAXIMUM,
        POWER_LEVEL_MEDIUM,
        POWER_LEVEL_MINIMUM,
        POWER_LEVEL_UNKNOWN
    };

   /**
    * Wipower States
    * {@hide}
    */
    public enum WipowerState {
        OFF,
        ON
    };

   /**
    * Wipower power applied event
    * {@hide}
    */
    public enum PowerApplyEvent {
        OFF,
        ON
    };

    /* helper function to invoke callbacks to application layer*/
    void updateWipowerState(WipowerState state){
       if (mCallbacks != null) {
           int n = mCallbacks.size();
           Log.v(TAG,"Broadcasting updateAdapterState() to " + n + " receivers.");
           for (int i = 0; i < n; i++) {
                  mCallbacks.get(i).onWipowerStateChange(state);
           }
        }
    }

    /* helper function to invoke callbacks to application layer*/
    void updateWipowerData(WipowerDynamicParam pruData){
       if (mCallbacks != null) {
           int n = mCallbacks.size();
           Log.v(TAG,"Broadcasting updateWipowerData() to " + n + " receivers.");
           for (int i = 0; i < n; i++) {
                  mCallbacks.get(i).onWipowerData(pruData);
           }
        }
    }

    /* helper function to invoke callbacks to application layer*/
    void updateWipowerAlert(WipowerAlert alert){
       if (mCallbacks != null) {
           int n = mCallbacks.size();
           Log.v(TAG,"Broadcasting updateWipowerAlert() to " + n + " receivers.");
           for (int i = 0; i < n; i++) {
                  mCallbacks.get(i).onWipowerAlert(alert);
           }
        }
    }

    void updatePowerApplyAlert(PowerApplyEvent alert){
       if (mCallbacks != null) {
           int n = mCallbacks.size();
           if (VDBG) Log.v(TAG,"Broadcasting updatePowerApplyAlert() to " + n + " receivers.");
           for (int i = 0; i < n; i++) {
                  mCallbacks.get(i).onPowerApply(alert);
           }
        }
    }


    /* helper function to invoke callbacks to application layer*/
    void updateWipowerReady(){
       if (mCallbacks != null) {
           int n = mCallbacks.size();
           if (VDBG) Log.v(TAG,"Broadcasting updateWipowerReady " + n + " receivers.");
           for (int i = 0; i < n; i++) {
                  mCallbacks.get(i).onWipowerReady();
           }
        }
    }

    final private IWipowerManagerCallback mWiPowerMangerCallback =
            new IWipowerManagerCallback.Stub() {

        public void onWipowerStateChange(int state) {
            WipowerState s;

            if (state == 1) {
                s = WipowerState.ON;
            } else {
                s = WipowerState.OFF;
            }

            Log.v(TAG, "onWipowerStateChange: state" + state);
            updateWipowerState(s);
        }

        public void onWipowerAlert(byte alert) {
            WipowerAlert wp_alert = WipowerAlert.ALERT_NONE;
            Log.v(TAG, "onWipowerAlert: alert" + alert);
            /*Convert to WipowerAlert*/

            updateWipowerAlert(wp_alert);

        }

        public void onPowerApply(byte alert) {
            PowerApplyEvent s;

            if (alert == 0x1) {
                s = PowerApplyEvent.ON;
            } else {
                s = PowerApplyEvent.OFF;
            }

            if (VDBG) Log.v(TAG, "onPowerApply: alert" + alert);
            updatePowerApplyAlert(s);

        }

        public void onWipowerData(byte[] value) {
            Log.v(TAG, "onWipowerData: " + value);
            if (mPruData != null) {
                 mPruData.setValue(value);
                 updateWipowerData(mPruData);
            } else {
                 Log.e(TAG, "mPruData is null");
            }

        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = IWipower.Stub.asInterface(service);

            if (DBG) Log.v(TAG, "Proxy object connected: " +  mService);
            try {
                mService.registerCallback(mWiPowerMangerCallback);
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "not able to register as client");
            }

            Log.v(TAG, "Calling onWipowerReady");
            updateWipowerReady();

        }
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) Log.v(TAG, "Proxy object disconnected");
            try {
                mService.unregisterCallback(mWiPowerMangerCallback);
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "not able to unregister as client");
            }
            mService = null;
        }
    };

    public static synchronized WipowerManager getWipowerManger(Context context, WipowerManagerCallback callback) {
       if (!isWipowerSupported()) {
           Log.e(TAG, "Wipower not supported");
           return null;
       }

       if (mWipowerManager == null) {
           if (DBG) Log.v(TAG, "Instantiate Singleton");
           mWipowerManager = new WipowerManager(context.getApplicationContext(), callback);
       }
       return mWipowerManager;
    }

   /**
    * WipowerManager is the main class which exposes Application interfaces
    * Wireless Charging control
    *
    * {@hide}
    */
    private WipowerManager(Context context, WipowerManagerCallback callback) {
       if (mService == null) {
           try {

               Intent bindIntent = new Intent(IWipower.class.getName());
               ComponentName comp = bindIntent.resolveSystemService(context.getPackageManager(), 0);
               bindIntent.setComponent(comp);
               if (comp == null || !context.bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE)) {
                  Log.e(TAG, "Could not bind to Wipower Service");
               }
           } catch (SecurityException  e) {
                  Log.e(TAG, "Security Exception");
           }
       }

       Log.v(TAG, "Bound to Wipower Service");
       mPruData = new WipowerDynamicParam();
       mCallbacks = new ArrayList<WipowerManagerCallback>();
    }

    static boolean isWipowerSupported() {

        if (SystemProperties.getBoolean("ro.bluetooth.a4wp", false) == true) {
            Log.v(TAG, "System.getProperty is true");
            return true;
        } else {
            Log.v(TAG, "System.getProperty is false");
            return false;
        }
    }


   /**
    * startCharing initiate the wireless charging process by internally
    * setting output voltage for the wireless charging module
    * <p>This is an asynchronous call: Status of this indicated by
    * {@link WipowerManagerCallback::onWipowerStateChange}
    *
    * @return true on sucess,
    *         false if start charging fails or if the hardware doesn't support this feature
    * {@hide}
    */
    public boolean startCharging() {
        boolean ret = false;

        if (!isWipowerSupported()) {
            Log.e(TAG, "Wipower not supported");
            return false;
        }

        if (mService == null) {
            Log.e(TAG, "startCharging: Service  not available");
        } else {
            try {
                ret = mService.startCharging();
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "Service  Exceptione");
            }
        }

        return ret;
    }

  /**
    * stopCharing stop the wireless charging process by internally
    * setting output voltage for the wireless charging module
    * <p>This is an asynchronous call: Status of this indicated by
    * {@link WipowerManagerCallback::onWipowerStateChange}
    *
    * @return true on sucess
    *         false if start charging fails or if the hardware doesn't support this feature
    * {@hide}
    */
    public boolean stopCharging() {
        boolean ret = false;

        if (!isWipowerSupported()) {
            Log.e(TAG, "Wipower not supported");
            return false;
        }

        if (mService == null) {
            Log.e(TAG, " Wipower Service not available");
        } else {
            try {
                ret = mService.stopCharging();
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "Service  Exceptione");
            }
        }

        return ret;
    }

  /**
    * Sets the current level for Wireless charging module
    *
    * @param {@link Powerlevel} indicating the desired power level
    *
    * @return true on success
    *         false if start charging fails or if the hardware doesn't support this feature
    *
    * {@hide}
    */
    public boolean setPowerLevel(PowerLevel powerlevel) {
        boolean ret = false;

        if (!isWipowerSupported()) {
            Log.e(TAG, "Wipower not supported");
            return false;
        }

        if (mService == null) {
            Log.e(TAG, " Wipower Service not available");
        } else {
            byte level = 0;
            if( powerlevel == PowerLevel.POWER_LEVEL_MINIMUM) level = 2;
            else if(  powerlevel == PowerLevel.POWER_LEVEL_MEDIUM) level = 1;
            else if(  powerlevel == PowerLevel.POWER_LEVEL_MAXIMUM) level = 0;
            try {
                ret = mService.setCurrentLimit(level);
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "Service  Exceptione");
            }
        }
        return ret;
    }

  /**
    * Gets the current level for Wireless charging module
    *
    * @return {@link Powerlevel} current power level
    *
    * {@hide}
    */
    public PowerLevel getPowerLevel() {
        PowerLevel ret = PowerLevel.POWER_LEVEL_UNKNOWN;

        if (mService == null) {
            Log.e(TAG, " Wipower Service not available");
        } else {
            byte res = 0;
            try {
                res = mService.getCurrentLimit();
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "Service  Exceptione");
            }
            if(res == 0) ret = PowerLevel.POWER_LEVEL_MINIMUM;
            else if(res == 1) ret = PowerLevel.POWER_LEVEL_MEDIUM;
            else if(res == 2) ret = PowerLevel.POWER_LEVEL_MAXIMUM;
        }
        return ret;
    }

  /**
    * Gets the current state of Wireless charging
    *
    * @return {@link WipowerState } wireless charging state
    * {@hide}
    */
    public WipowerState getState() {
        WipowerState ret = WipowerState.OFF;
        if (mService == null) {
            Log.e(TAG, " Wipower Service not available");
        } else {
            int res = 0;
            try {
                res = mService.getState();
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "Service  Exceptione");
            }
            if (res == 0) {
                ret = WipowerState.OFF;
            }
            else {
                ret = WipowerState.ON;
            }
        }
        return ret;
    }

  /**
    * Enables Alert notifications.
    *
    *  @return true on success or flase otherwise
    * {@hide}
    */
    public boolean enableAlertNotification(boolean enable) {
        boolean ret = false;
        if (mService == null) {
            Log.e(TAG, "Service  not available");
        } else {
            try {
                ret = mService.enableAlert(enable);
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "Service  Exception");
            }
        }

        return ret;
    }

   /**
    * Enables data notifications.
    *
    *  @return true on success or flase otherwise
    * {@hide}
    */
    public boolean enableDataNotification(boolean enable) {
        boolean ret = false;
        if (mService == null) {
            Log.e(TAG, "Service  not available");
        } else {
            try {
                ret = mService.enableData(enable);
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "Service  Exceptione");
            }
        }

        return ret;
    }

   /**
    * Enables power detection command.
    *
    *  @return true on success or flase otherwise
    * {@hide}
    */
    public boolean enablePowerApply(boolean enable, boolean on, boolean time_flag) {
        boolean ret = false;
        Log.v(TAG,"enablePowerApply: enable: " + enable + " on: " + on + "time_flag" + time_flag);
        if (mService == null) {
            Log.e(TAG, "Service  not available");
        } else {
            try {
                ret = mService.enablePowerApply(enable, on, time_flag);
            } catch (android.os.RemoteException e) {
                Log.e(TAG, "Service  Exceptione");
            }
        }

        return ret;
    }


   /** API used to reigester the wipower callbacks.
    * {@hide}
    */
    public void registerCallback(WipowerManagerCallback callback) {
        if (VDBG) Log.v(TAG, "registerCallback:Service called");
        if (mService == null) {
            Log.e(TAG, "registerCallback:Service  not available");
        }

        mCallbacks.add(callback);
    }

   /** API used to unreigester the wipower callbacks.
    * {@hide}
    */
    public void unregisterCallback(WipowerManagerCallback callback) {
        if (mService == null) {
            Log.e(TAG, "Service  not available");
        }
        mCallbacks.remove(callback);
    }
}
