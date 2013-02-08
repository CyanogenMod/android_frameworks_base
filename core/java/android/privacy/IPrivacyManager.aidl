package android.privacy;

import android.content.pm.PackageInfo;

/**
 * API for privacy manager service
 *
 * {@hide}
 */
interface IPrivacyManager {

  /**
   * alternative implementation of IServiceManager.getService but returns package specific substitute
   */
  IBinder getPrivacySubstituteService(in String service);

  /**
   * modify PackageInfo returned to calling app
   * @see android.content.pm.IPackageManager#getPackageInfo
   * @param packageName requested package
   */
  PackageInfo filterPackageInfo(in PackageInfo info, in String packageName, in int flags);

  /**
   * modify guids returned to calling app - has to create a new int[]!
   * @param packageName requested package
   */
  int[] filterPackageGids(in int[] gids, in String packageName);
  /**
   * deny permission for package that otherwise would have been granted
   * @return true if permission should still be granted
   */
  boolean filterGrantedPermission(in String permName, in String packageName);

  /**
   * caller must have permission <code>android.permission.MANAGE_PRIVACY_SETTINGS</code>
   * @return implementation specific reference to config interface
   */
  IBinder getConfig();
}
