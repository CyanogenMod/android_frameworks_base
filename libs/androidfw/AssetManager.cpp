/*
 * Copyright (C) 2006 The Android Open Source Project
 * This code has been modified.  Portions copyright (C) 2010, T-Mobile USA, Inc.
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

//
// Provide access to read-only assets.
//

#define LOG_TAG "asset"
#define ATRACE_TAG ATRACE_TAG_RESOURCES
//#define LOG_NDEBUG 0

#include <androidfw/Asset.h>
#include <androidfw/AssetDir.h>
#include <androidfw/AssetManager.h>
#include <androidfw/misc.h>
#include <androidfw/ResourceTypes.h>
#include <androidfw/ZipFileRO.h>
#include <utils/Atomic.h>
#include <utils/Log.h>
#include <utils/String8.h>
#include <utils/String8.h>
#include <utils/threads.h>
#include <utils/Timers.h>
#ifdef HAVE_ANDROID_OS
#include <cutils/trace.h>
#endif

#include <assert.h>
#include <dirent.h>
#include <errno.h>
#include <string.h> // strerror
#include <strings.h>

#ifndef TEMP_FAILURE_RETRY
/* Used to retry syscalls that can return EINTR. */
#define TEMP_FAILURE_RETRY(exp) ({         \
    typeof (exp) _rc;                      \
    do {                                   \
        _rc = (exp);                       \
    } while (_rc == -1 && errno == EINTR); \
    _rc; })
#endif

#ifdef HAVE_ANDROID_OS
#define MY_TRACE_BEGIN(x) ATRACE_BEGIN(x)
#define MY_TRACE_END() ATRACE_END()
#else
#define MY_TRACE_BEGIN(x)
#define MY_TRACE_END()
#endif

using namespace android;

static const bool kIsDebug = false;

/*
 * Names for default app, locale, and vendor.  We might want to change
 * these to be an actual locale, e.g. always use en-US as the default.
 */
static const char* kDefaultLocale = "default";
static const char* kDefaultVendor = "default";
static const char* kAssetsRoot = "assets";
static const char* kAppZipName = NULL; //"classes.jar";
static const char* kSystemAssets = "framework/framework-res.apk";
static const char* kCMSDKAssets = "framework/org.cyanogenmod.platform-res.apk";
static const char* kAndroidManifest = "AndroidManifest.xml";
static const int   kComposedIconAsset = 128;

#ifdef HAVE_ANDROID_OS
static const char* kResourceCache = "resource-cache";
#endif

static const char* kExcludeExtension = ".EXCLUDE";

static Asset* const kExcludedAsset = (Asset*) 0xd000000d;

static volatile int32_t gCount = 0;

const char* AssetManager::RESOURCES_FILENAME = "resources.arsc";
const char* AssetManager::IDMAP_BIN = "/system/bin/idmap";
const char* AssetManager::OVERLAY_DIR = "/vendor/overlay";
const char* AssetManager::TARGET_PACKAGE_NAME = "android";
const char* AssetManager::TARGET_APK_PATH = "/system/framework/framework-res.apk";
const char* AssetManager::IDMAP_DIR = "/data/resource-cache";
const char* AssetManager::APK_EXTENSION = ".apk";

namespace {
    /*
     * Like strdup(), but uses C++ "new" operator instead of malloc.
     */
    static char* strdupNew(const char* str)
    {
        char* newStr;
        int len;

        if (str == NULL)
            return NULL;

        len = strlen(str);
        newStr = new char[len+1];
        memcpy(newStr, str, len+1);

        return newStr;
    }
}

/*
 * ===========================================================================
 *      AssetManager
 * ===========================================================================
 */

int32_t AssetManager::getGlobalCount()
{
    return gCount;
}

AssetManager::AssetManager(CacheMode cacheMode)
    : mLocale(NULL), mVendor(NULL),
      mResources(NULL), mConfig(new ResTable_config),
      mBasePackageIndex(-1), mCacheMode(cacheMode),
      mCacheValid(false)
{
    int count = android_atomic_inc(&gCount) + 1;
    if (kIsDebug) {
        ALOGI("Creating AssetManager %p #%d\n", this, count);
    }
    memset(mConfig, 0, sizeof(ResTable_config));
}

AssetManager::~AssetManager(void)
{
    int count = android_atomic_dec(&gCount);
    if (kIsDebug) {
        ALOGI("Destroying AssetManager in %p #%d\n", this, count);
    }

    delete mConfig;
    delete mResources;

    // don't have a String class yet, so make sure we clean up
    delete[] mLocale;
    delete[] mVendor;
}

bool AssetManager::addAssetPath(const String8& path, int32_t* cookie)
{
    AutoMutex _l(mLock);

    asset_path ap;

    String8 realPath(path);
    if (kAppZipName) {
        realPath.appendPath(kAppZipName);
    }
    ap.type = ::getFileType(realPath.string());
    if (ap.type == kFileTypeRegular) {
        ap.path = realPath;
    } else {
        ap.path = path;
        ap.type = ::getFileType(path.string());
        if (ap.type != kFileTypeDirectory && ap.type != kFileTypeRegular) {
            ALOGW("Asset path %s is neither a directory nor file (type=%d).",
                 path.string(), (int)ap.type);
            return false;
        }
    }

    // Skip if we have it already.
    for (size_t i=0; i<mAssetPaths.size(); i++) {
        if (mAssetPaths[i].path == ap.path) {
            if (cookie) {
                *cookie = static_cast<int32_t>(i+1);
            }
            return true;
        }
    }

    ALOGV("In %p Asset %s path: %s", this,
         ap.type == kFileTypeDirectory ? "dir" : "zip", ap.path.string());

    // Check that the path has an AndroidManifest.xml
    Asset* manifestAsset = const_cast<AssetManager*>(this)->openNonAssetInPathLocked(
            kAndroidManifest, Asset::ACCESS_BUFFER, ap);
    if (manifestAsset == NULL) {
        // This asset path does not contain any resources.
        delete manifestAsset;
        return false;
    }
    delete manifestAsset;

    mAssetPaths.add(ap);

    if (mResources != NULL) {
        size_t index = mAssetPaths.size() - 1;
        appendPathToResTable(ap, &index);
    }

    // new paths are always added at the end
    if (cookie) {
        *cookie = static_cast<int32_t>(mAssetPaths.size());
    }

#ifdef HAVE_ANDROID_OS
    // Load overlays, if any
    asset_path oap;
    for (size_t idx = 0; mZipSet.getOverlay(ap.path, idx, &oap); idx++) {
        mAssetPaths.add(oap);
    }
#endif

    return true;
}

/**
 * packagePath: Path to the APK that contains our overlay
 * cookie: Set by this method. The caller can use this cookie to refer to the asset path that has
 *         been added.
 * resApkPath: Path to the overlay's processed and cached resources.
 * targetPkgPath: Path to the APK we are trying to overlay
 * prefixPath: This is the base path internal to the overlay APK.
 *             For example, if we have theme "com.redtheme.apk"
 * with a launcher overlay then this theme will have a structure like this:
 *  assets/
 *    com.android.launcher/
 *        res/
 *          drawable/
 *              foo.png
 * Our resources.arsc will reference foo.png's path as "res/drawable/foo.png"
 * so we need "assets/com.android.launcher/" as a prefix
 */
bool AssetManager::addOverlayPath(const String8& idmapPath, const String8& overlayPackagePath,
        int32_t* cookie, const String8& resApkPath, const String8& targetPkgPath,
        const String8& prefixPath)
{
    AutoMutex _l(mLock);

    ALOGV("overlayApkPath: %s, idmap Path: %s, resApkPath %s, targetPkgPath: %s",
           overlayPackagePath.string(), idmapPath.string(),
           resApkPath.string(),
           targetPkgPath.string());

    for (size_t i = 0; i < mAssetPaths.size(); ++i) {
        if (mAssetPaths[i].idmap == idmapPath) {
           *cookie = static_cast<int32_t>(i + 1);
            return true;
         }
     }

    Asset* idmap = NULL;
    if ((idmap = openAssetFromFileLocked(idmapPath, Asset::ACCESS_BUFFER)) == NULL) {
        ALOGW("failed to open idmap file %s\n", idmapPath.string());
        return false;
    }

    String8 targetPath;
    String8 overlayPath;
    if (!ResTable::getIdmapInfo(idmap->getBuffer(false), idmap->getLength(),
                NULL, NULL, NULL, &targetPath, &overlayPath)) {
        ALOGW("failed to read idmap file %s\n", idmapPath.string());
        delete idmap;
        return false;
    }
    delete idmap;

    if (overlayPath != overlayPackagePath) {
        ALOGW("idmap file %s inconcistent: expected path %s does not match actual path %s\n",
                idmapPath.string(), overlayPackagePath.string(), overlayPath.string());
        return false;
    }
    if (access(targetPath.string(), R_OK) != 0) {
        ALOGW("failed to access file %s: %s\n", targetPath.string(), strerror(errno));
        return false;
    }
    if (access(idmapPath.string(), R_OK) != 0) {
        ALOGW("failed to access file %s: %s\n", idmapPath.string(), strerror(errno));
        return false;
    }
    if (access(overlayPath.string(), R_OK) != 0) {
        ALOGW("failed to access file %s: %s\n", overlayPath.string(), strerror(errno));
        return false;
    }

    asset_path oap;
    oap.path = overlayPath;
    oap.type = ::getFileType(overlayPath.string());
    oap.idmap = idmapPath;
    oap.resApkPath = resApkPath;
#if 0
    ALOGD("Overlay added: targetPath=%s overlayPath=%s idmapPath=%s\n",
            targetPath.string(), overlayPath.string(), idmapPath.string());
#endif
    oap.prefixPath = prefixPath; //ex: assets/com.foo.bar
    mAssetPaths.add(oap);
    *cookie = static_cast<int32_t>(mAssetPaths.size());

    if (mResources != NULL) {
        size_t index = mAssetPaths.size() - 1;
        appendPathToResTable(oap, &index);
    }

    return true;
 }

