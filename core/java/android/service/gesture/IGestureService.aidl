package android.service.gesture;

import android.app.PendingIntent;

/** @hide */
interface IGestureService {

    void setOnLongPressPendingIntent(in PendingIntent pendingIntent);
    void setOnDoubleClickPendingIntent(in PendingIntent pendingIntent);

}
