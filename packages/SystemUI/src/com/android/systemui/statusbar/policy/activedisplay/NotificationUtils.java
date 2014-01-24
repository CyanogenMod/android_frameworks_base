/*
* Copyright (C) 2013-2014 AChep@xda <artemchep@gmail.com>
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
* MA 02110-1301, USA.
*/
package com.android.systemui.statusbar.policy.activedisplay;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.widget.RoundedDrawable;

import java.util.ArrayList;

/**
 * The list of currently displaying notifications.
 */
public class NotificationUtils {

    public static NotificationData getNotificationData(Context context, StatusBarNotification notification) {
        NotificationData nd = new NotificationData();
        final Notification n = notification.getNotification();
        try {
             Bundle extras = notification.getNotification().extras;
             nd.iconApp = RoundedDrawable.drawableToBitmap(RoundedDrawable.fromBitmap(n.largeIcon));
             if (nd.iconApp == null) {
                 nd.iconApp = RoundedDrawable.drawableToBitmap(RoundedDrawable.fromDrawable(getIconDrawable(context, notification)));
             }
             nd.iconAppSmall = RoundedDrawable.drawableToBitmap(RoundedDrawable.fromDrawable(getIconDrawable(context, notification)));
             nd.titleText = extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
             if (nd.titleText == null) {
                 nd.titleText = extras.getCharSequence(Notification.EXTRA_TITLE);
             }
             nd.infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT);
             nd.subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
             nd.summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT);
             nd.tickerText = n.tickerText;
             nd.number = n.number;

             // large message
             CharSequence[] textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
             if (textLines != null) {
                 StringBuilder sb = new StringBuilder();
                 for (CharSequence line : textLines) {
                      sb.append(line);
                      sb.append('\n');
                 }
                 nd.largeMessageText = removeSpaces(sb.toString());
             }
             if (nd.messageText != null) {
                 nd.messageText = removeSpaces(nd.messageText.toString());
             }
        } catch (Exception e) {
             return null;
        }
        if (nd.titleText != null && nd.messageText != null) {
            return nd;
        }

        // Replace app's context with notification's context
        // to be able to get its resources.
        Context appContext = createPackageContext(context, notification);
        final RemoteViews rvs = n.bigContentView == null ? n.contentView : n.bigContentView;
        ViewGroup view;

        try {
            LayoutInflater inflater = (LayoutInflater) appContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = (ViewGroup) inflater.inflate(rvs.getLayoutId(), null);
            if (view == null) {
                return null;
            }
            rvs.reapply(appContext, view);
        } catch (Exception e) {
            return null;
        }

        ArrayList<ViewParentLink<TextView>> textViews =
                new RecursiveFinder(TextView.class).expand(view, true);
        ViewParentLink<TextView> title = findTitleTextView(textViews);

        nd.titleText = title.view.getText();
        nd.messageText = findMessageText(context, textViews, n, title);

        return nd;
    }

    public static String removeSpaces(String string) {
        if (string == null) {
            return null;
        }
        return string
                .replaceAll("(\\s+$|^\\s+)", "")
                .replaceAll("\n+", "\n");
    }

    public static Context createPackageContext(Context context, StatusBarNotification n) {
        try {
            return context.createPackageContext(n.getPackageName(), Context.CONTEXT_RESTRICTED);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    public static Drawable getIconDrawable(Context context, StatusBarNotification sbn) {
        Drawable drawable = null;
        try {
            Context pkgContext = context.createPackageContext(sbn.getPackageName(), Context.CONTEXT_RESTRICTED);
            drawable = pkgContext.getResources().getDrawable(sbn.getNotification().icon);
        } catch (NameNotFoundException nnfe) {
            drawable = null;
        } catch (Resources.NotFoundException nfe) {
            drawable = null;
        }
        return drawable;
    }

    public static ViewParentLink<TextView> findTitleTextView(
            ArrayList<ViewParentLink<TextView>> textViewsList) {
        int item = 0;
        float maxTextSize = textViewsList.get(item).view.getTextSize();

        final int size = textViewsList.size();
        for (int i = 1; i < size; i++) {
            float textSize = textViewsList.get(i).view.getTextSize();
            if (textSize > maxTextSize) {
                maxTextSize = textSize;
                item = i;
            }
        }

        return textViewsList.get(item);
    }

    public static String findMessageText(Context context,
                                   ArrayList<ViewParentLink<TextView>> textViewsList,
                                   Notification notification,
                                   ViewParentLink<TextView> title) {
        float subtextSize = context.getResources().getDimension(R.dimen.notification_subtext_size);
        int offset = 0;

        // Remove title view
        textViewsList.remove(title);

        // Remove a lot of unneeded action texts
        if (notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                final int size = textViewsList.size();
                for (int i = 0; i < size; i++) {
                    if (textViewsList.get(i).view.getText().equals(action.title)) {
                        textViewsList.remove(i);
                        break;
                    }
                }
            }
        }

        // Remove subtexts such as time or progress text.
        int size = textViewsList.size();
        for (int i = 0; i < size; i++) {
            final int k = i + offset;
            final TextView view = textViewsList.get(k).view;
            if (view.getTextSize() == subtextSize
                    || view.toString().matches("^(\\s*|)$")) {
                textViewsList.remove(k);
                offset--;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (ViewParentLink<TextView> tv : textViewsList) {
            sb.append(tv.view.getText().toString().replaceAll("\\s+$", ""));
            sb.append('\n');
        }
        if (sb.length() != 0) {
            sb.delete(sb.length() - 1, sb.length());
        }
        return sb.toString();
    }

    public static class RecursiveFinder<T extends View> {

        private final ArrayList<ViewParentLink<T>> list;
        private final Class<T> clazz;

        public RecursiveFinder(Class<T> clazz) {
            this.list = new ArrayList();
            this.clazz = clazz;
        }

        private ArrayList<ViewParentLink<T>> expand(ViewGroup viewGroup, boolean visibleOnly) {
            int offset = 0;
            int childCount = viewGroup.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = viewGroup.getChildAt(i + offset);

                if (child == null || (visibleOnly && child.getVisibility() != View.VISIBLE)) {
                    continue;
                }

                if (clazz.isAssignableFrom(child.getClass())) {
                    //noinspection unchecked
                    list.add(new ViewParentLink((T) child, viewGroup));
                } else if (child instanceof ViewGroup) {
                    expand((ViewGroup) child, visibleOnly);
                }
            }
            return list;
        }
    }

    public static class ViewParentLink<T extends View> {

        private ViewGroup parent;
        private T view;

        public ViewParentLink(T view, ViewGroup parent) {
            this.parent = parent;
            this.view = view;
        }

    }
}