bool AssetManager::addCommonOverlayPath(const String8& themePackagePath, int32_t* cookie,
        const String8& resApkPath, const String8& prefixPath)
{
    AutoMutex _l(mLock);

    ALOGV("themePackagePath: %s, resApkPath %s, prefixPath %s",
            themePackagePath.string(), resApkPath.string(), prefixPath.string());

    // Skip if we have it already.
    for (size_t i = 0; i < mAssetPaths.size(); ++i) {
        if (mAssetPaths[i].path == themePackagePath && mAssetPaths[i].resApkPath == resApkPath) {
            *cookie = static_cast<int32_t>(i + 1);
            return true;
        }
    }

    if (access(themePackagePath.string(), R_OK) != 0) {
        ALOGW("failed to access file %s: %s\n", themePackagePath.string(), strerror(errno));
        return false;
    }

    if (access(resApkPath.string(), R_OK) != 0) {
        ALOGW("failed to access file %s: %s\n", resApkPath.string(), strerror(errno));
        return false;
    }

    asset_path oap;
    oap.path = themePackagePath;
    oap.type = ::getFileType(themePackagePath.string());
    oap.resApkPath = resApkPath;
    oap.prefixPath = prefixPath;
    mAssetPaths.add(oap);
    *cookie = static_cast<int32_t>(mAssetPaths.size());

    if (mResources != NULL) {
        size_t index = mAssetPaths.size() - 1;
        appendPathToResTable(oap, &index);
    }

    return true;
}

/*
 * packagePath: Path to the APK that contains our icon assets
 * cookie: Set by this method. The caller can use this cookie to refer to the added asset path.
 * resApkPath: Path to the icon APK's processed and cached resources.
 * prefixPath: This is the base path internal to the icon APK.
               For example, if we have theme "com.redtheme.apk"
 *  assets/
 *    icons/
 *        res/
 *          drawable/
 *              foo.png
 * Our restable will reference foo.png's path as "res/drawable/foo.png"
 * so we need "assets/com.android.launcher/" as a prefix
 * pkgIdOverride: The package id we want to give. This will overridet the id in the res table.
 *                This is necessary for legacy icon packs because they are compiled with the
 *                standard 7F package id.
*/
bool AssetManager::addIconPath(const String8& packagePath, int32_t* cookie,
        const String8& resApkPath, const String8& prefixPath,
        uint32_t pkgIdOverride)
{
    AutoMutex _l(mLock);

    ALOGV("package path: %s, resApkPath %s, prefixPath %s",
            packagePath.string(),
            resApkPath.string(), prefixPath.string());

    // Skip if we have it already.
    for (size_t i = 0; i < mAssetPaths.size(); i++) {
        if (mAssetPaths[i].path == packagePath && mAssetPaths[i].resApkPath == resApkPath) {
            *cookie = static_cast<int32_t>(i + 1);
            return true;
        }
    }

    asset_path oap;
    oap.path = packagePath;
    oap.type = ::getFileType(packagePath.string());
    oap.resApkPath = resApkPath;
    oap.prefixPath = prefixPath;
    oap.pkgIdOverride = pkgIdOverride;
    mAssetPaths.add(oap);
    *cookie = static_cast<int32_t>(mAssetPaths.size());

    if (mResources != NULL) {
        size_t index = mAssetPaths.size() - 1;
        appendPathToResTable(oap, &index);
    }

   return true;
}

String8 AssetManager::getPkgName(const char *apkPath) {
        String8 pkgName;

        asset_path ap;
        ap.type = kFileTypeRegular;
        ap.path = String8(apkPath);

        ResXMLTree tree;

        Asset* manifestAsset = openNonAssetInPathLocked(kAndroidManifest, Asset::ACCESS_BUFFER, ap);
        tree.setTo(manifestAsset->getBuffer(true),
                       manifestAsset->getLength());
        tree.restart();

        size_t len;
        ResXMLTree::event_code_t code;
        while ((code=tree.next()) != ResXMLTree::END_DOCUMENT && code != ResXMLTree::BAD_DOCUMENT) {
            if (code != ResXMLTree::START_TAG) {
                    continue;
            }
            String8 tag(tree.getElementName(&len));
            if (tag != "manifest") break; //Manifest does not start with <manifest>
            size_t len;
            ssize_t idx = tree.indexOfAttribute(NULL, "package");
            const char16_t* str = tree.getAttributeStringValue(idx, &len);
            pkgName = (str ? String8(str, len) : String8());

        }

        manifestAsset->close();
        return pkgName;
    }

/**
 * Returns the base package name as defined in the AndroidManifest.xml
 */
String8 AssetManager::getBasePackageName(uint32_t index)
{
    if (index >= mAssetPaths.size()) return String8::empty();

    if (mBasePackageName.isEmpty() || mBasePackageIndex != index) {
        mBasePackageName = getPkgName(mAssetPaths[index].path.string());
        mBasePackageIndex = index;
    }
    return mBasePackageName;
}

String8 AssetManager::getOverlayResPath(const char* cachePath)
{
    String8 resPath(cachePath);
    resPath.append("/");
    resPath.append("resources");
    resPath.append(AssetManager::APK_EXTENSION);
    return resPath;
}

bool AssetManager::createIdmap(const char* targetApkPath, const char* overlayApkPath,
        const char *cache_path, uint32_t targetCrc, uint32_t overlayCrc,
        time_t targetMtime, time_t overlayMtime,
        uint32_t** outData, size_t* outSize)
{
    AutoMutex _l(mLock);
    const String8 paths[2] = { String8(targetApkPath), String8(overlayApkPath) };
    ResTable tables[2];

    //Our overlay APK might use an external restable
    String8 resPath = getOverlayResPath(cache_path);

    for (int i = 0; i < 2; ++i) {
        asset_path ap;
        ap.type = kFileTypeRegular;
        ap.path = paths[i];
        Asset* ass;
        if (i == 1 && access(resPath.string(), R_OK) != -1) {
            ap.path = resPath;
            ass = openNonAssetInPathLocked("resources.arsc", Asset::ACCESS_BUFFER, ap);
        } else {
            ass = openNonAssetInPathLocked("resources.arsc", Asset::ACCESS_BUFFER, ap);
        }
        if (ass == NULL) {
            ALOGW("failed to find resources.arsc in %s\n", ap.path.string());
            return false;
        }
        tables[i].add(ass);
    }

    return tables[0].createIdmap(tables[1], targetCrc, overlayCrc, targetMtime, overlayMtime,
            targetApkPath, overlayApkPath, (void**)outData, outSize) == NO_ERROR;
}

bool AssetManager::addDefaultAssets()
{
    const char* root = getenv("ANDROID_ROOT");
    LOG_ALWAYS_FATAL_IF(root == NULL, "ANDROID_ROOT not set");

    String8 path(root);
    path.appendPath(kSystemAssets);

    String8 pathCM(root);
    pathCM.appendPath(kCMSDKAssets);

    return addAssetPath(path, NULL) & addAssetPath(pathCM, NULL);
}

int32_t AssetManager::nextAssetPath(const int32_t cookie) const
{
    AutoMutex _l(mLock);
    const size_t next = static_cast<size_t>(cookie) + 1;
    return next > mAssetPaths.size() ? -1 : next;
}

String8 AssetManager::getAssetPath(const int32_t cookie) const
{
    AutoMutex _l(mLock);
    const size_t which = static_cast<size_t>(cookie) - 1;
    if (which < mAssetPaths.size()) {
        return mAssetPaths[which].path;
    }
    return String8();
}

/*
 * Set the current locale.  Use NULL to indicate no locale.
 *
 * Close and reopen Zip archives as appropriate, and reset cached
 * information in the locale-specific sections of the tree.
 */
void AssetManager::setLocale(const char* locale)
{
    AutoMutex _l(mLock);
    setLocaleLocked(locale);
}


static const char kFilPrefix[] = "fil";
static const char kTlPrefix[] = "tl";

// The sizes of the prefixes, excluding the 0 suffix.
// char.
static const int kFilPrefixLen = sizeof(kFilPrefix) - 1;
static const int kTlPrefixLen = sizeof(kTlPrefix) - 1;

void AssetManager::setLocaleLocked(const char* locale)
{
    if (mLocale != NULL) {
        /* previously set, purge cached data */
        purgeFileNameCacheLocked();
        //mZipSet.purgeLocale();
        delete[] mLocale;
    }

    // If we're attempting to set a locale that starts with "fil",
    // we should convert it to "tl" for backwards compatibility since
    // we've been using "tl" instead of "fil" prior to L.
    //
    // If the resource table already has entries for "fil", we use that
    // instead of attempting a fallback.
    if (strncmp(locale, kFilPrefix, kFilPrefixLen) == 0) {
        Vector<String8> locales;
        ResTable* res = mResources;
        if (res != NULL) {
            res->getLocales(&locales);
        }
        const size_t localesSize = locales.size();
        bool hasFil = false;
        for (size_t i = 0; i < localesSize; ++i) {
            if (locales[i].find(kFilPrefix) == 0) {
                hasFil = true;
                break;
            }
        }


        if (!hasFil) {
            const size_t newLocaleLen = strlen(locale);
            // This isn't a bug. We really do want mLocale to be 1 byte
            // shorter than locale, because we're replacing "fil-" with
            // "tl-".
            mLocale = new char[newLocaleLen];
            // Copy over "tl".
            memcpy(mLocale, kTlPrefix, kTlPrefixLen);
            // Copy the rest of |locale|, including the terminating '\0'.
            memcpy(mLocale + kTlPrefixLen, locale + kFilPrefixLen,
                   newLocaleLen - kFilPrefixLen + 1);
            updateResourceParamsLocked();
            return;
        }
    }

    mLocale = strdupNew(locale);
    updateResourceParamsLocked();
}

