/**
* Copyright (C) 2012 CollegeDev
* This program is free software; you can redistribute it and/or modify it under
* the terms of the GNU General Public License as published by the Free Software
* Foundation; either version 3 of the License, or (at your option) any later version.
* This program is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* this program; if not, see <http://www.gnu.org/licenses>.
*/

package android.privacy.surrogate;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.IWifiManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.ServiceManager;
import android.privacy.IPrivacySettingsManager;
import android.privacy.PrivacySettings;
import android.privacy.PrivacySettingsManager;
import android.util.Log;

/**
 * Provides privacy handling for WifiManager
 * @author CollegeDev
 * {@hide}
 */
public class PrivacyWifiManager extends WifiManager{

	private Context context;
	
	private PrivacySettingsManager pSetMan;
	
	private static final String P_TAG = "PrivacyWifiManager";
	

	public PrivacyWifiManager(Context context, IWifiManager service){
		super(context, service);
		this.context = context;
		pSetMan = new PrivacySettingsManager(context, IPrivacySettingsManager.Stub.asInterface(ServiceManager.getService("privacy")));
	}
	
	@Override
	public List<WifiConfiguration> getConfiguredNetworks() {
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		List<WifiConfiguration> output = new ArrayList<WifiConfiguration>(); //create empty list!
		if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){

			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);   
			return output;
		}
		else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null); 
			return super.getConfiguredNetworks();
		}
	}
	
	@Override
	public WifiInfo getConnectionInfo() {
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		//we have to change WifiInfo constructor for faking data -> change WifiInfo in framework!
		WifiInfo output = new WifiInfo(true);
		if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return output;
		}
		else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null); 
			return super.getConnectionInfo();
		}
	}
	
	@Override
	public List<ScanResult> getScanResults() {
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		List<ScanResult> output = new ArrayList<ScanResult>(); //create empty list!
		if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return output;
		}
		else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null); 
			return super.getScanResults();
		}
	}
	
	@Override
	public int getFrequencyBand() {
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return -1;
		}
		else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null); 
			return super.getFrequencyBand();
		}
	}
	
	@Override
	public DhcpInfo getDhcpInfo(){
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return new DhcpInfo();
		}
		else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null); 
			return super.getDhcpInfo();
		}
	}
	
	/**
	 * @hide
	 * @return
	 */
	@Override
	public WifiConfiguration getWifiApConfiguration(){
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return new WifiConfiguration();
		}
		else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null); 
			return super.getWifiApConfiguration();
		}
	}
	

	@Override
	public String getConfigFile(){
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return "";
		}
		else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null); 
			return super.getConfigFile();
		}
	}
	//new
	@Override
	public boolean startScan(){
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return false;
		} else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null); 
			return super.startScan();
		}
	}
	
	
	@Override
	public boolean startScanActive(){
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return false;
		} else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null); 
			return super.startScanActive();
		}
	}
	
	@Override
	public boolean setWifiEnabled(boolean enabled){
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		if(pSetMan != null && settings != null && settings.getSwitchWifiStateSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_SWITCH_WIFI_STATE, null, null);  
			return false;
		} else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_SWITCH_WIFI_STATE, null, null); 
			return super.setWifiEnabled(enabled);
		}
	}
	
	@Override
	public int getWifiState(){
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		if(pSetMan != null && settings != null && settings.getForceOnlineState() == PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return WIFI_STATE_ENABLED;
		} else if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return WIFI_STATE_UNKNOWN;
		} else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return super.getWifiState();
		}
	}
	
	@Override
	public boolean isWifiEnabled(){
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Binder.getCallingUid());
		if(pSetMan != null && settings != null && settings.getForceOnlineState() == PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return true;
		} else if(pSetMan != null && settings != null && settings.getWifiInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.EMPTY, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return false;
		} else{
			pSetMan.notification(context.getPackageName(),-1, PrivacySettings.REAL, PrivacySettings.DATA_WIFI_INFO, null, null);  
			return super.isWifiEnabled();
		}
	}
}
