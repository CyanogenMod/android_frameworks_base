/*
 ** Copyright 2003-2010, VisualOn, Inc.
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */
/*******************************************************************************
        File:           channel_map.h

        Content:        channel mapping functions

*******************************************************************************/

#ifndef _CHANNEL_MAP_H
#define _CHANNEL_MAP_H

#include "psy_const.h"
#include "qc_data.h"

Word16 InitElementInfo (Word16 nChannels, ELEMENT_INFO* elInfo);

Word16 InitElementBits(ELEMENT_BITS *elementBits,
                       ELEMENT_INFO elInfo,
                       Word32 bitrateTot,
                       Word16 averageBitsTot,
                       Word16 staticBitsTot);

#endif /* CHANNEL_MAP_H */
