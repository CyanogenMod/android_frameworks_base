package com.android.internal.util.cm;

import android.app.Notification;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

public class SpamFilter {

    public static final String AUTHORITY = "com.cyanogenmod.spam";
    public static final String MESSAGE_PATH = "message";
    public static final Uri NOTIFICATION_URI = new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(AUTHORITY)
            .appendEncodedPath(MESSAGE_PATH)
            .build();

    public static final class SpamContract {

        public static final class PackageTable {
            public static final String TABLE_NAME = "packages";
            public static final String ID = "_id";
            public static final String PACKAGE_NAME = "package_name";
        }

        public static final class NotificationTable {
            public static final String TABLE_NAME = "notifications";
            public static final String ID = "_id";
            public static final String PACKAGE_ID = "package_id";
            public static final String MESSAGE_TEXT = "message_text";
            public static final String COUNT = "count";
            public static final String LAST_BLOCKED = "last_blocked";
            public static final String NORMALIZED_TEXT = "normalized_text";
        }

    }

    public static String getNormalizedContent(String msg) {
        return msg.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]+", "");
    }

    public static String getNormalizedNotificationContent(Notification notification) {
        String content = getNotificationContent(notification);
        return getNormalizedContent(content);
    }

    public static String getNotificationContent(Notification notification) {
        CharSequence notificationTitle = getNotificationTitle(notification);
        CharSequence notificationMessage = getNotificationMessage(notification);
        return notificationTitle + "\n" + notificationMessage;
    }

    private static CharSequence getNotificationTitle(Notification notification) {
        Bundle extras = notification.extras;
        String titleExtra = extras.containsKey(Notification.EXTRA_TITLE_BIG)
                ? Notification.EXTRA_TITLE_BIG : Notification.EXTRA_TITLE;
        CharSequence notificationTitle = extras.getCharSequence(titleExtra);
        return notificationTitle;
    }

    private static CharSequence getNotificationMessage(Notification notification) {
        Bundle extras = notification.extras;
        CharSequence notificationMessage = extras.getCharSequence(Notification.EXTRA_TEXT);

        if (TextUtils.isEmpty(notificationMessage)) {
            CharSequence[] inboxLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (inboxLines == null || inboxLines.length == 0) {
                notificationMessage = "";
            } else {
                notificationMessage = TextUtils.join("\n", inboxLines);
            }
        }
        return notificationMessage;
    }

    public static boolean hasFilterableContent(Notification notification) {
        CharSequence notificationTitle = getNotificationTitle(notification);
        CharSequence notificationMessage = getNotificationMessage(notification);
        return !(TextUtils.isEmpty(notificationTitle) && TextUtils.isEmpty(notificationMessage));
    }
}
