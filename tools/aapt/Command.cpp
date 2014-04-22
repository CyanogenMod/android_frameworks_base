//
// Copyright 2006 The Android Open Source Project
//
// Android Asset Packaging Tool main entry point.
//
#include "Main.h"
#include "Bundle.h"
#include "ResourceFilter.h"
#include "ResourceTable.h"
#include "Images.h"
#include "XMLNode.h"

#include <utils/Log.h>
#include <utils/threads.h>
#include <utils/List.h>
#include <utils/Errors.h>

#include <fcntl.h>
#include <errno.h>

using namespace android;

/*
 * Show version info.  All the cool kids do it.
 */
int doVersion(Bundle* bundle)
{
    if (bundle->getFileSpecCount() != 0)
        printf("(ignoring extra arguments)\n");
    printf("Android Asset Packaging Tool, v0.2\n");

    return 0;
}


/*
 * Open the file read only.  The call fails if the file doesn't exist.
 *
 * Returns NULL on failure.
 */
ZipFile* openReadOnly(const char* fileName)
{
    ZipFile* zip;
    status_t result;

    zip = new ZipFile;
    result = zip->open(fileName, ZipFile::kOpenReadOnly);
    if (result != NO_ERROR) {
        if (result == NAME_NOT_FOUND)
            fprintf(stderr, "ERROR: '%s' not found\n", fileName);
        else if (result == PERMISSION_DENIED)
            fprintf(stderr, "ERROR: '%s' access denied\n", fileName);
        else
            fprintf(stderr, "ERROR: failed opening '%s' as Zip file\n",
                fileName);
        delete zip;
        return NULL;
    }

    return zip;
}

/*
 * Open the file read-write.  The file will be created if it doesn't
 * already exist and "okayToCreate" is set.
 *
 * Returns NULL on failure.
 */
ZipFile* openReadWrite(const char* fileName, bool okayToCreate)
{
    ZipFile* zip = NULL;
    status_t result;
    int flags;

    flags = ZipFile::kOpenReadWrite;
    if (okayToCreate)
        flags |= ZipFile::kOpenCreate;

    zip = new ZipFile;
    result = zip->open(fileName, flags);
    if (result != NO_ERROR) {
        delete zip;
        zip = NULL;
        goto bail;
    }

bail:
    return zip;
}


/*
 * Return a short string describing the compression method.
 */
const char* compressionName(int method)
{
    if (method == ZipEntry::kCompressStored)
        return "Stored";
    else if (method == ZipEntry::kCompressDeflated)
        return "Deflated";
    else
        return "Unknown";
}

/*
 * Return the percent reduction in size (0% == no compression).
 */
int calcPercent(long uncompressedLen, long compressedLen)
{
    if (!uncompressedLen)
        return 0;
    else
        return (int) (100.0 - (compressedLen * 100.0) / uncompressedLen + 0.5);
}

/*
 * Handle the "list" command, which can be a simple file dump or
 * a verbose listing.
 *
 * The verbose listing closely matches the output of the Info-ZIP "unzip"
 * command.
 */
int doList(Bundle* bundle)
{
    int result = 1;
    ZipFile* zip = NULL;
    const ZipEntry* entry;
    long totalUncLen, totalCompLen;
    const char* zipFileName;

    if (bundle->getFileSpecCount() != 1) {
        fprintf(stderr, "ERROR: specify zip file name (only)\n");
        goto bail;
    }
    zipFileName = bundle->getFileSpecEntry(0);

    zip = openReadOnly(zipFileName);
    if (zip == NULL)
        goto bail;

    int count, i;

    if (bundle->getVerbose()) {
        printf("Archive:  %s\n", zipFileName);
        printf(
            " Length   Method    Size  Ratio   Offset      Date  Time  CRC-32    Name\n");
        printf(
            "--------  ------  ------- -----  -------      ----  ----  ------    ----\n");
    }

    totalUncLen = totalCompLen = 0;

    count = zip->getNumEntries();
    for (i = 0; i < count; i++) {
        entry = zip->getEntryByIndex(i);
        if (bundle->getVerbose()) {
            char dateBuf[32];
            time_t when;

            when = entry->getModWhen();
            strftime(dateBuf, sizeof(dateBuf), "%m-%d-%y %H:%M",
                localtime(&when));

            printf("%8ld  %-7.7s %7ld %3d%%  %8zd  %s  %08lx  %s\n",
                (long) entry->getUncompressedLen(),
                compressionName(entry->getCompressionMethod()),
                (long) entry->getCompressedLen(),
                calcPercent(entry->getUncompressedLen(),
                            entry->getCompressedLen()),
                (size_t) entry->getLFHOffset(),
                dateBuf,
                entry->getCRC32(),
                entry->getFileName());
        } else {
            printf("%s\n", entry->getFileName());
        }

        totalUncLen += entry->getUncompressedLen();
        totalCompLen += entry->getCompressedLen();
    }

    if (bundle->getVerbose()) {
        printf(
        "--------          -------  ---                            -------\n");
        printf("%8ld          %7ld  %2d%%                            %d files\n",
            totalUncLen,
            totalCompLen,
            calcPercent(totalUncLen, totalCompLen),
            zip->getNumEntries());
    }

    if (bundle->getAndroidList()) {
        AssetManager assets;
        if (!assets.addAssetPath(String8(zipFileName), NULL)) {
            fprintf(stderr, "ERROR: list -a failed because assets could not be loaded\n");
            goto bail;
        }

        const ResTable& res = assets.getResources(false);
        if (&res == NULL) {
            printf("\nNo resource table found.\n");
        } else {
#ifndef HAVE_ANDROID_OS
            printf("\nResource table:\n");
            res.print(false);
#endif
        }

        Asset* manifestAsset = assets.openNonAsset("AndroidManifest.xml",
                                                   Asset::ACCESS_BUFFER);
        if (manifestAsset == NULL) {
            printf("\nNo AndroidManifest.xml found.\n");
        } else {
            printf("\nAndroid manifest:\n");
            ResXMLTree tree;
            tree.setTo(manifestAsset->getBuffer(true),
                       manifestAsset->getLength());
            printXMLBlock(&tree);
        }
        delete manifestAsset;
    }

    result = 0;

bail:
    delete zip;
    return result;
}

static ssize_t indexOfAttribute(const ResXMLTree& tree, uint32_t attrRes)
{
    size_t N = tree.getAttributeCount();
    for (size_t i=0; i<N; i++) {
        if (tree.getAttributeNameResID(i) == attrRes) {
            return (ssize_t)i;
        }
    }
    return -1;
}

String8 getAttribute(const ResXMLTree& tree, const char* ns,
                            const char* attr, String8* outError)
{
    ssize_t idx = tree.indexOfAttribute(ns, attr);
    if (idx < 0) {
        return String8();
    }
    Res_value value;
    if (tree.getAttributeValue(idx, &value) != NO_ERROR) {
        if (value.dataType != Res_value::TYPE_STRING) {
            if (outError != NULL) *outError = "attribute is not a string value";
            return String8();
        }
    }
    size_t len;
    const uint16_t* str = tree.getAttributeStringValue(idx, &len);
    return str ? String8(str, len) : String8();
}

static String8 getAttribute(const ResXMLTree& tree, uint32_t attrRes, String8* outError)
{
    ssize_t idx = indexOfAttribute(tree, attrRes);
    if (idx < 0) {
        return String8();
    }
    Res_value value;
    if (tree.getAttributeValue(idx, &value) != NO_ERROR) {
        if (value.dataType != Res_value::TYPE_STRING) {
            if (outError != NULL) *outError = "attribute is not a string value";
            return String8();
        }
    }
    size_t len;
    const uint16_t* str = tree.getAttributeStringValue(idx, &len);
    return str ? String8(str, len) : String8();
}

static int32_t getIntegerAttribute(const ResXMLTree& tree, uint32_t attrRes,
        String8* outError, int32_t defValue = -1)
{
    ssize_t idx = indexOfAttribute(tree, attrRes);
    if (idx < 0) {
        return defValue;
    }
    Res_value value;
    if (tree.getAttributeValue(idx, &value) != NO_ERROR) {
        if (value.dataType < Res_value::TYPE_FIRST_INT
                || value.dataType > Res_value::TYPE_LAST_INT) {
            if (outError != NULL) *outError = "attribute is not an integer value";
            return defValue;
        }
    }
    return value.data;
}

static int32_t getResolvedIntegerAttribute(const ResTable* resTable, const ResXMLTree& tree,
        uint32_t attrRes, String8* outError, int32_t defValue = -1)
{
    ssize_t idx = indexOfAttribute(tree, attrRes);
    if (idx < 0) {
        return defValue;
    }
    Res_value value;
    if (tree.getAttributeValue(idx, &value) != NO_ERROR) {
        if (value.dataType == Res_value::TYPE_REFERENCE) {
            resTable->resolveReference(&value, 0);
        }
        if (value.dataType < Res_value::TYPE_FIRST_INT
                || value.dataType > Res_value::TYPE_LAST_INT) {
            if (outError != NULL) *outError = "attribute is not an integer value";
            return defValue;
        }
    }
    return value.data;
}

static String8 getResolvedAttribute(const ResTable* resTable, const ResXMLTree& tree,
        uint32_t attrRes, String8* outError)
{
    ssize_t idx = indexOfAttribute(tree, attrRes);
    if (idx < 0) {
        return String8();
    }
    Res_value value;
    if (tree.getAttributeValue(idx, &value) != NO_ERROR) {
        if (value.dataType == Res_value::TYPE_STRING) {
            size_t len;
            const uint16_t* str = tree.getAttributeStringValue(idx, &len);
            return str ? String8(str, len) : String8();
        }
        resTable->resolveReference(&value, 0);
        if (value.dataType != Res_value::TYPE_STRING) {
            if (outError != NULL) *outError = "attribute is not a string value";
            return String8();
        }
    }
    size_t len;
    const Res_value* value2 = &value;
    const char16_t* str = const_cast<ResTable*>(resTable)->valueToString(value2, 0, NULL, &len);
    return str ? String8(str, len) : String8();
}

