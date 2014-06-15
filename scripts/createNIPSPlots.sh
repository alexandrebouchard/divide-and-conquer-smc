#!/bin/bash

mkdir nipsFinalPlots
DATE=$(date +%F--%T | tr ':' '-')
mkdir nipsFinalPlots/$DATE

for i in 10 100 1000 10000
do
  echo Running DCMC with $i particles
  cp -r `./multilevel -nParticles $i  | grep outputFolder | sed 's/outputFolder : //'` nipsFinalPlots/$DATE/$i
done