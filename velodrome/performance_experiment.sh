#!/bin/bash

DATE=`date +"%m-%d-%y"`
VELODROME_DIR=$DATE"_velodrome_perf_test"

if [[ $# -ne 1 ]]; then
    echo "Usage: sh <script_name> <local | remote>"
    exit 1
fi

DOEXP="doexp-"$1

cd /home/biswass

#$DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,OctetDefault,VelodromeASInsertPostBarriers,VelodromeASTrackMetadata,VelodromeASCrossThreadEdges,VelodromeASDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch9-fixed,sunflow9,xalan9,pmd9,luindex9,avrora9,jython9 --tasks=sync,build,exp --trial=5 --retryTrials=true --timeout=180 

$DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeASInsertPostBarriers,VelodromeASTrackMetadata,VelodromeASCrossThreadEdges,VelodromeASDefault,VelodromeCustom1 --bench=sor,tsp --tasks=exp,product --trial=5 --retryTrials=true --timeout=180 --baseName=$VELODROME_DIR



