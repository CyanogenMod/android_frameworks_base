package com.android.internal.util.cm;

import android.app.Notification;
import android.content.ContentResolver;
import android.net.Uri;
import android.text.TextUtils;

public class SpamFilter {

    public static final String AUTHORITY = "com.cyanogenmod.spam";
    public static final Uri sNotificationUri;
    static {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(ContentResolver.SCHEME_CONTENT);
        builder.authority(AUTHORITY);
        sNotificationUri = builder.build();
    }

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
        msg = msg.toLowerCase().replaceAll("[^A-Za-z0-9]", "");
        return msg;
    }

    public static String getNotificationContent(Notification notification) {
        CharSequence notificationTitle = "";
        if (!notification.extras.containsKey(Notification.EXTRA_TITLE_BIG)) {
            // Regular notification style
            notificationTitle = notification.extras
                    .getCharSequence(Notification.EXTRA_TITLE);
        } else {
            // Big view notification style (BigText,BigPicture,Inbox)
            notificationTitle = notification.extras
                    .getCharSequence(Notification.EXTRA_TITLE_BIG);
        }
        CharSequence notificationMessage = notification.extras
                .getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(notificationMessage)) {
            CharSequence[] inboxLines = notification.extras
                    .getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (inboxLines == null || inboxLines.length == 0) {
                notificationMessage = "";
            } else {
                notificationMessage = TextUtils.join("\n", inboxLines);
            }
        }
        String result = notificationTitle + "\n" + notificationMessage;
        return result;
    }
}
