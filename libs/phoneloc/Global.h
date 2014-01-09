/* Copyright (C) 2013 The MoKee Open Source Project
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

#ifndef _MPGLOBAL_INCLUDED_
#define _MPGLOBAL_INCLUDED_
#pragma pack (1)

//链表节点类
class StringNode
{
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
class IndexNode
{
public:
	int NumStart;
	int NumEnd;
	StringNode * Address;
	IndexNode * next;

	IndexNode();
	IndexNode(int ns, int ne, StringNode * ad=NULL);
};

//索引记录结构体
typedef struct _IndexStruct
{
	int NumStart;
	int NumEnd;
	unsigned short Offset;
} IndexStruct;

//手机归属地结构体类型
typedef struct _MpLocation
{
	int NumStart;
	int NumEnd;
	char Location[48];
	int locationCode;
} MpLocation;

#endif
