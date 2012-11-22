/*
 * NAME
 *       idmap - create or display idmap files
 *
 * SYNOPSIS
 *       idmap --fd target overlay fd
 *       idmap --path target overlay idmap
 *       idmap --scan dir-to-scan target-to-look-for target dir-to-hold-idmaps dir-to-hold-symlinks
 *       idmap --inspect idmap
 *
 * DESCRIPTION
 *       Idmap files play an integral part in the runtime resource overlay framework. An idmap
 *       file contains a mapping of resource identifiers between overlay package and its target
 *       package; this mapping is used during resource lookup. Idmap files also act as control
 *       files by their existence: if not present, the corresponding overlay package is ignored
 *       when the resource context is created.
 *
 *       Idmap files are stored in /data/resource-cache. For each pair (target package, overlay
 *       package), there exists exactly one idmap file, or none if the overlay should not be used.
 *
 *       Idmap files may be disabled/enabled by not looking directly for the idmap but instead
 *       for a symbolic link to it. The symlinks may be removed and recreated at a lower cost than
 *       re-calculating the idmap file contents.
 *
 * NOMENCLATURE
 *       target: the original, non-overlay, package. Each target package may be associated with
 *               any number of overlay packages.
 *
 *       overlay: an overlay package. Each overlay package is associated with exactly one target
 *                package, specified in the overlay's manifest using the <overlay target="..."/>
 *                tag.
 *
 * OPTIONS
 *       --fd: create idmap for target package 'target' (path to apk) and overlay package 'overlay'
 *             (path to apk); write results to file descriptor 'fd' (integer). This invocation
 *             version is intended to be used by a parent process with higher privileges to call
 *             idmap in a controlled way: the parent will open a suitable file descriptor, fork,
 *             drop its privileges and exec. This tool will continue execution without the extra
 *             privileges, but still have write access to a file it could not have opened on its
 *             own.
 *
 *       --path: create idmap for target package 'target' (path to apk) and overlay package
 *               'overlay' (path to apk); write results to 'idmap' (path).
 *
 *       --scan: non-recursively search directory 'dir-to-scan' (path) for overlay packages with
 *               target package 'target-to-look-for' (package name) present at 'target' (path to
 *               apk). For each overlay package found, create an idmap file in 'dir-to-hold-idmaps'
 *               (path) and a corresponding symlink in 'dir-to-hold-symlinks'.
 *
 *       --inspect: decode the binary format of 'idmap' (path) and display the contents in a
 *                  debug-friendly format.
 *
 * EXAMPLES
 *       Create an idmap file:
 *
 *       $ adb shell idmap --path /system/app/target.apk \
 *                                /vendor/overlay/overlay.apk \
 *                                /data/resource-cache/vendor@overlay@overlay.apk@idmap
 *
 *       Display an idmap file:
 *
 *       $ adb shell idmap --inspect /data/resource-cache/vendor@overlay@overlay.apk@idmap
 *       SECTION      ENTRY        VALUE      OFFSET    COMMENT
 *       IDMAP HEADER magic        0x706d6469 0x0
 *                    base crc     0x484aa77f 0x1
 *                    overlay crc  0x03c66fa5 0x2
 *                    base path    .......... 0x03-0x42 /system/app/target.apk
 *                    overlay path .......... 0x43-0x82 /vendor/overlay/overlay.apk
 *       DATA HEADER  types count  0x00000003 0x83
 *                    padding      0x00000000 0x84
 *                    type offset  0x00000004 0x85      absolute offset 0x87, xml
 *                    type offset  0x00000007 0x86      absolute offset 0x8a, string
 *       DATA BLOCK   entry count  0x00000001 0x87
 *                    entry offset 0x00000000 0x88
 *                    entry        0x7f020000 0x89      xml/integer
 *       DATA BLOCK   entry count  0x00000002 0x8a
 *                    entry offset 0x00000000 0x8b
 *                    entry        0x7f030000 0x8c      string/str
 *                    entry        0x7f030001 0x8d      string/str2
 *
 *       In this example, the overlay package provides three alternative resource values:
 *       xml/integer, string/str and string/str2.
 *
 * NOTES
 *       This tool and its expected invocation from installd is modelled on dexopt.
 */
#include "idmap.h"

#include <private/android_filesystem_config.h> // for AID_SYSTEM

#include <stdlib.h>
#include <string.h>

namespace {
    bool verify_directory_readable(const char *path)
    {
        return access(path, R_OK | X_OK);
    }

    bool verify_directory_writable(const char *path)
    {
        return access(path, W_OK);
    }

