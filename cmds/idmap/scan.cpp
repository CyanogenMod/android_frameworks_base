#include <dirent.h>
#include <inttypes.h>
#include <sys/stat.h>

#include "idmap.h"

#include <UniquePtr.h>
#include <androidfw/ResourceTypes.h>
#include <androidfw/StreamingZipInflater.h>
#include <androidfw/ZipFileRO.h>
#include <private/android_filesystem_config.h> // for AID_SYSTEM
#include <utils/SortedVector.h>
#include <utils/String16.h>
#include <utils/String8.h>

#define NO_OVERLAY_TAG (-1000)

using namespace android;

namespace {
    struct Overlay {
        Overlay() {}
        Overlay(const String8& a, const String8& i, int p) :
            apk_path(a), idmap_path(i), priority(p) {}

        bool operator<(Overlay const& rhs) const
        {
            return rhs.priority > priority;
        }

        String8 apk_path;
        String8 idmap_path;
        int priority;
    };

    bool writePackagesList(const char *filename, const SortedVector<Overlay>& overlayVector)
    {
        FILE* fout = fopen(filename, "w");
        if (fout == NULL) {
            return false;
        }

        for (size_t i = 0; i < overlayVector.size(); ++i) {
            const Overlay& overlay = overlayVector[i];
            fprintf(fout, "%s %s\n", overlay.apk_path.string(), overlay.idmap_path.string());
        }

        fclose(fout);

        // Make file world readable since Zygote (running as root) will read
        // it when creating the initial AssetManger object
        const mode_t mode = S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH; // 0644
        if (chmod(filename, mode) == -1) {
            unlink(filename);
            return false;
        }

        return true;
    }

    String8 flatten_path(const char *path)
    {
        String16 tmp(path);
        tmp.replaceAll('/', '@');
        return String8(tmp);
    }

    int parse_overlay_tag(const ResXMLTree& parser, const char *target_package_name)
    {
        const size_t N = parser.getAttributeCount();
        String16 target;
        int priority = -1;
        for (size_t i = 0; i < N; ++i) {
            size_t len;
            String16 key(parser.getAttributeName(i, &len));
            if (key == String16("targetPackage")) {
                const char16_t *p = parser.getAttributeStringValue(i, &len);
                if (p != NULL) {
                    target = String16(p, len);
                }
            } else if (key == String16("priority")) {
                Res_value v;
                if (parser.getAttributeValue(i, &v) == sizeof(Res_value)) {
                    priority = v.data;
                    if (priority < 0 || priority > 9999) {
                        return -1;
                    }
                }
            }
        }
        if (target == String16(target_package_name)) {
            return priority;
        }
        return NO_OVERLAY_TAG;
    }

    int parse_manifest(const void *data, size_t size, const char *target_package_name)
    {
        ResXMLTree parser;
        parser.setTo(data, size);
        if (parser.getError() != NO_ERROR) {
            ALOGD("%s failed to init xml parser, error=0x%08x\n", __FUNCTION__, parser.getError());
            return -1;
        }

        ResXMLParser::event_code_t type;
        do {
            type = parser.next();
            if (type == ResXMLParser::START_TAG) {
                size_t len;
                String16 tag(parser.getElementName(&len));
                if (tag == String16("overlay")) {
                    return parse_overlay_tag(parser, target_package_name);
                }
            }
        } while (type != ResXMLParser::BAD_DOCUMENT && type != ResXMLParser::END_DOCUMENT);

        return NO_OVERLAY_TAG;
    }

