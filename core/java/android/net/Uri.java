/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;

/**
 * Immutable URI reference. A URI reference includes a URI and a fragment, the
 * component of the URI following a '#'. Builds and parses URI references
 * which conform to
 * <a href="http://www.faqs.org/rfcs/rfc2396.html">RFC 2396</a>.
 *
 * <p>In the interest of performance, this class performs little to no
 * validation. Behavior is undefined for invalid input. This class is very
 * forgiving--in the face of invalid input, it will return garbage
 * rather than throw an exception unless otherwise specified.
 */
public abstract class Uri implements Parcelable, Comparable<Uri> {

    /*

    This class aims to do as little up front work as possible. To accomplish
    that, we vary the implementation dependending on what the user passes in.
    For example, we have one implementation if the user passes in a
    URI string (StringUri) and another if the user passes in the
    individual components (OpaqueUri).

    *Concurrency notes*: Like any truly immutable object, this class is safe
    for concurrent use. This class uses a caching pattern in some places where
    it doesn't use volatile or synchronized. This is safe to do with ints
    because getting or setting an int is atomic. It's safe to do with a String
    because the internal fields are final and the memory model guarantees other
    threads won't see a partially initialized instance. We are not guaranteed
    that some threads will immediately see changes from other threads on
    certain platforms, but we don't mind if those threads reconstruct the
    cached result. As a result, we get thread safe caching with no concurrency
    overhead, which means the most common case, access from a single thread,
    is as fast as possible.

    From the Java Language spec.:

    "17.5 Final Field Semantics

    ... when the object is seen by another thread, that thread will always
    see the correctly constructed version of that object's final fields.
    It will also see versions of any object or array referenced by
    those final fields that are at least as up-to-date as the final fields
    are."

    In that same vein, all non-transient fields within Uri
    implementations should be final and immutable so as to ensure true
    immutability for clients even when they don't use proper concurrency
    control.

    For reference, from RFC 2396:

    "4.3. Parsing a URI Reference

       A URI reference is typically parsed according to the four main
       components and fragment identifier in order to determine what
       components are present and whether the reference is relative or
       absolute.  The individual components are then parsed for their
       subparts and, if not opaque, to verify their validity.

       Although the BNF defines what is allowed in each component, it is
       ambiguous in terms of differentiating between an authority component
       and a path component that begins with two slash characters.  The
       greedy algorithm is used for disambiguation: the left-most matching
       rule soaks up as much of the URI reference string as it is capable of
       matching.  In other words, the authority component wins."

    The "four main components" of a hierarchical URI consist of
    <scheme>://<authority><path>?<query>

    */

    /** Log tag. */
    private static final String LOG = Uri.class.getSimpleName();

    /**
     * NOTE: EMPTY accesses this field during its own initialization, so this
     * field *must* be initialized first, or else EMPTY will see a null value!
     *
     * Placeholder for strings which haven't been cached. This enables us
     * to cache null. We intentionally create a new String instance so we can
     * compare its identity and there is no chance we will confuse it with
     * user data.
     */
    @SuppressWarnings("RedundantStringConstructorCall")
    private static final String NOT_CACHED = new String("NOT CACHED");

    /**
     * The empty URI, equivalent to "".
     */
    public static final Uri EMPTY = new HierarchicalUri(null, Part.NULL,
            PathPart.EMPTY, Part.NULL, Part.NULL);

    /**
     * Prevents external subclassing.
     */
    private Uri() {}

    /**
     * Returns true if this URI is hierarchical like "http://google.com".
     * Absolute URIs are hierarchical if the scheme-specific part starts with
     * a '/'. Relative URIs are always hierarchical.
     */
    public abstract boolean isHierarchical();

    /**
     * Returns true if this URI is opaque like "mailto:nobody@google.com". The
     * scheme-specific part of an opaque URI cannot start with a '/'.
     */
    public boolean isOpaque() {
        return !isHierarchical();
    }

    /**
     * Returns true if this URI is relative, i.e. if it doesn't contain an
     * explicit scheme.
     *
     * @return true if this URI is relative, false if it's absolute
     */
    public abstract boolean isRelative();

    /**
     * Returns true if this URI is absolute, i.e. if it contains an
     * explicit scheme.
     *
     * @return true if this URI is absolute, false if it's relative
     */
    public boolean isAbsolute() {
        return !isRelative();
    }

    /**
     * Gets the scheme of this URI. Example: "http"
     *
     * @return the scheme or null if this is a relative URI
     */
    public abstract String getScheme();

    /**
     * Gets the scheme-specific part of this URI, i.e. everything between the
     * scheme separator ':' and the fragment separator '#'. If this is a
     * relative URI, this method returns the entire URI. Decodes escaped octets.
     *
     * <p>Example: "//www.google.com/search?q=android"
     *
     * @return the decoded scheme-specific-part
     */
    public abstract String getSchemeSpecificPart();

    /**
     * Gets the scheme-specific part of this URI, i.e. everything between the
     * scheme separator ':' and the fragment separator '#'. If this is a
     * relative URI, this method returns the entire URI. Leaves escaped octets
     * intact.
     *
     * <p>Example: "//www.google.com/search?q=android"
     *
     * @return the decoded scheme-specific-part
     */
    public abstract String getEncodedSchemeSpecificPart();

    /**
     * Gets the decoded authority part of this URI. For
     * server addresses, the authority is structured as follows:
     * {@code [ userinfo '@' ] host [ ':' port ]}
     *
     * <p>Examples: "google.com", "bob@google.com:80"
     *
     * @return the authority for this URI or null if not present
     */
    public abstract String getAuthority();

    /**
     * Gets the encoded authority part of this URI. For
     * server addresses, the authority is structured as follows:
     * {@code [ userinfo '@' ] host [ ':' port ]}
     *
     * <p>Examples: "google.com", "bob@google.com:80"
     *
     * @return the authority for this URI or null if not present
     */
    public abstract String getEncodedAuthority();

    /**
     * Gets the decoded user information from the authority.
     * For example, if the authority is "nobody@google.com", this method will
     * return "nobody".
     *
     * @return the user info for this URI or null if not present
     */
    public abstract String getUserInfo();

    /**
     * Gets the encoded user information from the authority.
     * For example, if the authority is "nobody@google.com", this method will
     * return "nobody".
     *
     * @return the user info for this URI or null if not present
     */
    public abstract String getEncodedUserInfo();

    /**
     * Gets the encoded host from the authority for this URI. For example,
     * if the authority is "bob@google.com", this method will return
     * "google.com".
     *
     * @return the host for this URI or null if not present
     */
    public abstract String getHost();

    /**
     * Gets the port from the authority for this URI. For example,
     * if the authority is "google.com:80", this method will return 80.
     *
     * @return the port for this URI or -1 if invalid or not present
     */
    public abstract int getPort();

    /**
     * Gets the decoded path.
     *
     * @return the decoded path, or null if this is not a hierarchical URI
     * (like "mailto:nobody@google.com") or the URI is invalid
     */
    public abstract String getPath();

