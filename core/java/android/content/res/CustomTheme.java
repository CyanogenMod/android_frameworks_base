package android.content.res;

/**
 * 
 * @author pankaj
 * @hide
 */
public final class CustomTheme implements Cloneable {

    private int mThemeId;
    private String mThemePackageName;
    private String mThemeResourcePath;
    private int mParentThemeId = -1;
    private boolean mForceUpdate = false;
    
    private static CustomTheme defaultTheme = new CustomTheme();
    
    private CustomTheme() {
        mThemeId = com.android.internal.R.style.Theme;
        mThemePackageName = "";
    }

    public CustomTheme(int themeId, String packageName) {
        mThemeId = themeId;
        mThemePackageName = packageName;
    }

    public static CustomTheme getDefault(){
        return defaultTheme;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof CustomTheme) {
            CustomTheme o = (CustomTheme) object;
            if (mThemeId != o.mThemeId) {
                return false;
            }
            String currentPackageName = (mThemePackageName == null)? "" : mThemePackageName;
            String newPackageName = (o.mThemePackageName == null)? "" : o.mThemePackageName;
            return (currentPackageName.trim().equalsIgnoreCase(newPackageName.trim())) &&
                    (o.getParentThemeId() == getParentThemeId()) &&
                    !isForceUpdate();
        }
        return false;
    }

    @Override
    public final String toString() {
        StringBuilder result = new StringBuilder();
        result.append(mThemeId);
        result.append("_");
        if (mThemePackageName != null && mThemePackageName.length() > 0){
            result.append(mThemePackageName);
        } else {
            result.append("_");
        }
        result.append(":_");
        if (mThemeResourcePath != null && mThemeResourcePath.length() > 0){
            result.append(mThemeResourcePath);
        } else {
            result.append("_");
        }
        result.append("_");
        result.append(mParentThemeId);
        result.append("_");
        result.append(mForceUpdate);

        return result.toString();
    }

    @Override
    public synchronized int hashCode() {
        return new Integer(mThemeId).hashCode() + mThemePackageName.hashCode();
    }

    public int getThemeId() {
        return mThemeId;
    }

    public void setThemeId(int themeId) {
        mThemeId = themeId;
    }

    public String getThemePackageName() {
        return mThemePackageName;
    }

    public void setThemePackageName(String themePackageName) {
        mThemePackageName = themePackageName;
    }

    public String getThemeResourcePath() {
        return mThemeResourcePath;
    }

    public void setThemeResourcePath(String resourcePath) {
        mThemeResourcePath = resourcePath;
    }

    public int getParentThemeId() {
        return mParentThemeId;
    }

    public void setParentThemeId(int parentThemeId) {
        mParentThemeId = parentThemeId;
    }

    public boolean isForceUpdate() {
        return mForceUpdate;
    }

    public void setForceUpdate(boolean update) {
        mForceUpdate = update;
    }

}
