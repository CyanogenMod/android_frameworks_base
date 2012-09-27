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

// more than enough... uses about 100kb of memory
static const int lutCapacity = 4096;
static struct lutEntry lut[lutCapacity];
static int lutUsed = 0;

uint32_t ResourceIdCache::lookup(const String16& package,
                                 const String16& type,
                                 const String16& name,
                                 bool onlyPublic)
{
    for (int i = 0; i < lutUsed; i++) {
        if (
            // name is most likely to be different
            (name == lut[i].name) &&
            (package == lut[i].package) &&
            (type == lut[i].type) &&
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