    /**
     * Gets the encoded path.
     *
     * @return the encoded path, or null if this is not a hierarchical URI
     * (like "mailto:nobody@google.com") or the URI is invalid
     */
    public abstract String getEncodedPath();

    /**
     * Gets the decoded query component from this URI. The query comes after
     * the query separator ('?') and before the fragment separator ('#'). This
     * method would return "q=android" for
     * "http://www.google.com/search?q=android".
     *
     * @return the decoded query or null if there isn't one
     */
    public abstract String getQuery();

    /**
     * Gets the encoded query component from this URI. The query comes after
     * the query separator ('?') and before the fragment separator ('#'). This
     * method would return "q=android" for
     * "http://www.google.com/search?q=android".
     *
     * @return the encoded query or null if there isn't one
     */
    public abstract String getEncodedQuery();

    /**
     * Gets the decoded fragment part of this URI, everything after the '#'.
     *
     * @return the decoded fragment or null if there isn't one
     */
    public abstract String getFragment();

    /**
     * Gets the encoded fragment part of this URI, everything after the '#'.
     *
     * @return the encoded fragment or null if there isn't one
     */
    public abstract String getEncodedFragment();

    /**
     * Gets the decoded path segments.
     *
     * @return decoded path segments, each without a leading or trailing '/'
     */
    public abstract List<String> getPathSegments();

    /**
     * Gets the decoded last segment in the path.
     *
     * @return the decoded last segment or null if the path is empty
     */
    public abstract String getLastPathSegment();

    /**
     * Compares this Uri to another object for equality. Returns true if the
     * encoded string representations of this Uri and the given Uri are
     * equal. Case counts. Paths are not normalized. If one Uri specifies a
     * default port explicitly and the other leaves it implicit, they will not
     * be considered equal.
     */
    public boolean equals(Object o) {
        if (!(o instanceof Uri)) {
            return false;
        }

        Uri other = (Uri) o;

        return toString().equals(other.toString());
    }

    /**
     * Hashes the encoded string represention of this Uri consistently with
     * {@link #equals(Object)}.
     */
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Compares the string representation of this Uri with that of
     * another.
     */
    public int compareTo(Uri other) {
        return toString().compareTo(other.toString());
    }

    /**
     * Returns the encoded string representation of this URI.
     * Example: "http://google.com/"
     */
    public abstract String toString();

    /**
     * Constructs a new builder, copying the attributes from this Uri.
     */
    public abstract Builder buildUpon();

    /** Index of a component which was not found. */
    private final static int NOT_FOUND = -1;

    /** Placeholder value for an index which hasn't been calculated yet. */
    private final static int NOT_CALCULATED = -2;

    /**
     * Error message presented when a user tries to treat an opaque URI as
     * hierarchical.
     */
    private static final String NOT_HIERARCHICAL
            = "This isn't a hierarchical URI.";

    /** Default encoding. */
    private static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * Creates a Uri which parses the given encoded URI string.
     *
     * @param uriString an RFC 2396-compliant, encoded URI
     * @throws NullPointerException if uriString is null
     * @return Uri for this given uri string
     */
    public static Uri parse(String uriString) {
        return new StringUri(uriString);
    }

    /**
     * Creates a Uri from a file. The URI has the form
     * "file://<absolute path>". Encodes path characters with the exception of
     * '/'.
     *
     * <p>Example: "file:///tmp/android.txt"
     *
     * @throws NullPointerException if file is null
     * @return a Uri for the given file
     */
    public static Uri fromFile(File file) {
        if (file == null) {
            throw new NullPointerException("file");
        }

        PathPart path = PathPart.fromDecoded(file.getAbsolutePath());
        return new HierarchicalUri(
                "file", Part.EMPTY, path, Part.NULL, Part.NULL);
    }

    /**
     * An implementation which wraps a String URI. This URI can be opaque or
     * hierarchical, but we extend AbstractHierarchicalUri in case we need
     * the hierarchical functionality.
     */
    private static class StringUri extends AbstractHierarchicalUri {

        /** Used in parcelling. */
        static final int TYPE_ID = 1;

        /** URI string representation. */
        private final String uriString;

        private StringUri(String uriString) {
            if (uriString == null) {
                throw new NullPointerException("uriString");
            }

            this.uriString = uriString;
        }

        static Uri readFrom(Parcel parcel) {
            return new StringUri(parcel.readString());
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(TYPE_ID);
            parcel.writeString(uriString);
        }

        /** Cached scheme separator index. */
        private volatile int cachedSsi = NOT_CALCULATED;

        /** Finds the first ':'. Returns -1 if none found. */
        private int findSchemeSeparator() {
            return cachedSsi == NOT_CALCULATED
                    ? cachedSsi = uriString.indexOf(':')
                    : cachedSsi;
        }

        /** Cached fragment separator index. */
        private volatile int cachedFsi = NOT_CALCULATED;

        /** Finds the first '#'. Returns -1 if none found. */
        private int findFragmentSeparator() {
            return cachedFsi == NOT_CALCULATED
                    ? cachedFsi = uriString.indexOf('#', findSchemeSeparator())
                    : cachedFsi;
        }

        public boolean isHierarchical() {
            int ssi = findSchemeSeparator();

            if (ssi == NOT_FOUND) {
                // All relative URIs are hierarchical.
                return true;
            }

            if (uriString.length() == ssi + 1) {
                // No ssp.
                return false;
            }

            // If the ssp starts with a '/', this is hierarchical.
            return uriString.charAt(ssi + 1) == '/';
        }

        public boolean isRelative() {
            // Note: We return true if the index is 0
            return findSchemeSeparator() == NOT_FOUND;
        }

        private volatile String scheme = NOT_CACHED;

        public String getScheme() {
            @SuppressWarnings("StringEquality")
            boolean cached = (scheme != NOT_CACHED);
            return cached ? scheme : (scheme = parseScheme());
        }

        private String parseScheme() {
            int ssi = findSchemeSeparator();
            return ssi == NOT_FOUND ? null : uriString.substring(0, ssi);
        }

        private Part ssp;

        private Part getSsp() {
            return ssp == null ? ssp = Part.fromEncoded(parseSsp()) : ssp;
        }

        public String getEncodedSchemeSpecificPart() {
            return getSsp().getEncoded();
        }

        public String getSchemeSpecificPart() {
            return getSsp().getDecoded();
        }

        private String parseSsp() {
            int ssi = findSchemeSeparator();
            int fsi = findFragmentSeparator();

            // Return everything between ssi and fsi.
            return fsi == NOT_FOUND
                    ? uriString.substring(ssi + 1)
                    : uriString.substring(ssi + 1, fsi);
        }

        private Part authority;

        private Part getAuthorityPart() {
            if (authority == null) {
                String encodedAuthority
                        = parseAuthority(this.uriString, findSchemeSeparator());
                return authority = Part.fromEncoded(encodedAuthority);
            }

            return authority;
        }

