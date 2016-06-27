#!/bin/bash

DATE=`date +"%m-%d-%y"`
VELODROME_DIR=$DATE"_velodrome_correctness"
DCHECKER_DIR=$DATE"_doublechecker_correctness"

if [[ $# -ne 1 ]]; then
    echo "Usage: sh <script_name> <local | remote>"
    exit 1
fi

DOEXP="doexp-"$1

cd /home/biswass

$DOEXP --project=velodrome --buildPrefix=FullAdaptive --workloadSize=small --config=BaseConfig,VelodromeASTrackMetadata,VelodromeASCrossThreadEdges,VelodromeASDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch9-fixed,sunflow9,xalan9,pmd9,luindex9,avrora9,jython9 --tasks=sync,build,exp --trial=10 --timeout=240 --baseName=$VELODROME_DIR


