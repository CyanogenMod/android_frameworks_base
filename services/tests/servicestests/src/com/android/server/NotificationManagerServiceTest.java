package com.android.server;

import java.util.Calendar;

import android.os.IBinder;
import android.test.AndroidTestCase;

import com.android.server.status.IconData;
import com.android.server.status.NotificationData;
import com.android.server.status.StatusBarServiceDefinition;
import com.android.server.status.StatusBarService.NotificationCallbacks;

public class NotificationManagerServiceTest extends AndroidTestCase {
    private Calendar mCalendar = Calendar.getInstance();

    private NotificationManagerService mService;

    public void setUp() {
        mService = new NotificationManagerService(getContext(), mMockStatusBarService, mLightsService);
    }

    public void testInQuietHours_Overnight() {
        mService.setQuietHours(true, (22 * 60), (4 * 60), true, true, true);

        mCalendar.set(Calendar.HOUR_OF_DAY, 20);
        mCalendar.set(Calendar.MINUTE, 0);
        assertFalse(mService.inQuietHours(mCalendar));
        mCalendar.set(Calendar.HOUR_OF_DAY, 23);
        assertTrue(mService.inQuietHours(mCalendar));
    }

    private StatusBarServiceDefinition mMockStatusBarService = new StatusBarServiceDefinition() {
        @Override
        public void activate() {
        }

        @Override
        public IBinder addIcon(String slot, String iconPackage, int iconId, int iconLevel) {
            return null;
        }

        @Override
        public IBinder addIcon(IconData icon, NotificationData n) {
            return null;
        }

        @Override
        public void deactivate() {
        }

        @Override
        public void disable(int what, IBinder token, String pkg) {
        }

        @Override
        public void removeIcon(IBinder key) {
        }

        @Override
        public void setNotificationCallbacks(NotificationCallbacks notificationCallbacks) {
        }

        @Override
        public void toggle() {
        }

        @Override
        public void updateIcon(IBinder key, IconData icon, NotificationData n) {
        }

        @Override
        public void updateIcon(IBinder key, String slot, String iconPackage, int iconId,
                int iconLevel) {
        }
    };

    private LightsServiceDefinition mLightsService = new LightsServiceDefinition() {
        @Override
        public LightDefinition getLight(int lightIdBattery) {
            return new LightDefinition() {
                @Override
                public void pulse() {
                }

                @Override
                public void pulse(int color, int onMS) {
                }

                @Override
                public void setBrightness(int brightness) {
                }

                @Override
                public void setBrightness(int brightness, int mode) {
                }

                @Override
                public void setColor(int color) {
                }

                @Override
                public void setFlashing(int color, int mode, int onMS, int offMS) {
                }

                @Override
                public void notificationPulse(int color, int onMS, int offMS) {
                }

                @Override
                public void turnOff() {
                }
            };
        }
    };
}