        public String getEncodedAuthority() {
            return getAuthorityPart().getEncoded();
        }

        public String getAuthority() {
            return getAuthorityPart().getDecoded();
        }

        private PathPart path;

        private PathPart getPathPart() {
            return path == null
                    ? path = PathPart.fromEncoded(parsePath())
                    : path;
        }

        public String getPath() {
            return getPathPart().getDecoded();
        }

        public String getEncodedPath() {
            return getPathPart().getEncoded();
        }

        public List<String> getPathSegments() {
            return getPathPart().getPathSegments();
        }

        private String parsePath() {
            String uriString = this.uriString;
            int ssi = findSchemeSeparator();

            // If the URI is absolute.
            if (ssi > -1) {
                // Is there anything after the ':'?
                boolean schemeOnly = ssi + 1 == uriString.length();
                if (schemeOnly) {
                    // Opaque URI.
                    return null;
                }

                // A '/' after the ':' means this is hierarchical.
                if (uriString.charAt(ssi + 1) != '/') {
                    // Opaque URI.
                    return null;
                }
            } else {
                // All relative URIs are hierarchical.
            }

            return parsePath(uriString, ssi);
        }

        private Part query;

        private Part getQueryPart() {
            return query == null
                    ? query = Part.fromEncoded(parseQuery()) : query;
        }

        public String getEncodedQuery() {
            return getQueryPart().getEncoded();
        }

        private String parseQuery() {
            // It doesn't make sense to cache this index. We only ever
            // calculate it once.
            int qsi = uriString.indexOf('?', findSchemeSeparator());
            if (qsi == NOT_FOUND) {
                return null;
            }

            int fsi = findFragmentSeparator();

            if (fsi == NOT_FOUND) {
                return uriString.substring(qsi + 1);
            }

            if (fsi < qsi) {
                // Invalid.
                return null;
            }

            return uriString.substring(qsi + 1, fsi);
        }

        public String getQuery() {
            return getQueryPart().getDecoded();
        }

        private Part fragment;

        private Part getFragmentPart() {
            return fragment == null
                    ? fragment = Part.fromEncoded(parseFragment()) : fragment;
        }

        public String getEncodedFragment() {
            return getFragmentPart().getEncoded();
        }

        private String parseFragment() {
            int fsi = findFragmentSeparator();
            return fsi == NOT_FOUND ? null : uriString.substring(fsi + 1);
        }

        public String getFragment() {
            return getFragmentPart().getDecoded();
        }

        public String toString() {
            return uriString;
        }

        /**
         * Parses an authority out of the given URI string.
         *
         * @param uriString URI string
         * @param ssi scheme separator index, -1 for a relative URI
         *
         * @return the authority or null if none is found
         */
        static String parseAuthority(String uriString, int ssi) {
            int length = uriString.length();

            // If "//" follows the scheme separator, we have an authority.
            if (length > ssi + 2
                    && uriString.charAt(ssi + 1) == '/'
                    && uriString.charAt(ssi + 2) == '/') {
                // We have an authority.

                // Look for the start of the path, query, or fragment, or the
                // end of the string.
                int end = ssi + 3;
                LOOP: while (end < length) {
                    switch (uriString.charAt(end)) {
                        case '/': // Start of path
                        case '?': // Start of query
                        case '#': // Start of fragment
                            break LOOP;
                    }
                    end++;
                }

                return uriString.substring(ssi + 3, end);
            } else {
                return null;
            }

        }

        /**
         * Parses a path out of this given URI string.
         *
         * @param uriString URI string
         * @param ssi scheme separator index, -1 for a relative URI
         *
         * @return the path
         */
        static String parsePath(String uriString, int ssi) {
            int length = uriString.length();

            // Find start of path.
            int pathStart;
            if (length > ssi + 2
                    && uriString.charAt(ssi + 1) == '/'
                    && uriString.charAt(ssi + 2) == '/') {
                // Skip over authority to path.
                pathStart = ssi + 3;
                LOOP: while (pathStart < length) {
                    switch (uriString.charAt(pathStart)) {
                        case '?': // Start of query
                        case '#': // Start of fragment
                            return ""; // Empty path.
                        case '/': // Start of path!
                            break LOOP;
                    }
                    pathStart++;
                }
            } else {
                // Path starts immediately after scheme separator.
                pathStart = ssi + 1;
            }

            // Find end of path.
            int pathEnd = pathStart;
            LOOP: while (pathEnd < length) {
                switch (uriString.charAt(pathEnd)) {
                    case '?': // Start of query
                    case '#': // Start of fragment
                        break LOOP;
                }
                pathEnd++;
            }

            return uriString.substring(pathStart, pathEnd);
        }

        public Builder buildUpon() {
            if (isHierarchical()) {
                return new Builder()
                        .scheme(getScheme())
                        .authority(getAuthorityPart())
                        .path(getPathPart())
                        .query(getQueryPart())
                        .fragment(getFragmentPart());
            } else {
                return new Builder()
                        .scheme(getScheme())
                        .opaquePart(getSsp())
                        .fragment(getFragmentPart());
            }
        }
    }

    /**
     * Creates an opaque Uri from the given components. Encodes the ssp
     * which means this method cannot be used to create hierarchical URIs.
     *
     * @param scheme of the URI
     * @param ssp scheme-specific-part, everything between the
     *  scheme separator (':') and the fragment separator ('#'), which will
     *  get encoded
     * @param fragment fragment, everything after the '#', null if undefined,
     *  will get encoded
     *
     * @throws NullPointerException if scheme or ssp is null
     * @return Uri composed of the given scheme, ssp, and fragment
     *
     * @see Builder if you don't want the ssp and fragment to be encoded
     */
    public static Uri fromParts(String scheme, String ssp,
            String fragment) {
        if (scheme == null) {
            throw new NullPointerException("scheme");
        }
        if (ssp == null) {
            throw new NullPointerException("ssp");
        }

        return new OpaqueUri(scheme, Part.fromDecoded(ssp),
                Part.fromDecoded(fragment));
    }

    /**
     * Opaque URI.
     */
    private static class OpaqueUri extends Uri {

        /** Used in parcelling. */
        static final int TYPE_ID = 2;

        private final String scheme;
        private final Part ssp;
        private final Part fragment;

        private OpaqueUri(String scheme, Part ssp, Part fragment) {
            this.scheme = scheme;
            this.ssp = ssp;
            this.fragment = fragment == null ? Part.NULL : fragment;
        }

        static Uri readFrom(Parcel parcel) {
            return new OpaqueUri(
                parcel.readString(),
                Part.readFrom(parcel),
                Part.readFrom(parcel)
            );
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(TYPE_ID);
            parcel.writeString(scheme);
            ssp.writeTo(parcel);
            fragment.writeTo(parcel);
        }

        public boolean isHierarchical() {
            return false;
        }

        public boolean isRelative() {
            return scheme == null;
        }

        public String getScheme() {
            return this.scheme;
        }

        public String getEncodedSchemeSpecificPart() {
            return ssp.getEncoded();
        }