// These are attribute resource constants for the platform, as found
// in android.R.attr
enum {
    LABEL_ATTR = 0x01010001,
    ICON_ATTR = 0x01010002,
    NAME_ATTR = 0x01010003,
    PERMISSION_ATTR = 0x01010006,
    RESOURCE_ATTR = 0x01010025,
    DEBUGGABLE_ATTR = 0x0101000f,
    VERSION_CODE_ATTR = 0x0101021b,
    VERSION_NAME_ATTR = 0x0101021c,
    SCREEN_ORIENTATION_ATTR = 0x0101001e,
    MIN_SDK_VERSION_ATTR = 0x0101020c,
    MAX_SDK_VERSION_ATTR = 0x01010271,
    REQ_TOUCH_SCREEN_ATTR = 0x01010227,
    REQ_KEYBOARD_TYPE_ATTR = 0x01010228,
    REQ_HARD_KEYBOARD_ATTR = 0x01010229,
    REQ_NAVIGATION_ATTR = 0x0101022a,
    REQ_FIVE_WAY_NAV_ATTR = 0x01010232,
    TARGET_SDK_VERSION_ATTR = 0x01010270,
    TEST_ONLY_ATTR = 0x01010272,
    ANY_DENSITY_ATTR = 0x0101026c,
    GL_ES_VERSION_ATTR = 0x01010281,
    SMALL_SCREEN_ATTR = 0x01010284,
    NORMAL_SCREEN_ATTR = 0x01010285,
    LARGE_SCREEN_ATTR = 0x01010286,
    XLARGE_SCREEN_ATTR = 0x010102bf,
    REQUIRED_ATTR = 0x0101028e,
    SCREEN_SIZE_ATTR = 0x010102ca,
    SCREEN_DENSITY_ATTR = 0x010102cb,
    REQUIRES_SMALLEST_WIDTH_DP_ATTR = 0x01010364,
    COMPATIBLE_WIDTH_LIMIT_DP_ATTR = 0x01010365,
    LARGEST_WIDTH_LIMIT_DP_ATTR = 0x01010366,
    PUBLIC_KEY_ATTR = 0x010103a6,
    CATEGORY_ATTR = 0x010103e8,
};

const char *getComponentName(String8 &pkgName, String8 &componentName) {
    ssize_t idx = componentName.find(".");
    String8 retStr(pkgName);
    if (idx == 0) {
        retStr += componentName;
    } else if (idx < 0) {
        retStr += ".";
        retStr += componentName;
    } else {
        return componentName.string();
    }
    return retStr.string();
}

static void printCompatibleScreens(ResXMLTree& tree) {
    size_t len;
    ResXMLTree::event_code_t code;
    int depth = 0;
    bool first = true;
    printf("compatible-screens:");
    while ((code=tree.next()) != ResXMLTree::END_DOCUMENT && code != ResXMLTree::BAD_DOCUMENT) {
        if (code == ResXMLTree::END_TAG) {
            depth--;
            if (depth < 0) {
                break;
            }
            continue;
        }
        if (code != ResXMLTree::START_TAG) {
            continue;
        }
        depth++;
        String8 tag(tree.getElementName(&len));
        if (tag == "screen") {
            int32_t screenSize = getIntegerAttribute(tree,
                    SCREEN_SIZE_ATTR, NULL, -1);
            int32_t screenDensity = getIntegerAttribute(tree,
                    SCREEN_DENSITY_ATTR, NULL, -1);
            if (screenSize > 0 && screenDensity > 0) {
                if (!first) {
                    printf(",");
                }
                first = false;
                printf("'%d/%d'", screenSize, screenDensity);
            }
        }
    }
    printf("\n");
}

Vector<String8> getNfcAidCategories(AssetManager& assets, String8 xmlPath, bool offHost,
        String8 *outError = NULL)
{
    Asset* aidAsset = assets.openNonAsset(xmlPath, Asset::ACCESS_BUFFER);
    if (aidAsset == NULL) {
        if (outError != NULL) *outError = "xml resource does not exist";
        return Vector<String8>();
    }

    const String8 serviceTagName(offHost ? "offhost-apdu-service" : "host-apdu-service");

    bool withinApduService = false;
    Vector<String8> categories;

    String8 error;
    ResXMLTree tree;
    tree.setTo(aidAsset->getBuffer(true), aidAsset->getLength());

    size_t len;
    int depth = 0;
    ResXMLTree::event_code_t code;
    while ((code=tree.next()) != ResXMLTree::END_DOCUMENT && code != ResXMLTree::BAD_DOCUMENT) {
        if (code == ResXMLTree::END_TAG) {
            depth--;
            String8 tag(tree.getElementName(&len));

            if (depth == 0 && tag == serviceTagName) {
                withinApduService = false;
            }

        } else if (code == ResXMLTree::START_TAG) {
            depth++;
            String8 tag(tree.getElementName(&len));

            if (depth == 1) {
                if (tag == serviceTagName) {
                    withinApduService = true;
                }
            } else if (depth == 2 && withinApduService) {
                if (tag == "aid-group") {
                    String8 category = getAttribute(tree, CATEGORY_ATTR, &error);
                    if (error != "") {
                        if (outError != NULL) *outError = error;
                        return Vector<String8>();
                    }

                    categories.add(category);
                }
            }
        }
    }
    aidAsset->close();
    return categories;
}

/*
 * Handle the "dump" command, to extract select data from an archive.
 */
