/*
 * Copyright (C) 2014 The Android Open Source Project
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