        public String getSchemeSpecificPart() {
            return ssp.getDecoded();
        }

        public String getAuthority() {
            return null;
        }

        public String getEncodedAuthority() {
            return null;
        }

        public String getPath() {
            return null;
        }

        public String getEncodedPath() {
            return null;
        }

        public String getQuery() {
            return null;
        }

        public String getEncodedQuery() {
            return null;
        }

        public String getFragment() {
            return fragment.getDecoded();
        }

        public String getEncodedFragment() {
            return fragment.getEncoded();
        }

        public List<String> getPathSegments() {
            return Collections.emptyList();
        }

        public String getLastPathSegment() {
            return null;
        }

        public String getUserInfo() {
            return null;
        }

        public String getEncodedUserInfo() {
            return null;
        }

        public String getHost() {
            return null;
        }

        public int getPort() {
            return -1;
        }

        private volatile String cachedString = NOT_CACHED;

        public String toString() {
            @SuppressWarnings("StringEquality")
            boolean cached = cachedString != NOT_CACHED;
            if (cached) {
                return cachedString;
            }

            StringBuilder sb = new StringBuilder();

            sb.append(scheme).append(':');
            sb.append(getEncodedSchemeSpecificPart());

            if (!fragment.isEmpty()) {
                sb.append('#').append(fragment.getEncoded());
            }

            return cachedString = sb.toString();
        }

        public Builder buildUpon() {
            return new Builder()
                    .scheme(this.scheme)
                    .opaquePart(this.ssp)
                    .fragment(this.fragment);
        }
    }

    /**
     * Wrapper for path segment array.
     */
    static class PathSegments extends AbstractList<String>
            implements RandomAccess {

        static final PathSegments EMPTY = new PathSegments(null, 0);

        final String[] segments;
        final int size;

        PathSegments(String[] segments, int size) {
            this.segments = segments;
            this.size = size;
        }

        public String get(int index) {
            if (index >= size) {
                throw new IndexOutOfBoundsException();
            }

            return segments[index];
        }

        public int size() {
            return this.size;
        }
    }

    /**
     * Builds PathSegments.
     */
    static class PathSegmentsBuilder {

        String[] segments;
        int size = 0;

        void add(String segment) {
            if (segments == null) {
                segments = new String[4];
            } else if (size + 1 == segments.length) {
                String[] expanded = new String[segments.length * 2];
                System.arraycopy(segments, 0, expanded, 0, segments.length);
                segments = expanded;
            }

            segments[size++] = segment;
        }

        PathSegments build() {
            if (segments == null) {
                return PathSegments.EMPTY;
            }

            try {
                return new PathSegments(segments, size);
            } finally {
                // Makes sure this doesn't get reused.
                segments = null;
            }
        }
    }

    /**
     * Support for hierarchical URIs.
     */
    private abstract static class AbstractHierarchicalUri extends Uri {

        public String getLastPathSegment() {
            // TODO: If we haven't parsed all of the segments already, just
            // grab the last one directly so we only allocate one string.

            List<String> segments = getPathSegments();
            int size = segments.size();
            if (size == 0) {
                return null;
            }
            return segments.get(size - 1);
        }

        private Part userInfo;

        private Part getUserInfoPart() {
            return userInfo == null
                    ? userInfo = Part.fromEncoded(parseUserInfo()) : userInfo;
        }

        public final String getEncodedUserInfo() {
            return getUserInfoPart().getEncoded();
        }

        private String parseUserInfo() {
            String authority = getEncodedAuthority();
            if (authority == null) {
                return null;
            }

            int end = authority.indexOf('@');
            return end == NOT_FOUND ? null : authority.substring(0, end);
        }

        public String getUserInfo() {
            return getUserInfoPart().getDecoded();
        }

        private volatile String host = NOT_CACHED;

        public String getHost() {
            @SuppressWarnings("StringEquality")
            boolean cached = (host != NOT_CACHED);
            return cached ? host
                    : (host = parseHost());
        }

        private String parseHost() {
            String authority = getEncodedAuthority();
            if (authority == null) {
                return null;
            }

            // Parse out user info and then port.
            int userInfoSeparator = authority.indexOf('@');
            int portSeparator = authority.indexOf(':', userInfoSeparator);

            String encodedHost = portSeparator == NOT_FOUND
                    ? authority.substring(userInfoSeparator + 1)
                    : authority.substring(userInfoSeparator + 1, portSeparator);

            return decode(encodedHost);
        }

        private volatile int port = NOT_CALCULATED;

        public int getPort() {
            return port == NOT_CALCULATED
                    ? port = parsePort()
                    : port;
        }

        private int parsePort() {
            String authority = getEncodedAuthority();
            if (authority == null) {
                return -1;
            }

            // Make sure we look for the port separtor *after* the user info
            // separator. We have URLs with a ':' in the user info.
            int userInfoSeparator = authority.indexOf('@');
            int portSeparator = authority.indexOf(':', userInfoSeparator);

            if (portSeparator == NOT_FOUND) {
                return -1;
            }

            String portString = decode(authority.substring(portSeparator + 1));
            try {
                return Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                Log.w(LOG, "Error parsing port string.", e);
                return -1;
            }
        }
    }

    /**
     * Hierarchical Uri.
     */
    private static class HierarchicalUri extends AbstractHierarchicalUri {

        /** Used in parcelling. */
        static final int TYPE_ID = 3;

        private final String scheme; // can be null
        private final Part authority;
        private final PathPart path;
        private final Part query;
        private final Part fragment;

        private HierarchicalUri(String scheme, Part authority, PathPart path,
                Part query, Part fragment) {
            this.scheme = scheme;
            this.authority = Part.nonNull(authority);
            this.path = path == null ? PathPart.NULL : path;
            this.query = Part.nonNull(query);
            this.fragment = Part.nonNull(fragment);
        }

        static Uri readFrom(Parcel parcel) {
            return new HierarchicalUri(
                parcel.readString(),
                Part.readFrom(parcel),
                PathPart.readFrom(parcel),
                Part.readFrom(parcel),
                Part.readFrom(parcel)
            );
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(TYPE_ID);
            parcel.writeString(scheme);
            authority.writeTo(parcel);
            path.writeTo(parcel);
            query.writeTo(parcel);
            fragment.writeTo(parcel);
        }

        public boolean isHierarchical() {
            return true;
        }

        public boolean isRelative() {
            return scheme == null;
        }

        public String getScheme() {
            return scheme;
        }

        private Part ssp;

        private Part getSsp() {
            return ssp == null
                    ? ssp = Part.fromEncoded(makeSchemeSpecificPart()) : ssp;
        }

        public String getEncodedSchemeSpecificPart() {
            return getSsp().getEncoded();
        }

        public String getSchemeSpecificPart() {
            return getSsp().getDecoded();
        }

        /**
         * Creates the encoded scheme-specific part from its sub parts.
         */
        private String makeSchemeSpecificPart() {
            StringBuilder builder = new StringBuilder();
            appendSspTo(builder);
            return builder.toString();
        }