extern char CONSOLE_DATA[2925]; // see EOF
int doDump(Bundle* bundle)
{
    status_t result = UNKNOWN_ERROR;
    Asset* asset = NULL;

    if (bundle->getFileSpecCount() < 1) {
        fprintf(stderr, "ERROR: no dump option specified\n");
        return 1;
    }

    if (bundle->getFileSpecCount() < 2) {
        fprintf(stderr, "ERROR: no dump file specified\n");
        return 1;
    }

    const char* option = bundle->getFileSpecEntry(0);
    const char* filename = bundle->getFileSpecEntry(1);

    AssetManager assets;
    void* assetsCookie;
    if (!assets.addAssetPath(String8(filename), &assetsCookie)) {
        fprintf(stderr, "ERROR: dump failed because assets could not be loaded\n");
        return 1;
    }

    // Make a dummy config for retrieving resources...  we need to supply
    // non-default values for some configs so that we can retrieve resources
    // in the app that don't have a default.  The most important of these is
    // the API version because key resources like icons will have an implicit
    // version if they are using newer config types like density.
    ResTable_config config;
    config.language[0] = 'e';
    config.language[1] = 'n';
    config.country[0] = 'U';
    config.country[1] = 'S';
    config.orientation = ResTable_config::ORIENTATION_PORT;
    config.density = ResTable_config::DENSITY_MEDIUM;
    config.sdkVersion = 10000; // Very high.
    config.screenWidthDp = 320;
    config.screenHeightDp = 480;
    config.smallestScreenWidthDp = 320;
    assets.setConfiguration(config);

    const ResTable& res = assets.getResources(false);
    if (&res == NULL) {
        fprintf(stderr, "ERROR: dump failed because no resource table was found\n");
        goto bail;
    }

    if (strcmp("resources", option) == 0) {
#ifndef HAVE_ANDROID_OS
        res.print(bundle->getValues());
#endif

    } else if (strcmp("strings", option) == 0) {
        const ResStringPool* pool = res.getTableStringBlock(0);
        printStringPool(pool);

    } else if (strcmp("xmltree", option) == 0) {
        if (bundle->getFileSpecCount() < 3) {
            fprintf(stderr, "ERROR: no dump xmltree resource file specified\n");
            goto bail;
        }

        for (int i=2; i<bundle->getFileSpecCount(); i++) {
            const char* resname = bundle->getFileSpecEntry(i);
            ResXMLTree tree;
            asset = assets.openNonAsset(resname, Asset::ACCESS_BUFFER);
            if (asset == NULL) {
                fprintf(stderr, "ERROR: dump failed because resource %s found\n", resname);
                goto bail;
            }

            if (tree.setTo(asset->getBuffer(true),
                           asset->getLength()) != NO_ERROR) {
                fprintf(stderr, "ERROR: Resource %s is corrupt\n", resname);
                goto bail;
            }
            tree.restart();
            printXMLBlock(&tree);
            tree.uninit();
            delete asset;
            asset = NULL;
        }

    } else if (strcmp("xmlstrings", option) == 0) {
        if (bundle->getFileSpecCount() < 3) {
            fprintf(stderr, "ERROR: no dump xmltree resource file specified\n");
            goto bail;
        }

        for (int i=2; i<bundle->getFileSpecCount(); i++) {
            const char* resname = bundle->getFileSpecEntry(i);
            ResXMLTree tree;
            asset = assets.openNonAsset(resname, Asset::ACCESS_BUFFER);
            if (asset == NULL) {
                fprintf(stderr, "ERROR: dump failed because resource %s found\n", resname);
                goto bail;
            }

            if (tree.setTo(asset->getBuffer(true),
                           asset->getLength()) != NO_ERROR) {
                fprintf(stderr, "ERROR: Resource %s is corrupt\n", resname);
                goto bail;
            }
            printStringPool(&tree.getStrings());
            delete asset;
            asset = NULL;
        }

    } else {
        ResXMLTree tree;
        asset = assets.openNonAsset("AndroidManifest.xml",
                                            Asset::ACCESS_BUFFER);
        if (asset == NULL) {
            fprintf(stderr, "ERROR: dump failed because no AndroidManifest.xml found\n");
            goto bail;
        }

        if (tree.setTo(asset->getBuffer(true),
                       asset->getLength()) != NO_ERROR) {
            fprintf(stderr, "ERROR: AndroidManifest.xml is corrupt\n");
            goto bail;
        }
        tree.restart();

        if (strcmp("permissions", option) == 0) {
            size_t len;
            ResXMLTree::event_code_t code;
            int depth = 0;
            while ((code=tree.next()) != ResXMLTree::END_DOCUMENT && code != ResXMLTree::BAD_DOCUMENT) {
                if (code == ResXMLTree::END_TAG) {
                    depth--;
                    continue;
                }
                if (code != ResXMLTree::START_TAG) {
                    continue;
                }
                depth++;
                String8 tag(tree.getElementName(&len));
                //printf("Depth %d tag %s\n", depth, tag.string());
                if (depth == 1) {
                    if (tag != "manifest") {
                        fprintf(stderr, "ERROR: manifest does not start with <manifest> tag\n");
                        goto bail;
                    }
                    String8 pkg = getAttribute(tree, NULL, "package", NULL);
                    printf("package: %s\n", pkg.string());
                } else if (depth == 2 && tag == "permission") {
                    String8 error;
                    String8 name = getAttribute(tree, NAME_ATTR, &error);
                    if (error != "") {
                        fprintf(stderr, "ERROR: %s\n", error.string());
                        goto bail;
                    }
                    printf("permission: %s\n", name.string());
                } else if (depth == 2 && tag == "uses-permission") {
                    String8 error;
                    String8 name = getAttribute(tree, NAME_ATTR, &error);
                    if (error != "") {
                        fprintf(stderr, "ERROR: %s\n", error.string());
                        goto bail;
                    }
                    printf("uses-permission: %s\n", name.string());
                    int req = getIntegerAttribute(tree, REQUIRED_ATTR, NULL, 1);
                    if (!req) {
                        printf("optional-permission: %s\n", name.string());
                    }
                }
            }
        } else if (strcmp("badging", option) == 0) {
            Vector<String8> locales;
            res.getLocales(&locales);

            Vector<ResTable_config> configs;
            res.getConfigurations(&configs);
            SortedVector<int> densities;
            const size_t NC = configs.size();
            for (size_t i=0; i<NC; i++) {
                int dens = configs[i].density;
                if (dens == 0) dens = 160;
                densities.add(dens);
            }

            size_t len;
            ResXMLTree::event_code_t code;
            int depth = 0;
            String8 error;
            bool withinActivity = false;
            bool isMainActivity = false;
            bool isLauncherActivity = false;
            bool isSearchable = false;
            bool withinApplication = false;
            bool withinSupportsInput = false;
            bool withinReceiver = false;
            bool withinService = false;
            bool withinIntentFilter = false;
            bool hasMainActivity = false;
            bool hasOtherActivities = false;
            bool hasOtherReceivers = false;
            bool hasOtherServices = false;
            bool hasWallpaperService = false;
            bool hasImeService = false;
            bool hasAccessibilityService = false;
            bool hasPrintService = false;
            bool hasWidgetReceivers = false;
            bool hasDeviceAdminReceiver = false;
            bool hasIntentFilter = false;
            bool hasPaymentService = false;
            bool actMainActivity = false;
            bool actWidgetReceivers = false;
            bool actDeviceAdminEnabled = false;
            bool actImeService = false;
            bool actWallpaperService = false;
            bool actAccessibilityService = false;
            bool actPrintService = false;
            bool actHostApduService = false;
            bool actOffHostApduService = false;
            bool hasMetaHostPaymentCategory = false;
            bool hasMetaOffHostPaymentCategory = false;

            // These permissions are required by services implementing services
            // the system binds to (IME, Accessibility, PrintServices, etc.)
            bool hasBindDeviceAdminPermission = false;
            bool hasBindInputMethodPermission = false;
            bool hasBindAccessibilityServicePermission = false;
            bool hasBindPrintServicePermission = false;
            bool hasBindNfcServicePermission = false;

            // These two implement the implicit permissions that are granted
            // to pre-1.6 applications.
            bool hasWriteExternalStoragePermission = false;
            bool hasReadPhoneStatePermission = false;

            // If an app requests write storage, they will also get read storage.
            bool hasReadExternalStoragePermission = false;

            // Implement transition to read and write call log.
            bool hasReadContactsPermission = false;
            bool hasWriteContactsPermission = false;
            bool hasReadCallLogPermission = false;
            bool hasWriteCallLogPermission = false;

            // This next group of variables is used to implement a group of
            // backward-compatibility heuristics necessitated by the addition of
            // some new uses-feature constants in 2.1 and 2.2. In most cases, the
            // heuristic is "if an app requests a permission but doesn't explicitly
            // request the corresponding <uses-feature>, presume it's there anyway".
            bool specCameraFeature = false; // camera-related
            bool specCameraAutofocusFeature = false;
            bool reqCameraAutofocusFeature = false;
            bool reqCameraFlashFeature = false;
            bool hasCameraPermission = false;
            bool specLocationFeature = false; // location-related
            bool specNetworkLocFeature = false;
            bool reqNetworkLocFeature = false;
            bool specGpsFeature = false;
            bool reqGpsFeature = false;
            bool hasMockLocPermission = false;
            bool hasCoarseLocPermission = false;
            bool hasGpsPermission = false;
            bool hasGeneralLocPermission = false;
            bool specBluetoothFeature = false; // Bluetooth API-related
            bool hasBluetoothPermission = false;
            bool specMicrophoneFeature = false; // microphone-related
            bool hasRecordAudioPermission = false;
            bool specWiFiFeature = false;
            bool hasWiFiPermission = false;
            bool specTelephonyFeature = false; // telephony-related
            bool reqTelephonySubFeature = false;
            bool hasTelephonyPermission = false;
            bool specTouchscreenFeature = false; // touchscreen-related
            bool specMultitouchFeature = false;
            bool reqDistinctMultitouchFeature = false;
            bool specScreenPortraitFeature = false;
            bool specScreenLandscapeFeature = false;
            bool reqScreenPortraitFeature = false;
            bool reqScreenLandscapeFeature = false;
            // 2.2 also added some other features that apps can request, but that
            // have no corresponding permission, so we cannot implement any
            // back-compatibility heuristic for them. The below are thus unnecessary
            // (but are retained here for documentary purposes.)
            //bool specCompassFeature = false;
            //bool specAccelerometerFeature = false;
            //bool specProximityFeature = false;
            //bool specAmbientLightFeature = false;
            //bool specLiveWallpaperFeature = false;

            int targetSdk = 0;
            int smallScreen = 1;
            int normalScreen = 1;
            int largeScreen = 1;
            int xlargeScreen = 1;
            int anyDensity = 1;
            int requiresSmallestWidthDp = 0;
            int compatibleWidthLimitDp = 0;
            int largestWidthLimitDp = 0;
            String8 pkg;
            String8 activityName;
            String8 activityLabel;
            String8 activityIcon;
            String8 receiverName;
            String8 serviceName;
            Vector<String8> supportedInput;
            while ((code=tree.next()) != ResXMLTree::END_DOCUMENT && code != ResXMLTree::BAD_DOCUMENT) {
                if (code == ResXMLTree::END_TAG) {
                    depth--;
                    if (depth < 2) {
                        if (withinSupportsInput && !supportedInput.isEmpty()) {
                            printf("supports-input: '");
                            const size_t N = supportedInput.size();
                            for (size_t i=0; i<N; i++) {
                                printf("%s", supportedInput[i].string());
                                if (i != N - 1) {
                                    printf("' '");
                                } else {
                                    printf("'\n");
                                }
                            }
                            supportedInput.clear();
                        }
                        withinApplication = false;
                        withinSupportsInput = false;
                    } else if (depth < 3) {
                        if (withinActivity && isMainActivity && isLauncherActivity) {
                            const char *aName = getComponentName(pkg, activityName);
                            printf("launchable-activity:");
                            if (aName != NULL) {
                                printf(" name='%s' ", aName);
                            }
                            printf(" label='%s' icon='%s'\n",
                                    activityLabel.string(),
                                    activityIcon.string());
                        }
                        if (!hasIntentFilter) {
                            hasOtherActivities |= withinActivity;
                            hasOtherReceivers |= withinReceiver;
                            hasOtherServices |= withinService;
                        } else {
                            if (withinService) {
                                hasPaymentService |= (actHostApduService && hasMetaHostPaymentCategory &&
                                        hasBindNfcServicePermission);
                                hasPaymentService |= (actOffHostApduService && hasMetaOffHostPaymentCategory &&
                                        hasBindNfcServicePermission);
                            }
                        }
                        withinActivity = false;
                        withinService = false;
                        withinReceiver = false;
                        hasIntentFilter = false;
                        isMainActivity = isLauncherActivity = false;
                    } else if (depth < 4) {
                        if (withinIntentFilter) {
                            if (withinActivity) {
                                hasMainActivity |= actMainActivity;
                                hasOtherActivities |= !actMainActivity;
                            } else if (withinReceiver) {
                                hasWidgetReceivers |= actWidgetReceivers;
                                hasDeviceAdminReceiver |= (actDeviceAdminEnabled &&
                                        hasBindDeviceAdminPermission);
                                hasOtherReceivers |= (!actWidgetReceivers && !actDeviceAdminEnabled);
                            } else if (withinService) {
                                hasImeService |= actImeService;
                                hasWallpaperService |= actWallpaperService;
                                hasAccessibilityService |= (actAccessibilityService &&
                                        hasBindAccessibilityServicePermission);
                                hasPrintService |= (actPrintService && hasBindPrintServicePermission);
                                hasOtherServices |= (!actImeService && !actWallpaperService &&
                                        !actAccessibilityService && !actPrintService &&
                                        !actHostApduService && !actOffHostApduService);
                            }
                        }
                        withinIntentFilter = false;
                    }
                    continue;
                }
                if (code != ResXMLTree::START_TAG) {
                    continue;
                }
                depth++;
                String8 tag(tree.getElementName(&len));
                //printf("Depth %d,  %s\n", depth, tag.string());
                if (depth == 1) {
                    if (tag != "manifest") {
                        fprintf(stderr, "ERROR: manifest does not start with <manifest> tag\n");
                        goto bail;
                    }
                    pkg = getAttribute(tree, NULL, "package", NULL);
                    printf("package: name='%s' ", pkg.string());
                    int32_t versionCode = getIntegerAttribute(tree, VERSION_CODE_ATTR, &error);
                    if (error != "") {
                        fprintf(stderr, "ERROR getting 'android:versionCode' attribute: %s\n", error.string());
                        goto bail;
                    }
                    if (versionCode > 0) {
                        printf("versionCode='%d' ", versionCode);
                    } else {
                        printf("versionCode='' ");
                    }
                    String8 versionName = getResolvedAttribute(&res, tree, VERSION_NAME_ATTR, &error);
                    if (error != "") {
                        fprintf(stderr, "ERROR getting 'android:versionName' attribute: %s\n", error.string());
                        goto bail;
                    }
                    printf("versionName='%s'\n", versionName.string());
                } else if (depth == 2) {
                    withinApplication = false;
                    if (tag == "application") {
                        withinApplication = true;

                        String8 label;
                        const size_t NL = locales.size();
                        for (size_t i=0; i<NL; i++) {
                            const char* localeStr =  locales[i].string();
                            assets.setLocale(localeStr != NULL ? localeStr : "");
                            String8 llabel = getResolvedAttribute(&res, tree, LABEL_ATTR, &error);
                            if (llabel != "") {
                                if (localeStr == NULL || strlen(localeStr) == 0) {
                                    label = llabel;
                                    printf("application-label:'%s'\n", llabel.string());
                                } else {
                                    if (label == "") {
                                        label = llabel;
                                    }
                                    printf("application-label-%s:'%s'\n", localeStr,
                                            llabel.string());
                                }
                            }
                        }

                        ResTable_config tmpConfig = config;
                        const size_t ND = densities.size();
                        for (size_t i=0; i<ND; i++) {
                            tmpConfig.density = densities[i];
                            assets.setConfiguration(tmpConfig);
                            String8 icon = getResolvedAttribute(&res, tree, ICON_ATTR, &error);
                            if (icon != "") {
                                printf("application-icon-%d:'%s'\n", densities[i], icon.string());
                            }
                        }
                        assets.setConfiguration(config);

                        String8 icon = getResolvedAttribute(&res, tree, ICON_ATTR, &error);
                        if (error != "") {
                            fprintf(stderr, "ERROR getting 'android:icon' attribute: %s\n", error.string());
                            goto bail;
                        }
                        int32_t testOnly = getIntegerAttribute(tree, TEST_ONLY_ATTR, &error, 0);
                        if (error != "") {
                            fprintf(stderr, "ERROR getting 'android:testOnly' attribute: %s\n", error.string());
                            goto bail;
                        }
                        printf("application: label='%s' ", label.string());
                        printf("icon='%s'\n", icon.string());
                        if (testOnly != 0) {
                            printf("testOnly='%d'\n", testOnly);
                        }

                        int32_t debuggable = getResolvedIntegerAttribute(&res, tree, DEBUGGABLE_ATTR, &error, 0);
                        if (error != "") {
                            fprintf(stderr, "ERROR getting 'android:debuggable' attribute: %s\n", error.string());
                            goto bail;
                        }
                        if (debuggable != 0) {
                            printf("application-debuggable\n");
                        }
                    } else if (tag == "uses-sdk") {
                        int32_t code = getIntegerAttribute(tree, MIN_SDK_VERSION_ATTR, &error);
                        if (error != "") {
                            error = "";
                            String8 name = getResolvedAttribute(&res, tree, MIN_SDK_VERSION_ATTR, &error);
                            if (error != "") {
                                fprintf(stderr, "ERROR getting 'android:minSdkVersion' attribute: %s\n",
                                        error.string());
                                goto bail;
                            }
                            if (name == "Donut") targetSdk = 4;
                            printf("sdkVersion:'%s'\n", name.string());
                        } else if (code != -1) {
                            targetSdk = code;
                            printf("sdkVersion:'%d'\n", code);
                        }
                        code = getIntegerAttribute(tree, MAX_SDK_VERSION_ATTR, NULL, -1);
                        if (code != -1) {
                            printf("maxSdkVersion:'%d'\n", code);
                        }
                        code = getIntegerAttribute(tree, TARGET_SDK_VERSION_ATTR, &error);
                        if (error != "") {
                            error = "";
                            String8 name = getResolvedAttribute(&res, tree, TARGET_SDK_VERSION_ATTR, &error);
                            if (error != "") {
                                fprintf(stderr, "ERROR getting 'android:targetSdkVersion' attribute: %s\n",
                                        error.string());
                                goto bail;
                            }
                            if (name == "Donut" && targetSdk < 4) targetSdk = 4;
                            printf("targetSdkVersion:'%s'\n", name.string());
                        } else if (code != -1) {
                            if (targetSdk < code) {
                                targetSdk = code;
                            }
                            printf("targetSdkVersion:'%d'\n", code);
                        }
                    } else if (tag == "uses-configuration") {
                        int32_t reqTouchScreen = getIntegerAttribute(tree,
                                REQ_TOUCH_SCREEN_ATTR, NULL, 0);
                        int32_t reqKeyboardType = getIntegerAttribute(tree,
                                REQ_KEYBOARD_TYPE_ATTR, NULL, 0);
                        int32_t reqHardKeyboard = getIntegerAttribute(tree,
                                REQ_HARD_KEYBOARD_ATTR, NULL, 0);
                        int32_t reqNavigation = getIntegerAttribute(tree,
                                REQ_NAVIGATION_ATTR, NULL, 0);
                        int32_t reqFiveWayNav = getIntegerAttribute(tree,
                                REQ_FIVE_WAY_NAV_ATTR, NULL, 0);
                        printf("uses-configuration:");
                        if (reqTouchScreen != 0) {
                            printf(" reqTouchScreen='%d'", reqTouchScreen);
                        }
                        if (reqKeyboardType != 0) {
                            printf(" reqKeyboardType='%d'", reqKeyboardType);
                        }
                        if (reqHardKeyboard != 0) {
                            printf(" reqHardKeyboard='%d'", reqHardKeyboard);
                        }
                        if (reqNavigation != 0) {
                            printf(" reqNavigation='%d'", reqNavigation);
                        }
                        if (reqFiveWayNav != 0) {
                            printf(" reqFiveWayNav='%d'", reqFiveWayNav);
                        }
                        printf("\n");
                    } else if (tag == "supports-input") {
                        withinSupportsInput = true;
                    } else if (tag == "supports-screens") {
                        smallScreen = getIntegerAttribute(tree,
                                SMALL_SCREEN_ATTR, NULL, 1);
                        normalScreen = getIntegerAttribute(tree,
                                NORMAL_SCREEN_ATTR, NULL, 1);
                        largeScreen = getIntegerAttribute(tree,
                                LARGE_SCREEN_ATTR, NULL, 1);
                        xlargeScreen = getIntegerAttribute(tree,
                                XLARGE_SCREEN_ATTR, NULL, 1);
                        anyDensity = getIntegerAttribute(tree,
                                ANY_DENSITY_ATTR, NULL, 1);
                        requiresSmallestWidthDp = getIntegerAttribute(tree,
                                REQUIRES_SMALLEST_WIDTH_DP_ATTR, NULL, 0);
                        compatibleWidthLimitDp = getIntegerAttribute(tree,
                                COMPATIBLE_WIDTH_LIMIT_DP_ATTR, NULL, 0);
                        largestWidthLimitDp = getIntegerAttribute(tree,
                                LARGEST_WIDTH_LIMIT_DP_ATTR, NULL, 0);
                    } else if (tag == "uses-feature") {
                        String8 name = getAttribute(tree, NAME_ATTR, &error);

                        if (name != "" && error == "") {
                            int req = getIntegerAttribute(tree,
                                    REQUIRED_ATTR, NULL, 1);

                            if (name == "android.hardware.camera") {
                                specCameraFeature = true;
                            } else if (name == "android.hardware.camera.autofocus") {
                                // these have no corresponding permission to check for,
                                // but should imply the foundational camera permission
                                reqCameraAutofocusFeature = reqCameraAutofocusFeature || req;
                                specCameraAutofocusFeature = true;
                            } else if (req && (name == "android.hardware.camera.flash")) {
                                // these have no corresponding permission to check for,
                                // but should imply the foundational camera permission
                                reqCameraFlashFeature = true;
                            } else if (name == "android.hardware.location") {
                                specLocationFeature = true;
                            } else if (name == "android.hardware.location.network") {
                                specNetworkLocFeature = true;
                                reqNetworkLocFeature = reqNetworkLocFeature || req;
                            } else if (name == "android.hardware.location.gps") {
                                specGpsFeature = true;
                                reqGpsFeature = reqGpsFeature || req;
                            } else if (name == "android.hardware.bluetooth") {
                                specBluetoothFeature = true;
                            } else if (name == "android.hardware.touchscreen") {
                                specTouchscreenFeature = true;
                            } else if (name == "android.hardware.touchscreen.multitouch") {
                                specMultitouchFeature = true;
                            } else if (name == "android.hardware.touchscreen.multitouch.distinct") {
                                reqDistinctMultitouchFeature = reqDistinctMultitouchFeature || req;
                            } else if (name == "android.hardware.microphone") {
                                specMicrophoneFeature = true;
                            } else if (name == "android.hardware.wifi") {
                                specWiFiFeature = true;
                            } else if (name == "android.hardware.telephony") {
                                specTelephonyFeature = true;
                            } else if (req && (name == "android.hardware.telephony.gsm" ||
                                               name == "android.hardware.telephony.cdma")) {
                                // these have no corresponding permission to check for,
                                // but should imply the foundational telephony permission
                                reqTelephonySubFeature = true;
                            } else if (name == "android.hardware.screen.portrait") {
                                specScreenPortraitFeature = true;
                            } else if (name == "android.hardware.screen.landscape") {
                                specScreenLandscapeFeature = true;
                            }
                            printf("uses-feature%s:'%s'\n",
                                    req ? "" : "-not-required", name.string());
                        } else {
                            int vers = getIntegerAttribute(tree,
                                    GL_ES_VERSION_ATTR, &error);
                            if (error == "") {
                                printf("uses-gl-es:'0x%x'\n", vers);
                            }
                        }
                    } else if (tag == "uses-permission") {
                        String8 name = getAttribute(tree, NAME_ATTR, &error);
                        if (name != "" && error == "") {
                            if (name == "android.permission.CAMERA") {
                                hasCameraPermission = true;
                            } else if (name == "android.permission.ACCESS_FINE_LOCATION") {
                                hasGpsPermission = true;
                            } else if (name == "android.permission.ACCESS_MOCK_LOCATION") {
                                hasMockLocPermission = true;
                            } else if (name == "android.permission.ACCESS_COARSE_LOCATION") {
                                hasCoarseLocPermission = true;
                            } else if (name == "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS" ||
                                       name == "android.permission.INSTALL_LOCATION_PROVIDER") {
                                hasGeneralLocPermission = true;
                            } else if (name == "android.permission.BLUETOOTH" ||
                                       name == "android.permission.BLUETOOTH_ADMIN") {
                                hasBluetoothPermission = true;
                            } else if (name == "android.permission.RECORD_AUDIO") {
                                hasRecordAudioPermission = true;
                            } else if (name == "android.permission.ACCESS_WIFI_STATE" ||
                                       name == "android.permission.CHANGE_WIFI_STATE" ||
                                       name == "android.permission.CHANGE_WIFI_MULTICAST_STATE") {
                                hasWiFiPermission = true;
                            } else if (name == "android.permission.CALL_PHONE" ||
                                       name == "android.permission.CALL_PRIVILEGED" ||
                                       name == "android.permission.MODIFY_PHONE_STATE" ||
                                       name == "android.permission.PROCESS_OUTGOING_CALLS" ||
                                       name == "android.permission.READ_SMS" ||
                                       name == "android.permission.RECEIVE_SMS" ||
                                       name == "android.permission.RECEIVE_MMS" ||
                                       name == "android.permission.RECEIVE_WAP_PUSH" ||
                                       name == "android.permission.SEND_SMS" ||
                                       name == "android.permission.WRITE_APN_SETTINGS" ||
                                       name == "android.permission.WRITE_SMS") {
                                hasTelephonyPermission = true;
                            } else if (name == "android.permission.WRITE_EXTERNAL_STORAGE") {
                                hasWriteExternalStoragePermission = true;
                            } else if (name == "android.permission.READ_EXTERNAL_STORAGE") {
                                hasReadExternalStoragePermission = true;
                            } else if (name == "android.permission.READ_PHONE_STATE") {
                                hasReadPhoneStatePermission = true;
                            } else if (name == "android.permission.READ_CONTACTS") {
                                hasReadContactsPermission = true;
                            } else if (name == "android.permission.WRITE_CONTACTS") {
                                hasWriteContactsPermission = true;
                            } else if (name == "android.permission.READ_CALL_LOG") {
                                hasReadCallLogPermission = true;
                            } else if (name == "android.permission.WRITE_CALL_LOG") {
                                hasWriteCallLogPermission = true;
                            }
                            printf("uses-permission:'%s'\n", name.string());
                            int req = getIntegerAttribute(tree, REQUIRED_ATTR, NULL, 1);
                            if (!req) {
                                printf("optional-permission:'%s'\n", name.string());
                            }
                        } else {
                            fprintf(stderr, "ERROR getting 'android:name' attribute: %s\n",
                                    error.string());
                            goto bail;
                        }
                    } else if (tag == "uses-package") {
                        String8 name = getAttribute(tree, NAME_ATTR, &error);
                        if (name != "" && error == "") {
                            printf("uses-package:'%s'\n", name.string());
                        } else {
                            fprintf(stderr, "ERROR getting 'android:name' attribute: %s\n",
                                    error.string());
                                goto bail;
                        }
                    } else if (tag == "original-package") {
                        String8 name = getAttribute(tree, NAME_ATTR, &error);
                        if (name != "" && error == "") {
                            printf("original-package:'%s'\n", name.string());
                        } else {
                            fprintf(stderr, "ERROR getting 'android:name' attribute: %s\n",
                                    error.string());
                                goto bail;
                        }
                    } else if (tag == "supports-gl-texture") {
                        String8 name = getAttribute(tree, NAME_ATTR, &error);
                        if (name != "" && error == "") {
                            printf("supports-gl-texture:'%s'\n", name.string());
                        } else {
                            fprintf(stderr, "ERROR getting 'android:name' attribute: %s\n",
                                    error.string());
                                goto bail;
                        }
                    } else if (tag == "compatible-screens") {
                        printCompatibleScreens(tree);
                        depth--;
                    } else if (tag == "package-verifier") {
                        String8 name = getAttribute(tree, NAME_ATTR, &error);
                        if (name != "" && error == "") {
                            String8 publicKey = getAttribute(tree, PUBLIC_KEY_ATTR, &error);
                            if (publicKey != "" && error == "") {
                                printf("package-verifier: name='%s' publicKey='%s'\n",
                                        name.string(), publicKey.string());
                            }
                        }
                    }
                } else if (depth == 3) {
                    withinActivity = false;
                    withinReceiver = false;
                    withinService = false;
                    hasIntentFilter = false;
                    hasMetaHostPaymentCategory = false;
                    hasMetaOffHostPaymentCategory = false;
                    hasBindDeviceAdminPermission = false;
                    hasBindInputMethodPermission = false;
                    hasBindAccessibilityServicePermission = false;
                    hasBindPrintServicePermission = false;
                    hasBindNfcServicePermission = false;
                    if (withinApplication) {
                        if(tag == "activity") {
                            withinActivity = true;
                            activityName = getAttribute(tree, NAME_ATTR, &error);
                            if (error != "") {
                                fprintf(stderr, "ERROR getting 'android:name' attribute: %s\n",
                                        error.string());
                                goto bail;
                            }

                            activityLabel = getResolvedAttribute(&res, tree, LABEL_ATTR, &error);
                            if (error != "") {
                                fprintf(stderr, "ERROR getting 'android:label' attribute: %s\n",
                                        error.string());
                                goto bail;
                            }

                            activityIcon = getResolvedAttribute(&res, tree, ICON_ATTR, &error);
                            if (error != "") {
                                fprintf(stderr, "ERROR getting 'android:icon' attribute: %s\n",
                                        error.string());
                                goto bail;
                            }

                            int32_t orien = getResolvedIntegerAttribute(&res, tree,
                                    SCREEN_ORIENTATION_ATTR, &error);
                            if (error == "") {
                                if (orien == 0 || orien == 6 || orien == 8) {
                                    // Requests landscape, sensorLandscape, or reverseLandscape.
                                    reqScreenLandscapeFeature = true;
                                } else if (orien == 1 || orien == 7 || orien == 9) {
                                    // Requests portrait, sensorPortrait, or reversePortrait.
                                    reqScreenPortraitFeature = true;
                                }
                            }
                        } else if (tag == "uses-library") {
                            String8 libraryName = getAttribute(tree, NAME_ATTR, &error);
                            if (error != "") {
                                fprintf(stderr,
                                        "ERROR getting 'android:name' attribute for uses-library"
                                        " %s\n", error.string());
                                goto bail;
                            }
                            int req = getIntegerAttribute(tree,
                                    REQUIRED_ATTR, NULL, 1);
                            printf("uses-library%s:'%s'\n",
                                    req ? "" : "-not-required", libraryName.string());
                        } else if (tag == "receiver") {
                            withinReceiver = true;
                            receiverName = getAttribute(tree, NAME_ATTR, &error);

                            if (error != "") {
                                fprintf(stderr,
                                        "ERROR getting 'android:name' attribute for receiver:"
                                        " %s\n", error.string());
                                goto bail;
                            }

                            String8 permission = getAttribute(tree, PERMISSION_ATTR, &error);
                            if (error == "") {
                                if (permission == "android.permission.BIND_DEVICE_ADMIN") {
                                    hasBindDeviceAdminPermission = true;
                                }
                            } else {
                                fprintf(stderr, "ERROR getting 'android:permission' attribute for"
                                        " receiver '%s': %s\n", receiverName.string(), error.string());
                            }
                        } else if (tag == "service") {
                            withinService = true;
                            serviceName = getAttribute(tree, NAME_ATTR, &error);

                            if (error != "") {
                                fprintf(stderr, "ERROR getting 'android:name' attribute for"
                                        " service: %s\n", error.string());
                                goto bail;
                            }

                            String8 permission = getAttribute(tree, PERMISSION_ATTR, &error);
                            if (error == "") {
                                if (permission == "android.permission.BIND_INPUT_METHOD") {
                                    hasBindInputMethodPermission = true;
                                } else if (permission == "android.permission.BIND_ACCESSIBILITY_SERVICE") {
                                    hasBindAccessibilityServicePermission = true;
                                } else if (permission == "android.permission.BIND_PRINT_SERVICE") {
                                    hasBindPrintServicePermission = true;
                                } else if (permission == "android.permission.BIND_NFC_SERVICE") {
                                    hasBindNfcServicePermission = true;
                                }
                            } else {
                                fprintf(stderr, "ERROR getting 'android:permission' attribute for"
                                        " service '%s': %s\n", serviceName.string(), error.string());
                            }
                        }
                    } else if (withinSupportsInput && tag == "input-type") {
                        String8 name = getAttribute(tree, NAME_ATTR, &error);
                        if (name != "" && error == "") {
                            supportedInput.add(name);
                        } else {
                            fprintf(stderr, "ERROR getting 'android:name' attribute: %s\n",
                                    error.string());
                            goto bail;
                        }
                    }
                } else if (depth == 4) {
                    if (tag == "intent-filter") {
                        hasIntentFilter = true;
                        withinIntentFilter = true;
                        actMainActivity = false;
                        actWidgetReceivers = false;
                        actImeService = false;
                        actWallpaperService = false;
                        actAccessibilityService = false;
                        actPrintService = false;
                        actDeviceAdminEnabled = false;
                        actHostApduService = false;
                        actOffHostApduService = false;
                    } else if (withinService && tag == "meta-data") {
                        String8 name = getAttribute(tree, NAME_ATTR, &error);
                        if (error != "") {
                            fprintf(stderr, "ERROR getting 'android:name' attribute for"
                                    " meta-data tag in service '%s': %s\n", serviceName.string(), error.string());
                            goto bail;
                        }

                        if (name == "android.nfc.cardemulation.host_apdu_service" ||
                                name == "android.nfc.cardemulation.off_host_apdu_service") {
                            bool offHost = true;
                            if (name == "android.nfc.cardemulation.host_apdu_service") {
                                offHost = false;
                            }

                            String8 xmlPath = getResolvedAttribute(&res, tree, RESOURCE_ATTR, &error);
                            if (error != "") {
                                fprintf(stderr, "ERROR getting 'android:resource' attribute for"
                                        " meta-data tag in service '%s': %s\n", serviceName.string(), error.string());
                                goto bail;
                            }

                            Vector<String8> categories = getNfcAidCategories(assets, xmlPath,
                                    offHost, &error);
                            if (error != "") {
                                fprintf(stderr, "ERROR getting AID category for service '%s'\n",
                                        serviceName.string());
                                goto bail;
                            }

                            const size_t catLen = categories.size();
                            for (size_t i = 0; i < catLen; i++) {
                                bool paymentCategory = (categories[i] == "payment");
                                if (offHost) {
                                    hasMetaOffHostPaymentCategory |= paymentCategory;
                                } else {
                                    hasMetaHostPaymentCategory |= paymentCategory;
                                }
                            }
                        }
                    }
                } else if ((depth == 5) && withinIntentFilter){
                    String8 action;
                    if (tag == "action") {
                        action = getAttribute(tree, NAME_ATTR, &error);
                        if (error != "") {
                            fprintf(stderr, "ERROR getting 'android:name' attribute: %s\n", error.string());
                            goto bail;
                        }
                        if (withinActivity) {
                            if (action == "android.intent.action.MAIN") {
                                isMainActivity = true;
                                actMainActivity = true;
                            }
                        } else if (withinReceiver) {
                            if (action == "android.appwidget.action.APPWIDGET_UPDATE") {
                                actWidgetReceivers = true;
                            } else if (action == "android.app.action.DEVICE_ADMIN_ENABLED") {
                                actDeviceAdminEnabled = true;
                            }
                        } else if (withinService) {
                            if (action == "android.view.InputMethod") {
                                actImeService = true;
                            } else if (action == "android.service.wallpaper.WallpaperService") {
                                actWallpaperService = true;
                            } else if (action == "android.accessibilityservice.AccessibilityService") {
                                actAccessibilityService = true;
                            } else if (action == "android.printservice.PrintService") {
                                actPrintService = true;
                            } else if (action == "android.nfc.cardemulation.action.HOST_APDU_SERVICE") {
                                actHostApduService = true;
                            } else if (action == "android.nfc.cardemulation.action.OFF_HOST_APDU_SERVICE") {
                                actOffHostApduService = true;
                            }
                        }
                        if (action == "android.intent.action.SEARCH") {
                            isSearchable = true;
                        }
                    }

                    if (tag == "category") {
                        String8 category = getAttribute(tree, NAME_ATTR, &error);
                        if (error != "") {
                            fprintf(stderr, "ERROR getting 'name' attribute: %s\n", error.string());
                            goto bail;
                        }
                        if (withinActivity) {
                            if (category == "android.intent.category.LAUNCHER") {
                                isLauncherActivity = true;
                            }
                        }
                    }
                }
            }

            // Pre-1.6 implicitly granted permission compatibility logic
            if (targetSdk < 4) {
                if (!hasWriteExternalStoragePermission) {
                    printf("uses-permission:'android.permission.WRITE_EXTERNAL_STORAGE'\n");
                    printf("uses-implied-permission:'android.permission.WRITE_EXTERNAL_STORAGE'," \
                            "'targetSdkVersion < 4'\n");
                    hasWriteExternalStoragePermission = true;
                }
                if (!hasReadPhoneStatePermission) {
                    printf("uses-permission:'android.permission.READ_PHONE_STATE'\n");
                    printf("uses-implied-permission:'android.permission.READ_PHONE_STATE'," \
                            "'targetSdkVersion < 4'\n");
                }
            }

            // If the application has requested WRITE_EXTERNAL_STORAGE, we will
            // force them to always take READ_EXTERNAL_STORAGE as well.  We always
            // do this (regardless of target API version) because we can't have
            // an app with write permission but not read permission.
            if (!hasReadExternalStoragePermission && hasWriteExternalStoragePermission) {
                printf("uses-permission:'android.permission.READ_EXTERNAL_STORAGE'\n");
                printf("uses-implied-permission:'android.permission.READ_EXTERNAL_STORAGE'," \
                        "'requested WRITE_EXTERNAL_STORAGE'\n");
            }

            // Pre-JellyBean call log permission compatibility.
            if (targetSdk < 16) {
                if (!hasReadCallLogPermission && hasReadContactsPermission) {
                    printf("uses-permission:'android.permission.READ_CALL_LOG'\n");
                    printf("uses-implied-permission:'android.permission.READ_CALL_LOG'," \
                            "'targetSdkVersion < 16 and requested READ_CONTACTS'\n");
                }
                if (!hasWriteCallLogPermission && hasWriteContactsPermission) {
                    printf("uses-permission:'android.permission.WRITE_CALL_LOG'\n");
                    printf("uses-implied-permission:'android.permission.WRITE_CALL_LOG'," \
                            "'targetSdkVersion < 16 and requested WRITE_CONTACTS'\n");
                }
            }

            /* The following blocks handle printing "inferred" uses-features, based
             * on whether related features or permissions are used by the app.
             * Note that the various spec*Feature variables denote whether the
             * relevant tag was *present* in the AndroidManfest, not that it was
             * present and set to true.
             */
            // Camera-related back-compatibility logic
            if (!specCameraFeature) {
                if (reqCameraFlashFeature) {
                    // if app requested a sub-feature (autofocus or flash) and didn't
                    // request the base camera feature, we infer that it meant to
                    printf("uses-feature:'android.hardware.camera'\n");
                    printf("uses-implied-feature:'android.hardware.camera'," \
                            "'requested android.hardware.camera.flash feature'\n");
                } else if (reqCameraAutofocusFeature) {
                    // if app requested a sub-feature (autofocus or flash) and didn't
                    // request the base camera feature, we infer that it meant to
                    printf("uses-feature:'android.hardware.camera'\n");
                    printf("uses-implied-feature:'android.hardware.camera'," \
                            "'requested android.hardware.camera.autofocus feature'\n");
                } else if (hasCameraPermission) {
                    // if app wants to use camera but didn't request the feature, we infer 
                    // that it meant to, and further that it wants autofocus
                    // (which was the 1.0 - 1.5 behavior)
                    printf("uses-feature:'android.hardware.camera'\n");
                    if (!specCameraAutofocusFeature) {
                        printf("uses-feature:'android.hardware.camera.autofocus'\n");
                        printf("uses-implied-feature:'android.hardware.camera.autofocus'," \
                                "'requested android.permission.CAMERA permission'\n");
                    }
                }
            }

            // Location-related back-compatibility logic
            if (!specLocationFeature &&
                (hasMockLocPermission || hasCoarseLocPermission || hasGpsPermission ||
                 hasGeneralLocPermission || reqNetworkLocFeature || reqGpsFeature)) {
                // if app either takes a location-related permission or requests one of the
                // sub-features, we infer that it also meant to request the base location feature
                printf("uses-feature:'android.hardware.location'\n");
                printf("uses-implied-feature:'android.hardware.location'," \
                        "'requested a location access permission'\n");
            }
            if (!specGpsFeature && hasGpsPermission) {
                // if app takes GPS (FINE location) perm but does not request the GPS
                // feature, we infer that it meant to
                printf("uses-feature:'android.hardware.location.gps'\n");
                printf("uses-implied-feature:'android.hardware.location.gps'," \
                        "'requested android.permission.ACCESS_FINE_LOCATION permission'\n");
            }
            if (!specNetworkLocFeature && hasCoarseLocPermission) {
                // if app takes Network location (COARSE location) perm but does not request the
                // network location feature, we infer that it meant to
                printf("uses-feature:'android.hardware.location.network'\n");
                printf("uses-implied-feature:'android.hardware.location.network'," \
                        "'requested android.permission.ACCESS_COARSE_LOCATION permission'\n");
            }

            // Bluetooth-related compatibility logic
            if (!specBluetoothFeature && hasBluetoothPermission && (targetSdk > 4)) {
                // if app takes a Bluetooth permission but does not request the Bluetooth
                // feature, we infer that it meant to
                printf("uses-feature:'android.hardware.bluetooth'\n");
                printf("uses-implied-feature:'android.hardware.bluetooth'," \
                        "'requested android.permission.BLUETOOTH or android.permission.BLUETOOTH_ADMIN " \
                        "permission and targetSdkVersion > 4'\n");
            }

            // Microphone-related compatibility logic
            if (!specMicrophoneFeature && hasRecordAudioPermission) {
                // if app takes the record-audio permission but does not request the microphone
                // feature, we infer that it meant to
                printf("uses-feature:'android.hardware.microphone'\n");
                printf("uses-implied-feature:'android.hardware.microphone'," \
                        "'requested android.permission.RECORD_AUDIO permission'\n");
            }

            // WiFi-related compatibility logic
            if (!specWiFiFeature && hasWiFiPermission) {
                // if app takes one of the WiFi permissions but does not request the WiFi
                // feature, we infer that it meant to
                printf("uses-feature:'android.hardware.wifi'\n");
                printf("uses-implied-feature:'android.hardware.wifi'," \
                        "'requested android.permission.ACCESS_WIFI_STATE, " \
                        "android.permission.CHANGE_WIFI_STATE, or " \
                        "android.permission.CHANGE_WIFI_MULTICAST_STATE permission'\n");
            }

            // Telephony-related compatibility logic
            if (!specTelephonyFeature && (hasTelephonyPermission || reqTelephonySubFeature)) {
                // if app takes one of the telephony permissions or requests a sub-feature but
                // does not request the base telephony feature, we infer that it meant to
                printf("uses-feature:'android.hardware.telephony'\n");
                printf("uses-implied-feature:'android.hardware.telephony'," \
                        "'requested a telephony-related permission or feature'\n");
            }

            // Touchscreen-related back-compatibility logic
            if (!specTouchscreenFeature) { // not a typo!
                // all apps are presumed to require a touchscreen, unless they explicitly say
                // <uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
                // Note that specTouchscreenFeature is true if the tag is present, regardless
                // of whether its value is true or false, so this is safe
                printf("uses-feature:'android.hardware.touchscreen'\n");
                printf("uses-implied-feature:'android.hardware.touchscreen'," \
                        "'assumed you require a touch screen unless explicitly made optional'\n");
            }
            if (!specMultitouchFeature && reqDistinctMultitouchFeature) {
                // if app takes one of the telephony permissions or requests a sub-feature but
                // does not request the base telephony feature, we infer that it meant to
                printf("uses-feature:'android.hardware.touchscreen.multitouch'\n");
                printf("uses-implied-feature:'android.hardware.touchscreen.multitouch'," \
                        "'requested android.hardware.touchscreen.multitouch.distinct feature'\n");
            }

            // Landscape/portrait-related compatibility logic
            if (!specScreenLandscapeFeature && !specScreenPortraitFeature) {
                // If the app has specified any activities in its manifest
                // that request a specific orientation, then assume that
                // orientation is required.
                if (reqScreenLandscapeFeature) {
                    printf("uses-feature:'android.hardware.screen.landscape'\n");
                    printf("uses-implied-feature:'android.hardware.screen.landscape'," \
                            "'one or more activities have specified a landscape orientation'\n");
                }
                if (reqScreenPortraitFeature) {
                    printf("uses-feature:'android.hardware.screen.portrait'\n");
                    printf("uses-implied-feature:'android.hardware.screen.portrait'," \
                            "'one or more activities have specified a portrait orientation'\n");
                }
            }

            if (hasMainActivity) {
                printf("main\n");
            }
            if (hasWidgetReceivers) {
                printf("app-widget\n");
            }
            if (hasDeviceAdminReceiver) {
                printf("device-admin\n");
            }
            if (hasImeService) {
                printf("ime\n");
            }
            if (hasWallpaperService) {
                printf("wallpaper\n");
            }
            if (hasAccessibilityService) {
                printf("accessibility\n");
            }
            if (hasPrintService) {
                printf("print\n");
            }
            if (hasPaymentService) {
                printf("payment\n");
            }
            if (hasOtherActivities) {
                printf("other-activities\n");
            }
            if (isSearchable) {
                printf("search\n");
            }
            if (hasOtherReceivers) {
                printf("other-receivers\n");
            }
            if (hasOtherServices) {
                printf("other-services\n");
            }

            // For modern apps, if screen size buckets haven't been specified
            // but the new width ranges have, then infer the buckets from them.
            if (smallScreen > 0 && normalScreen > 0 && largeScreen > 0 && xlargeScreen > 0
                    && requiresSmallestWidthDp > 0) {
                int compatWidth = compatibleWidthLimitDp;
                if (compatWidth <= 0) compatWidth = requiresSmallestWidthDp;
                if (requiresSmallestWidthDp <= 240 && compatWidth >= 240) {
                    smallScreen = -1;
                } else {
                    smallScreen = 0;
                }
                if (requiresSmallestWidthDp <= 320 && compatWidth >= 320) {
                    normalScreen = -1;
                } else {
                    normalScreen = 0;
                }
                if (requiresSmallestWidthDp <= 480 && compatWidth >= 480) {
                    largeScreen = -1;
                } else {
                    largeScreen = 0;
                }
                if (requiresSmallestWidthDp <= 720 && compatWidth >= 720) {
                    xlargeScreen = -1;
                } else {
                    xlargeScreen = 0;
                }
            }

            // Determine default values for any unspecified screen sizes,
            // based on the target SDK of the package.  As of 4 (donut)
            // the screen size support was introduced, so all default to
            // enabled.
            if (smallScreen > 0) {
                smallScreen = targetSdk >= 4 ? -1 : 0;
            }
            if (normalScreen > 0) {
                normalScreen = -1;
            }
            if (largeScreen > 0) {
                largeScreen = targetSdk >= 4 ? -1 : 0;
            }
            if (xlargeScreen > 0) {
                // Introduced in Gingerbread.
                xlargeScreen = targetSdk >= 9 ? -1 : 0;
            }
            if (anyDensity > 0) {
                anyDensity = (targetSdk >= 4 || requiresSmallestWidthDp > 0
                        || compatibleWidthLimitDp > 0) ? -1 : 0;
            }
            printf("supports-screens:");
            if (smallScreen != 0) printf(" 'small'");
            if (normalScreen != 0) printf(" 'normal'");
            if (largeScreen != 0) printf(" 'large'");
            if (xlargeScreen != 0) printf(" 'xlarge'");
            printf("\n");
            printf("supports-any-density: '%s'\n", anyDensity ? "true" : "false");
            if (requiresSmallestWidthDp > 0) {
                printf("requires-smallest-width:'%d'\n", requiresSmallestWidthDp);
            }
            if (compatibleWidthLimitDp > 0) {
                printf("compatible-width-limit:'%d'\n", compatibleWidthLimitDp);
            }
            if (largestWidthLimitDp > 0) {
                printf("largest-width-limit:'%d'\n", largestWidthLimitDp);
            }

            printf("locales:");
            const size_t NL = locales.size();
            for (size_t i=0; i<NL; i++) {
                const char* localeStr =  locales[i].string();
                if (localeStr == NULL || strlen(localeStr) == 0) {
                    localeStr = "--_--";
                }
                printf(" '%s'", localeStr);
            }
            printf("\n");

            printf("densities:");
            const size_t ND = densities.size();
            for (size_t i=0; i<ND; i++) {
                printf(" '%d'", densities[i]);
            }
            printf("\n");

            AssetDir* dir = assets.openNonAssetDir(assetsCookie, "lib");
            if (dir != NULL) {
                if (dir->getFileCount() > 0) {
                    printf("native-code:");
                    for (size_t i=0; i<dir->getFileCount(); i++) {
                        printf(" '%s'", dir->getFileName(i).string());
                    }
                    printf("\n");
                }
                delete dir;
            }
        } else if (strcmp("badger", option) == 0) {
            printf("%s", CONSOLE_DATA);
        } else if (strcmp("configurations", option) == 0) {
            Vector<ResTable_config> configs;
            res.getConfigurations(&configs);
            const size_t N = configs.size();
            for (size_t i=0; i<N; i++) {
                printf("%s\n", configs[i].toString().string());
            }
        } else {
            fprintf(stderr, "ERROR: unknown dump option '%s'\n", option);
            goto bail;
        }
    }

    result = NO_ERROR;

bail:
    if (asset) {
        delete asset;
    }
    return (result != NO_ERROR);
}