/*
 * Set the current vendor.  Use NULL to indicate no vendor.
 *
 * Close and reopen Zip archives as appropriate, and reset cached
 * information in the vendor-specific sections of the tree.
 */
void AssetManager::setVendor(const char* vendor)
{
    AutoMutex _l(mLock);

    if (mVendor != NULL) {
        /* previously set, purge cached data */
        purgeFileNameCacheLocked();
        //mZipSet.purgeVendor();
        delete[] mVendor;
    }
    mVendor = strdupNew(vendor);
}

void AssetManager::setConfiguration(const ResTable_config& config, const char* locale)
{
    AutoMutex _l(mLock);
    *mConfig = config;
    if (locale) {
        setLocaleLocked(locale);
    } else if (config.language[0] != 0) {
        char spec[RESTABLE_MAX_LOCALE_LEN];
        config.getBcp47Locale(spec);
        setLocaleLocked(spec);
    } else {
        updateResourceParamsLocked();
    }
}

void AssetManager::getConfiguration(ResTable_config* outConfig) const
{
    AutoMutex _l(mLock);
    *outConfig = *mConfig;
}

/*
 * Open an asset.
 *
 * The data could be;
 *  - In a file on disk (assetBase + fileName).
 *  - In a compressed file on disk (assetBase + fileName.gz).
 *  - In a Zip archive, uncompressed or compressed.
 *
 * It can be in a number of different directories and Zip archives.
 * The search order is:
 *  - [appname]
 *    - locale + vendor
 *    - "default" + vendor
 *    - locale + "default"
 *    - "default + "default"
 *  - "common"
 *    - (same as above)
 *
 * To find a particular file, we have to try up to eight paths with
 * all three forms of data.
 *
 * We should probably reject requests for "illegal" filenames, e.g. those
 * with illegal characters or "../" backward relative paths.
 */
Asset* AssetManager::open(const char* fileName, AccessMode mode)
{
    AutoMutex _l(mLock);

    LOG_FATAL_IF(mAssetPaths.size() == 0, "No assets added to AssetManager");


    if (mCacheMode != CACHE_OFF && !mCacheValid)
        loadFileNameCacheLocked();

    String8 assetName(kAssetsRoot);
    assetName.appendPath(fileName);

    /*
     * For each top-level asset path, search for the asset.
     */

    size_t i = mAssetPaths.size();
    while (i > 0) {
        i--;
        ALOGV("Looking for asset '%s' in '%s'\n",
                assetName.string(), mAssetPaths.itemAt(i).path.string());

        // Skip theme/icon attached assets
        if (mAssetPaths.itemAt(i).prefixPath.length() > 0) continue;

        Asset* pAsset = openNonAssetInPathLocked(assetName.string(), mode, mAssetPaths.itemAt(i));
        if (pAsset != NULL) {
            return pAsset != kExcludedAsset ? pAsset : NULL;
        }
    }

    return NULL;
}

/*
 * Open a non-asset file as if it were an asset.
 *
 * The "fileName" is the partial path starting from the application
 * name.
 */
Asset* AssetManager::openNonAsset(const char* fileName, AccessMode mode, int32_t* outCookie)
{
    AutoMutex _l(mLock);

    LOG_FATAL_IF(mAssetPaths.size() == 0, "No assets added to AssetManager");


    if (mCacheMode != CACHE_OFF && !mCacheValid)
        loadFileNameCacheLocked();

    /*
     * For each top-level asset path, search for the asset.
     */

    size_t i = mAssetPaths.size();
    while (i > 0) {
        i--;
        ALOGV("Looking for non-asset '%s' in '%s'\n", fileName, mAssetPaths.itemAt(i).path.string());

        // Skip theme/icon attached assets
        if (mAssetPaths.itemAt(i).prefixPath.length() > 0) continue;

        Asset* pAsset = openNonAssetInPathLocked(
            fileName, mode, mAssetPaths.itemAt(i));
        if (pAsset != NULL) {
            if (outCookie != NULL) *outCookie = static_cast<int32_t>(i + 1);
            return pAsset != kExcludedAsset ? pAsset : NULL;
        }
    }

    return NULL;
}

Asset* AssetManager::openNonAsset(const int32_t cookie, const char* fileName, AccessMode mode)
{
    const size_t which = static_cast<size_t>(cookie) - 1;

    AutoMutex _l(mLock);

    LOG_FATAL_IF(mAssetPaths.size() == 0, "No assets added to AssetManager");

    if (mCacheMode != CACHE_OFF && !mCacheValid)
        loadFileNameCacheLocked();

    if (which < mAssetPaths.size()) {
        ALOGV("Looking for non-asset '%s' in '%s'\n", fileName,
                mAssetPaths.itemAt(which).path.string());
        Asset* pAsset = openNonAssetInPathLocked(
            fileName, mode, mAssetPaths.itemAt(which));
        if (pAsset != NULL) {
            return pAsset != kExcludedAsset ? pAsset : NULL;
        }
    } else if ((size_t)cookie == kComposedIconAsset) {
        asset_path ap;
        String8 path(fileName);
        ap.type = kFileTypeDirectory;
        ap.path = path.getPathDir();
        Asset* pAsset = openNonAssetInPathLocked(
            path.getPathLeaf().string(), mode, ap);
        if (pAsset != NULL) {
            return pAsset;
        }
    }

    return NULL;
}

/*
 * Get the type of a file in the asset namespace.
 *
 * This currently only works for regular files.  All others (including
 * directories) will return kFileTypeNonexistent.
 */
FileType AssetManager::getFileType(const char* fileName)
{
    Asset* pAsset = NULL;

    /*
     * Open the asset.  This is less efficient than simply finding the
     * file, but it's not too bad (we don't uncompress or mmap data until
     * the first read() call).
     */
    pAsset = open(fileName, Asset::ACCESS_STREAMING);
    delete pAsset;

    if (pAsset == NULL)
        return kFileTypeNonexistent;
    else
        return kFileTypeRegular;
}

bool AssetManager::appendPathToResTable(const asset_path& ap, size_t* entryIdx) const {
    // skip those ap's that correspond to system overlays
    if (ap.isSystemOverlay) {
        return true;
    }

    Asset* ass = NULL;
    ResTable* sharedRes = NULL;
    bool shared = true;
    bool onlyEmptyResources = true;
    MY_TRACE_BEGIN(ap.path.string());
    Asset* idmap = openIdmapLocked(ap);
    ALOGV("Looking for resource asset in '%s'\n", ap.path.string());
    if (!ap.resApkPath.isEmpty()) {
        // Avoid using prefix path in this case since the compiled resApk will simply have resources.arsc in it
        ass = const_cast<AssetManager*>(this)->openNonAssetInPathLocked("resources.arsc", Asset::ACCESS_BUFFER, ap, false);
        shared = false;
    } else if (ap.type != kFileTypeDirectory) {
        if (*entryIdx == 0) {
            // The first item is typically the framework resources,
            // which we want to avoid parsing every time.
            sharedRes = const_cast<AssetManager*>(this)->
                mZipSet.getZipResourceTable(ap.path);
            if (sharedRes != NULL) {
                // skip ahead the number of system overlay packages preloaded
                *entryIdx += sharedRes->getTableCount() - 1;
            }
        }
        if (sharedRes == NULL) {
            ass = const_cast<AssetManager*>(this)->
                mZipSet.getZipResourceTableAsset(ap.path);
            if (ass == NULL) {
                ALOGV("loading resource table %s\n", ap.path.string());
                ass = const_cast<AssetManager*>(this)->
                    openNonAssetInPathLocked("resources.arsc",
                                             Asset::ACCESS_BUFFER,
                                             ap);
                if (ass != NULL && ass != kExcludedAsset) {
                    ass = const_cast<AssetManager*>(this)->
                        mZipSet.setZipResourceTableAsset(ap.path, ass);
                }
            }
            
            if (*entryIdx == 0 && ass != NULL) {
                // If this is the first resource table in the asset
                // manager, then we are going to cache it so that we
                // can quickly copy it out for others.
                ALOGV("Creating shared resources for %s", ap.path.string());
                sharedRes = new ResTable();
                sharedRes->add(ass, idmap, *entryIdx + 1, false, ap.pkgIdOverride);
#ifdef HAVE_ANDROID_OS
                const char* data = getenv("ANDROID_DATA");
                LOG_ALWAYS_FATAL_IF(data == NULL, "ANDROID_DATA not set");
                String8 overlaysListPath(data);
                overlaysListPath.appendPath(kResourceCache);
                overlaysListPath.appendPath("overlays.list");
                addSystemOverlays(overlaysListPath.string(), ap.path, sharedRes, *entryIdx);
#endif
                sharedRes = const_cast<AssetManager*>(this)->
                    mZipSet.setZipResourceTable(ap.path, sharedRes);
            }
        }
    } else {
        ALOGV("loading resource table %s\n", ap.path.string());
        ass = const_cast<AssetManager*>(this)->
            openNonAssetInPathLocked("resources.arsc",
                                     Asset::ACCESS_BUFFER,
                                     ap);
        shared = false;
    }

    if ((ass != NULL || sharedRes != NULL) && ass != kExcludedAsset) {
        ALOGV("Installing resource asset %p in to table %p\n", ass, mResources);
        if (sharedRes != NULL) {
            ALOGV("Copying existing resources for %s", ap.path.string());
            mResources->add(sharedRes);
        } else {
            ALOGV("Parsing resources for %s", ap.path.string());
            mResources->add(ass, idmap, *entryIdx + 1, !shared, ap.pkgIdOverride);
        }
        onlyEmptyResources = false;

        if (!shared) {
            delete ass;
        }
    } else {
        ALOGV("Installing empty resources in to table %p\n", mResources);
        mResources->addEmpty(*entryIdx + 1);
    }

    if (idmap != NULL) {
        delete idmap;
    }
    MY_TRACE_END();

    return onlyEmptyResources;
}