        private void appendSspTo(StringBuilder builder) {
            String encodedAuthority = authority.getEncoded();
            if (encodedAuthority != null) {
                // Even if the authority is "", we still want to append "//".
                builder.append("//").append(encodedAuthority);
            }

            String encodedPath = path.getEncoded();
            if (encodedPath != null) {
                builder.append(encodedPath);
            }

            if (!query.isEmpty()) {
                builder.append('?').append(query.getEncoded());
            }
        }

        public String getAuthority() {
            return this.authority.getDecoded();
        }

        public String getEncodedAuthority() {
            return this.authority.getEncoded();
        }

        public String getEncodedPath() {
            return this.path.getEncoded();
        }

        public String getPath() {
            return this.path.getDecoded();
        }

        public String getQuery() {
            return this.query.getDecoded();
        }

        public String getEncodedQuery() {
            return this.query.getEncoded();
        }

        public String getFragment() {
            return this.fragment.getDecoded();
        }

        public String getEncodedFragment() {
            return this.fragment.getEncoded();
        }

        public List<String> getPathSegments() {
            return this.path.getPathSegments();
        }

        private volatile String uriString = NOT_CACHED;

        @Override
        public String toString() {
            @SuppressWarnings("StringEquality")
            boolean cached = (uriString != NOT_CACHED);
            return cached ? uriString
                    : (uriString = makeUriString());
        }

        private String makeUriString() {
            StringBuilder builder = new StringBuilder();

            if (scheme != null) {
                builder.append(scheme).append(':');
            }

            appendSspTo(builder);

            if (!fragment.isEmpty()) {
                builder.append('#').append(fragment.getEncoded());
            }

            return builder.toString();
        }

        public Builder buildUpon() {
            return new Builder()
                    .scheme(scheme)
                    .authority(authority)
                    .path(path)
                    .query(query)
                    .fragment(fragment);
        }
    }

    /**
     * Helper class for building or manipulating URI references. Not safe for
     * concurrent use.
     *
     * <p>An absolute hierarchical URI reference follows the pattern:
     * {@code &lt;scheme&gt;://&lt;authority&gt;&lt;absolute path&gt;?&lt;query&gt;#&lt;fragment&gt;}
     *
     * <p>Relative URI references (which are always hierarchical) follow one
     * of two patterns: {@code &lt;relative or absolute path&gt;?&lt;query&gt;#&lt;fragment&gt;}
     * or {@code //&lt;authority&gt;&lt;absolute path&gt;?&lt;query&gt;#&lt;fragment&gt;}
     *
     * <p>An opaque URI follows this pattern:
     * {@code &lt;scheme&gt;:&lt;opaque part&gt;#&lt;fragment&gt;}
     */
    public static final class Builder {

        private String scheme;
        private Part opaquePart;
        private Part authority;
        private PathPart path;
        private Part query;
        private Part fragment;

        /**
         * Constructs a new Builder.
         */
        public Builder() {}

        /**
         * Sets the scheme.
         *
         * @param scheme name or {@code null} if this is a relative Uri
         */
        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        Builder opaquePart(Part opaquePart) {
            this.opaquePart = opaquePart;
            return this;
        }

        /**
         * Encodes and sets the given opaque scheme-specific-part.
         *
         * @param opaquePart decoded opaque part
         */
        public Builder opaquePart(String opaquePart) {
            return opaquePart(Part.fromDecoded(opaquePart));
        }

        /**
         * Sets the previously encoded opaque scheme-specific-part.
         *
         * @param opaquePart encoded opaque part
         */
        public Builder encodedOpaquePart(String opaquePart) {
            return opaquePart(Part.fromEncoded(opaquePart));
        }

        Builder authority(Part authority) {
            // This URI will be hierarchical.
            this.opaquePart = null;

            this.authority = authority;
            return this;
        }

        /**
         * Encodes and sets the authority.
         */
        public Builder authority(String authority) {
            return authority(Part.fromDecoded(authority));
        }

        /**
         * Sets the previously encoded authority.
         */
        public Builder encodedAuthority(String authority) {
            return authority(Part.fromEncoded(authority));
        }

        Builder path(PathPart path) {
            // This URI will be hierarchical.
            this.opaquePart = null;

            this.path = path;
            return this;
        }

        /**
         * Sets the path. Leaves '/' characters intact but encodes others as
         * necessary.
         *
         * <p>If the path is not null and doesn't start with a '/', and if
         * you specify a scheme and/or authority, the builder will prepend the
         * given path with a '/'.
         */
        public Builder path(String path) {
            return path(PathPart.fromDecoded(path));
        }

        /**
         * Sets the previously encoded path.
         *
         * <p>If the path is not null and doesn't start with a '/', and if
         * you specify a scheme and/or authority, the builder will prepend the
         * given path with a '/'.
         */
        public Builder encodedPath(String path) {
            return path(PathPart.fromEncoded(path));
        }

        /**
         * Encodes the given segment and appends it to the path.
         */
        public Builder appendPath(String newSegment) {
            return path(PathPart.appendDecodedSegment(path, newSegment));
        }

        /**
         * Appends the given segment to the path.
         */
        public Builder appendEncodedPath(String newSegment) {
            return path(PathPart.appendEncodedSegment(path, newSegment));
        }

        Builder query(Part query) {
            // This URI will be hierarchical.
            this.opaquePart = null;

            this.query = query;
            return this;
        }

        /**
         * Encodes and sets the query.
         */
        public Builder query(String query) {
            return query(Part.fromDecoded(query));
        }

        /**
         * Sets the previously encoded query.
         */
        public Builder encodedQuery(String query) {
            return query(Part.fromEncoded(query));
        }

        Builder fragment(Part fragment) {
            this.fragment = fragment;
            return this;
        }

        /**
         * Encodes and sets the fragment.
         */
        public Builder fragment(String fragment) {
            return fragment(Part.fromDecoded(fragment));
        }

        /**
         * Sets the previously encoded fragment.
         */
        public Builder encodedFragment(String fragment) {
            return fragment(Part.fromEncoded(fragment));
        }

        /**
         * Encodes the key and value and then appends the parameter to the
         * query string.
         *
         * @param key which will be encoded
         * @param value which will be encoded
         */
        public Builder appendQueryParameter(String key, String value) {
            // This URI will be hierarchical.
            this.opaquePart = null;

            String encodedParameter = encode(key, null) + "="
                    + encode(value, null);

            if (query == null) {
                query = Part.fromEncoded(encodedParameter);
                return this;
            }

            String oldQuery = query.getEncoded();
            if (oldQuery == null || oldQuery.length() == 0) {
                query = Part.fromEncoded(encodedParameter);
            } else {
                query = Part.fromEncoded(oldQuery + "&" + encodedParameter);
            }

            return this;
        }