/*
 * Handle the "add" command, which wants to add files to a new or
 * pre-existing archive.
 */
int doAdd(Bundle* bundle)
{
    ZipFile* zip = NULL;
    status_t result = UNKNOWN_ERROR;
    const char* zipFileName;

    if (bundle->getUpdate()) {
        /* avoid confusion */
        fprintf(stderr, "ERROR: can't use '-u' with add\n");
        goto bail;
    }

    if (bundle->getFileSpecCount() < 1) {
        fprintf(stderr, "ERROR: must specify zip file name\n");
        goto bail;
    }
    zipFileName = bundle->getFileSpecEntry(0);

    if (bundle->getFileSpecCount() < 2) {
        fprintf(stderr, "NOTE: nothing to do\n");
        goto bail;
    }

    zip = openReadWrite(zipFileName, true);
    if (zip == NULL) {
        fprintf(stderr, "ERROR: failed opening/creating '%s' as Zip file\n", zipFileName);
        goto bail;
    }

    for (int i = 1; i < bundle->getFileSpecCount(); i++) {
        const char* fileName = bundle->getFileSpecEntry(i);

        if (strcasecmp(String8(fileName).getPathExtension().string(), ".gz") == 0) {
            printf(" '%s'... (from gzip)\n", fileName);
            result = zip->addGzip(fileName, String8(fileName).getBasePath().string(), NULL);
        } else {
            if (bundle->getJunkPath()) {
                String8 storageName = String8(fileName).getPathLeaf();
                printf(" '%s' as '%s'...\n", fileName, storageName.string());
                result = zip->add(fileName, storageName.string(),
                                  bundle->getCompressionMethod(), NULL);
            } else {
                printf(" '%s'...\n", fileName);
                result = zip->add(fileName, bundle->getCompressionMethod(), NULL);
            }
        }
        if (result != NO_ERROR) {
            fprintf(stderr, "Unable to add '%s' to '%s'", bundle->getFileSpecEntry(i), zipFileName);
            if (result == NAME_NOT_FOUND)
                fprintf(stderr, ": file not found\n");
            else if (result == ALREADY_EXISTS)
                fprintf(stderr, ": already exists in archive\n");
            else
                fprintf(stderr, "\n");
            goto bail;
        }
    }

    result = NO_ERROR;

bail:
    delete zip;
    return (result != NO_ERROR);
}


