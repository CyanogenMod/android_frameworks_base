#ifndef __APP_R_H
#define __APP_R_H

namespace app {
namespace R {

namespace attr {
    enum {
        number         = 0x7f010000,   // default
    };
}

namespace style {
    enum {
        Theme_One      = 0x7f020000,   // default
        Theme_Two      = 0x7f020001,   // default
        Theme_Three    = 0x7f020002,   // default
        Theme_Four     = 0x7f020003,   // default
    };
}

namespace color {
    enum {
        app_color    = 0x7f030000,   // default
    };
}

} // namespace R
} // namespace app

#endif // __APP_R_H
