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

#include <stdio.h>
#include <string.h>
#include "Global.h"

StringNode::StringNode(const char * val,unsigned short cityCode) {
    this->length=strlen(val);
    this->value=new char[this->length+1];
    strcpy(this->value,val);
    this->cityCode = cityCode;
    this->next=NULL;
}
StringNode::StringNode() {
    value=NULL;
    length=0;
    next=NULL;
}
StringNode::~StringNode() {
    if(value) delete[] value;
}

IndexNode::IndexNode() {
    NumStart=NumEnd=0;
    Address=NULL;
    next=NULL;
}
IndexNode::IndexNode(int ns, int ne, StringNode * ad) {
    NumStart=ns;
    NumEnd=ne;
    Address=ad;
    next=NULL;
}

