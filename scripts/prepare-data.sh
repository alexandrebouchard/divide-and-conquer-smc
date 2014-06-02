#!/bin/bash

mkdir data &> /dev/null
cd data

wget --output-document=raw-nyc-data.csv http://data.cityofnewyork.us/api/views/jufi-gzgp/rows.csv?accessType=DOWNLOAD

java -Xmx2g -cp ../build/install/multilevelSMC/lib/\* multilevel.io.PreprocessNYSchoolData -inputFile raw-nyc-data.csv &> /dev/null
cp results/latest/preprocessedNYSData.csv preprocessedNYSData.csv 

cd -