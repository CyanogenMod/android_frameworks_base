#!/bin/bash
make
./generateData Mps -c mokee-phoneloc.txt
cp mokee-phoneloc.dat $dirname $(dirname $(dirname $(dirname $(dirname $(pwd)))))/vendor/cm/prebuilt/common/etc/phoneloc.dat
make clean
rm mokee-phoneloc.dat
