package android.content.res;

import java.io.Serializable;

/**
 * 
 * @author pankaj
 * @hide
 */
public final class CustomTheme implements Cloneable, Serializable{

    /**
     * 
     */
    private static final long serialVersionUID = -5240272891725909930L;
    
    
    private transient int mThemeId = 0;
    private transient String mThemePackageName = "";
    
    private static CustomTheme defaultTheme = new CustomTheme();
    
    private CustomTheme() {
       mThemeId = com.android.internal.R.style.Theme;
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
            if(mThemeId != o.mThemeId){
                return false;
            }else {
                   String currentPackageName = (mThemePackageName == null)?"":mThemePackageName;
                   String newPackageName = (o.mThemePackageName == null)?"":o.mThemePackageName;
                   if(currentPackageName.trim().equalsIgnoreCase(newPackageName.trim())){
                       return true;
                   }
            }
        }
        return false;
    }
    
    @Override
    public final String toString() {
        StringBuilder result = new StringBuilder();
        result.append(mThemeId);
        if(mThemePackageName != null && mThemePackageName.length() > 0){
            result.append("_");
            result.append(mThemePackageName);
        }else{
            result.append("__");
        }
        
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
}
