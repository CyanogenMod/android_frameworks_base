package com.android.server;

import com.android.internal.telephony.IPhoneStateListener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.privacy.IPrivacySettingsManager;
import android.privacy.PrivacySettings;
import android.privacy.PrivacySettingsManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellIdentityGsm;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import android.util.Log;
import android.os.Process;


public class PrivacyTelephonyRegistry extends TelephonyRegistry{

	private static final String P_TAG = "PrivacyTelephonyRegistry";
	
	private PrivacySettingsManager pSetMan;
	
	private static final int PERMISSION_CELL_LOCATION = 0;
	
	private static final int PERMISSION_CELL_INFO = 1;
	
	private static final int PERMISSION_SIGNAL_STRENGTH = 2;
	
	private static final int PERMISSION_CALL_STATE = 3;
	
	private static final int PERMISSION_SERVICE_STATE = 4;
 
  private Context _context;
	
	public PrivacyTelephonyRegistry(Context context) {
		super(context);
    this._context = context;
		Log.i(P_TAG,"constructor ready");
	}
	
  private void initialize() {
               if(ServiceManager.getService("privacy") != null) {
                       pSetMan = new PrivacySettingsManager(_context, IPrivacySettingsManager.Stub.asInterface(ServiceManager.getService("privacy")));
                       try{
                               registerPrivacy();
                       } catch(Exception e){
                               Log.e(P_TAG,"failed to register privacy broadcastreceiver");
                       }
               }
       }
 
