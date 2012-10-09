//
// Copyright 2012 The Android Open Source Project
//
// Cache for resIds - we tend to lookup the same thing repeatedly
//

#include "ResourceIdCache.h"

struct lutEntry {
    String16 package;
    String16 type;
    String16 name;
    bool onlyPublic;
    uint32_t resId;
};

// more than enough - wastes about 100kb of memory (ex strings)
static const int lutCapacity = 4096;
static struct lutEntry* lut = NULL;
static int lutUsed = 0;

static bool lutAlloc() {
    if (lut == NULL) lut = new struct lutEntry[lutCapacity];
    return (lut != NULL);
}

uint32_t ResourceIdCache::lookup(const String16& package,
                                 const String16& type,
                                 const String16& name,
                                 bool onlyPublic)
{
    if (!lutAlloc()) return 0;

    for (int i = 0; i < lutUsed; i++) {
        if (
            // name is most likely to be different
            (name == lut[i].name) &&
            (type == lut[i].type) &&
            (package == lut[i].package) &&
            (onlyPublic == lut[i].onlyPublic)
        ) {
            return lut[i].resId;
        }
    }
    return 0;
}

bool ResourceIdCache::store(const String16& package,
                            const String16& type,
                            const String16& name,
                            bool onlyPublic,
                            uint32_t resId)
{
    if (!lutAlloc()) return false;

    if (lutUsed < lutCapacity) {
        lut[lutUsed].package = String16(package);
        lut[lutUsed].type = String16(type);
        lut[lutUsed].name = String16(name);
        lut[lutUsed].onlyPublic = onlyPublic;
        lut[lutUsed].resId = resId;
        lutUsed++;
        return true;
    } else {
        return false;
    }
}
