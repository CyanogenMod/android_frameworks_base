package com.tmobile.preferences;


import android.content.Context;

import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import com.android.internal.R;

/**
 * Essentially the same as CheckBoxPreference except for using a toggle button in place of a check box
 *
 * @hide
 */
public class ToggleButtonPreference extends CheckBoxPreference {
    public ToggleButtonPreference(Context context) {
        super(context, null);
        
        setWidgetLayoutResource(R.layout.tmobile_preference_widget_toggle);        
    }
    
    // This is the constructor called by the inflater
    public ToggleButtonPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        setWidgetLayoutResource(R.layout.tmobile_preference_widget_toggle);        
    }
    
    public ToggleButtonPreference(Context context, AttributeSet attrs, int defStyle){
        super(context, attrs, defStyle);
        setWidgetLayoutResource(R.layout.tmobile_preference_widget_toggle);   
    }
}