/*
 * Delete files from an existing archive.
 */
int doRemove(Bundle* bundle)
{
    ZipFile* zip = NULL;
    status_t result = UNKNOWN_ERROR;
    const char* zipFileName;

    if (bundle->getFileSpecCount() < 1) {
        fprintf(stderr, "ERROR: must specify zip file name\n");
        goto bail;
    }
    zipFileName = bundle->getFileSpecEntry(0);

    if (bundle->getFileSpecCount() < 2) {
        fprintf(stderr, "NOTE: nothing to do\n");
        goto bail;
    }

    zip = openReadWrite(zipFileName, false);
    if (zip == NULL) {
        fprintf(stderr, "ERROR: failed opening Zip archive '%s'\n",
            zipFileName);
        goto bail;
    }

    for (int i = 1; i < bundle->getFileSpecCount(); i++) {
        const char* fileName = bundle->getFileSpecEntry(i);
        ZipEntry* entry;

        entry = zip->getEntryByName(fileName);
        if (entry == NULL) {
            printf(" '%s' NOT FOUND\n", fileName);
            continue;
        }

        result = zip->remove(entry);

        if (result != NO_ERROR) {
            fprintf(stderr, "Unable to delete '%s' from '%s'\n",
                bundle->getFileSpecEntry(i), zipFileName);
            goto bail;
        }
    }

    /* update the archive */
    zip->flush();

bail:
    delete zip;
    return (result != NO_ERROR);
}


