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
         * install/update time in millisecs. When the row is inserted this column
         * is populated by the PackageInfo. It is used for syncing to PM
         * <P>Type: INTEGER</P>
         * <P>Default: 0</P>
         */
        public static final String LAST_UPDATE_TIME = "updateTime";
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
        public static final String KEY_NOTIFICATIONS = "mixnmatch_notifications";
        public static final String KEY_RINGTONE = "mixnmatch_ringtone";
        public static final String KEY_OVERLAYS = "mixnmatch_overlays";

        public static final String[] ROWS = { KEY_HOMESCREEN,
            KEY_LOCKSCREEN,
            KEY_ICONS,
            KEY_STATUS_BAR,
            KEY_BOOT_ANIM,
            KEY_FONT,
            KEY_NOTIFICATIONS,
            KEY_RINGTONE,
            KEY_OVERLAYS
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
            } else if (component.equals(ThemesColumns.MODIFIES_NOTIFICATIONS)) {
                return MixnMatchColumns.KEY_NOTIFICATIONS;
            } else if (component.equals(ThemesColumns.MODIFIES_OVERLAYS)) {
                return MixnMatchColumns.KEY_OVERLAYS;
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
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_NOTIFICATIONS)) {
                return ThemesColumns.MODIFIES_NOTIFICATIONS;
            } else if (mixnmatchKey.equals(MixnMatchColumns.KEY_OVERLAYS)) {
                return ThemesColumns.MODIFIES_OVERLAYS;
            }
            return null;
        }
    }
}