	/** This broadCastReceiver receives the privacy intent for blocking phonecalls and faking phonestate */
	private final BroadcastReceiver privacyReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("android.privacy.BLOCKED_PHONE_CALL")){
            	Bundle data = new Bundle();
            	data = intent.getExtras();
            	String packageName = data.getString("packageName");
            	if(data.containsKey("packageName")){
            		Log.i(P_TAG, "got blocked phone call INTENT from package: " + data.getString("packageName"));
            	} else{
            		Log.i(P_TAG, "got blocked phone call INTENT without package information");
            	}
            	if(packageName == null) return;
            	if(data.containsKey("phoneState")){
            		int state = data.getInt("phoneState");
            		switch(state){
            			case TelephonyManager.CALL_STATE_IDLE:
            				notifyPrivacyCallState(TelephonyManager.CALL_STATE_IDLE, null, packageName);
            				return;
            			case TelephonyManager.CALL_STATE_OFFHOOK:
            				notifyPrivacyCallState(TelephonyManager.CALL_STATE_OFFHOOK, null, packageName);
            				return;
            			case TelephonyManager.CALL_STATE_RINGING:
            				notifyPrivacyCallState(TelephonyManager.CALL_STATE_RINGING, "12345", packageName);
            				return;
            			default:
            				return;
            		}
            	}
            	Log.i(P_TAG,"we forgot to put phoneState in Intent?");
            }
        }
    };
    
    /**
     * This method allows us to fake a call state if application uses phoneStateListener. It will call the onCallStateChanged method with faked state and number
     * @param state {@link TelephonyManager} TelephonyManager.CALL_STATE_IDLE <br> TelephonyManager.CALL_STATE_OFFHOOK <br> TelephonyManager.CALL_STATE_RINGING <br>
     * @param incomingNumber pass null if you don't choose ringing!
     * @param packageName the affected package to fake callstate!
     * @author CollegeDev
     */
    public void notifyPrivacyCallState(int state, String incomingNumber, String packageName) {
    	//we do not need to check for permissions
//        if (!checkNotifyPermission("notifyCallState()")) {
//            return;
//        }
        synchronized (mRecords) {
            //mCallState = state;
            //mCallIncomingNumber = incomingNumber;
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_CALL_STATE) != 0) {
                    try {
                    	//only notify the affected application
                    	if(r.pkgForDebug.equals(packageName)){
                    		r.callback.onCallStateChanged(state, incomingNumber);
                    	}
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
        //broadcastCallStateChanged(state, incomingNumber);
    }
    
    private void registerPrivacy(){
    	 IntentFilter intentFilter = new IntentFilter("android.privacy.BLOCKED_PHONE_CALL");
    	 mContext.registerReceiver(privacyReceiver, intentFilter);
    }
	
	public void listen(String pkgForDebug, IPhoneStateListener callback, int events,
            boolean notifyNow) {
        // Slog.d(TAG, "listen pkg=" + pkgForDebug + " events=0x" +
        // Integer.toHexString(events));
		try{
			registerPrivacy();
		} catch(Exception e){
			Log.e(P_TAG,"failed to register privacy broadcastreceiver");
		}
        if (events != 0) {
            /* Checks permission and throws Security exception */
            checkListenerPermission(events);

            synchronized (mRecords) {
                // register
                Record r = null;
                find_and_add: {
                    IBinder b = callback.asBinder();
                    final int N = mRecords.size();
                    for (int i = 0; i < N; i++) {
                        r = mRecords.get(i);
                        if (b == r.binder) {
                            break find_and_add;
                        }
                    }
                    r = new Record();
                    r.binder = b;
                    r.callback = callback;
                    r.pkgForDebug = pkgForDebug;
                    mRecords.add(r);
                }
                int send = events & (events ^ r.events);
                r.events = events;
                if (notifyNow) {
                    if ((events & PhoneStateListener.LISTEN_SERVICE_STATE) != 0) {
//                        try {
//                        	//not forward now, wait for next
//                            //r.callback.onServiceStateChanged(new ServiceState(mServiceState));
//                        } catch (RemoteException ex) {
//                            remove(r.binder);
//                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTH) != 0) {
                        try {
                            int gsmSignalStrength = mSignalStrength.getGsmSignalStrength();
                            r.callback.onSignalStrengthChanged((gsmSignalStrength == 99 ? -1
                                    : gsmSignalStrength));
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR) != 0) {
                        try {
                            r.callback.onMessageWaitingIndicatorChanged(mMessageWaiting);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR) != 0) {
                        try {
                            r.callback.onCallForwardingIndicatorChanged(mCallForwarding);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_CELL_LOCATION) != 0) {
//                        try {
//                        	//we do not forward now!
//                            //r.callback.onCellLocationChanged(new Bundle(mCellLocation));
//                        } catch (RemoteException ex) {
//                            remove(r.binder);
//                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_CALL_STATE) != 0) {
                        try {
                            r.callback.onCallStateChanged(mCallState, mCallIncomingNumber);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_DATA_CONNECTION_STATE) != 0) {
                        try {
                            r.callback.onDataConnectionStateChanged(mDataConnectionState,
                                mDataConnectionNetworkType);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_DATA_ACTIVITY) != 0) {
                        try {
                            r.callback.onDataActivity(mDataActivity);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) != 0) {
                        try {
                            r.callback.onSignalStrengthsChanged(mSignalStrength);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_OTASP_CHANGED) != 0) {
                        try {
                            r.callback.onOtaspChanged(mOtaspMode);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_CELL_INFO) != 0) {
//                        try {
//                        	//we also do not forward now
//                            //r.callback.onCellInfoChanged(new CellInfo(mCellInfo));
//                        } catch (RemoteException ex) {
//                            remove(r.binder);
//                        }
                    }
                }
            }
        } else {
            remove(callback.asBinder());
        }
    }
	
	
	private boolean isPackageAllowed(int PERMISSION, String packageName){

    if(pSetMan == null) { 
        initialize();
        if(pSetMan == null) return false;
    }

		PrivacySettings settings = pSetMan.getSettings(packageName, Process.myUid());
		if(settings == null) return false;
		switch(PERMISSION){
			case PERMISSION_CELL_LOCATION:
				if(((settings.getLocationNetworkSetting() != PrivacySettings.REAL) || (settings.getLocationGpsSetting() != PrivacySettings.REAL)))
					return false;
				else 
					return true;
			case PERMISSION_CELL_INFO:
				if(settings.getLocationNetworkSetting() != PrivacySettings.REAL)
					return false;
				else
					return true;
			case PERMISSION_SIGNAL_STRENGTH:
				if(settings.getLocationNetworkSetting() != PrivacySettings.REAL)
					return false;
				else
					return true;
			case PERMISSION_CALL_STATE:
				if(settings.getLocationNetworkSetting() != PrivacySettings.REAL)
					return false;
				else
					return true;
			case PERMISSION_SERVICE_STATE:
				if(settings.getLocationNetworkSetting() != PrivacySettings.REAL)
					return false;
				else
					return true;
			default:
				return false;
		}
	}

	@Override
	public void notifyServiceState(ServiceState state) {
        if (!checkNotifyPermission("notifyServiceState()")){
            return;
        }
        synchronized (mRecords) {
            mServiceState = state;
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_SERVICE_STATE) != 0) {
                    try {
                    	if(!isPackageAllowed(PERMISSION_SERVICE_STATE,r.pkgForDebug)){
                    		 state.setOperatorName("", "", "");
                    		 Log.i(P_TAG,"package: " + r.pkgForDebug + " blocked for Cellinfo");
                    	}
                    	else
                    		Log.i(P_TAG,"package: " + r.pkgForDebug + " allowed for Cellinfo");
                        r.callback.onServiceStateChanged(new ServiceState(state));
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
        broadcastServiceStateChanged(state);
    }
	
	@Override
	public void notifyCellInfo(List<CellInfo> cellInfo) {
        if (!checkNotifyPermission("notifyCellInfo()")) {
            return;
        }
        synchronized (mRecords) {
            mCellInfo = cellInfo;
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_CELL_INFO) != 0) {
                    try {
                    	if(!isPackageAllowed(PERMISSION_CELL_INFO,r.pkgForDebug)){
                    		//for testings only at first
                            CellInfoGsm fakeCellInfo = new CellInfoGsm(); 
                            CellIdentityGsm fakeCellIdentity = new CellIdentityGsm(11,11,549,525,2);
                            fakeCellInfo.setCellIdentity(fakeCellIdentity);
                    		//r.callback.onCellInfoChanged(new CellInfoGsm(CellInfo.TIMESTAMP_TYPE_UNKNOWN,System.currentTimeMillis(),System.currentTimeMillis(),true,new SignalStrength(),new CellIdentityGsm(11,11,549,545,2,"unknown")));
                    		r.callback.onCellInfoChanged(new ArrayList<CellInfo>(Arrays.asList(fakeCellInfo)));
                    		Log.i(P_TAG,"package: " + r.pkgForDebug + " blocked for Cellinfo");
                    	}
                    	else{
                    		r.callback.onCellInfoChanged(cellInfo);
                    		Log.i(P_TAG,"package: " + r.pkgForDebug + " allowed for Cellinfo");
                    	}
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }
                }
            }
            handleRemoveListLocked();
        }
    }
	
	@Override
	public void notifyCellLocation(Bundle cellLocation) {
        if (!checkNotifyPermission("notifyCellLocation()")) {
            return;
        }
        synchronized (mRecords) {
            mCellLocation = cellLocation;
            boolean isCDMA = false;
            boolean goNormal = false;
            try{
            	if(cellLocation.containsKey("lac")){
            		//it is gsm cell location object, handle it!
            		isCDMA = false;
            	}
            	else{
            		//it is cdma cell location object, handle it!
            		isCDMA = true;
            	}
            }
            catch(Exception e){
            	//nothing here at all
            	goNormal = true;
            }
            for (Record r : mRecords) {
                if ((r.events & PhoneStateListener.LISTEN_CELL_LOCATION) != 0) {
                    try {
                    	if(!isPackageAllowed(PERMISSION_CELL_LOCATION,r.pkgForDebug) && !goNormal){
                    		Bundle output = new Bundle();
                    		if(isCDMA){
                    			CdmaCellLocation tmp = new CdmaCellLocation();
                    			tmp.fillInNotifierBundle(output);
                    		}
                    		else{
                    			GsmCellLocation tmp = new GsmCellLocation();
                    			tmp.fillInNotifierBundle(output);
                    		}
                    		r.callback.onCellLocationChanged(new Bundle(output));
                    		Log.i(P_TAG,"package: " + r.pkgForDebug + " blocked for CellLocation");
                    	}
                    	else{
                    		r.callback.onCellLocationChanged(new Bundle(cellLocation));
                    		Log.i(P_TAG,"package: " + r.pkgForDebug + " allowed for CellLocation");
                    	}
                    } catch (RemoteException ex) {
                        mRemoveList.add(r.binder);
                    }

                }
            }
            handleRemoveListLocked();
        }
    }
}