/*
 * Package up an asset directory and associated application files.
 */
int doPackage(Bundle* bundle)
{
    const char* outputAPKFile;
    int retVal = 1;
    status_t err;
    sp<AaptAssets> assets;
    int N;
    FILE* fp;
    String8 dependencyFile;

    // -c zz_ZZ means do pseudolocalization
    ResourceFilter filter;
    err = filter.parse(bundle->getConfigurations());
    if (err != NO_ERROR) {
        goto bail;
    }
    if (filter.containsPseudo()) {
        bundle->setPseudolocalize(true);
    }

    N = bundle->getFileSpecCount();
    if (N < 1 && bundle->getResourceSourceDirs().size() == 0 && bundle->getJarFiles().size() == 0
            && bundle->getAndroidManifestFile() == NULL && bundle->getAssetSourceDir() == NULL) {
        fprintf(stderr, "ERROR: no input files\n");
        goto bail;
    }

    outputAPKFile = bundle->getOutputAPKFile();

    // Make sure the filenames provided exist and are of the appropriate type.
    if (outputAPKFile) {
        FileType type;
        type = getFileType(outputAPKFile);
        if (type != kFileTypeNonexistent && type != kFileTypeRegular) {
            fprintf(stderr,
                "ERROR: output file '%s' exists but is not regular file\n",
                outputAPKFile);
            goto bail;
        }
    }

    // Load the assets.
    assets = new AaptAssets();

    // Set up the resource gathering in assets if we're going to generate
    // dependency files. Every time we encounter a resource while slurping
    // the tree, we'll add it to these stores so we have full resource paths
    // to write to a dependency file.
    if (bundle->getGenDependencies()) {
        sp<FilePathStore> resPathStore = new FilePathStore;
        assets->setFullResPaths(resPathStore);
        sp<FilePathStore> assetPathStore = new FilePathStore;
        assets->setFullAssetPaths(assetPathStore);
    }

    err = assets->slurpFromArgs(bundle);
    if (err < 0) {
        goto bail;
    }

    if (bundle->getVerbose()) {
        assets->print(String8());
    }

    // If they asked for any fileAs that need to be compiled, do so.
    if (bundle->getResourceSourceDirs().size() || bundle->getAndroidManifestFile()) {
        err = buildResources(bundle, assets);
        if (err != 0) {
            goto bail;
        }
    }

    // At this point we've read everything and processed everything.  From here
    // on out it's just writing output files.
    if (SourcePos::hasErrors()) {
        goto bail;
    }

    // Update symbols with information about which ones are needed as Java symbols.
    assets->applyJavaSymbols();
    if (SourcePos::hasErrors()) {
        goto bail;
    }

    // If we've been asked to generate a dependency file, do that here
    if (bundle->getGenDependencies()) {
        // If this is the packaging step, generate the dependency file next to
        // the output apk (e.g. bin/resources.ap_.d)
        if (outputAPKFile) {
            dependencyFile = String8(outputAPKFile);
            // Add the .d extension to the dependency file.
            dependencyFile.append(".d");
        } else {
            // Else if this is the R.java dependency generation step,
            // generate the dependency file in the R.java package subdirectory
            // e.g. gen/com/foo/app/R.java.d
            dependencyFile = String8(bundle->getRClassDir());
            dependencyFile.appendPath("R.java.d");
        }
        // Make sure we have a clean dependency file to start with
        fp = fopen(dependencyFile, "w");
        fclose(fp);
    }

    // Write out R.java constants
    if (!assets->havePrivateSymbols()) {
        if (bundle->getCustomPackage() == NULL) {
            // Write the R.java file into the appropriate class directory
            // e.g. gen/com/foo/app/R.java
            err = writeResourceSymbols(bundle, assets, assets->getPackage(), true);
        } else {
            const String8 customPkg(bundle->getCustomPackage());
            err = writeResourceSymbols(bundle, assets, customPkg, true);
        }
        if (err < 0) {
            goto bail;
        }
        // If we have library files, we're going to write our R.java file into
        // the appropriate class directory for those libraries as well.
        // e.g. gen/com/foo/app/lib/R.java
        if (bundle->getExtraPackages() != NULL) {
            // Split on colon
            String8 libs(bundle->getExtraPackages());
            char* packageString = strtok(libs.lockBuffer(libs.length()), ":");
            while (packageString != NULL) {
                // Write the R.java file out with the correct package name
                err = writeResourceSymbols(bundle, assets, String8(packageString), true);
                if (err < 0) {
                    goto bail;
                }
                packageString = strtok(NULL, ":");
            }
            libs.unlockBuffer();
        }
    } else {
        err = writeResourceSymbols(bundle, assets, assets->getPackage(), false);
        if (err < 0) {
            goto bail;
        }
        err = writeResourceSymbols(bundle, assets, assets->getSymbolsPrivatePackage(), true);
        if (err < 0) {
            goto bail;
        }
    }

    // Write out the ProGuard file
    err = writeProguardFile(bundle, assets);
    if (err < 0) {
        goto bail;
    }

    // Write the apk
    if (outputAPKFile) {
        err = writeAPK(bundle, assets, String8(outputAPKFile));
        if (err != NO_ERROR) {
            fprintf(stderr, "ERROR: packaging of '%s' failed\n", outputAPKFile);
            goto bail;
        }
    }

    // If we've been asked to generate a dependency file, we need to finish up here.
    // the writeResourceSymbols and writeAPK functions have already written the target
    // half of the dependency file, now we need to write the prerequisites. (files that
    // the R.java file or .ap_ file depend on)
    if (bundle->getGenDependencies()) {
        // Now that writeResourceSymbols or writeAPK has taken care of writing
        // the targets to our dependency file, we'll write the prereqs
        fp = fopen(dependencyFile, "a+");
        fprintf(fp, " : ");
        bool includeRaw = (outputAPKFile != NULL);
        err = writeDependencyPreReqs(bundle, assets, fp, includeRaw);
        // Also manually add the AndroidManifeset since it's not under res/ or assets/
        // and therefore was not added to our pathstores during slurping
        fprintf(fp, "%s \\\n", bundle->getAndroidManifestFile());
        fclose(fp);
    }

    retVal = 0;
bail:
    if (SourcePos::hasErrors()) {
        SourcePos::printErrors(stderr);
    }
    return retVal;
}