    int parse_apk(const char *path, const char *target_package_name)
    {
        UniquePtr<ZipFileRO> zip(ZipFileRO::open(path));
        if (zip.get() == NULL) {
            ALOGW("%s: failed to open zip %s\n", __FUNCTION__, path);
            return -1;
        }
        ZipEntryRO entry;
        if ((entry = zip->findEntryByName("AndroidManifest.xml")) == NULL) {
            ALOGW("%s: failed to find entry AndroidManifest.xml\n", __FUNCTION__);
            return -1;
        }
        uint32_t uncompLen = 0;
        uint16_t method;
        if (!zip->getEntryInfo(entry, &method, &uncompLen, NULL, NULL, NULL, NULL)) {
            ALOGW("%s: failed to read entry info\n", __FUNCTION__);
            return -1;
        }
        if (method != ZipFileRO::kCompressDeflated) {
            ALOGW("%s: cannot handle zip compression method %" PRIu16 "\n", __FUNCTION__, method);
            return -1;
        }
        FileMap *dataMap = zip->createEntryFileMap(entry);
        if (dataMap == NULL) {
            ALOGW("%s: failed to create FileMap\n", __FUNCTION__);
            return -1;
        }
        char *buf = new char[uncompLen];
        if (NULL == buf) {
            ALOGW("%s: failed to allocate %" PRIu32 " byte\n", __FUNCTION__, uncompLen);
            delete dataMap;
            return -1;
        }
        StreamingZipInflater inflater(dataMap, uncompLen);
        if (inflater.read(buf, uncompLen) < 0) {
            ALOGW("%s: failed to inflate %" PRIu32 " byte\n", __FUNCTION__, uncompLen);
            delete[] buf;
            delete dataMap;
            return -1;
        }

        int priority = parse_manifest(buf, static_cast<size_t>(uncompLen), target_package_name);
        delete[] buf;
        delete dataMap;
        return priority;
    }

    int idmap_scan(const char *overlay_dir, const char *target_package_name,
            const char *target_apk_path, const char *idmap_dir,
            SortedVector<Overlay>& overlayVector)
    {
        DIR *dir = opendir(overlay_dir);
        if (dir == NULL) {
            return EXIT_FAILURE;
        }

        struct dirent *dirent;
        while ((dirent = readdir(dir)) != NULL) {
            struct stat st;
            char overlay_apk_path[PATH_MAX + 1];
            snprintf(overlay_apk_path, PATH_MAX, "%s/%s", overlay_dir, dirent->d_name);
            if (stat(overlay_apk_path, &st) < 0) {
                continue;
            }
            if (!S_ISREG(st.st_mode) && !S_ISDIR(st.st_mode)) {
                continue;
            }

            if (S_ISDIR(st.st_mode)) {
                String8 dir_name = String8(overlay_apk_path).getPathLeaf();
                if (dir_name == "." || dir_name == "..") {
                    // Skip the "." and ".." dir.
                    continue;
                }
                idmap_scan(overlay_apk_path, target_package_name, target_apk_path, idmap_dir,
                        overlayVector);
            } else {
                int priority = parse_apk(overlay_apk_path, target_package_name);
                if (priority < 0) {
                    continue;
                }

                String8 idmap_path(idmap_dir);
                idmap_path.appendPath(flatten_path(overlay_apk_path + 1));
                idmap_path.append("@idmap");

                if (idmap_create_path(target_apk_path, overlay_apk_path, NULL, 0, 0,
                        idmap_path.string()) != 0) {
                    ALOGE("error: failed to create idmap for target=%s overlay=%s idmap=%s\n",
                            target_apk_path, overlay_apk_path, idmap_path.string());
                    continue;
                }

                Overlay overlay(String8(overlay_apk_path), idmap_path, priority);
                overlayVector.add(overlay);
            }
        }

        closedir(dir);

        return EXIT_SUCCESS;
    }
}

int idmap_scan(const char *overlay_dir, const char *target_package_name,
        const char *target_apk_path, const char *idmap_dir)
{
    String8 filename = String8(idmap_dir);
    filename.appendPath("overlays.list");
    if (unlink(filename.string()) != 0 && errno != ENOENT) {
        return EXIT_FAILURE;
    }

    SortedVector<Overlay> overlayVector;
    int res = idmap_scan(overlay_dir, target_package_name, target_apk_path, idmap_dir,
            overlayVector);

    if (res == EXIT_FAILURE || !writePackagesList(filename.string(), overlayVector)) {
        return EXIT_FAILURE;
    }

    return res;
}
