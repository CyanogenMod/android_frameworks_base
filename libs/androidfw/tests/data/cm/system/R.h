#ifndef __ANDROID_R_H
#define __ANDROID_R_H

namespace android {
namespace R {

namespace attr {
    enum {
        background      = 0x01010000, // default
        foreground      = 0x01010001, // default
        some_dimen      = 0x01010002, // default
        another_dimen   = 0x01010003, // default
        windowNoTitle   = 0x01010056, // default
    };
}

namespace style {
    enum {
        Theme_One      = 0x01020000,   // default
    };
}

} // namespace R
} // namespace android

#endif // __ANDROID_R_H