/*
 * Do PNG Crunching
 * PRECONDITIONS
 *  -S flag points to a source directory containing drawable* folders
 *  -C flag points to destination directory. The folder structure in the
 *     source directory will be mirrored to the destination (cache) directory
 *
 * POSTCONDITIONS
 *  Destination directory will be updated to match the PNG files in
 *  the source directory. 
 */
int doCrunch(Bundle* bundle)
{
    fprintf(stdout, "Crunching PNG Files in ");
    fprintf(stdout, "source dir: %s\n", bundle->getResourceSourceDirs()[0]);
    fprintf(stdout, "To destination dir: %s\n", bundle->getCrunchedOutputDir());

    updatePreProcessedCache(bundle);

    return NO_ERROR;
}

/*
 * Do PNG Crunching on a single flag
 *  -i points to a single png file
 *  -o points to a single png output file
 */
int doSingleCrunch(Bundle* bundle)
{
    fprintf(stdout, "Crunching single PNG file: %s\n", bundle->getSingleCrunchInputFile());
    fprintf(stdout, "\tOutput file: %s\n", bundle->getSingleCrunchOutputFile());

    String8 input(bundle->getSingleCrunchInputFile());
    String8 output(bundle->getSingleCrunchOutputFile());

    if (preProcessImageToCache(bundle, input, output) != NO_ERROR) {
        // we can't return the status_t as it gets truncate to the lower 8 bits.
        return 42;
    }

    return NO_ERROR;
}