const ResTable* AssetManager::getResTable(bool required) const
{
    ResTable* rt = mResources;
    if (rt) {
        return rt;
    }

    // Iterate through all asset packages, collecting resources from each.

    AutoMutex _l(mLock);

    if (mResources != NULL) {
        return mResources;
    }

    if (required) {
        LOG_FATAL_IF(mAssetPaths.size() == 0, "No assets added to AssetManager");
    }

    if (mCacheMode != CACHE_OFF && !mCacheValid) {
        const_cast<AssetManager*>(this)->loadFileNameCacheLocked();
    }

    mResources = new ResTable();
    updateResourceParamsLocked();

    bool onlyEmptyResources = true;
    const size_t N = mAssetPaths.size();
    for (size_t i=0; i<N; i++) {
        bool empty = appendPathToResTable(mAssetPaths.itemAt(i), &i);
        onlyEmptyResources = onlyEmptyResources && empty;
    }

    if (required && onlyEmptyResources) {
        ALOGW("Unable to find resources file resources.arsc");
        delete mResources;
        mResources = NULL;
    }

    return mResources;
}

void AssetManager::updateResourceParamsLocked() const
{
    ResTable* res = mResources;
    if (!res) {
        return;
    }

    if (mLocale) {
        mConfig->setBcp47Locale(mLocale);
    } else {
        mConfig->clearLocale();
    }

    res->setParameters(mConfig);
}

Asset* AssetManager::openIdmapLocked(const struct asset_path& ap) const
{
    Asset* ass = NULL;
    if (ap.idmap.size() != 0) {
        ass = const_cast<AssetManager*>(this)->
            openAssetFromFileLocked(ap.idmap, Asset::ACCESS_BUFFER);
        if (ass) {
            ALOGV("loading idmap %s\n", ap.idmap.string());
        } else {
            ALOGW("failed to load idmap %s\n", ap.idmap.string());
        }
    }
    return ass;
}

void AssetManager::addSystemOverlays(const char* pathOverlaysList,
        const String8& targetPackagePath, ResTable* sharedRes, size_t offset) const
{
    FILE* fin = fopen(pathOverlaysList, "r");
    if (fin == NULL) {
        return;
    }

    char buf[1024];
    while (fgets(buf, sizeof(buf), fin)) {
        // format of each line:
        //   <path to apk><space><path to idmap><newline>
        char* space = strchr(buf, ' ');
        char* newline = strchr(buf, '\n');
        asset_path oap;

        if (space == NULL || newline == NULL || newline < space) {
            continue;
        }

        oap.path = String8(buf, space - buf);
        oap.type = kFileTypeRegular;
        oap.idmap = String8(space + 1, newline - space - 1);
        oap.isSystemOverlay = true;

        Asset* oass = const_cast<AssetManager*>(this)->
            openNonAssetInPathLocked("resources.arsc",
                    Asset::ACCESS_BUFFER,
                    oap);

        if (oass != NULL) {
            Asset* oidmap = openIdmapLocked(oap);
            offset++;
            sharedRes->add(oass, oidmap, offset + 1, false, oap.pkgIdOverride);
            const_cast<AssetManager*>(this)->mAssetPaths.add(oap);
            const_cast<AssetManager*>(this)->mZipSet.addOverlay(targetPackagePath, oap);
        }
    }
    fclose(fin);
}

bool AssetManager::removeOverlayPath(const String8& packageName, int32_t cookie)
{
    AutoMutex _l(mLock);

    const size_t which = ((size_t)cookie)-1;
    if (which >= mAssetPaths.size()) {
        ALOGE("cookie was larger than paths size");
        return false;
    }

    const asset_path& oap = mAssetPaths.itemAt(which);
    mZipSet.closeZip(oap.resApkPath);

    mAssetPaths.removeAt(which);

    ResTable* rt = mResources;
    if (rt == NULL) {
        ALOGE("Unable to remove overlayPath, ResTable must not be NULL");
        return false;
    }

    rt->removeAssetsByCookie(packageName, cookie);

    return true;
}

const ResTable& AssetManager::getResources(bool required) const
{
    const ResTable* rt = getResTable(required);
    return *rt;
}

bool AssetManager::isUpToDate()
{
    AutoMutex _l(mLock);
    return mZipSet.isUpToDate();
}

void AssetManager::getLocales(Vector<String8>* locales) const
{
    ResTable* res = mResources;
    if (res != NULL) {
        res->getLocales(locales);
    }

    const size_t numLocales = locales->size();
    for (size_t i = 0; i < numLocales; ++i) {
        const String8& localeStr = locales->itemAt(i);
        if (localeStr.find(kTlPrefix) == 0) {
            String8 replaced("fil");
            replaced += (localeStr.string() + kTlPrefixLen);
            locales->editItemAt(i) = replaced;
        }
    }
}

/*
 * Open a non-asset file as if it were an asset, searching for it in the
 * specified app.
 *
 *
 * Pass in a NULL values for "appName" if the common app directory should
 * be used.
 */
Asset* AssetManager::openNonAssetInPathLocked(const char* fileName, AccessMode mode,
    const asset_path& ap, bool usePrefix)
{
    Asset* pAsset = NULL;

    // Append asset_path prefix if needed
    const char* fileNameFinal = fileName;
    String8 fileNameWithPrefix;
    if (usePrefix && ap.prefixPath.length() > 0) {
      fileNameWithPrefix.append(ap.prefixPath);
      fileNameWithPrefix.append(fileName);
      fileNameFinal = fileNameWithPrefix.string();
    }

    /* look at the filesystem on disk */
    if (ap.type == kFileTypeDirectory) {
        String8 path(ap.path);
        path.appendPath(fileNameFinal);

        pAsset = openAssetFromFileLocked(path, mode);

        if (pAsset == NULL) {
            /* try again, this time with ".gz" */
            path.append(".gz");
            pAsset = openAssetFromFileLocked(path, mode);
        }

        if (pAsset != NULL) {
            //printf("FOUND NA '%s' on disk\n", fileName);
            pAsset->setAssetSource(path);
        }

    /* look inside the zip file */
    } else {
        String8 path(fileName);
        const char* zipPath;

        /* check the appropriate Zip file */
        ZipFileRO* pZip;
        ZipEntryRO entry;

        if (!ap.resApkPath.isEmpty()) {
            pZip = getZipFileLocked(ap.resApkPath);
            if (pZip != NULL) {
                //printf("GOT zip, checking NA '%s'\n", (const char*) path);
                entry = pZip->findEntryByName(path.string());
                if (entry != NULL) {
                    //printf("FOUND NA in Zip file for %s\n", appName ? appName : kAppCommon);
                    pAsset = openAssetFromZipLocked(pZip, entry, mode, path);
                    zipPath = ap.resApkPath.string();
                }
            }
        }

        if (pAsset == NULL) {
            path.setTo(fileNameFinal);
            pZip = getZipFileLocked(ap);
            if (pZip != NULL) {
                //printf("GOT zip, checking NA '%s'\n", (const char*) path);
                ZipEntryRO entry = pZip->findEntryByName(path.string());
                if (entry != NULL) {
                    //printf("FOUND NA in Zip file for %s\n", appName ? appName : kAppCommon);
                    pAsset = openAssetFromZipLocked(pZip, entry, mode, path);
                    pZip->releaseEntry(entry);
                    zipPath = ap.path.string();
                }
            }
        }

        if (pAsset != NULL) {
            /* create a "source" name, for debug/display */
            pAsset->setAssetSource(
                    createZipSourceNameLocked(ZipSet::getPathName(zipPath), String8(""),
                                                String8(path.string())));
        }
    }

    return pAsset;
}

/*
 * Open an asset, searching for it in the directory hierarchy for the
 * specified app.
 *
 * Pass in a NULL values for "appName" if the common app directory should
 * be used.
 */
Asset* AssetManager::openInPathLocked(const char* fileName, AccessMode mode,
    const asset_path& ap)
{
    Asset* pAsset = NULL;

    /*
     * Try various combinations of locale and vendor.
     */
    if (mLocale != NULL && mVendor != NULL)
        pAsset = openInLocaleVendorLocked(fileName, mode, ap, mLocale, mVendor);
    if (pAsset == NULL && mVendor != NULL)
        pAsset = openInLocaleVendorLocked(fileName, mode, ap, NULL, mVendor);
    if (pAsset == NULL && mLocale != NULL)
        pAsset = openInLocaleVendorLocked(fileName, mode, ap, mLocale, NULL);
    if (pAsset == NULL)
        pAsset = openInLocaleVendorLocked(fileName, mode, ap, NULL, NULL);

    return pAsset;
}

/*
 * Open an asset, searching for it in the directory hierarchy for the
 * specified locale and vendor.
 *
 * We also search in "app.jar".
 *
 * Pass in NULL values for "appName", "locale", and "vendor" if the
 * defaults should be used.
 */
