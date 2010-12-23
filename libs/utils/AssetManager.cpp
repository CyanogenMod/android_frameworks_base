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
//#define LOG_NDEBUG 0

#include <utils/AssetManager.h>
#include <utils/AssetDir.h>
#include <utils/Asset.h>
#include <utils/Atomic.h>
#include <utils/String8.h>
#include <utils/ResourceTypes.h>
#include <utils/String8.h>
#include <utils/ZipFileRO.h>
#include <utils/Log.h>
#include <utils/Timers.h>
#include <utils/threads.h>
#include <utils/FileLock.h>

#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <errno.h>
#include <assert.h>

#define REDIRECT_NOISY(x) //x

using namespace android;

/*
 * Names for default app, locale, and vendor.  We might want to change
 * these to be an actual locale, e.g. always use en-US as the default.
 */
static const char* kDefaultLocale = "default";
static const char* kDefaultVendor = "default";
static const char* kAssetsRoot = "assets";
static const char* kAppZipName = NULL; //"classes.jar";
static const char* kSystemAssets = "framework/framework-res.apk";

static const char* kExcludeExtension = ".EXCLUDE";

static const char* kThemeResCacheDir = "res-cache/";

static Asset* const kExcludedAsset = (Asset*) 0xd000000d;

static volatile int32_t gCount = 0;


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
	  mThemePackageName(NULL),
      mResources(NULL), mConfig(new ResTable_config),
      mCacheMode(cacheMode), mCacheValid(false)
{
    int count = android_atomic_inc(&gCount)+1;
    //LOGI("Creating AssetManager %p #%d\n", this, count);
    memset(mConfig, 0, sizeof(ResTable_config));
}

AssetManager::~AssetManager(void)
{
    int count = android_atomic_dec(&gCount);
    //LOGI("Destroying AssetManager in %p #%d\n", this, count);

    delete mConfig;
    delete mResources;

    // don't have a String class yet, so make sure we clean up
    delete[] mLocale;
    delete[] mVendor;

	if (mThemePackageName != NULL) {
		delete[] mThemePackageName;
	}
}

bool AssetManager::addAssetPath(const String8& path, void** cookie)
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
            LOGW("Asset path %s is neither a directory nor file (type=%d).",
                 path.string(), (int)ap.type);
            return false;
        }
    }

    // Skip if we have it already.
    for (size_t i=0; i<mAssetPaths.size(); i++) {
        if (mAssetPaths[i].path == ap.path) {
            if (cookie) {
                *cookie = (void*)(i+1);
            }
            return true;
        }
    }

    LOGV("In %p Asset %s path: %s", this,
         ap.type == kFileTypeDirectory ? "dir" : "zip", ap.path.string());

    mAssetPaths.add(ap);

    // new paths are always added at the end
    if (cookie) {
        *cookie = (void*)mAssetPaths.size();
    }

    return true;
}

bool AssetManager::addDefaultAssets()
{
    const char* root = getenv("ANDROID_ROOT");
    LOG_ALWAYS_FATAL_IF(root == NULL, "ANDROID_ROOT not set");

    String8 path(root);
    path.appendPath(kSystemAssets);

    return addAssetPath(path, NULL);
}

void* AssetManager::nextAssetPath(void* cookie) const
{
    AutoMutex _l(mLock);
    size_t next = ((size_t)cookie)+1;
    return next > mAssetPaths.size() ? NULL : (void*)next;
}

String8 AssetManager::getAssetPath(void* cookie) const
{
    AutoMutex _l(mLock);
    const size_t which = ((size_t)cookie)-1;
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

void AssetManager::setLocaleLocked(const char* locale)
{
    if (mLocale != NULL) {
        /* previously set, purge cached data */
        purgeFileNameCacheLocked();
        //mZipSet.purgeLocale();
        delete[] mLocale;
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
        char spec[9];
        spec[0] = config.language[0];
        spec[1] = config.language[1];
        if (config.country[0] != 0) {
            spec[2] = '_';
            spec[3] = config.country[0];
            spec[4] = config.country[1];
            spec[5] = 0;
        } else {
            spec[3] = 0;
        }
        setLocaleLocked(spec);
    } else {
        updateResourceParamsLocked();
    }
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
        LOGV("Looking for asset '%s' in '%s'\n",
                assetName.string(), mAssetPaths.itemAt(i).path.string());
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
Asset* AssetManager::openNonAsset(const char* fileName, AccessMode mode)
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
        LOGV("Looking for non-asset '%s' in '%s'\n", fileName, mAssetPaths.itemAt(i).path.string());
        Asset* pAsset = openNonAssetInPathLocked(
            fileName, mode, mAssetPaths.itemAt(i));
        if (pAsset != NULL) {
            return pAsset != kExcludedAsset ? pAsset : NULL;
        }
    }

    return NULL;
}

