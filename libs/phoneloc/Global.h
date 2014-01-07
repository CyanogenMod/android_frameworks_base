/*
 * Copyright (C) 2012 - 2014 The MoKee OpenSource Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef _MPGLOBAL_INCLUDED_
#define _MPGLOBAL_INCLUDED_
#pragma pack (1)

//链表节点类
class StringNode {
public:
    unsigned short cityCode;
    char * value;
    int length;
    unsigned short offset;
    StringNode * next;

    StringNode(const char * val, unsigned short cityCode);
    StringNode();
    ~StringNode();
};


//索引表节点类
class IndexNode {
public:
    int NumStart;
    int NumEnd;
    StringNode * Address;
    IndexNode * next;

    IndexNode();
    IndexNode(int ns, int ne, StringNode * ad=NULL);
};

//索引记录结构体
typedef struct _IndexStruct {
    int NumStart;
    int NumEnd;
    unsigned short Offset;
} IndexStruct;

//手机归属地结构体类型
typedef struct _MpLocation {
    int NumStart;
    int NumEnd;
    char Location[48];
    int locationCode;
} MpLocation;

#endif
