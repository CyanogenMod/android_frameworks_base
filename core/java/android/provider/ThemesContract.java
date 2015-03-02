package android.provider;

import android.net.Uri;

/**
 * @hide
 */
public class ThemesContract {
    public static final String AUTHORITY = "com.cyanogenmod.themes";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    public static class ThemesColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "themes");

        /**
         * The unique ID for a row.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String _ID = "_id";

        /**
         * The user visible title.
         * <P>Type: TEXT</P>
         */
        public static final String TITLE = "title";

        /**
         * Unique text to identify the apk pkg. ie "com.foo.bar"
         * <P>Type: TEXT</P>
         */
        public static final String PKG_NAME = "pkg_name";

        /**
         * A 32 bit RRGGBB color representative of the themes color scheme
         * <P>Type: INTEGER</P>
         */
        public static final String PRIMARY_COLOR = "primary_color";

        /**
         * A 2nd 32 bit RRGGBB color representative of the themes color scheme
         * <P>Type: INTEGER</P>
         */
        public static final String SECONDARY_COLOR = "secondary_color";

        /**
         * Name of the author of the theme
         * <P>Type: TEXT</P>
         */
        public static final String AUTHOR = "author";

        /**
         * The time that this row was created on its originating client (msecs
         * since the epoch).
         * <P>Type: INTEGER</P>
         */
        public static final String DATE_CREATED = "created";

        /**
         * URI to an image that shows the homescreen with the theme applied
         * since the epoch).
         * <P>Type: TEXT</P>
         */
        public static final String HOMESCREEN_URI = "homescreen_uri";

        /**
         * URI to an image that shows the lockscreen with theme applied
         * <P>Type: TEXT</P>
         */
        public static final String LOCKSCREEN_URI = "lockscreen_uri";

        /**
         * URI to an image that shows the style (aka skin) with theme applied
         * <P>Type: TEXT</P>
         */
        public static final String STYLE_URI = "style_uri";

        /**
         * TODO: Figure structure for actual animation instead of static
         * URI to an image of the boot_anim.
         * <P>Type: TEXT</P>
         */
        public static final String BOOT_ANIM_URI = "bootanim_uri";

        /**
         * URI to an image of the status bar for this theme.
         * <P>Type: TEXT</P>
         */
        public static final String STATUSBAR_URI = "status_uri";

        /**
         * URI to an image of the fonts in this theme.
         * <P>Type: TEXT</P>
         */
        public static final String FONT_URI = "font_uri";

        /**
         * URI to an image of the fonts in this theme.
         * <P>Type: TEXT</P>
         */
        public static final String ICON_URI = "icon_uri";

        /**
         * URI to an image of the fonts in this theme.
         * <P>Type: TEXT</P>
         */
        public static final String OVERLAYS_URI = "overlays_uri";

        /**
         * 1 if theme modifies the launcher/homescreen else 0
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_LAUNCHER = "mods_homescreen";

        /**
         * 1 if theme modifies the lockscreen else 0
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_LOCKSCREEN = "mods_lockscreen";

        /**
         * 1 if theme modifies icons else 0
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_ICONS = "mods_icons";

        /**
         * 1 if theme modifies fonts
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_FONTS = "mods_fonts";

        /**
         * 1 if theme modifies boot animation
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_BOOT_ANIM = "mods_bootanim";

        /**
         * 1 if theme modifies notifications
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_NOTIFICATIONS = "mods_notifications";

        /**
         * 1 if theme modifies alarm sounds
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_ALARMS = "mods_alarms";

        /**
         * 1 if theme modifies ringtones
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_RINGTONES = "mods_ringtones";

        /**
         * 1 if theme has overlays
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_OVERLAYS = "mods_overlays";

        /**
         * 1 if theme has an overlay for SystemUI/StatusBar
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_STATUS_BAR = "mods_status_bar";

        /**
         * 1 if theme has an overlay for SystemUI/NavBar
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String MODIFIES_NAVIGATION_BAR = "mods_navigation_bar";

        /**
         * URI to the theme's wallpaper. We should support multiple wallpaper
         * but for now we will just have 1.
         * <P>Type: TEXT</P>
         */
        public static final String WALLPAPER_URI = "wallpaper_uri";

        /**
         * 1 if this row should actually be presented as a theme to the user.
         * For example if a "theme" only modifies one component (ex icons) then
         * we do not present it to the user under the themes table.
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String PRESENT_AS_THEME = "present_as_theme";

        /**
         * 1 if this theme is a legacy theme.
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String IS_LEGACY_THEME = "is_legacy_theme";

        /**
         * 1 if this theme is the system default theme.
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String IS_DEFAULT_THEME = "is_default_theme";

        /**
         * 1 if this theme is a legacy iconpack. A legacy icon pack is an APK that was written
         * for Trebuchet or a 3rd party launcher.
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String IS_LEGACY_ICONPACK = "is_legacy_iconpack";

        /**
         * install/update time in millisecs. When the row is inserted this column
         * is populated by the PackageInfo. It is used for syncing to PM
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String LAST_UPDATE_TIME = "updateTime";

        /**
         * install time in millisecs. When the row is inserted this column
         * is populated by the PackageInfo.
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String INSTALL_TIME = "install_time";

        /**
         * The target API this theme supports
         * is populated by the PackageInfo.
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String TARGET_API = "target_api";

        /**
         * The install state of the theme.
         * Can be one of the following:
         * {@link InstallState#UNKNOWN}
         * {@link InstallState#INSTALLING}
         * {@link InstallState#UPDATING}
         * {@link InstallState#INSTALLED}
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String INSTALL_STATE = "install_state";

        public static class InstallState {
            public static final int UNKNOWN = 0;
            public static final int INSTALLING = 1;
            public static final int UPDATING = 2;
            public static final int INSTALLED = 3;
        }
    }

    /**
     * Key-value table which assigns a component (ex wallpaper) to a theme's package
     */
    public static class MixnMatchColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "mixnmatch");

        /**
         * The unique key for a row. See the KEY_* constants
         * for valid examples
         * <P>Type: TEXT</P>
         */
        public static final String COL_KEY = "key";

        /**
         * The package name that corresponds to a given component.
         * <P>Type: String</P>
         */
        public static final String COL_VALUE = "value";

        /**
         * Valid keys
         */
        public static final String KEY_HOMESCREEN = "mixnmatch_homescreen";
        public static final String KEY_LOCKSCREEN = "mixnmatch_lockscreen";
        public static final String KEY_ICONS = "mixnmatch_icons";
        public static final String KEY_STATUS_BAR = "mixnmatch_status_bar";
        public static final String KEY_BOOT_ANIM = "mixnmatch_boot_anim";
        public static final String KEY_FONT = "mixnmatch_font";
        public static final String KEY_ALARM = "mixnmatch_alarm";
        public static final String KEY_NOTIFICATIONS = "mixnmatch_notifications";
        public static final String KEY_RINGTONE = "mixnmatch_ringtone";
        public static final String KEY_OVERLAYS = "mixnmatch_overlays";
        public static final String KEY_NAVIGATION_BAR = "mixnmatch_navigation_bar";

        public static final String[] ROWS = { KEY_HOMESCREEN,
            KEY_LOCKSCREEN,
            KEY_ICONS,
            KEY_STATUS_BAR,
            KEY_BOOT_ANIM,
            KEY_FONT,
            KEY_NOTIFICATIONS,
            KEY_RINGTONE,
            KEY_ALARM,
            KEY_OVERLAYS,
            KEY_NAVIGATION_BAR
        };

        /**
         * For a given key value in the MixNMatch table, return the column
         * associated with it in the Themes Table. This is useful for URI based
         * elements like wallpaper where the caller wishes to determine the
         * wallpaper URI.
         */
        public static String componentToImageColName(String component) {
            if (component.equals(MixnMatchColumns.KEY_HOMESCREEN)) {
                return ThemesColumns.HOMESCREEN_URI;
            } else if (component.equals(MixnMatchColumns.KEY_LOCKSCREEN)) {
                return ThemesColumns.LOCKSCREEN_URI;
            } else if (component.equals(MixnMatchColumns.KEY_BOOT_ANIM)) {
                return ThemesColumns.BOOT_ANIM_URI;
            } else if (component.equals(MixnMatchColumns.KEY_FONT)) {
                return ThemesColumns.FONT_URI;
            } else if (component.equals(MixnMatchColumns.KEY_ICONS)) {
                return ThemesColumns.ICON_URI;
            } else if (component.equals(MixnMatchColumns.KEY_STATUS_BAR)) {
                return ThemesColumns.STATUSBAR_URI;
            } else if (component.equals(MixnMatchColumns.KEY_NOTIFICATIONS)) {
                throw new IllegalArgumentException("Notifications mixnmatch component does not have a related column");
            } else if (component.equals(MixnMatchColumns.KEY_RINGTONE)) {
                throw new IllegalArgumentException("Ringtone mixnmatch component does not have a related column");
            } else if (component.equals(MixnMatchColumns.KEY_OVERLAYS)) {
                return ThemesColumns.OVERLAYS_URI;
            } else if (component.equals(MixnMatchColumns.KEY_STATUS_BAR)) {
                throw new IllegalArgumentException(
                        "Status bar mixnmatch component does not have a related column");
            } else if (component.equals(MixnMatchColumns.KEY_NAVIGATION_BAR)) {
                throw new IllegalArgumentException(
                        "Navigation bar mixnmatch component does not have a related column");
            }
            return null;
        }

        /**
         * A component in the themes table (IE "mods_wallpaper") has an
         * equivalent key in mixnmatch table
         */
        public static String componentToMixNMatchKey(String component) {
            if (component.equals(ThemesColumns.MODIFIES_LAUNCHER)) {
                return MixnMatchColumns.KEY_HOMESCREEN;
            } else if (component.equals(ThemesColumns.MODIFIES_ICONS)) {
                return MixnMatchColumns.KEY_ICONS;
            } else if (component.equals(ThemesColumns.MODIFIES_LOCKSCREEN)) {
                return MixnMatchColumns.KEY_LOCKSCREEN;
            } else if (component.equals(ThemesColumns.MODIFIES_FONTS)) {
                return MixnMatchColumns.KEY_FONT;
            } else if (component.equals(ThemesColumns.MODIFIES_BOOT_ANIM)) {
                return MixnMatchColumns.KEY_BOOT_ANIM;
            } else if (component.equals(ThemesColumns.MODIFIES_ALARMS)) {
                return MixnMatchColumns.KEY_ALARM;
            } else if (component.equals(ThemesColumns.MODIFIES_NOTIFICATIONS)) {
                return MixnMatchColumns.KEY_NOTIFICATIONS;
            } else if (component.equals(ThemesColumns.MODIFIES_RINGTONES)) {
                return MixnMatchColumns.KEY_RINGTONE;
            } else if (component.equals(ThemesColumns.MODIFIES_OVERLAYS)) {
                return MixnMatchColumns.KEY_OVERLAYS;
            } else if (component.equals(ThemesColumns.MODIFIES_STATUS_BAR)) {
                return MixnMatchColumns.KEY_STATUS_BAR;
            } else if (component.equals(ThemesColumns.MODIFIES_NAVIGATION_BAR)) {
                return MixnMatchColumns.KEY_NAVIGATION_BAR;
            }
            return null;
        }

        /**
         * A mixnmatch key in has an
         * equivalent value in the themes table
         */
        public static String mixNMatchKeyToComponent(String mixnmatchKey) {
            if (mixnmatchKey.equals(MixnMatchColumns.KEY_HOMESCREEN)) {
                return ThemesColumns.MODIFIES_LAUNCHER;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_ICONS)) {
                return ThemesColumns.MODIFIES_ICONS;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_LOCKSCREEN)) {
                return ThemesColumns.MODIFIES_LOCKSCREEN;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_FONT)) {
                return ThemesColumns.MODIFIES_FONTS;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_BOOT_ANIM)) {
                return ThemesColumns.MODIFIES_BOOT_ANIM;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_ALARM)) {
                return ThemesColumns.MODIFIES_ALARMS;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_NOTIFICATIONS)) {
                return ThemesColumns.MODIFIES_NOTIFICATIONS;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_RINGTONE)) {
                return ThemesColumns.MODIFIES_RINGTONES;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_OVERLAYS)) {
                return ThemesColumns.MODIFIES_OVERLAYS;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_STATUS_BAR)) {
                return ThemesColumns.MODIFIES_STATUS_BAR;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_NAVIGATION_BAR)) {
                return ThemesColumns.MODIFIES_NAVIGATION_BAR;
            }
            return null;
        }
    }

    /**
     * Table containing cached preview blobs for a given theme
     */
    public static class PreviewColumns {
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "previews");

        /**
         * Uri for retrieving the previews for the currently applied components.
         * Querying the themes provider using this URI will return a cursor with a single row
         * containing all the previews for the components that are currently applied.
         */
        public static final Uri APPLIED_URI = Uri.withAppendedPath(AUTHORITY_URI,
                "applied_previews");

        /**
         * The unique ID for a row.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String _ID = "_id";

        /**
         * The unique ID for the theme these previews belong to.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String THEME_ID = "theme_id";

        /**
         * Cached image of the themed status bar background.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String STATUSBAR_BACKGROUND = "statusbar_background";

        /**
         * Cached image of the themed bluetooth status icon.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String STATUSBAR_BLUETOOTH_ICON = "statusbar_bluetooth_icon";

        /**
         * Cached image of the themed wifi status icon.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String STATUSBAR_WIFI_ICON = "statusbar_wifi_icon";

        /**
         * Cached image of the themed cellular signal status icon.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String STATUSBAR_SIGNAL_ICON = "statusbar_signal_icon";

        /**
         * Cached image of the themed battery using portrait style.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String STATUSBAR_BATTERY_PORTRAIT = "statusbar_battery_portrait";

        /**
         * Cached image of the themed battery using landscape style.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String STATUSBAR_BATTERY_LANDSCAPE = "statusbar_battery_landscape";

        /**
         * Cached image of the themed battery using circle style.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String STATUSBAR_BATTERY_CIRCLE = "statusbar_battery_circle";

        /**
         * The themed margin value between the wifi and rssi signal icons.
         * <P>Type: INTEGER (int)</P>
         */
        public static final String STATUSBAR_WIFI_COMBO_MARGIN_END = "wifi_combo_margin_end";

        /**
         * The themed color used for clock text in the status bar.
         * <P>Type: INTEGER (int)</P>
         */
        public static final String STATUSBAR_CLOCK_TEXT_COLOR = "statusbar_clock_text_color";

        /**
         * Cached image of the themed navigation bar background.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String NAVBAR_BACKGROUND = "navbar_background";

        /**
         * Cached image of the themed back button.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String NAVBAR_BACK_BUTTON = "navbar_back_button";

        /**
         * Cached image of the themed home button.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String NAVBAR_HOME_BUTTON = "navbar_home_button";

        /**
         * Cached image of the themed recents button.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String NAVBAR_RECENT_BUTTON = "navbar_recent_button";

        /**
         * Cached image of the 1/4 icons
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String ICON_PREVIEW_1 = "icon_preview_1";

        /**
         * Cached image of the 2/4 icons
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String ICON_PREVIEW_2 = "icon_preview_2";

        /**
         * Cached image of the 3/4 icons
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String ICON_PREVIEW_3 = "icon_preview_3";

        /**
         * Cached image of the 4/4 icons
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String ICON_PREVIEW_4 = "icon_preview_4";

        /**
         * Cached preview of UI controls representing the theme's style
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String STYLE_PREVIEW = "style_preview";

        /**
         * Cached thumbnail preview of UI controls representing the theme's style
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String STYLE_THUMBNAIL = "style_thumbnail";

        /**
         * Cached thumbnail of the theme's boot animation
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String BOOTANIMATION_THUMBNAIL = "bootanimation_thumbnail";

        /**
         * Cached thumbnail of the theme's wallpaper
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String WALLPAPER_THUMBNAIL = "wallpaper_thumbnail";

        /**
         * Cached preview of the theme's wallpaper which is larger than the thumbnail
         * but smaller than the full sized wallpaper.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String WALLPAPER_PREVIEW = "wallpaper_preview";

        /**
         * Cached thumbnail of the theme's lockscreen wallpaper
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String LOCK_WALLPAPER_THUMBNAIL = "lock_wallpaper_thumbnail";

        /**
         * Cached preview of the theme's lockscreen  wallpaper which is larger than the thumbnail
         * but smaller than the full sized lockscreen wallpaper.
         * <P>Type: BLOB (bitmap)</P>
         */
        public static final String LOCK_WALLPAPER_PREVIEW = "lock_wallpaper_preview";
    }

    public static class Intent {
        /**
         * Action sent from the provider when a theme has been fully installed.  Fully installed
         * means that the apk was installed by PackageManager and the theme resources were
         * processed and cached by {@link com.android.server.ThemeService}
         * Requires the {@link android.Manifest.permission#READ_THEMES} permission to receive
         * this broadcast.
         */
        public static final String ACTION_THEME_INSTALLED =
                "themescontract.intent.action.THEME_INSTALLED";

        /**
         * Action sent from the provider when a theme has been updated.
         * Requires the {@link android.Manifest.permission#READ_THEMES} permission to receive
         * this broadcast.
         */
        public static final String ACTION_THEME_UPDATED =
                "themescontract.intent.action.THEME_UPDATED";

        /**
         * Action sent from the provider when a theme has been removed.
         * Requires the {@link android.Manifest.permission#READ_THEMES} permission to receive
         * this broadcast.
         */
        public static final String ACTION_THEME_REMOVED =
                "themescontract.intent.action.THEME_REMOVED";

        /**
         * Uri scheme used to broadcast the theme's package name when broadcasting
         * {@link android.provider.ThemesContract.Intent#ACTION_THEME_INSTALLED} or
         * {@link android.provider.ThemesContract.Intent#ACTION_THEME_REMOVED}
         */
        public static final String URI_SCHEME_PACKAGE = "package";
    }
}
