/*
 * Copyright (C) Texas Instruments - http://www.ti.com/
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)

#ifndef TI_SEI_MESSAGES_PARSER_H_INCLUDED
#define TI_SEI_MESSAGES_PARSER_H_INCLUDED

#include <utils/Log.h>

namespace android {

/*values for possible S3D modes*/
enum {
    S3D_MODE_OFF = 0,
    S3D_MODE_ON = 1,
    S3D_MODE_ANAGLYPH = 2,
};

/*values for possible S3D format types*/
enum {
    S3D_FORMAT_NONE = 0,
    S3D_FORMAT_OVERUNDER,
    S3D_FORMAT_SIDEBYSIDE,
    S3D_FORMAT_ROW_IL,
    S3D_FORMAT_COL_IL,
    S3D_FORMAT_PIX_IL,
    S3D_FORMAT_CHECKB,
    S3D_FORMAT_FRM_SEQ,
};

/*values for possible S3D order types*/
enum {
    S3D_ORDER_LF = 0,
    S3D_ORDER_RF,
};

/*values for possible S3D subsampling modes*/
enum {
    S3D_SS_NONE = 0,
    S3D_SS_HOR,
    S3D_SS_VERT,
};

struct S3D_params
{
    bool active;
    uint32_t mode;
    uint32_t fmt;
    uint32_t order;
    uint32_t subsampling;
};

}
#endif //TI_M4V_CONFIG_PARSER_H_INCLUDED
#endif //OMAP_ENHANCEMENT

