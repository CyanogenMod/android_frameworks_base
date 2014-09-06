/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.cm;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * The smart package stats contract
 */
public class SmartPackageStatsContracts {
    /** The authority for the smart package stats provider */
    public static final String AUTHORITY = "com.cyngn.smartpackagestats";

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
         * <p>Type: TEXT</p>
         */
        public static final String PKG_NAME = "pkg_name";

        /**
         * The component name for a row.
         * <p>Type: TEXT</p>
         */
        public static final String COMP_NAME = "comp_name";

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
