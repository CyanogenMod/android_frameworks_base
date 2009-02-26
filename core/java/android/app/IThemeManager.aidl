package android.app;

/**
 * System private API for talking with the theme manager service.
 *
 * @hide
 */
interface IThemeManager {
	void setTheme(int themeId);
	int getCurrentTheme();
	void setPackage(String packageName);
	String getPackageName();
	void setThemePluto();
}