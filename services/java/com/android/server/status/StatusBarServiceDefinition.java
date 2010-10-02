package com.android.server.status;

import com.android.server.status.StatusBarService.NotificationCallbacks;

import android.os.IBinder;

public interface StatusBarServiceDefinition {
    void activate();
    void deactivate();
    void toggle();
    void disable(int what, IBinder token, String pkg);
    IBinder addIcon(String slot, String iconPackage, int iconId, int iconLevel);
    IBinder addIcon(IconData icon, NotificationData n);
    void updateIcon(IBinder key, IconData icon, NotificationData n);
    void updateIcon(IBinder key, String slot, String iconPackage, int iconId, int iconLevel);
    void removeIcon(IBinder key);
    void setNotificationCallbacks(NotificationCallbacks notificationCallbacks);
}