Asset* AssetManager::openInLocaleVendorLocked(const char* fileName, AccessMode mode,
    const asset_path& ap, const char* locale, const char* vendor)
{
    Asset* pAsset = NULL;

    if (ap.type == kFileTypeDirectory) {
        if (mCacheMode == CACHE_OFF) {
            /* look at the filesystem on disk */
            String8 path(createPathNameLocked(ap, locale, vendor));
            path.appendPath(fileName);
    
            String8 excludeName(path);
            excludeName.append(kExcludeExtension);
            if (::getFileType(excludeName.string()) != kFileTypeNonexistent) {
                /* say no more */
                //printf("+++ excluding '%s'\n", (const char*) excludeName);
                return kExcludedAsset;
            }
    
            pAsset = openAssetFromFileLocked(path, mode);
    
            if (pAsset == NULL) {
                /* try again, this time with ".gz" */
                path.append(".gz");
                pAsset = openAssetFromFileLocked(path, mode);
            }
    
            if (pAsset != NULL)
                pAsset->setAssetSource(path);
        } else {
            /* find in cache */
            String8 path(createPathNameLocked(ap, locale, vendor));
            path.appendPath(fileName);
    
            AssetDir::FileInfo tmpInfo;
            bool found = false;
    
            String8 excludeName(path);
            excludeName.append(kExcludeExtension);
    
            if (mCache.indexOf(excludeName) != NAME_NOT_FOUND) {
                /* go no farther */
                //printf("+++ Excluding '%s'\n", (const char*) excludeName);
                return kExcludedAsset;
            }

            /*
             * File compression extensions (".gz") don't get stored in the
             * name cache, so we have to try both here.
             */
            if (mCache.indexOf(path) != NAME_NOT_FOUND) {
                found = true;
                pAsset = openAssetFromFileLocked(path, mode);
                if (pAsset == NULL) {
                    /* try again, this time with ".gz" */
                    path.append(".gz");
                    pAsset = openAssetFromFileLocked(path, mode);
                }
            }

            if (pAsset != NULL)
                pAsset->setAssetSource(path);

            /*
             * Don't continue the search into the Zip files.  Our cached info
             * said it was a file on disk; to be consistent with openDir()
             * we want to return the loose asset.  If the cached file gets
             * removed, we fail.
             *
             * The alternative is to update our cache when files get deleted,
             * or make some sort of "best effort" promise, but for now I'm
             * taking the hard line.
             */
            if (found) {
                if (pAsset == NULL)
                    ALOGD("Expected file not found: '%s'\n", path.string());
                return pAsset;
            }
        }
    }

    /*
     * Either it wasn't found on disk or on the cached view of the disk.
     * Dig through the currently-opened set of Zip files.  If caching
     * is disabled, the Zip file may get reopened.
     */
    if (pAsset == NULL && ap.type == kFileTypeRegular) {
        String8 path;

        path.appendPath((locale != NULL) ? locale : kDefaultLocale);
        path.appendPath((vendor != NULL) ? vendor : kDefaultVendor);
        path.appendPath(fileName);

        /* check the appropriate Zip file */
        ZipFileRO* pZip = getZipFileLocked(ap);
        if (pZip != NULL) {
            //printf("GOT zip, checking '%s'\n", (const char*) path);
            ZipEntryRO entry = pZip->findEntryByName(path.string());
            if (entry != NULL) {
                //printf("FOUND in Zip file for %s/%s-%s\n",
                //    appName, locale, vendor);
                pAsset = openAssetFromZipLocked(pZip, entry, mode, path);
                pZip->releaseEntry(entry);
            }
        }

        if (pAsset != NULL) {
            /* create a "source" name, for debug/display */
            pAsset->setAssetSource(createZipSourceNameLocked(ZipSet::getPathName(ap.path.string()),
                                                             String8(""), String8(fileName)));
        }
    }

    return pAsset;
}

/*
 * Create a "source name" for a file from a Zip archive.
 */
String8 AssetManager::createZipSourceNameLocked(const String8& zipFileName,
    const String8& dirName, const String8& fileName)
{
    String8 sourceName("zip:");
    sourceName.append(zipFileName);
    sourceName.append(":");
    if (dirName.length() > 0) {
        sourceName.appendPath(dirName);
    }
    sourceName.appendPath(fileName);
    return sourceName;
}

/*
 * Create a path to a loose asset (asset-base/app/locale/vendor).
 */
String8 AssetManager::createPathNameLocked(const asset_path& ap, const char* locale,
    const char* vendor)
{
    String8 path(ap.path);
    path.appendPath((locale != NULL) ? locale : kDefaultLocale);
    path.appendPath((vendor != NULL) ? vendor : kDefaultVendor);
    return path;
}

/*
 * Create a path to a loose asset (asset-base/app/rootDir).
 */
String8 AssetManager::createPathNameLocked(const asset_path& ap, const char* rootDir)
{
    String8 path(ap.path);
    if (rootDir != NULL) path.appendPath(rootDir);
    return path;
}

/*
 * Return a pointer to one of our open Zip archives.  Returns NULL if no
 * matching Zip file exists.
 *
 * Right now we have 2 possible Zip files (1 each in app/"common").
 *
 * If caching is set to CACHE_OFF, to get the expected behavior we
 * need to reopen the Zip file on every request.  That would be silly
 * and expensive, so instead we just check the file modification date.
 *
 * Pass in NULL values for "appName", "locale", and "vendor" if the
 * generics should be used.
 */
ZipFileRO* AssetManager::getZipFileLocked(const asset_path& ap)
{
    return getZipFileLocked(ap.path);
}

ZipFileRO* AssetManager::getZipFileLocked(const String8& path)
{
    ALOGV("getZipFileLocked() in %p\n", this);

    return mZipSet.getZip(path);
}

/*
 * Try to open an asset from a file on disk.
 *
 * If the file is compressed with gzip, we seek to the start of the
 * deflated data and pass that in (just like we would for a Zip archive).
 *
 * For uncompressed data, we may already have an mmap()ed version sitting
 * around.  If so, we want to hand that to the Asset instead.
 *
 * This returns NULL if the file doesn't exist, couldn't be opened, or
 * claims to be a ".gz" but isn't.
 */
Asset* AssetManager::openAssetFromFileLocked(const String8& pathName,
    AccessMode mode)
{
    Asset* pAsset = NULL;

    if (strcasecmp(pathName.getPathExtension().string(), ".gz") == 0) {
        //printf("TRYING '%s'\n", (const char*) pathName);
        pAsset = Asset::createFromCompressedFile(pathName.string(), mode);
    } else {
        //printf("TRYING '%s'\n", (const char*) pathName);
        pAsset = Asset::createFromFile(pathName.string(), mode);
    }

    return pAsset;
}

/*
 * Given an entry in a Zip archive, create a new Asset object.
 *
 * If the entry is uncompressed, we may want to create or share a
 * slice of shared memory.
 */
Asset* AssetManager::openAssetFromZipLocked(const ZipFileRO* pZipFile,
    const ZipEntryRO entry, AccessMode mode, const String8& entryName)
{
    Asset* pAsset = NULL;

    // TODO: look for previously-created shared memory slice?
    uint16_t method;
    uint32_t uncompressedLen;

    //printf("USING Zip '%s'\n", pEntry->getFileName());

    if (!pZipFile->getEntryInfo(entry, &method, &uncompressedLen, NULL, NULL,
            NULL, NULL))
    {
        ALOGW("getEntryInfo failed\n");
        return NULL;
    }

    FileMap* dataMap = pZipFile->createEntryFileMap(entry);
    if (dataMap == NULL) {
        ALOGW("create map from entry failed\n");
        return NULL;
    }

    if (method == ZipFileRO::kCompressStored) {
        pAsset = Asset::createFromUncompressedMap(dataMap, mode);
        ALOGV("Opened uncompressed entry %s in zip %s mode %d: %p", entryName.string(),
                dataMap->getFileName(), mode, pAsset);
    } else {
        pAsset = Asset::createFromCompressedMap(dataMap,
            static_cast<size_t>(uncompressedLen), mode);
        ALOGV("Opened compressed entry %s in zip %s mode %d: %p", entryName.string(),
                dataMap->getFileName(), mode, pAsset);
    }
    if (pAsset == NULL) {
        /* unexpected */
        ALOGW("create from segment failed\n");
    }

    return pAsset;
}



/*
 * Open a directory in the asset namespace.
 *
 * An "asset directory" is simply the combination of all files in all
 * locations, with ".gz" stripped for loose files.  With app, locale, and
 * vendor defined, we have 8 directories and 2 Zip archives to scan.
 *
 * Pass in "" for the root dir.
 */
