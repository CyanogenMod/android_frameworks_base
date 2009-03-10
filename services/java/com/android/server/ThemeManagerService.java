package com.android.server;

import android.app.ActivityManagerNative;
import android.app.IThemeManager;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.util.Log;

/**
 * 
 * @author 
 *@hide
 */
public class ThemeManagerService extends IThemeManager.Stub {
	private static int mCurrentResourceTheme = com.android.internal.R.style.Theme_Light;
	private String mPackageName = null;
	
	public ThemeManagerService(){
	}
		
	public int getCurrentTheme(){
		try	{	
			Configuration mCurConfig = ActivityManagerNative.getDefault().getConfiguration();
			mCurrentResourceTheme = mCurConfig.themeResource;
		}catch(RemoteException e){
			
		}
		return mCurrentResourceTheme;
	}
	
	public void setPackage(String packageName){
	}
	
	public String getPackageName(){
		return mPackageName;
	}
	
	public void setTheme(int id){
		Log.d("ThemeManagerService", "Setting Theme to :"+id);
		mCurrentResourceTheme = id;
		try	{
			Configuration mCurConfig = ActivityManagerNative.getDefault().getConfiguration();
			mCurConfig.themeResource = mCurrentResourceTheme;			
			ActivityManagerNative.getDefault().updateConfiguration(mCurConfig);
		}catch(RemoteException e){
			Log.d("ThemeManagerService", "Could not update config wiht themeid:"+ id, e);
		}
	}
	
	public void setThemePluto(){		
		int id = com.android.internal.R.style.Pluto;
		setTheme(id);
	}
}