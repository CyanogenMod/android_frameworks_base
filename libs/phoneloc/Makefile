# Copyright (C) 2012 - 2014 The MoKee OpenSource Project
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