AssetDir* AssetManager::openDir(const char* dirName)
{
    AutoMutex _l(mLock);

    AssetDir* pDir = NULL;
    SortedVector<AssetDir::FileInfo>* pMergedInfo = NULL;

    LOG_FATAL_IF(mAssetPaths.size() == 0, "No assets added to AssetManager");
    assert(dirName != NULL);

    //printf("+++ openDir(%s) in '%s'\n", dirName, (const char*) mAssetBase);

    if (mCacheMode != CACHE_OFF && !mCacheValid)
        loadFileNameCacheLocked();

    pDir = new AssetDir;

    /*
     * Scan the various directories, merging what we find into a single
     * vector.  We want to scan them in reverse priority order so that
     * the ".EXCLUDE" processing works correctly.  Also, if we decide we
     * want to remember where the file is coming from, we'll get the right
     * version.
     *
     * We start with Zip archives, then do loose files.
     */
    pMergedInfo = new SortedVector<AssetDir::FileInfo>;

    size_t i = mAssetPaths.size();
    while (i > 0) {
        i--;
        const asset_path& ap = mAssetPaths.itemAt(i);

        // Skip theme/icon attached assets
        if (ap.prefixPath.length() > 0) continue;

        if (ap.type == kFileTypeRegular) {
            ALOGV("Adding directory %s from zip %s", dirName, ap.path.string());
            scanAndMergeZipLocked(pMergedInfo, ap, kAssetsRoot, dirName);
        } else {
            ALOGV("Adding directory %s from dir %s", dirName, ap.path.string());
            scanAndMergeDirLocked(pMergedInfo, ap, kAssetsRoot, dirName);
        }
    }

#if 0
    printf("FILE LIST:\n");
    for (i = 0; i < (size_t) pMergedInfo->size(); i++) {
        printf(" %d: (%d) '%s'\n", i,
            pMergedInfo->itemAt(i).getFileType(),
            (const char*) pMergedInfo->itemAt(i).getFileName());
    }
#endif

    pDir->setFileList(pMergedInfo);
    return pDir;
}

/*
 * Open a directory in the non-asset namespace.
 *
 * An "asset directory" is simply the combination of all files in all
 * locations, with ".gz" stripped for loose files.  With app, locale, and
 * vendor defined, we have 8 directories and 2 Zip archives to scan.
 *
 * Pass in "" for the root dir.
 */
AssetDir* AssetManager::openNonAssetDir(const int32_t cookie, const char* dirName)
{
    AutoMutex _l(mLock);

    AssetDir* pDir = NULL;
    SortedVector<AssetDir::FileInfo>* pMergedInfo = NULL;

    LOG_FATAL_IF(mAssetPaths.size() == 0, "No assets added to AssetManager");
    assert(dirName != NULL);

    //printf("+++ openDir(%s) in '%s'\n", dirName, (const char*) mAssetBase);

    if (mCacheMode != CACHE_OFF && !mCacheValid)
        loadFileNameCacheLocked();

    pDir = new AssetDir;

    pMergedInfo = new SortedVector<AssetDir::FileInfo>;

    const size_t which = static_cast<size_t>(cookie) - 1;

    if (which < mAssetPaths.size()) {
        const asset_path& ap = mAssetPaths.itemAt(which);
        if (ap.type == kFileTypeRegular) {
            ALOGV("Adding directory %s from zip %s", dirName, ap.path.string());
            scanAndMergeZipLocked(pMergedInfo, ap, NULL, dirName);
        } else {
            ALOGV("Adding directory %s from dir %s", dirName, ap.path.string());
            scanAndMergeDirLocked(pMergedInfo, ap, NULL, dirName);
        }
    }

#if 0
    printf("FILE LIST:\n");
    for (i = 0; i < (size_t) pMergedInfo->size(); i++) {
        printf(" %d: (%d) '%s'\n", i,
            pMergedInfo->itemAt(i).getFileType(),
            (const char*) pMergedInfo->itemAt(i).getFileName());
    }
#endif

    pDir->setFileList(pMergedInfo);
    return pDir;
}

/*
 * Scan the contents of the specified directory and merge them into the
 * "pMergedInfo" vector, removing previous entries if we find "exclude"
 * directives.
 *
 * Returns "false" if we found nothing to contribute.
 */
bool AssetManager::scanAndMergeDirLocked(SortedVector<AssetDir::FileInfo>* pMergedInfo,
    const asset_path& ap, const char* rootDir, const char* dirName)
{
    SortedVector<AssetDir::FileInfo>* pContents;
    String8 path;

    assert(pMergedInfo != NULL);

    //printf("scanAndMergeDir: %s %s %s %s\n", appName, locale, vendor,dirName);

    if (mCacheValid) {
        int i, start, count;

        pContents = new SortedVector<AssetDir::FileInfo>;

        /*
         * Get the basic partial path and find it in the cache.  That's
         * the start point for the search.
         */
        path = createPathNameLocked(ap, rootDir);
        if (dirName[0] != '\0')
            path.appendPath(dirName);

        start = mCache.indexOf(path);
        if (start == NAME_NOT_FOUND) {
            //printf("+++ not found in cache: dir '%s'\n", (const char*) path);
            delete pContents;
            return false;
        }

        /*
         * The match string looks like "common/default/default/foo/bar/".
         * The '/' on the end ensures that we don't match on the directory
         * itself or on ".../foo/barfy/".
         */
        path.append("/");

        count = mCache.size();

        /*
         * Pick out the stuff in the current dir by examining the pathname.
         * It needs to match the partial pathname prefix, and not have a '/'
         * (fssep) anywhere after the prefix.
         */
        for (i = start+1; i < count; i++) {
            if (mCache[i].getFileName().length() > path.length() &&
                strncmp(mCache[i].getFileName().string(), path.string(), path.length()) == 0)
            {
                const char* name = mCache[i].getFileName().string();
                // XXX THIS IS BROKEN!  Looks like we need to store the full
                // path prefix separately from the file path.
                if (strchr(name + path.length(), '/') == NULL) {
                    /* grab it, reducing path to just the filename component */
                    AssetDir::FileInfo tmp = mCache[i];
                    tmp.setFileName(tmp.getFileName().getPathLeaf());
                    pContents->add(tmp);
                }
            } else {
                /* no longer in the dir or its subdirs */
                break;
            }

        }
    } else {
        path = createPathNameLocked(ap, rootDir);
        if (dirName[0] != '\0')
            path.appendPath(dirName);
        pContents = scanDirLocked(path);
        if (pContents == NULL)
            return false;
    }

    // if we wanted to do an incremental cache fill, we would do it here

    /*
     * Process "exclude" directives.  If we find a filename that ends with
     * ".EXCLUDE", we look for a matching entry in the "merged" set, and
     * remove it if we find it.  We also delete the "exclude" entry.
     */
    int i, count, exclExtLen;

    count = pContents->size();
    exclExtLen = strlen(kExcludeExtension);
    for (i = 0; i < count; i++) {
        const char* name;
        int nameLen;

        name = pContents->itemAt(i).getFileName().string();
        nameLen = strlen(name);
        if (nameLen > exclExtLen &&
            strcmp(name + (nameLen - exclExtLen), kExcludeExtension) == 0)
        {
            String8 match(name, nameLen - exclExtLen);
            int matchIdx;

            matchIdx = AssetDir::FileInfo::findEntry(pMergedInfo, match);
            if (matchIdx > 0) {
                ALOGV("Excluding '%s' [%s]\n",
                    pMergedInfo->itemAt(matchIdx).getFileName().string(),
                    pMergedInfo->itemAt(matchIdx).getSourceName().string());
                pMergedInfo->removeAt(matchIdx);
            } else {
                //printf("+++ no match on '%s'\n", (const char*) match);
            }

            ALOGD("HEY: size=%d removing %d\n", (int)pContents->size(), i);
            pContents->removeAt(i);
            i--;        // adjust "for" loop
            count--;    //  and loop limit
        }
    }

    mergeInfoLocked(pMergedInfo, pContents);

    delete pContents;

    return true;
}

/*
 * Scan the contents of the specified directory, and stuff what we find
 * into a newly-allocated vector.
 *
 * Files ending in ".gz" will have their extensions removed.
 *
 * We should probably think about skipping files with "illegal" names,
 * e.g. illegal characters (/\:) or excessive length.
 *
 * Returns NULL if the specified directory doesn't exist.
 */
SortedVector<AssetDir::FileInfo>* AssetManager::scanDirLocked(const String8& path)
{
    SortedVector<AssetDir::FileInfo>* pContents = NULL;
    DIR* dir;
    struct dirent* entry;
    FileType fileType;

    ALOGV("Scanning dir '%s'\n", path.string());

    dir = opendir(path.string());
    if (dir == NULL)
        return NULL;

    pContents = new SortedVector<AssetDir::FileInfo>;

    while (1) {
        entry = readdir(dir);
        if (entry == NULL)
            break;

        if (strcmp(entry->d_name, ".") == 0 ||
            strcmp(entry->d_name, "..") == 0)
            continue;

#ifdef _DIRENT_HAVE_D_TYPE
        if (entry->d_type == DT_REG)
            fileType = kFileTypeRegular;
        else if (entry->d_type == DT_DIR)
            fileType = kFileTypeDirectory;
        else
            fileType = kFileTypeUnknown;
#else
        // stat the file
        fileType = ::getFileType(path.appendPathCopy(entry->d_name).string());
#endif

        if (fileType != kFileTypeRegular && fileType != kFileTypeDirectory)
            continue;

        AssetDir::FileInfo info;
        info.set(String8(entry->d_name), fileType);
        if (strcasecmp(info.getFileName().getPathExtension().string(), ".gz") == 0)
            info.setFileName(info.getFileName().getBasePath());
        info.setSourceName(path.appendPathCopy(info.getFileName()));
        pContents->add(info);
    }

    closedir(dir);
    return pContents;
}

/*
 * Scan the contents out of the specified Zip archive, and merge what we
 * find into "pMergedInfo".  If the Zip archive in question doesn't exist,
 * we return immediately.
 *
 * Returns "false" if we found nothing to contribute.
 */
