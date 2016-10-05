package com.android.systemui.cm;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.util.Log;
import com.android.internal.R;
import cyanogenmod.providers.CMSettings;

/**
 * Callback to check when headphones are plugged/unplugged
 * to display a notification (if enabled in the settings)
 *
 * @author Adrien 'Litarvan' Navratil
 */
public class HeadsetStatusCallback extends AudioDeviceCallback
{
    public static final String TAG = "HeadsetStatus";
    public static final int NOTIFICATION_ID = 2565;

    private int mNotificationID = -1;
    private Context mContext;

    public HeadsetStatusCallback(Context mContext)
    {
        this.mContext = mContext;
    }

    @Override
    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices)
    {
        super.onAudioDevicesAdded(addedDevices);

        if (addedDevices.length == 1 && mNotificationID == -1 && enabled())
        {
            Notification.Builder mBuilder = new Notification.Builder(mContext).setContentTitle("Headset plugged").setContentText("A headset is plugged").setSmallIcon(R.drawable.ic_settings);
            NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification notification = mBuilder.build();

            notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

            mNotificationManager.notify(NOTIFICATION_ID, notification);
            mNotificationID = NOTIFICATION_ID;
        }
    }

    public boolean enabled()
    {
        return CMSettings.System.getInt(mContext.getContentResolver(), CMSettings.System.HEADSET_NOTIFICATION, 1) == 1;
    }

    @Override
    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices)
    {
        super.onAudioDevicesRemoved(removedDevices);

        if (mNotificationID != -1)
        {
            NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(mNotificationID);

            mNotificationID = -1;
        }
    }
}
