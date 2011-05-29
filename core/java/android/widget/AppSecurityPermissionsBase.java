package android.widget;

import android.view.View;

/**
 * {@hide}
 */
public abstract class AppSecurityPermissionsBase implements View.OnClickListener {
    public abstract View getPermissionsView();

    public abstract int getPermissionCount();
}