bool AssetManager::scanAndMergeZipLocked(SortedVector<AssetDir::FileInfo>* pMergedInfo,
    const asset_path& ap, const char* rootDir, const char* baseDirName)
{
    ZipFileRO* pZip;
    Vector<String8> dirs;
    AssetDir::FileInfo info;
    SortedVector<AssetDir::FileInfo> contents;
    String8 sourceName, zipName, dirName;

    pZip = mZipSet.getZip(ap.path);
    if (pZip == NULL) {
        ALOGW("Failure opening zip %s\n", ap.path.string());
        return false;
    }

    zipName = ZipSet::getPathName(ap.path.string());

    /* convert "sounds" to "rootDir/sounds" */
    if (rootDir != NULL) dirName = rootDir;
    dirName.appendPath(baseDirName);

    /*
     * Scan through the list of files, looking for a match.  The files in
     * the Zip table of contents are not in sorted order, so we have to
     * process the entire list.  We're looking for a string that begins
     * with the characters in "dirName", is followed by a '/', and has no
     * subsequent '/' in the stuff that follows.
     *
     * What makes this especially fun is that directories are not stored
     * explicitly in Zip archives, so we have to infer them from context.
     * When we see "sounds/foo.wav" we have to leave a note to ourselves
     * to insert a directory called "sounds" into the list.  We store
     * these in temporary vector so that we only return each one once.
     *
     * Name comparisons are case-sensitive to match UNIX filesystem
     * semantics.
     */
    int dirNameLen = dirName.length();
    void *iterationCookie;
    if (!pZip->startIteration(&iterationCookie)) {
        ALOGW("ZipFileRO::startIteration returned false");
        return false;
    }

    ZipEntryRO entry;
    while ((entry = pZip->nextEntry(iterationCookie)) != NULL) {
        char nameBuf[256];

        if (pZip->getEntryFileName(entry, nameBuf, sizeof(nameBuf)) != 0) {
            // TODO: fix this if we expect to have long names
            ALOGE("ARGH: name too long?\n");
            continue;
        }
        //printf("Comparing %s in %s?\n", nameBuf, dirName.string());
        if (dirNameLen == 0 ||
            (strncmp(nameBuf, dirName.string(), dirNameLen) == 0 &&
             nameBuf[dirNameLen] == '/'))
        {
            const char* cp;
            const char* nextSlash;

            cp = nameBuf + dirNameLen;
            if (dirNameLen != 0)
                cp++;       // advance past the '/'

            nextSlash = strchr(cp, '/');
//xxx this may break if there are bare directory entries
            if (nextSlash == NULL) {
                /* this is a file in the requested directory */

                info.set(String8(nameBuf).getPathLeaf(), kFileTypeRegular);

                info.setSourceName(
                    createZipSourceNameLocked(zipName, dirName, info.getFileName()));

                contents.add(info);
                //printf("FOUND: file '%s'\n", info.getFileName().string());
            } else {
                /* this is a subdir; add it if we don't already have it*/
                String8 subdirName(cp, nextSlash - cp);
                size_t j;
                size_t N = dirs.size();

                for (j = 0; j < N; j++) {
                    if (subdirName == dirs[j]) {
                        break;
                    }
                }
                if (j == N) {
                    dirs.add(subdirName);
                }

                //printf("FOUND: dir '%s'\n", subdirName.string());
            }
        }
    }

    pZip->endIteration(iterationCookie);

    /*
     * Add the set of unique directories.
     */
    for (int i = 0; i < (int) dirs.size(); i++) {
        info.set(dirs[i], kFileTypeDirectory);
        info.setSourceName(
            createZipSourceNameLocked(zipName, dirName, info.getFileName()));
        contents.add(info);
    }

    mergeInfoLocked(pMergedInfo, &contents);

    return true;
}


/*
 * Merge two vectors of FileInfo.
 *
 * The merged contents will be stuffed into *pMergedInfo.
 *
 * If an entry for a file exists in both "pMergedInfo" and "pContents",
 * we use the newer "pContents" entry.
 */
void AssetManager::mergeInfoLocked(SortedVector<AssetDir::FileInfo>* pMergedInfo,
    const SortedVector<AssetDir::FileInfo>* pContents)
{
    /*
     * Merge what we found in this directory with what we found in
     * other places.
     *
     * Two basic approaches:
     * (1) Create a new array that holds the unique values of the two
     *     arrays.
     * (2) Take the elements from pContents and shove them into pMergedInfo.
     *
     * Because these are vectors of complex objects, moving elements around
     * inside the vector requires constructing new objects and allocating
     * storage for members.  With approach #1, we're always adding to the
     * end, whereas with #2 we could be inserting multiple elements at the
     * front of the vector.  Approach #1 requires a full copy of the
     * contents of pMergedInfo, but approach #2 requires the same copy for
     * every insertion at the front of pMergedInfo.
     *
     * (We should probably use a SortedVector interface that allows us to
     * just stuff items in, trusting us to maintain the sort order.)
     */
    SortedVector<AssetDir::FileInfo>* pNewSorted;
    int mergeMax, contMax;
    int mergeIdx, contIdx;

    pNewSorted = new SortedVector<AssetDir::FileInfo>;
    mergeMax = pMergedInfo->size();
    contMax = pContents->size();
    mergeIdx = contIdx = 0;

    while (mergeIdx < mergeMax || contIdx < contMax) {
        if (mergeIdx == mergeMax) {
            /* hit end of "merge" list, copy rest of "contents" */
            pNewSorted->add(pContents->itemAt(contIdx));
            contIdx++;
        } else if (contIdx == contMax) {
            /* hit end of "cont" list, copy rest of "merge" */
            pNewSorted->add(pMergedInfo->itemAt(mergeIdx));
            mergeIdx++;
        } else if (pMergedInfo->itemAt(mergeIdx) == pContents->itemAt(contIdx))
        {
            /* items are identical, add newer and advance both indices */
            pNewSorted->add(pContents->itemAt(contIdx));
            mergeIdx++;
            contIdx++;
        } else if (pMergedInfo->itemAt(mergeIdx) < pContents->itemAt(contIdx))
        {
            /* "merge" is lower, add that one */
            pNewSorted->add(pMergedInfo->itemAt(mergeIdx));
            mergeIdx++;
        } else {
            /* "cont" is lower, add that one */
            assert(pContents->itemAt(contIdx) < pMergedInfo->itemAt(mergeIdx));
            pNewSorted->add(pContents->itemAt(contIdx));
            contIdx++;
        }
    }

    /*
     * Overwrite the "merged" list with the new stuff.
     */
    *pMergedInfo = *pNewSorted;
    delete pNewSorted;

#if 0       // for Vector, rather than SortedVector
    int i, j;
    for (i = pContents->size() -1; i >= 0; i--) {
        bool add = true;

        for (j = pMergedInfo->size() -1; j >= 0; j--) {
            /* case-sensitive comparisons, to behave like UNIX fs */
            if (strcmp(pContents->itemAt(i).mFileName,
                       pMergedInfo->itemAt(j).mFileName) == 0)
            {
                /* match, don't add this entry */
                add = false;
                break;
            }
        }

        if (add)
            pMergedInfo->add(pContents->itemAt(i));
    }
#endif
}


/*
 * Load all files into the file name cache.  We want to do this across
 * all combinations of { appname, locale, vendor }, performing a recursive
 * directory traversal.
 *
 * This is not the most efficient data structure.  Also, gathering the
 * information as we needed it (file-by-file or directory-by-directory)
 * would be faster.  However, on the actual device, 99% of the files will
 * live in Zip archives, so this list will be very small.  The trouble
 * is that we have to check the "loose" files first, so it's important
 * that we don't beat the filesystem silly looking for files that aren't
 * there.
 *
 * Note on thread safety: this is the only function that causes updates
 * to mCache, and anybody who tries to use it will call here if !mCacheValid,
 * so we need to employ a mutex here.
 */
void AssetManager::loadFileNameCacheLocked(void)
{
    assert(!mCacheValid);
    assert(mCache.size() == 0);

#ifdef DO_TIMINGS   // need to link against -lrt for this now
    DurationTimer timer;
    timer.start();
#endif

    fncScanLocked(&mCache, "");

#ifdef DO_TIMINGS
    timer.stop();
    ALOGD("Cache scan took %.3fms\n",
        timer.durationUsecs() / 1000.0);
#endif

#if 0
    int i;
    printf("CACHED FILE LIST (%d entries):\n", mCache.size());
    for (i = 0; i < (int) mCache.size(); i++) {
        printf(" %d: (%d) '%s'\n", i,
            mCache.itemAt(i).getFileType(),
            (const char*) mCache.itemAt(i).getFileName());
    }
#endif

    mCacheValid = true;
}

/*
 * Scan up to 8 versions of the specified directory.
 */
void AssetManager::fncScanLocked(SortedVector<AssetDir::FileInfo>* pMergedInfo,
    const char* dirName)
{
    size_t i = mAssetPaths.size();
    while (i > 0) {
        i--;
        const asset_path& ap = mAssetPaths.itemAt(i);

        // Skip theme/icon attached assets
        if (ap.prefixPath.length() > 0) continue;

        fncScanAndMergeDirLocked(pMergedInfo, ap, NULL, NULL, dirName);
        if (mLocale != NULL)
            fncScanAndMergeDirLocked(pMergedInfo, ap, mLocale, NULL, dirName);
        if (mVendor != NULL)
            fncScanAndMergeDirLocked(pMergedInfo, ap, NULL, mVendor, dirName);
        if (mLocale != NULL && mVendor != NULL)
            fncScanAndMergeDirLocked(pMergedInfo, ap, mLocale, mVendor, dirName);
    }
}

/*
 * Recursively scan this directory and all subdirs.
 *
 * This is similar to scanAndMergeDir, but we don't remove the .EXCLUDE
 * files, and we prepend the extended partial path to the filenames.
 */