        /**
         * Constructs a Uri with the current attributes.
         *
         * @throws UnsupportedOperationException if the URI is opaque and the
         *  scheme is null
         */
        public Uri build() {
            if (opaquePart != null) {
                if (this.scheme == null) {
                    throw new UnsupportedOperationException(
                            "An opaque URI must have a scheme.");
                }

                return new OpaqueUri(scheme, opaquePart, fragment);
            } else {
                // Hierarchical URIs should not return null for getPath().
                PathPart path = this.path;
                if (path == null || path == PathPart.NULL) {
                    path = PathPart.EMPTY;
                } else {
                    // If we have a scheme and/or authority, the path must
                    // be absolute. Prepend it with a '/' if necessary.
                    if (hasSchemeOrAuthority()) {
                        path = PathPart.makeAbsolute(path);
                    }
                }

                return new HierarchicalUri(
                        scheme, authority, path, query, fragment);
            }
        }

        private boolean hasSchemeOrAuthority() {
            return scheme != null
                    || (authority != null && authority != Part.NULL);

        }

        @Override
        public String toString() {
            return build().toString();
        }
    }

    /**
     * Searches the query string for parameter values with the given key.
     *
     * @param key which will be encoded
     *
     * @throws UnsupportedOperationException if this isn't a hierarchical URI
     * @throws NullPointerException if key is null
     *
     * @return a list of decoded values
     */
    public List<String> getQueryParameters(String key) {
        if (isOpaque()) {
            throw new UnsupportedOperationException(NOT_HIERARCHICAL);
        }

        String query = getEncodedQuery();
        if (query == null) {
            return Collections.emptyList();
        }

        String encodedKey;
        try {
            encodedKey = URLEncoder.encode(key, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }

        // Prepend query with "&" making the first parameter the same as the
        // rest.
        query = "&" + query;

        // Parameter prefix.
        String prefix = "&" + encodedKey + "=";

        ArrayList<String> values = new ArrayList<String>();

        int start = 0;
        int length = query.length();
        while (start < length) {
            start = query.indexOf(prefix, start);

            if (start == -1) {
                // No more values.
                break;
            }

            // Move start to start of value.
            start += prefix.length();

            // Find end of value.
            int end = query.indexOf('&', start);
            if (end == -1) {
                end = query.length();
            }

            String value = query.substring(start, end);
            values.add(decode(value));

            start = end;
        }

        return Collections.unmodifiableList(values);
    }

    /**
     * Searches the query string for the first value with the given key.
     *
     * @param key which will be encoded
     * @throws UnsupportedOperationException if this isn't a hierarchical URI
     * @throws NullPointerException if key is null
     *
     * @return the decoded value or null if no parameter is found
     */
    public String getQueryParameter(String key) {
        if (isOpaque()) {
            throw new UnsupportedOperationException(NOT_HIERARCHICAL);
        }
        if (key == null) {
          throw new NullPointerException("key");
        }

        final String query = getEncodedQuery();
        if (query == null) {
            return null;
        }

        final String encodedKey = encode(key, null);
        final int encodedKeyLength = encodedKey.length();

        int encodedKeySearchIndex = 0;
        final int encodedKeySearchEnd = query.length() - (encodedKeyLength + 1);

        while (encodedKeySearchIndex <= encodedKeySearchEnd) {
            int keyIndex = query.indexOf(encodedKey, encodedKeySearchIndex);
            if (keyIndex == -1) {
                break;
            }
            final int equalsIndex = keyIndex + encodedKeyLength;
            if (equalsIndex >= query.length()) {
                break;
            }
            if (query.charAt(equalsIndex) != '=') {
                encodedKeySearchIndex = equalsIndex + 1;
                continue;
            }
            if (keyIndex == 0 || query.charAt(keyIndex - 1) == '&') {
                int end = query.indexOf('&', equalsIndex);
                if (end == -1) {
                    end = query.length();
                }
                return decode(query.substring(equalsIndex + 1, end));
            } else {
                encodedKeySearchIndex = equalsIndex + 1;
            }
        }
        return null;
    }

    /** Identifies a null parcelled Uri. */
    private static final int NULL_TYPE_ID = 0;

    /**
     * Reads Uris from Parcels.
     */
    public static final Parcelable.Creator<Uri> CREATOR
            = new Parcelable.Creator<Uri>() {
        public Uri createFromParcel(Parcel in) {
            int type = in.readInt();
            switch (type) {
                case NULL_TYPE_ID: return null;
                case StringUri.TYPE_ID: return StringUri.readFrom(in);
                case OpaqueUri.TYPE_ID: return OpaqueUri.readFrom(in);
                case HierarchicalUri.TYPE_ID:
                    return HierarchicalUri.readFrom(in);
            }

            throw new AssertionError("Unknown URI type: " + type);
        }

        public Uri[] newArray(int size) {
            return new Uri[size];
        }
    };

    /**
     * Writes a Uri to a Parcel.
     *
     * @param out parcel to write to
     * @param uri to write, can be null
     */
    public static void writeToParcel(Parcel out, Uri uri) {
        if (uri == null) {
            out.writeInt(NULL_TYPE_ID);
        } else {
            uri.writeToParcel(out, 0);
        }
    }

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /**
     * Encodes characters in the given string as '%'-escaped octets
     * using the UTF-8 scheme. Leaves letters ("A-Z", "a-z"), numbers
     * ("0-9"), and unreserved characters ("_-!.~'()*") intact. Encodes
     * all other characters.
     *
     * @param s string to encode
     * @return an encoded version of s suitable for use as a URI component,
     *  or null if s is null
     */
    public static String encode(String s) {
        return encode(s, null);
    }

    /**
     * Encodes characters in the given string as '%'-escaped octets
     * using the UTF-8 scheme. Leaves letters ("A-Z", "a-z"), numbers
     * ("0-9"), and unreserved characters ("_-!.~'()*") intact. Encodes
     * all other characters with the exception of those specified in the
     * allow argument.
     *
     * @param s string to encode
     * @param allow set of additional characters to allow in the encoded form,
     *  null if no characters should be skipped
     * @return an encoded version of s suitable for use as a URI component,
     *  or null if s is null
     */
    public static String encode(String s, String allow) {
        if (s == null) {
            return null;
        }

        // Lazily-initialized buffers.
        StringBuilder encoded = null;

        int oldLength = s.length();

        // This loop alternates between copying over allowed characters and
        // encoding in chunks. This results in fewer method calls and
        // allocations than encoding one character at a time.
        int current = 0;
        while (current < oldLength) {
            // Start in "copying" mode where we copy over allowed chars.

            // Find the next character which needs to be encoded.
            int nextToEncode = current;
            while (nextToEncode < oldLength
                    && isAllowed(s.charAt(nextToEncode), allow)) {
                nextToEncode++;
            }

            // If there's nothing more to encode...
            if (nextToEncode == oldLength) {
                if (current == 0) {
                    // We didn't need to encode anything!
                    return s;
                } else {
                    // Presumably, we've already done some encoding.
                    encoded.append(s, current, oldLength);
                    return encoded.toString();
                }
            }

            if (encoded == null) {
                encoded = new StringBuilder();
            }

            if (nextToEncode > current) {
                // Append allowed characters leading up to this point.
                encoded.append(s, current, nextToEncode);
            } else {
                // assert nextToEncode == current
            }

            // Switch to "encoding" mode.

            // Find the next allowed character.
            current = nextToEncode;
            int nextAllowed = current + 1;
            while (nextAllowed < oldLength
                    && !isAllowed(s.charAt(nextAllowed), allow)) {
                nextAllowed++;
            }

            // Convert the substring to bytes and encode the bytes as
            // '%'-escaped octets.
            String toEncode = s.substring(current, nextAllowed);
            try {
                byte[] bytes = toEncode.getBytes(DEFAULT_ENCODING);
                int bytesLength = bytes.length;
                for (int i = 0; i < bytesLength; i++) {
                    encoded.append('%');
                    encoded.append(HEX_DIGITS[(bytes[i] & 0xf0) >> 4]);
                    encoded.append(HEX_DIGITS[bytes[i] & 0xf]);
                }
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);
            }

            current = nextAllowed;
        }

        // Encoded could still be null at this point if s is empty.
        return encoded == null ? s : encoded.toString();
    }

