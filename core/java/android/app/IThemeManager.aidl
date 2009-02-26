/**
 * System private API for talking with the alarm manager service.
 *
 * @hide
 */
package android.app;
interface IThemeManager {
	void setTheme(int themeId);
	int getCurrentTheme();
	void setPackage(String packageName);
	String getPackageName();
	void setThemePluto();
}