Asset* AssetManager::openNonAsset(void* cookie, const char* fileName, AccessMode mode)
{
    const size_t which = ((size_t)cookie)-1;

    AutoMutex _l(mLock);

    LOG_FATAL_IF(mAssetPaths.size() == 0, "No assets added to AssetManager");


    if (mCacheMode != CACHE_OFF && !mCacheValid)
        loadFileNameCacheLocked();

    if (which < mAssetPaths.size()) {
        LOGV("Looking for non-asset '%s' in '%s'\n", fileName,
                mAssetPaths.itemAt(which).path.string());
        Asset* pAsset = openNonAssetInPathLocked(
            fileName, mode, mAssetPaths.itemAt(which));
        if (pAsset != NULL) {
            return pAsset != kExcludedAsset ? pAsset : NULL;
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

static void createDirIfNecessary(const char* path, mode_t mode, struct stat *statbuf)
{
    if (lstat(path, statbuf) != 0) {
        if (mkdir(path, mode) != 0) {
            LOGE("mkdir(%s,%04o) failed: %s\n", path, (int)mode, strerror(errno));
        }
    }
}

static SharedBuffer* addToEntriesByTypeBuffer(SharedBuffer* buf, uint32_t from, uint32_t to)
{
    size_t currentSize = (buf != NULL) ? buf->size() : 0;

    int type = Res_GETTYPE(from)+1;
    int entry = Res_GETENTRY(from);

    size_t typeSize = (type+1) * sizeof(uint32_t*);
    unsigned int requestSize = roundUpPower2(typeSize);
    if (typeSize > currentSize) {
        unsigned int requestSize = roundUpPower2(typeSize);
        if (buf == NULL) {
            buf = SharedBuffer::alloc(requestSize);
        } else {
            buf = buf->editResize(requestSize);
        }
        memset((unsigned char*)buf->data()+currentSize, 0, requestSize-currentSize);
    }

    uint32_t** entriesByType = (uint32_t**)buf->data();
    uint32_t* entries = entriesByType[type];
    SharedBuffer* entriesBuf = (entries != NULL) ? SharedBuffer::bufferFromData(entries) : NULL;
    currentSize = (entriesBuf != NULL) ? entriesBuf->size() : 0;
    size_t entrySize = (entry+1) * sizeof(uint32_t);
    if (entrySize > currentSize) {
        unsigned int requestSize = roundUpPower2(entrySize);
        if (entriesBuf == NULL) {
            entriesBuf = SharedBuffer::alloc(requestSize);
        } else {
            entriesBuf = entriesBuf->editResize(requestSize);
        }
        memset((unsigned char*)entriesBuf->data()+currentSize, 0, requestSize-currentSize);
        entriesByType[type] = (uint32_t*)entriesBuf->data();
    }
    entries = (uint32_t*)entriesBuf->data();
    entries[entry] = to;

    return buf;
}

// TODO: Terrible, terrible I/O error handling here!
static bool writeRedirections(const char* redirPath, SharedBuffer* entriesByTypeBuf)
{
    REDIRECT_NOISY(LOGW("writing %s\n", redirPath));

    FILE* fp = fopen(redirPath, "w");
    if (!fp) {
        LOGE("fopen(%s,r) failed: %s\n", redirPath, strerror(errno));
        return false;
    }

    uint16_t version = 1;
    fwrite(&version, sizeof(version), 1, fp);

    uint16_t totalTypes = 0;
    size_t typeSize = (entriesByTypeBuf != NULL) ? entriesByTypeBuf->size() / sizeof(uint32_t*) : 0;
    uint32_t** entriesByType = (uint32_t**)entriesByTypeBuf->data();
    for (size_t i=0; i<typeSize; i++) {
        uint32_t* entries = entriesByType[i];
        if (entries != NULL) {
            totalTypes++;
        }
    }

    REDIRECT_NOISY(LOGW("writing %d total types\n", (int)totalTypes));
    fwrite(&totalTypes, sizeof(totalTypes), 1, fp);

    // Start offset for the first type table.
    uint32_t typeSectionOffset = 4 + (9 * totalTypes);

    for (size_t i=0; i<typeSize; i++) {
        uint32_t* entries = entriesByType[i];
        if (entries != NULL) {
            uint8_t type = i;
            fwrite(&type, sizeof(type), 1, fp);
            size_t entrySize = SharedBuffer::bufferFromData(entries)->size() / sizeof(uint32_t);
            size_t numEntries = 0;
            for (size_t j=0; j<entrySize; j++) {
                if (entries[j] != 0) {
                    numEntries++;
                }
            }
            REDIRECT_NOISY(LOGW("%d entries for type %d\n", (int)numEntries, (int)type));
            fwrite(&typeSectionOffset, sizeof(typeSectionOffset), 1, fp);
            uint32_t typeSectionLength = numEntries * 6;
            fwrite(&typeSectionLength, sizeof(typeSectionLength), 1, fp);
            typeSectionOffset += typeSectionLength;
        }
    }

    for (size_t i=0; i<typeSize; i++) {
        uint32_t* entries = entriesByType[i];
        if (entries != NULL) {
            REDIRECT_NOISY(LOGW("writing for type %d...\n", i));
            size_t entrySize = SharedBuffer::bufferFromData(entries)->size() / sizeof(uint32_t);
            for (size_t j=0; j<entrySize; j++) {
                uint32_t resID = entries[j];
                if (resID != 0) {
                    uint16_t entryIndex = j;
                    REDIRECT_NOISY(LOGW("writing 0x%04x => 0x%08x\n", entryIndex, resID));
                    fwrite(&entryIndex, sizeof(entryIndex), 1, fp);
                    fwrite(&resID, sizeof(resID), 1, fp);
                }
            }
            SharedBuffer::bufferFromData(entries)->release();
        }
    }

    if (entriesByTypeBuf != NULL) {
        entriesByTypeBuf->release();
    }

    fclose(fp);

    REDIRECT_NOISY(LOGW("written...\n"));

    return true;
}

// Crude, lame brute force way to generate the initial framework redirections
// for testing.  This code should be generalized and follow a much better OO
// structure.
static SharedBuffer* generateFrameworkRedirections(SharedBuffer* entriesByTypeBuf, ResTable* rt,
    const char* themePackageName, const char* redirPath)
{
    REDIRECT_NOISY(LOGW("generateFrameworkRedirections: themePackageName=%s\n", themePackageName));

    // HACK HACK HACK
    if (strcmp(themePackageName, "com.tmobile.theme.Androidian") != 0) {
        LOGW("EEP, UNEXPECTED PACKAGE!");
        return entriesByTypeBuf;
    }

    rt->lock();

    // Load up a bag for the user-supplied theme.
    String16 type("style");
    String16 name("Androidian");
    String16 package(themePackageName);
    uint32_t ident = rt->identifierForName(name.string(), name.size(), type.string(), type.size(),
        package.string(), package.size());
    if (ident == 0) {
        LOGW("unable to locate theme identifier %s:%s/%s\n", String8(package).string(),
            String8(type).string(), String8(name).string());
        rt->unlock();
        return entriesByTypeBuf;
    }

    const ResTable::bag_entry* themeEnt = NULL;
    ssize_t N = rt->getBagLocked(ident, &themeEnt);
    const ResTable::bag_entry* endThemeEnt = themeEnt + N;

    // ...and a bag for the framework default.
    const ResTable::bag_entry* frameworkEnt = NULL;
    N = rt->getBagLocked(0x01030005, &frameworkEnt);
    const ResTable::bag_entry* endFrameworkEnt = frameworkEnt + N;

    // The first entry should be for the theme itself.
    entriesByTypeBuf = addToEntriesByTypeBuffer(entriesByTypeBuf, 0x01030005, ident);

    // Now compare them and infer resource redirections for attributes that
    // remap to different styles.  This works by essentially lining up all the
    // sorted attributes from each theme and detected TYPE_REFERENCE entries
    // that point to different resources.  When we find such a mismatch, we'll
    // create a resource redirection from the original framework resource ID to
    // the one in the theme.  This lets us do things like automatically find
    // redirections for @android:style/Widget.Button by looking at how the
    // theme overrides the android:attr/buttonStyle attribute.
    REDIRECT_NOISY(LOGW("delta between 0x01030005 and 0x%08x:\n", ident));
    for (; frameworkEnt < endFrameworkEnt; frameworkEnt++) {
        if (frameworkEnt->map.value.dataType != Res_value::TYPE_REFERENCE) {
            continue;
        }

        uint32_t curIdent = frameworkEnt->map.name.ident;

        // Walk along the theme entry looking for a match.
        while (themeEnt < endThemeEnt && curIdent > themeEnt->map.name.ident) {
            themeEnt++;
        }
        // Match found, compare the references.
        if (themeEnt < endThemeEnt && curIdent == themeEnt->map.name.ident) {
            if (themeEnt->map.value.data != frameworkEnt->map.value.data) {
                entriesByTypeBuf = addToEntriesByTypeBuffer(entriesByTypeBuf,
                    frameworkEnt->map.value.data, themeEnt->map.value.data);
                REDIRECT_NOISY(LOGW("   generated mapping from 0x%08x => 0x%08x (by attr 0x%08x)\n", frameworkEnt->map.value.data,
                    themeEnt->map.value.data, curIdent));
            }
            themeEnt++;
        }

        // Exhausted the theme, bail early.
        if (themeEnt >= endThemeEnt) {
            break;
        }
    }

    rt->unlock();

    return entriesByTypeBuf;
}

static SharedBuffer* parseRedirections(SharedBuffer* buf, ResTable* rt,
    ResXMLTree& xml, String16& themePackage, String16& resPackage)
{
    ResXMLTree::event_code_t eventType = xml.getEventType();
    REDIRECT_NOISY(LOGW("initial eventType=%d\n", (int)eventType));
    size_t len;
    while (eventType != ResXMLTree::END_DOCUMENT) {
        if (eventType == ResXMLTree::START_TAG) {
            String8 outerTag(xml.getElementName(&len));
            if (outerTag == "resource-redirections") {
                REDIRECT_NOISY(LOGW("got resource-redirections tag\n"));
                xml.next();
                while ((eventType = xml.getEventType()) != ResXMLTree::END_TAG) {
                    if (eventType == ResXMLTree::START_TAG) {
                        String8 itemTag(xml.getElementName(&len));
                        if (itemTag == "item") {
                            REDIRECT_NOISY(LOGW("got item tag\n"));
                            ssize_t nameIdx = xml.indexOfAttribute(NULL, "name");
                            size_t fromLen;
                            const char16_t* fromName = NULL;
                            size_t toLen;
                            const char16_t* toName = NULL;
                            if (nameIdx >= 0) {
                                fromName = xml.getAttributeStringValue(nameIdx, &fromLen);
                                REDIRECT_NOISY(LOGW("  got from %s\n", String8(fromName).string()));
                            }
                            if (xml.next() == ResXMLTree::TEXT) {
                                toName = xml.getText(&toLen);
                                REDIRECT_NOISY(LOGW("  got to %s\n", String8(toName).string()));
                                xml.next();
                            }
                            if (toName != NULL && fromName != NULL) {
                                // fromName should look "drawable/foo", so we'll
                                // let identifierForName parse that part of it, but
                                // make sure to provide the background ourselves.
                                // TODO: we should check that the package isn't
                                // already in the string and error out if it is...
                                uint32_t fromIdent = rt->identifierForName(fromName, fromLen, NULL, 0,
                                    resPackage.string(), resPackage.size());
                                if (fromIdent == 0) {
                                    LOGW("Failed to locate identifier for resource %s:%s\n",
                                        String8(fromName, fromLen).string(), String8(resPackage).string());
                                } else {
                                    uint32_t toIdent = rt->identifierForName(toName, toLen, NULL, 0,
                                        themePackage.string(), themePackage.size());
                                    if (toIdent == 0) {
                                        LOGW("Failed to locate identifier for resource %s:%s\n",
                                            String8(toName, toLen).string(), String8(themePackage).string());
                                    } else {
                                        REDIRECT_NOISY(LOGW("adding fromIdent=0x%08x to toIdent=0x%08x\n", fromIdent, toIdent));
                                        buf = addToEntriesByTypeBuffer(buf, fromIdent, toIdent);
                                    }
                                }
                            }
                        } else {
                            REDIRECT_NOISY(LOGW("unexpected tag %s\n", itemTag.string()));
                        }
                    } else if (eventType == ResXMLTree::END_DOCUMENT) {
                        return buf;
                    }
                    xml.next();
                }
            }
        }

        eventType = xml.next();
    }

    return buf;
}

// Generate redirections from XML meta data.  This code should be moved into
// the Java space at some point, generated by a special service.
SharedBuffer* AssetManager::generateRedirections(SharedBuffer* entriesByTypeBuf,
    ResTable* rt, const char* themePackageName,
    const char16_t* resPackageName)
{
    REDIRECT_NOISY(LOGW("generateRedirections: themePackageName=%s; resPackageName=%s\n",
        themePackageName, String8(resPackageName).string()));

    String16 type("xml");
    String16 name(resPackageName);
    name.replaceAll('.', '_');
    String16 package(themePackageName);
    uint32_t xmlIdent = rt->identifierForName(name.string(), name.size(), type.string(), type.size(),
        package.string(), package.size());
    REDIRECT_NOISY(LOGW("xmlIdent=0x%08x from %s:%s/%s\n", xmlIdent,
        String8(package).string(), String8(type).string(), String8(name).string()));
    if (xmlIdent != 0) {
        // All this junk is being simulated from the Java side implementation.
        // This is very clumsy and poorly thought through/tested.  This code
        // will eventually be merged into the Java layer.
        Res_value value;
        ssize_t block = rt->getResource(xmlIdent, &value);
        block = rt->resolveReference(&value, block, &xmlIdent);
        if (block < 0 || value.dataType != Res_value::TYPE_STRING) {
            LOGE("Bad redirection XML resource #0x%08x\n", xmlIdent);
        } else {
            size_t len;
            const char16_t* str = rt->valueToString(&value, block, NULL, &len);
            void* cookie = rt->getTableCookie(block);
            const size_t whichAssetPath = ((size_t)cookie)-1;
            Asset* asset = openNonAssetInPathLocked(
                String8(str).string(), Asset::ACCESS_BUFFER,
                mAssetPaths.itemAt(whichAssetPath));
            if (asset == NULL || asset == kExcludedAsset) {
                LOGE("XML resource %s not found in package\n", String8(str).string());
            } else {
                ResXMLTree xml;
                status_t err = xml.setTo(asset->getBuffer(true), asset->getLength());

                xml.restart();

                String16 resPackage(resPackageName);
                entriesByTypeBuf = parseRedirections(entriesByTypeBuf, rt, xml,
                    package, resPackage);

                xml.uninit();

                asset->close();
                delete asset;
            }
        }
    }

    return entriesByTypeBuf;
}

bool AssetManager::generateAndWriteRedirections(ResTable* rt,
    const char* themePackageName, const char16_t* resPackageName,
    const char* redirPath, bool isFramework) const
{
    // FIXME: the const is a lie!!!
    AssetManager* am = (AssetManager*)this;

    SharedBuffer* buf = NULL;
    if (isFramework) {
        // Special framework theme heuristic...
        buf = generateFrameworkRedirections(buf, rt, themePackageName, redirPath);
    }
    // Generate redirections from the package XML.
    buf = am->generateRedirections(buf, rt, themePackageName, resPackageName);

    return writeRedirections(redirPath, buf);
}

void AssetManager::loadRedirectionMappings(ResTable* rt) const
{
    rt->clearRedirections();

    if (mThemePackageName != NULL) {
        const char* data = getenv("ANDROID_DATA");
        LOG_ALWAYS_FATAL_IF(data == NULL, "ANDROID_DATA not set");

        // Create the basic directory structure on demand.
        struct stat statbuf;
        String8 basePath(data);
        basePath.appendPath(kThemeResCacheDir);
        createDirIfNecessary(basePath.string(), 0777, &statbuf);
        basePath.appendPath(mThemePackageName);
        createDirIfNecessary(basePath.string(), 0777, &statbuf);

        String8 themeDirLockPath(basePath);
        themeDirLockPath.append(".lck");

        FileLock* themeDirLock = new FileLock(themeDirLockPath.string());
        themeDirLock->lock();

        // Load (generating if necessary) the cache files for each installed
        // package in this ResTable, excluding the framework's "android"
        // package.
        bool hasFramework = false;
        const size_t N = rt->getBasePackageCount();
        for (size_t i=0; i<N; i++) {
            uint32_t packageId = rt->getBasePackageId(i);

            // No need to regenerate the 0x01 framework resources.
            if (packageId == 0x7f) {
                String8 redirPath(basePath);
                const char16_t* resPackageName = rt->getBasePackageName(i);
                redirPath.appendPath(String8(resPackageName));

                if (lstat(redirPath.string(), &statbuf) != 0) {
                    generateAndWriteRedirections(rt, mThemePackageName,
                        resPackageName, redirPath.string(), false);
                }

                rt->addRedirections(packageId, redirPath.string());
            } else if (packageId == 0x01) {
                hasFramework = true;
            }
        }

        // Handle the "android" package space as a special case using some
        // fancy heuristics.
        if (hasFramework) {
            String8 frameworkRedirPath(basePath);
            frameworkRedirPath.appendPath("android");

            if (lstat(frameworkRedirPath.string(), &statbuf) != 0) {
                generateAndWriteRedirections(rt, mThemePackageName,
                    String16("android").string(),
                    frameworkRedirPath.string(), true);
            }

            rt->addRedirections(0x01, frameworkRedirPath.string());
        }

        themeDirLock->unlock();
    }
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

    if (mCacheMode != CACHE_OFF && !mCacheValid)
        const_cast<AssetManager*>(this)->loadFileNameCacheLocked();

    mResources = rt = new ResTable();

    if (rt) {
        const size_t N = mAssetPaths.size();
        for (size_t i=0; i<N; i++) {
            const asset_path& ap = mAssetPaths.itemAt(i);
            updateResTableFromAssetPath(rt, ap, (void*)(i+1));
        }
        loadRedirectionMappings(rt);
    }

    if (required && !rt) LOGW("Unable to find resources file resources.arsc");
    if (!rt) {
        mResources = rt = new ResTable();
    }

    return rt;
}

void AssetManager::updateResTableFromAssetPath(ResTable *rt, const asset_path& ap, void *cookie) const
{
    Asset* ass = NULL;
    ResTable* sharedRes = NULL;
    bool shared = true;
    size_t cookiePos = (size_t)cookie;
    LOGV("Looking for resource asset in '%s'\n", ap.path.string());
    if (ap.type != kFileTypeDirectory) {
        if (cookiePos == 1) {
            // The first item is typically the framework resources,
            // which we want to avoid parsing every time.
            sharedRes = const_cast<AssetManager*>(this)->
                mZipSet.getZipResourceTable(ap.path);
        }
        if (sharedRes == NULL) {
            ass = const_cast<AssetManager*>(this)->
                mZipSet.getZipResourceTableAsset(ap.path);
            if (ass == NULL) {
                LOGV("loading resource table %s\n", ap.path.string());
                ass = const_cast<AssetManager*>(this)->
                    openNonAssetInPathLocked("resources.arsc",
                        Asset::ACCESS_BUFFER,
                        ap);
                if (ass != NULL && ass != kExcludedAsset) {
                    ass = const_cast<AssetManager*>(this)->
                        mZipSet.setZipResourceTableAsset(ap.path, ass);
                }
            }

            if (cookiePos == 0 && ass != NULL) {
                // If this is the first resource table in the asset
                // manager, then we are going to cache it so that we
                // can quickly copy it out for others.
                LOGV("Creating shared resources for %s", ap.path.string());
                sharedRes = new ResTable();
                sharedRes->add(ass, cookie, false);
                sharedRes = const_cast<AssetManager*>(this)->
                    mZipSet.setZipResourceTable(ap.path, sharedRes);
            }
        }
    } else {
        LOGV("loading resource table %s\n", ap.path.string());
        Asset* ass = const_cast<AssetManager*>(this)->
            openNonAssetInPathLocked("resources.arsc",
                Asset::ACCESS_BUFFER,
                ap);
        shared = false;
    }
    if ((ass != NULL || sharedRes != NULL) && ass != kExcludedAsset) {
        updateResourceParamsLocked();
        LOGV("Installing resource asset %p in to table %p\n", ass, mResources);
        if (sharedRes != NULL) {
            LOGV("Copying existing resources for %s", ap.path.string());
            rt->add(sharedRes);
        } else {
            LOGV("Parsing resources for %s", ap.path.string());
            rt->add(ass, cookie, !shared);
        }

        if (!shared) {
            delete ass;
        }
    }
}

void AssetManager::updateResourceParamsLocked() const
{
    ResTable* res = mResources;
    if (!res) {
        return;
    }

    size_t llen = mLocale ? strlen(mLocale) : 0;
    mConfig->language[0] = 0;
    mConfig->language[1] = 0;
    mConfig->country[0] = 0;
    mConfig->country[1] = 0;
    if (llen >= 2) {
        mConfig->language[0] = mLocale[0];
        mConfig->language[1] = mLocale[1];
    }
    if (llen >= 5) {
        mConfig->country[0] = mLocale[3];
        mConfig->country[1] = mLocale[4];
    }
    mConfig->size = sizeof(*mConfig);

    res->setParameters(mConfig);
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
}

/*
 * Open a non-asset file as if it were an asset, searching for it in the
 * specified app.
 *
 * Pass in a NULL values for "appName" if the common app directory should
 * be used.
 */
Asset* AssetManager::openNonAssetInPathLocked(const char* fileName, AccessMode mode,
    const asset_path& ap)
{
    Asset* pAsset = NULL;

    /* look at the filesystem on disk */
    if (ap.type == kFileTypeDirectory) {
        String8 path(ap.path);
        path.appendPath(fileName);

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

        /* check the appropriate Zip file */
        ZipFileRO* pZip;
        ZipEntryRO entry;

        pZip = getZipFileLocked(ap);
        if (pZip != NULL) {
            //printf("GOT zip, checking NA '%s'\n", (const char*) path);
            entry = pZip->findEntryByName(path.string());
            if (entry != NULL) {
                //printf("FOUND NA in Zip file for %s\n", appName ? appName : kAppCommon);
                pAsset = openAssetFromZipLocked(pZip, entry, mode, path);
            }
        }

        if (pAsset != NULL) {
            /* create a "source" name, for debug/display */
            pAsset->setAssetSource(
                    createZipSourceNameLocked(ZipSet::getPathName(ap.path.string()), String8(""),
                                                String8(fileName)));
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
                    LOGD("Expected file not found: '%s'\n", path.string());
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
        ZipFileRO* pZip;
        ZipEntryRO entry;

        pZip = getZipFileLocked(ap);
        if (pZip != NULL) {
            //printf("GOT zip, checking '%s'\n", (const char*) path);
            entry = pZip->findEntryByName(path.string());
            if (entry != NULL) {
                //printf("FOUND in Zip file for %s/%s-%s\n",
                //    appName, locale, vendor);
                pAsset = openAssetFromZipLocked(pZip, entry, mode, path);
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
    LOGV("getZipFileLocked() in %p\n", this);

    return mZipSet.getZip(ap.path);
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
    int method;
    long uncompressedLen;

    //printf("USING Zip '%s'\n", pEntry->getFileName());

    //pZipFile->getEntryInfo(entry, &method, &uncompressedLen, &compressedLen,
    //    &offset);
    if (!pZipFile->getEntryInfo(entry, &method, &uncompressedLen, NULL, NULL,
            NULL, NULL))
    {
        LOGW("getEntryInfo failed\n");
        return NULL;
    }

    FileMap* dataMap = pZipFile->createEntryFileMap(entry);
    if (dataMap == NULL) {
        LOGW("create map from entry failed\n");
        return NULL;
    }

    if (method == ZipFileRO::kCompressStored) {
        pAsset = Asset::createFromUncompressedMap(dataMap, mode);
        LOGV("Opened uncompressed entry %s in zip %s mode %d: %p", entryName.string(),
                dataMap->getFileName(), mode, pAsset);
    } else {
        pAsset = Asset::createFromCompressedMap(dataMap, method,
            uncompressedLen, mode);
        LOGV("Opened compressed entry %s in zip %s mode %d: %p", entryName.string(),
                dataMap->getFileName(), mode, pAsset);
    }
    if (pAsset == NULL) {
        /* unexpected */
        LOGW("create from segment failed\n");
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
        if (ap.type == kFileTypeRegular) {
            LOGV("Adding directory %s from zip %s", dirName, ap.path.string());
            scanAndMergeZipLocked(pMergedInfo, ap, kAssetsRoot, dirName);
        } else {
            LOGV("Adding directory %s from dir %s", dirName, ap.path.string());
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
AssetDir* AssetManager::openNonAssetDir(void* cookie, const char* dirName)
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

    const size_t which = ((size_t)cookie)-1;

    if (which < mAssetPaths.size()) {
        const asset_path& ap = mAssetPaths.itemAt(which);
        if (ap.type == kFileTypeRegular) {
            LOGV("Adding directory %s from zip %s", dirName, ap.path.string());
            scanAndMergeZipLocked(pMergedInfo, ap, NULL, dirName);
        } else {
            LOGV("Adding directory %s from dir %s", dirName, ap.path.string());
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
                LOGV("Excluding '%s' [%s]\n",
                    pMergedInfo->itemAt(matchIdx).getFileName().string(),
                    pMergedInfo->itemAt(matchIdx).getSourceName().string());
                pMergedInfo->removeAt(matchIdx);
            } else {
                //printf("+++ no match on '%s'\n", (const char*) match);
            }

            LOGD("HEY: size=%d removing %d\n", (int)pContents->size(), i);
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

    LOGV("Scanning dir '%s'\n", path.string());

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
        LOGW("Failure opening zip %s\n", ap.path.string());
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
    for (int i = 0; i < pZip->getNumEntries(); i++) {
        ZipEntryRO entry;
        char nameBuf[256];

        entry = pZip->findEntryByIndex(i);
        if (pZip->getEntryFileName(entry, nameBuf, sizeof(nameBuf)) != 0) {
            // TODO: fix this if we expect to have long names
            LOGE("ARGH: name too long?\n");
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
    LOGD("Cache scan took %.3fms\n",
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
    //LOGI("Creating SharedZip %p %s\n", this, (const char*)mPath);
    mZipFile = new ZipFileRO;
    LOGV("+++ opening zip '%s'\n", mPath.string());
    if (mZipFile->open(mPath.string()) != NO_ERROR) {
        LOGD("failed to open Zip archive '%s'\n", mPath.string());
        delete mZipFile;
        mZipFile = NULL;
    }
}

sp<AssetManager::SharedZip> AssetManager::SharedZip::get(const String8& path)
{
    AutoMutex _l(gLock);
    time_t modWhen = getFileModDate(path);
    sp<SharedZip> zip = gOpen.valueFor(path).promote();
    if (zip != NULL && zip->mModWhen == modWhen) {
        return zip;
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
    LOGV("Getting from SharedZip %p resource asset %p\n", this, mResourceTableAsset);
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
    LOGV("Getting from SharedZip %p resource table %p\n", this, mResourceTable);
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

AssetManager::SharedZip::~SharedZip()
{
    //LOGI("Destroying SharedZip %p %s\n", this, (const char*)mPath);
    if (mResourceTable != NULL) {
        delete mResourceTable;
    }
    if (mResourceTableAsset != NULL) {
        delete mResourceTableAsset;
    }
    if (mZipFile != NULL) {
        delete mZipFile;
        LOGV("Closed '%s'\n", mPath.string());
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

/*
 * Set the currently applied theme package name.
 *
 * This information is used when constructing the ResTable's resource
 * redirection map.
 */
void AssetManager::setThemePackageName(const char* packageName)
{
    if (mThemePackageName != NULL) {
        delete[] mThemePackageName;
    }
    mThemePackageName = strdupNew(packageName);
}

const char* AssetManager::getThemePackageName()
{
    return mThemePackageName;
}

bool AssetManager::updateWithAssetPath(const String8& path, void** cookie)
{
    bool res = addAssetPath(path, cookie);
    ResTable* rt = mResources;
    if (res && rt != NULL && ((size_t)*cookie == mAssetPaths.size())) {
        AutoMutex _l(mLock);
        const asset_path& ap = mAssetPaths.itemAt((size_t)*cookie - 1);
        updateResTableFromAssetPath(rt, ap, *cookie);
        loadRedirectionMappings(rt);
    }
    return res;
}

bool AssetManager::removeAssetPath(const String8 &packageName, void* cookie)
{
    AutoMutex _l(mLock);

    ResTable* rt = mResources;
    if (rt == NULL) {
        LOGV("ResTable must not be NULL");
        return false;
    }

    rt->clearRedirections();
    rt->removeAssetsByCookie(packageName, (void *)cookie);

    return true;
}

void AssetManager::dumpRes()
{
    ResTable* rt = mResources;
    if (rt == NULL) {
        fprintf(stderr, "ResTable must not be NULL");
        return;
    }
    rt->dump();
}
