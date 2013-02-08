
package com.android.server.privacy;

import android.content.pm.PackageInfo;
import android.os.IBinder;

/**
 * @hide
 */
public interface PrivacyManagerSPI {
    IBinder getConfig();

    IBinder getPrivacySubstituteService(String service, String packageName);

    PackageInfo filterPackageInfo(PackageInfo info, String packageName, int flags);

    int[] filterPackageGids(int[] gids, String packageName);

    boolean filterGrantedPermission(String permName, String pkgName); // true==grant
}
