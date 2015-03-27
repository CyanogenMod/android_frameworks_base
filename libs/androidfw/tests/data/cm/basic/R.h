#ifndef __BASE_R_H
#define __BASE_R_H

namespace base {
namespace R {

namespace attr {
    enum {
        attr1       = 0x7f010000, // default
        attr2       = 0x7f010001, // default
    };
}

namespace drawable {
    enum {
        drawable1   = 0x7f020000, // default
    };
}

namespace layout {
    enum {
        main        = 0x7f030000,  // default, fr-sw600dp-v13
    };
}

namespace string {
    enum {
        test1       = 0x7f040000,   // default
        test2       = 0x7f040001,   // default

        test3       = 0x7f0a0000,   // default (in feature)
        test4       = 0x7f0a0001,   // default (in feature)
    };
}

namespace integer {
    enum {
        number1     = 0x7f050000,   // default, sv
        number2     = 0x7f050001,   // default

        test3       = 0x7f0b0000,   // default (in feature)
    };
}

namespace style {
    enum {
        Theme1      = 0x7f060000,   // default
        Theme2      = 0x7f060001,   // default
    };
}

namespace array {
    enum {
        integerArray1 = 0x7f070000,   // default
    };
}

namespace dimen {
    enum {
        dimen1 = 0x7f080000,   // default
        dimen2 = 0x7f080001,   // default
    };
}

} // namespace R
} // namespace base

#endif // __BASE_R_H