bool AssetManager::fncScanAndMergeDirLocked(
    SortedVector<AssetDir::FileInfo>* pMergedInfo,
    const asset_path& ap, const char* locale, const char* vendor,
    const char* dirName)
{
    SortedVector<AssetDir::FileInfo>* pContents;
    String8 partialPath;
    String8 fullPath;

    // XXX This is broken -- the filename cache needs to hold the base
    // asset path separately from its filename.
    
    partialPath = createPathNameLocked(ap, locale, vendor);
    if (dirName[0] != '\0') {
        partialPath.appendPath(dirName);
    }

    fullPath = partialPath;
    pContents = scanDirLocked(fullPath);
    if (pContents == NULL) {
        return false;       // directory did not exist
    }

    /*
     * Scan all subdirectories of the current dir, merging what we find
     * into "pMergedInfo".
     */
    for (int i = 0; i < (int) pContents->size(); i++) {
        if (pContents->itemAt(i).getFileType() == kFileTypeDirectory) {
            String8 subdir(dirName);
            subdir.appendPath(pContents->itemAt(i).getFileName());

            fncScanAndMergeDirLocked(pMergedInfo, ap, locale, vendor, subdir.string());
        }
    }

    /*
     * To be consistent, we want entries for the root directory.  If
     * we're the root, add one now.
     */
    if (dirName[0] == '\0') {
        AssetDir::FileInfo tmpInfo;

        tmpInfo.set(String8(""), kFileTypeDirectory);
        tmpInfo.setSourceName(createPathNameLocked(ap, locale, vendor));
        pContents->add(tmpInfo);
    }

    /*
     * We want to prepend the extended partial path to every entry in
     * "pContents".  It's the same value for each entry, so this will
     * not change the sorting order of the vector contents.
     */
    for (int i = 0; i < (int) pContents->size(); i++) {
        const AssetDir::FileInfo& info = pContents->itemAt(i);
        pContents->editItemAt(i).setFileName(partialPath.appendPathCopy(info.getFileName()));
    }

    mergeInfoLocked(pMergedInfo, pContents);
    delete pContents;
    return true;
}

/*
 * Trash the cache.
 */
void AssetManager::purgeFileNameCacheLocked(void)
{
    mCacheValid = false;
    mCache.clear();
}

/*
 * ===========================================================================
 *      AssetManager::SharedZip
 * ===========================================================================
 */


Mutex AssetManager::SharedZip::gLock;
DefaultKeyedVector<String8, wp<AssetManager::SharedZip> > AssetManager::SharedZip::gOpen;

AssetManager::SharedZip::SharedZip(const String8& path, time_t modWhen)
    : mPath(path), mZipFile(NULL), mModWhen(modWhen),
      mResourceTableAsset(NULL), mResourceTable(NULL)
{
    if (kIsDebug) {
        ALOGI("Creating SharedZip %p %s\n", this, (const char*)mPath);
    }
    ALOGV("+++ opening zip '%s'\n", mPath.string());
    mZipFile = ZipFileRO::open(mPath.string());
    if (mZipFile == NULL) {
        ALOGD("failed to open Zip archive '%s'\n", mPath.string());
    }
}

sp<AssetManager::SharedZip> AssetManager::SharedZip::get(const String8& path,
        bool createIfNotPresent)
{
    AutoMutex _l(gLock);
    time_t modWhen = getFileModDate(path);
    sp<SharedZip> zip = gOpen.valueFor(path).promote();
    if (zip != NULL && zip->mModWhen == modWhen) {
        return zip;
    }
    if (zip == NULL && !createIfNotPresent) {
        return NULL;
    }
    zip = new SharedZip(path, modWhen);
    gOpen.add(path, zip);
    return zip;

}

ZipFileRO* AssetManager::SharedZip::getZip()
{
    return mZipFile;
}

Asset* AssetManager::SharedZip::getResourceTableAsset()
{
    ALOGV("Getting from SharedZip %p resource asset %p\n", this, mResourceTableAsset);
    return mResourceTableAsset;
}

Asset* AssetManager::SharedZip::setResourceTableAsset(Asset* asset)
{
    {
        AutoMutex _l(gLock);
        if (mResourceTableAsset == NULL) {
            mResourceTableAsset = asset;
            // This is not thread safe the first time it is called, so
            // do it here with the global lock held.
            asset->getBuffer(true);
            return asset;
        }
    }
    delete asset;
    return mResourceTableAsset;
}

ResTable* AssetManager::SharedZip::getResourceTable()
{
    ALOGV("Getting from SharedZip %p resource table %p\n", this, mResourceTable);
    return mResourceTable;
}

ResTable* AssetManager::SharedZip::setResourceTable(ResTable* res)
{
    {
        AutoMutex _l(gLock);
        if (mResourceTable == NULL) {
            mResourceTable = res;
            return res;
        }
    }
    delete res;
    return mResourceTable;
}

bool AssetManager::SharedZip::isUpToDate()
{
    time_t modWhen = getFileModDate(mPath.string());
    return mModWhen == modWhen;
}

void AssetManager::SharedZip::addOverlay(const asset_path& ap)
{
    mOverlays.add(ap);
}

bool AssetManager::SharedZip::getOverlay(size_t idx, asset_path* out) const
{
    if (idx >= mOverlays.size()) {
        return false;
    }
    *out = mOverlays[idx];
    return true;
}

AssetManager::SharedZip::~SharedZip()
{
    if (kIsDebug) {
        ALOGI("Destroying SharedZip %p %s\n", this, (const char*)mPath);
    }
    if (mResourceTable != NULL) {
        delete mResourceTable;
    }
    if (mResourceTableAsset != NULL) {
        delete mResourceTableAsset;
    }
    if (mZipFile != NULL) {
        delete mZipFile;
        ALOGV("Closed '%s'\n", mPath.string());
    }
}

/*
 * ===========================================================================
 *      AssetManager::ZipSet
 * ===========================================================================
 */

/*
 * Constructor.
 */
AssetManager::ZipSet::ZipSet(void)
{
}

/*
 * Destructor.  Close any open archives.
 */
AssetManager::ZipSet::~ZipSet(void)
{
    size_t N = mZipFile.size();
    for (size_t i = 0; i < N; i++)
        closeZip(i);
}

/*
 * Close a Zip file and reset the entry.
 */
void AssetManager::ZipSet::closeZip(int idx)
{
    mZipFile.editItemAt(idx) = NULL;
}


/*
 * Retrieve the appropriate Zip file from the set.
 */
ZipFileRO* AssetManager::ZipSet::getZip(const String8& path)
{
    int idx = getIndex(path);
    sp<SharedZip> zip = mZipFile[idx];
    if (zip == NULL) {
        zip = SharedZip::get(path);
        mZipFile.editItemAt(idx) = zip;
    }
    return zip->getZip();
}

void AssetManager::ZipSet::closeZip(const String8& zip) {
    closeZip(getIndex(zip));
}

Asset* AssetManager::ZipSet::getZipResourceTableAsset(const String8& path)
{
    int idx = getIndex(path);
    sp<SharedZip> zip = mZipFile[idx];
    if (zip == NULL) {
        zip = SharedZip::get(path);
        mZipFile.editItemAt(idx) = zip;
    }
    return zip->getResourceTableAsset();
}

Asset* AssetManager::ZipSet::setZipResourceTableAsset(const String8& path,
                                                 Asset* asset)
{
    int idx = getIndex(path);
    sp<SharedZip> zip = mZipFile[idx];
    // doesn't make sense to call before previously accessing.
    return zip->setResourceTableAsset(asset);
}

ResTable* AssetManager::ZipSet::getZipResourceTable(const String8& path)
{
    int idx = getIndex(path);
    sp<SharedZip> zip = mZipFile[idx];
    if (zip == NULL) {
        zip = SharedZip::get(path);
        mZipFile.editItemAt(idx) = zip;
    }
    return zip->getResourceTable();
}

ResTable* AssetManager::ZipSet::setZipResourceTable(const String8& path,
                                                    ResTable* res)
{
    int idx = getIndex(path);
    sp<SharedZip> zip = mZipFile[idx];
    // doesn't make sense to call before previously accessing.
    return zip->setResourceTable(res);
}

/*
 * Generate the partial pathname for the specified archive.  The caller
 * gets to prepend the asset root directory.
 *
 * Returns something like "common/en-US-noogle.jar".
 */
/*static*/ String8 AssetManager::ZipSet::getPathName(const char* zipPath)
{
    return String8(zipPath);
}

bool AssetManager::ZipSet::isUpToDate()
{
    const size_t N = mZipFile.size();
    for (size_t i=0; i<N; i++) {
        if (mZipFile[i] != NULL && !mZipFile[i]->isUpToDate()) {
            return false;
        }
    }
    return true;
}

void AssetManager::ZipSet::addOverlay(const String8& path, const asset_path& overlay)
{
    int idx = getIndex(path);
    sp<SharedZip> zip = mZipFile[idx];
    zip->addOverlay(overlay);
}

bool AssetManager::ZipSet::getOverlay(const String8& path, size_t idx, asset_path* out) const
{
    sp<SharedZip> zip = SharedZip::get(path, false);
    if (zip == NULL) {
        return false;
    }
    return zip->getOverlay(idx, out);
}

/*
 * Compute the zip file's index.
 *
 * "appName", "locale", and "vendor" should be set to NULL to indicate the
 * default directory.
 */
int AssetManager::ZipSet::getIndex(const String8& zip) const
{
    const size_t N = mZipPath.size();
    for (size_t i=0; i<N; i++) {
        if (mZipPath[i] == zip) {
            return i;
        }
    }

    mZipPath.add(zip);
    mZipFile.add(NULL);

    return mZipPath.size()-1;
}