    /**
     * Returns true if the given character is allowed.
     *
     * @param c character to check
     * @param allow characters to allow
     * @return true if the character is allowed or false if it should be
     *  encoded
     */
    private static boolean isAllowed(char c, String allow) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || "_-!.~'()*".indexOf(c) != NOT_FOUND
                || (allow != null && allow.indexOf(c) != NOT_FOUND);
    }

    /** Unicode replacement character: \\uFFFD. */
    private static final byte[] REPLACEMENT = { (byte) 0xFF, (byte) 0xFD };

    /**
     * Decodes '%'-escaped octets in the given string using the UTF-8 scheme.
     * Replaces invalid octets with the unicode replacement character
     * ("\\uFFFD").
     *
     * @param s encoded string to decode
     * @return the given string with escaped octets decoded, or null if
     *  s is null
     */
    public static String decode(String s) {
        /*
        Compared to java.net.URLEncoderDecoder.decode(), this method decodes a
        chunk at a time instead of one character at a time, and it doesn't
        throw exceptions. It also only allocates memory when necessary--if
        there's nothing to decode, this method won't do much.
        */

        if (s == null) {
            return null;
        }

        // Lazily-initialized buffers.
        StringBuilder decoded = null;
        ByteArrayOutputStream out = null;

        int oldLength = s.length();

        // This loop alternates between copying over normal characters and
        // escaping in chunks. This results in fewer method calls and
        // allocations than decoding one character at a time.
        int current = 0;
        while (current < oldLength) {
            // Start in "copying" mode where we copy over normal characters.

            // Find the next escape sequence.
            int nextEscape = s.indexOf('%', current);

            if (nextEscape == NOT_FOUND) {
                if (decoded == null) {
                    // We didn't actually decode anything.
                    return s;
                } else {
                    // Append the remainder and return the decoded string.
                    decoded.append(s, current, oldLength);
                    return decoded.toString();
                }
            }

            // Prepare buffers.
            if (decoded == null) {
                // Looks like we're going to need the buffers...
                // We know the new string will be shorter. Using the old length
                // may overshoot a bit, but it will save us from resizing the
                // buffer.
                decoded = new StringBuilder(oldLength);
                out = new ByteArrayOutputStream(4);
            } else {
                // Clear decoding buffer.
                out.reset();
            }

            // Append characters leading up to the escape.
            if (nextEscape > current) {
                decoded.append(s, current, nextEscape);

                current = nextEscape;
            } else {
                // assert current == nextEscape
            }

            // Switch to "decoding" mode where we decode a string of escape
            // sequences.

            // Decode and append escape sequences. Escape sequences look like
            // "%ab" where % is literal and a and b are hex digits.
            try {
                do {
                    if (current + 2 >= oldLength) {
                        // Truncated escape sequence.
                        out.write(REPLACEMENT);
                    } else {
                        int a = Character.digit(s.charAt(current + 1), 16);
                        int b = Character.digit(s.charAt(current + 2), 16);

                        if (a == -1 || b == -1) {
                            // Non hex digits.
                            out.write(REPLACEMENT);
                        } else {
                            // Combine the hex digits into one byte and write.
                            out.write((a << 4) + b);
                        }
                    }

                    // Move passed the escape sequence.
                    current += 3;
                } while (current < oldLength && s.charAt(current) == '%');

                // Decode UTF-8 bytes into a string and append it.
                decoded.append(out.toString(DEFAULT_ENCODING));
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        // If we don't have a buffer, we didn't have to decode anything.
        return decoded == null ? s : decoded.toString();
    }

    /**
     * Support for part implementations.
     */
    static abstract class AbstractPart {

        /**
         * Enum which indicates which representation of a given part we have.
         */
        static class Representation {
            static final int BOTH = 0;
            static final int ENCODED = 1;
            static final int DECODED = 2;
        }

        volatile String encoded;
        volatile String decoded;

        AbstractPart(String encoded, String decoded) {
            this.encoded = encoded;
            this.decoded = decoded;
        }

        abstract String getEncoded();

        final String getDecoded() {
            @SuppressWarnings("StringEquality")
            boolean hasDecoded = decoded != NOT_CACHED;
            return hasDecoded ? decoded : (decoded = decode(encoded));
        }

        final void writeTo(Parcel parcel) {
            @SuppressWarnings("StringEquality")
            boolean hasEncoded = encoded != NOT_CACHED;

            @SuppressWarnings("StringEquality")
            boolean hasDecoded = decoded != NOT_CACHED;

            if (hasEncoded && hasDecoded) {
                parcel.writeInt(Representation.BOTH);
                parcel.writeString(encoded);
                parcel.writeString(decoded);
            } else if (hasEncoded) {
                parcel.writeInt(Representation.ENCODED);
                parcel.writeString(encoded);
            } else if (hasDecoded) {
                parcel.writeInt(Representation.DECODED);
                parcel.writeString(decoded);
            } else {
                throw new AssertionError();
            }
        }
    }

    /**
     * Immutable wrapper of encoded and decoded versions of a URI part. Lazily
     * creates the encoded or decoded version from the other.
     */
    static class Part extends AbstractPart {

        /** A part with null values. */
        static final Part NULL = new EmptyPart(null);

        /** A part with empty strings for values. */
        static final Part EMPTY = new EmptyPart("");

        private Part(String encoded, String decoded) {
            super(encoded, decoded);
        }

        boolean isEmpty() {
            return false;
        }

        String getEncoded() {
            @SuppressWarnings("StringEquality")
            boolean hasEncoded = encoded != NOT_CACHED;
            return hasEncoded ? encoded : (encoded = encode(decoded));
        }

        static Part readFrom(Parcel parcel) {
            int representation = parcel.readInt();
            switch (representation) {
                case Representation.BOTH:
                    return from(parcel.readString(), parcel.readString());
                case Representation.ENCODED:
                    return fromEncoded(parcel.readString());
                case Representation.DECODED:
                    return fromDecoded(parcel.readString());
                default:
                    throw new AssertionError();
            }
        }

        /**
         * Returns given part or {@link #NULL} if the given part is null.
         */
        static Part nonNull(Part part) {
            return part == null ? NULL : part;
        }

        /**
         * Creates a part from the encoded string.
         *
         * @param encoded part string
         */
        static Part fromEncoded(String encoded) {
            return from(encoded, NOT_CACHED);
        }

        /**
         * Creates a part from the decoded string.
         *
         * @param decoded part string
         */
        static Part fromDecoded(String decoded) {
            return from(NOT_CACHED, decoded);
        }

        /**
         * Creates a part from the encoded and decoded strings.
         *
         * @param encoded part string
         * @param decoded part string
         */
        static Part from(String encoded, String decoded) {
            // We have to check both encoded and decoded in case one is
            // NOT_CACHED.

            if (encoded == null) {
                return NULL;
            }
            if (encoded.length() == 0) {
                return EMPTY;
            }

            if (decoded == null) {
                return NULL;
            }
            if (decoded .length() == 0) {
                return EMPTY;
            }

            return new Part(encoded, decoded);
        }

        private static class EmptyPart extends Part {
            public EmptyPart(String value) {
                super(value, value);
            }

            @Override
            boolean isEmpty() {
                return true;
            }
        }
    }

    /**
     * Immutable wrapper of encoded and decoded versions of a path part. Lazily
     * creates the encoded or decoded version from the other.
     */
    static class PathPart extends AbstractPart {

        /** A part with null values. */
        static final PathPart NULL = new PathPart(null, null);

        /** A part with empty strings for values. */
        static final PathPart EMPTY = new PathPart("", "");

        private PathPart(String encoded, String decoded) {
            super(encoded, decoded);
        }

        String getEncoded() {
            @SuppressWarnings("StringEquality")
            boolean hasEncoded = encoded != NOT_CACHED;

            // Don't encode '/'.
            return hasEncoded ? encoded : (encoded = encode(decoded, "/"));
        }

        /**
         * Cached path segments. This doesn't need to be volatile--we don't
         * care if other threads see the result.
         */
        private PathSegments pathSegments;

        /**
         * Gets the individual path segments. Parses them if necessary.
         *
         * @return parsed path segments or null if this isn't a hierarchical
         *  URI
         */
        PathSegments getPathSegments() {
            if (pathSegments != null) {
                return pathSegments;
            }

            String path = getEncoded();
            if (path == null) {
                return pathSegments = PathSegments.EMPTY;
            }

            PathSegmentsBuilder segmentBuilder = new PathSegmentsBuilder();

            int previous = 0;
            int current;
            while ((current = path.indexOf('/', previous)) > -1) {
                // This check keeps us from adding a segment if the path starts
                // '/' and an empty segment for "//".
                if (previous < current) {
                    String decodedSegment
                            = decode(path.substring(previous, current));
                    segmentBuilder.add(decodedSegment);
                }
                previous = current + 1;
            }

            // Add in the final path segment.
            if (previous < path.length()) {
                segmentBuilder.add(decode(path.substring(previous)));
            }

            return pathSegments = segmentBuilder.build();
        }

        static PathPart appendEncodedSegment(PathPart oldPart,
                String newSegment) {
            // If there is no old path, should we make the new path relative
            // or absolute? I pick absolute.

            if (oldPart == null) {
                // No old path.
                return fromEncoded("/" + newSegment);
            }

            String oldPath = oldPart.getEncoded();

            if (oldPath == null) {
                oldPath = "";
            }

            int oldPathLength = oldPath.length();
            String newPath;
            if (oldPathLength == 0) {
                // No old path.
                newPath = "/" + newSegment;
            } else if (oldPath.charAt(oldPathLength - 1) == '/') {
                newPath = oldPath + newSegment;
            } else {
                newPath = oldPath + "/" + newSegment;
            }

            return fromEncoded(newPath);
        }

        static PathPart appendDecodedSegment(PathPart oldPart, String decoded) {
            String encoded = encode(decoded);

            // TODO: Should we reuse old PathSegments? Probably not.
            return appendEncodedSegment(oldPart, encoded);
        }

        static PathPart readFrom(Parcel parcel) {
            int representation = parcel.readInt();
            switch (representation) {
                case Representation.BOTH:
                    return from(parcel.readString(), parcel.readString());
                case Representation.ENCODED:
                    return fromEncoded(parcel.readString());
                case Representation.DECODED:
                    return fromDecoded(parcel.readString());
                default:
                    throw new AssertionError();
            }
        }

        /**
         * Creates a path from the encoded string.
         *
         * @param encoded part string
         */
        static PathPart fromEncoded(String encoded) {
            return from(encoded, NOT_CACHED);
        }

        /**
         * Creates a path from the decoded string.
         *
         * @param decoded part string
         */
        static PathPart fromDecoded(String decoded) {
            return from(NOT_CACHED, decoded);
        }

        /**
         * Creates a path from the encoded and decoded strings.
         *
         * @param encoded part string
         * @param decoded part string
         */
        static PathPart from(String encoded, String decoded) {
            if (encoded == null) {
                return NULL;
            }

            if (encoded.length() == 0) {
                return EMPTY;
            }

            return new PathPart(encoded, decoded);
        }

        /**
         * Prepends path values with "/" if they're present, not empty, and
         * they don't already start with "/".
         */
        static PathPart makeAbsolute(PathPart oldPart) {
            @SuppressWarnings("StringEquality")
            boolean encodedCached = oldPart.encoded != NOT_CACHED;

            // We don't care which version we use, and we don't want to force
            // unneccessary encoding/decoding.
            String oldPath = encodedCached ? oldPart.encoded : oldPart.decoded;

            if (oldPath == null || oldPath.length() == 0
                    || oldPath.startsWith("/")) {
                return oldPart;
            }

            // Prepend encoded string if present.
            String newEncoded = encodedCached
                    ? "/" + oldPart.encoded : NOT_CACHED;

            // Prepend decoded string if present.
            @SuppressWarnings("StringEquality")
            boolean decodedCached = oldPart.decoded != NOT_CACHED;
            String newDecoded = decodedCached
                    ? "/" + oldPart.decoded
                    : NOT_CACHED;

            return new PathPart(newEncoded, newDecoded);
        }
    }

    /**
     * Creates a new Uri by appending an already-encoded path segment to a
     * base Uri.
     *
     * @param baseUri Uri to append path segment to
     * @param pathSegment encoded path segment to append
     * @return a new Uri based on baseUri with the given segment appended to
     *  the path
     * @throws NullPointerException if baseUri is null
     */
    public static Uri withAppendedPath(Uri baseUri, String pathSegment) {
        Builder builder = baseUri.buildUpon();
        builder = builder.appendEncodedPath(pathSegment);
        return builder.build();
    }
}
