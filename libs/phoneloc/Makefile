# Copyright (C) 2013 The MoKee Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

CC	= g++
CFLAGS	= -Wall -W -O2 -g

PROGS	= generateData
OBJS	= GenerateData.o  Global.o

all: depend $(PROGS)

.PHONY : depend
depend: $(patsubst %.o,%.cpp,$(filter %.o,$(OBJS)))
	@$(CC) -MM $(CFLAGS) -I.. $^ 1> .depend


clean: 
	-rm -f *.o $(PROGS) .depend

%.o: %.cpp
	$(CC) $(CFLAGS) -c -o $*.o $<

$(PROGS): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $(filter %.o,$^) 

ifneq ($(wildcard .depend),)
include .depend
endif
