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

#include <stdio.h>
#include <string.h>
#include "Global.h"

StringNode::StringNode(const char * val,unsigned short cityCode)
{
	this->length=strlen(val);
	this->value=new char[this->length+1];
	strcpy(this->value,val);
	this->cityCode = cityCode;
	this->next=NULL;
}
StringNode::StringNode()
{
	value=NULL;
	length=0;
	next=NULL;
}
StringNode::~StringNode()
{
	if(value) delete[] value;
}

IndexNode::IndexNode()
{
	NumStart=NumEnd=0;
	Address=NULL;
	next=NULL;
}
IndexNode::IndexNode(int ns, int ne, StringNode * ad)
{
	NumStart=ns; NumEnd=ne; Address=ad;
	next=NULL;
}

