#!/bin/bash

DATE=`date +"%m-%d-%y"`
VDROME_OUT_DIR=$DATE"_velodrome_performance_frost"
DC_OUT_DIR=$DATE"_doublechecker_performance_frost"

if [[ $# -ne 1 ]]; then
    echo "Usage: sh <script_name> <local | remote>"
    exit 1
fi

DOEXP="doexp-"$1
USER_PATH="/home/biswass/"
VDROME_DIR="velodromeRvmRoot"
VDROME_PATH=$USER_PATH$VDROME_DIR

cd $USER_PATH

# Delete contents of local VDROME directory
if [ -d "$VDROME_DIR" ]; then 
    cd $VDROME_DIR
    # Delete all files just to be sure
    rm -rf *
fi
cd .. # Move up one level

# Set up desired sym link
ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/velodrome-clone-application-methods velodromeRvmRoot
ls -l velodromeRvmRoot
ENDSSH

$DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,OctetDefault,VelodromeASInsertPostBarriers,VelodromeASTrackMetadata,VelodromeASCrossThreadEdges,VelodromeASDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch9-fixed,sunflow9,xalan9,pmd9,luindex9,avrora9,jython9 --tasks=sync,build,exp,product --trial=15 --retryTrials=true --timeout=240 --baseName=$VDROME_OUT_DIR


