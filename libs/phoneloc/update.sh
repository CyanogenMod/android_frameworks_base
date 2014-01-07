#!/bin/bash
#
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

make
./generateData Mps -c mokee-phoneloc.txt
cp mokee-phoneloc.dat $dirname $(dirname $(dirname $(dirname $(dirname $(pwd)))))/vendor/mk/prebuilt/common/media/mokee-phoneloc.dat
make clean
rm mokee-phoneloc.dat