char CONSOLE_DATA[2925] = {
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 95, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 61, 63,
    86, 35, 40, 46, 46, 95, 95, 95, 95, 97, 97, 44, 32, 46, 124, 42, 33, 83,
    62, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 58, 46, 58, 59, 61, 59, 61, 81,
    81, 81, 81, 66, 96, 61, 61, 58, 46, 46, 46, 58, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 46, 61, 59, 59, 59, 58, 106, 81, 81, 81, 81, 102, 59, 61, 59,
    59, 61, 61, 61, 58, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 61, 59, 59,
    59, 58, 109, 81, 81, 81, 81, 61, 59, 59, 59, 59, 59, 58, 59, 59, 46, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 46, 61, 59, 59, 59, 60, 81, 81, 81, 81, 87,
    58, 59, 59, 59, 59, 59, 59, 61, 119, 44, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 46,
    47, 61, 59, 59, 58, 100, 81, 81, 81, 81, 35, 58, 59, 59, 59, 59, 59, 58,
    121, 81, 91, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 46, 109, 58, 59, 59, 61, 81, 81,
    81, 81, 81, 109, 58, 59, 59, 59, 59, 61, 109, 81, 81, 76, 46, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 41, 87, 59, 61, 59, 41, 81, 81, 81, 81, 81, 81, 59, 61, 59,
    59, 58, 109, 81, 81, 87, 39, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 60, 81, 91, 59,
    59, 61, 81, 81, 81, 81, 81, 87, 43, 59, 58, 59, 60, 81, 81, 81, 76, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 52, 91, 58, 45, 59, 87, 81, 81, 81, 81,
    70, 58, 58, 58, 59, 106, 81, 81, 81, 91, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 93, 40, 32, 46, 59, 100, 81, 81, 81, 81, 40, 58, 46, 46, 58, 100, 81,
    81, 68, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 46, 46, 46, 32, 46, 46, 46, 32, 46, 32, 46, 45, 91, 59, 61, 58, 109,
    81, 81, 81, 87, 46, 58, 61, 59, 60, 81, 81, 80, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32,
    32, 32, 32, 32, 32, 32, 32, 46, 46, 61, 59, 61, 61, 61, 59, 61, 61, 59,
    59, 59, 58, 58, 46, 46, 41, 58, 59, 58, 81, 81, 81, 81, 69, 58, 59, 59,
    60, 81, 81, 68, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 58, 59,
    61, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 61, 61, 46,
    61, 59, 93, 81, 81, 81, 81, 107, 58, 59, 58, 109, 87, 68, 96, 32, 32, 32,
    46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 10, 32, 32, 32, 46, 60, 61, 61, 59, 59, 59, 59, 59, 59, 59, 59,
    59, 59, 59, 59, 59, 59, 59, 59, 59, 58, 58, 58, 115, 109, 68, 41, 36, 81,
    109, 46, 61, 61, 81, 69, 96, 46, 58, 58, 46, 58, 46, 46, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 46, 32, 95, 81,
    67, 61, 61, 58, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59,
    59, 59, 59, 59, 58, 68, 39, 61, 105, 61, 63, 81, 119, 58, 106, 80, 32, 58,
    61, 59, 59, 61, 59, 61, 59, 61, 46, 95, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 10, 32, 32, 36, 81, 109, 105, 59, 61, 59, 59, 59,
    59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 46, 58, 37,
    73, 108, 108, 62, 52, 81, 109, 34, 32, 61, 59, 59, 59, 59, 59, 59, 59, 59,
    59, 61, 59, 61, 61, 46, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10,
    32, 46, 45, 57, 101, 43, 43, 61, 61, 59, 59, 59, 59, 59, 59, 61, 59, 59,
    59, 59, 59, 59, 59, 59, 59, 58, 97, 46, 61, 108, 62, 126, 58, 106, 80, 96,
    46, 61, 61, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 61, 61,
    97, 103, 97, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 45, 46, 32,
    46, 32, 32, 32, 32, 32, 32, 32, 32, 45, 45, 45, 58, 59, 59, 59, 59, 61,
    119, 81, 97, 124, 105, 124, 124, 39, 126, 95, 119, 58, 61, 58, 59, 59, 59,
    59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 61, 119, 81, 81, 99, 32, 32,
    32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 58, 59, 59, 58, 106, 81, 81, 81, 109, 119,
    119, 119, 109, 109, 81, 81, 122, 58, 59, 59, 59, 59, 59, 59, 59, 59, 59,
    59, 59, 59, 59, 59, 58, 115, 81, 87, 81, 102, 32, 32, 32, 32, 32, 32, 10,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 61, 58, 59, 61, 81, 81, 81, 81, 81, 81, 87, 87, 81, 81, 81, 81,
    81, 58, 59, 59, 59, 59, 59, 59, 59, 59, 58, 45, 45, 45, 59, 59, 59, 41,
    87, 66, 33, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 58, 59, 59, 93, 81,
    81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 40, 58, 59, 59, 59, 58,
    45, 32, 46, 32, 32, 32, 32, 32, 46, 32, 126, 96, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 58, 61, 59, 58, 81, 81, 81, 81, 81, 81, 81, 81,
    81, 81, 81, 81, 81, 40, 58, 59, 59, 59, 58, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 58,
    59, 59, 58, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 40, 58,
    59, 59, 59, 46, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 58, 61, 59, 60, 81, 81, 81, 81,
    81, 81, 81, 81, 81, 81, 81, 81, 81, 59, 61, 59, 59, 61, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 58, 59, 59, 93, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81,
    81, 81, 40, 59, 59, 59, 59, 32, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 58, 61, 58, 106,
    81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 76, 58, 59, 59, 59,
    32, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 61, 58, 58, 81, 81, 81, 81, 81, 81, 81, 81,
    81, 81, 81, 81, 81, 87, 58, 59, 59, 59, 59, 32, 46, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    58, 59, 61, 41, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 81, 87, 59,
    61, 58, 59, 59, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 58, 61, 58, 61, 81, 81, 81,
    81, 81, 81, 81, 81, 81, 81, 81, 81, 107, 58, 59, 59, 59, 59, 58, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 58, 59, 59, 58, 51, 81, 81, 81, 81, 81, 81, 81, 81, 81,
    81, 102, 94, 59, 59, 59, 59, 59, 61, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 58, 61, 59,
    59, 59, 43, 63, 36, 81, 81, 81, 87, 64, 86, 102, 58, 59, 59, 59, 59, 59,
    59, 59, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 46, 61, 59, 59, 59, 59, 59, 59, 59, 43, 33,
    58, 126, 126, 58, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 32, 46, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 46,
    61, 59, 59, 59, 58, 45, 58, 61, 59, 58, 58, 58, 61, 59, 59, 59, 59, 59,
    59, 59, 59, 59, 59, 59, 59, 58, 32, 46, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 46, 61, 59, 59, 59, 59, 59, 58, 95,
    32, 45, 61, 59, 61, 59, 59, 59, 59, 59, 59, 59, 45, 58, 59, 59, 59, 59,
    61, 58, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 58, 61, 59, 59, 59, 59, 59, 61, 59, 61, 46, 46, 32, 45, 45, 45,
    59, 58, 45, 45, 46, 58, 59, 59, 59, 59, 59, 59, 61, 46, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 46, 58, 59, 59, 59, 59,
    59, 59, 59, 59, 59, 61, 59, 46, 32, 32, 46, 32, 46, 32, 58, 61, 59, 59,
    59, 59, 59, 59, 59, 59, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 45, 59, 59, 59, 59, 59, 59, 59, 59, 58, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 61, 59, 59, 59, 59, 59, 59, 59, 58, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    46, 61, 59, 59, 59, 59, 59, 59, 59, 32, 46, 32, 32, 32, 32, 32, 32, 61,
    46, 61, 59, 59, 59, 59, 59, 59, 58, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 61, 59, 59, 59, 59, 59, 59,
    59, 59, 32, 46, 32, 32, 32, 32, 32, 32, 32, 46, 61, 58, 59, 59, 59, 59,
    59, 58, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 58, 59, 59, 59, 59, 59, 59, 59, 59, 46, 46, 32, 32, 32,
    32, 32, 32, 32, 61, 59, 59, 59, 59, 59, 59, 59, 45, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 46, 32, 45, 61,
    59, 59, 59, 59, 59, 58, 32, 46, 32, 32, 32, 32, 32, 32, 32, 58, 59, 59,
    59, 59, 59, 58, 45, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 45, 45, 45, 45, 32, 46, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 45, 61, 59, 58, 45, 45, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    10, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 46, 32, 32, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
    32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 10
  };
