package com.android.internal.util.cm;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * The smart package stats contract
 */
public class SmartPackageStatsContracts {
    /** The authority for the smart package stats provider */
    public static final String AUTHORITY = "com.cnyng.smartpackagestats";

    /** A content:// style uri to the authority for the partner bookmarks provider */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    public static final class RawStats implements BaseColumns {
        private RawStats() {}

        /**
         * The path for raw stats
         */
        public static final String PATH = "rawstats";

        /**
         * The content:// style URI for this table
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, PATH);

        /**
         * The package name for a row.
         * <p>Type: TEXT </p>
         */
        public static final String PKG_NAME = "pkg_name";

        /**
         * The launch count of a package.
         * <p>Type: LONG (long)</p>
         */
        public static final String LAUNCH_COUNT = "launch_count";

        /**
         * The usage time of a package.
         * <p>Type: LONG (long)</p>
         */
        public static final String USAGE_TIME = "usage_time";

        /**
         * The paused time of a package.
         * <p>Type: LONG (long)</p>
         */
        public static final String PAUSED_TIME = "paused_time";

        /**
         * The resumed time of a package.
         * <p>Type: LONG (long)</p>
         */
        public static final String RESUMED_TIME = "resumed_time";
    }
}
