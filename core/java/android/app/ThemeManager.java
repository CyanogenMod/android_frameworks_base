package android.app;

import android.os.RemoteException;

/**
 * 
 *@hide
 *
 */
public class ThemeManager {
	private final int DEFAULT_THEME_ID = com.android.internal.R.style.Theme;	
	private final IThemeManager mService;

    /**
     * package private on purpose
     */
	ThemeManager(IThemeManager service) {
        mService = service;
    }
	
	public void setTheme(int themeId){
		try{
			mService.setTheme(themeId);
		} catch (RemoteException ex) {
			
        }
	}
	
	public void setThemePluto(){
		try{
			mService.setThemePluto();
		} catch (RemoteException ex) {
			
        }
	}
	
	public int getCurrentTheme(){
		int currentTheme = DEFAULT_THEME_ID;
		try{
			 currentTheme = mService.getCurrentTheme();
		} catch (RemoteException ex) {
			
        }
		
		return currentTheme;
	}
	
	public void setPackage(String packageName){
		try{
			mService.setPackage(packageName);
		} catch (RemoteException ex) {
			
        }
	}
	
	public String getPackageName(){
		String packageName = null;
		try{
			packageName = mService.getPackageName();
		} catch (RemoteException ex) {
			
        }
		
		return packageName;
	}
}