    bool verify_file_readable(const char *path)
    {
        return access(path, R_OK) == 0;
    }

    bool verify_root_or_system()
    {
        uid_t uid = getuid();
        gid_t gid = getgid();

        return (uid == 0 && gid == 0) || (uid == AID_SYSTEM && gid == AID_SYSTEM);
    }

    int maybe_create_fd(const char *target_apk_path, const char *overlay_apk_path,
            const char *idmap_str)
    {
        // anyone (not just root or system) may do --fd -- the file has
        // already been opened by someone else on our behalf

        char *endptr;
        int idmap_fd = strtol(idmap_str, &endptr, 10);
        if (*endptr != '\0') {
            fprintf(stderr, "error: failed to parse file descriptor argument %s\n", idmap_str);
            return -1;
        }

        if (verify_file_readable(target_apk_path) == -1) {
            ALOGD("error: failed to read apk %s: %s\n", target_apk_path, strerror(errno));
            return -1;
        }

        if (verify_file_readable(overlay_apk_path) == -1) {
            ALOGD("error: failed to read apk %s: %s\n", overlay_apk_path, strerror(errno));
            return -1;
        }

        return idmap_create_fd(target_apk_path, overlay_apk_path, idmap_fd);
    }

    int maybe_create_path(const char *target_apk_path, const char *overlay_apk_path,
            const char *idmap_path)
    {
        if (!verify_root_or_system()) {
            fprintf(stderr, "error: permission denied: not user root or user system\n");
            return -1;
        }

        if (verify_file_readable(target_apk_path) == -1) {
            ALOGD("error: failed to read apk %s: %s\n", target_apk_path, strerror(errno));
            return -1;
        }

        if (verify_file_readable(overlay_apk_path) == -1) {
            ALOGD("error: failed to read apk %s: %s\n", overlay_apk_path, strerror(errno));
            return -1;
        }

        return idmap_create_path(target_apk_path, overlay_apk_path, idmap_path);
    }

    int maybe_scan(const char *overlay_dir, const char *target_package_name,
            const char *target_apk_path, const char *idmap_dir, const char *symlink_dir)
    {
        if (!verify_root_or_system()) {
            fprintf(stderr, "error: permission denied: not user root or user system\n");
            return -1;
        }

        if (verify_directory_readable(overlay_dir) == -1) {
            ALOGD("error: no read access to %s: %s\n", overlay_dir, strerror(errno));
            return -1;
        }

        if (verify_file_readable(target_apk_path) == -1) {
            ALOGD("error: failed to read apk %s: %s\n", target_apk_path, strerror(errno));
            return -1;
        }

        if (verify_directory_writable(overlay_dir) == -1) {
            ALOGD("error: no write access to %s: %s\n", idmap_dir, strerror(errno));
            return -1;
        }

        if (verify_directory_writable(symlink_dir) == -1) {
            ALOGD("error: no write access to %s: %s\n", symlink_dir, strerror(errno));
            return -1;
        }

        return idmap_scan(overlay_dir, target_package_name, target_apk_path,
                idmap_dir, symlink_dir);
    }

    int maybe_inspect(const char *idmap_path)
    {
        // anyone (not just root or system) may do --inspect
        if (verify_file_readable(idmap_path) == -1) {
            ALOGD("error: failed to read idmap %s: %s\n", idmap_path, strerror(errno));
            return -1;
        }
        return idmap_inspect(idmap_path);
    }
}

int main(int argc, char **argv)
{
#if 0
    {
        char buf[1024];
        buf[0] = '\0';
        for (int i = 0; i < argc; ++i) {
            strncat(buf, argv[i], sizeof(buf) - 1);
            strncat(buf, " ", sizeof(buf) - 1);
        }
        ALOGD("%s:%d: uid=%d gid=%d argv=%s\n", __FILE__, __LINE__, getuid(), getgid(), buf);
    }
#endif

    if (argc == 5 && !strcmp(argv[1], "--fd")) {
        return maybe_create_fd(argv[2], argv[3], argv[4]);
    }

    if (argc == 5 && !strcmp(argv[1], "--path")) {
        return maybe_create_path(argv[2], argv[3], argv[4]);
    }

    if (argc == 7 && !strcmp(argv[1], "--scan")) {
        return maybe_scan(argv[2], argv[3], argv[4], argv[5], argv[6]);
    }

    if (argc == 3 && !strcmp(argv[1], "--inspect")) {
        return maybe_inspect(argv[2]);
    }

    fprintf(stderr, "Usage: don't use this (cf dexopt usage).\n");
    return EXIT_FAILURE;
}
