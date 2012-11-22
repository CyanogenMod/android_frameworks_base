#include "idmap.h"

#include <androidfw/ResourceTypes.h>
#include <androidfw/StreamingZipInflater.h>
#include <private/android_filesystem_config.h> // for AID_SYSTEM
#include <utils/String16.h>
#include <utils/String8.h>
#include <utils/ZipFileRO.h>

#include <dirent.h>

#define NO_OVERLAY_TAG (-1000)

using namespace android;

namespace {
    String8 flatten_path(const char *path)
    {
        String16 tmp(path);
        tmp.replaceAll('/', '@');
        return String8(tmp);
    }

    int mkdir_p(const String8& path, uid_t uid, gid_t gid)
    {
        static const mode_t mode =
            S_IRUSR | S_IWUSR | S_IXUSR | S_IRGRP | S_IWGRP | S_IXGRP | S_IROTH | S_IXOTH;
        struct stat st;

        if (stat(path.string(), &st) == 0) {
            return 0;
        }
        if (mkdir_p(path.getPathDir(), uid, gid) < 0) {
            return -1;
        }
        if (mkdir(path.string(), 0755) != 0) {
            return -1;
        }
        if (chown(path.string(), uid, gid) == -1) {
            return -1;
        }
        if (chmod(path.string(), mode) == -1) {
            return -1;
        }
        return 0;
    }

    int create_idmap_symlink(const char *old_path, const char *target_apk, int priority,
            const char *prefix)
    {
        const String8 new_path =
            String8::format("%s/%s/%04d", prefix, flatten_path(target_apk + 1).string(), priority);
        if (mkdir_p(new_path.getPathDir(), AID_SYSTEM, AID_SYSTEM) < 0) {
            return -1;
        }
        (void)unlink(new_path.string());
        int r = symlink(old_path, new_path.string());
        if (r != 0) {
            ALOGD("error: symlink: %s\n", strerror(errno));
        }
        return r;
    }

    int parse_overlay_tag(const ResXMLTree& parser, const char *target_package_name)
    {
        const size_t N = parser.getAttributeCount();
        String16 target;
        int priority = -1;
        for (size_t i = 0; i < N; ++i) {
            size_t len;
            String16 key(parser.getAttributeName(i, &len));
            String16 value("");
            const uint16_t *p = parser.getAttributeStringValue(i, &len);
            if (p) {
                value = String16(p);
            }
            if (key == String16("target")) {
                target = value;
            } else if (key == String16("priority") && value.size() > 0) {
                priority = atoi(String8(value).string());
                if (priority < 0 || priority > 9999) {
                    return -1;
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
        ResXMLTree parser(data, size);
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
        ZipFileRO zip;
        if (zip.open(path) != NO_ERROR) {
            ALOGW("%s: failed to open zip %s\n", __FUNCTION__, path);
            return -1;
        }
        ZipEntryRO entry;
        if ((entry = zip.findEntryByName("AndroidManifest.xml")) == NULL) {
            ALOGW("%s: failed to find entry AndroidManifest.xml\n", __FUNCTION__);
            return -1;
        }
        size_t uncompLen = 0;
        int method;
        if (!zip.getEntryInfo(entry, &method, &uncompLen, NULL, NULL, NULL, NULL)) {
            ALOGW("%s: failed to read entry info\n", __FUNCTION__);
            return -1;
        }
        if (method != ZipFileRO::kCompressDeflated) {
            ALOGW("%s: cannot handle zip compression method %d\n", __FUNCTION__, method);
            return -1;
        }
        FileMap *dataMap = zip.createEntryFileMap(entry);
        if (!dataMap) {
            ALOGW("%s: failed to create FileMap\n", __FUNCTION__);
            return -1;
        }
        char *buf = new char[uncompLen];
        if (NULL == buf) {
            ALOGW("%s: failed to allocate %d byte\n", __FUNCTION__, uncompLen);
            dataMap->release();
            return -1;
        }
        StreamingZipInflater inflater(dataMap, uncompLen);
        if (inflater.read(buf, uncompLen) < 0) {
            ALOGW("%s: failed to inflate %d byte\n", __FUNCTION__, uncompLen);
            delete[] buf;
            dataMap->release();
            return -1;
        }

        int priority = parse_manifest(buf, uncompLen, target_package_name);
        delete[] buf;
        dataMap->release();
        return priority;
    }
}

int idmap_scan(const char *overlay_dir, const char *target_package_name,
        const char *target_apk_path, const char *idmap_dir, const char *symlink_dir)
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
        if (!S_ISREG(st.st_mode)) {
            continue;
        }

        int priority = parse_apk(overlay_apk_path, target_package_name);
        if (priority < 0) {
            continue;
        }

        String8 idmap_path(idmap_dir);
        idmap_path.appendPath(flatten_path(overlay_apk_path + 1));
        idmap_path.append("@idmap");

        if (idmap_create_path(target_apk_path, overlay_apk_path, idmap_path.string()) != 0) {
            ALOGE("error: failed to create idmap for target=%s overlay=%s idmap=%s\n",
                    target_apk_path, overlay_apk_path, idmap_path.string());
            continue;
        }

        if (create_idmap_symlink(idmap_path.string(), target_apk_path, priority,
                    symlink_dir) != 0) {
            ALOGE("error: failed to create symlink for %s\n", idmap_path.string());
            (void)unlink(idmap_path.string());
            continue;
        }
    }

    closedir(dir);
    return EXIT_SUCCESS;
}
