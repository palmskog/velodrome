#!/bin/bash

DATE=`date +"%m-%d-%y"`
VDROME_OUT_DIR=$DATE"_velodrome_perf_frost"
DC_OUT_DIR=$DATE"_dc_perf_frost"

if [[ $# -ne 1 ]]; then
    echo "Usage: sh <script_name> <local | remote>"
    exit 1
fi

DOEXP="doexp-"$1
USER_PATH="/home/biswass/"
AVD_DIR="avdRvmRoot"
AVD_PATH=$USER_PATH$AVD_DIR
VDROME_DIR="velodromeRvmRoot"
VDROME_PATH=$USER_PATH$VDROME_DIR

cd $USER_PATH

# DoubleChecker

# First execute DoubleChecker in single-run mode

# Delete contents of local AVD directory
if [ -d "$AVD_DIR" ]; then
    cd $AVD_DIR
    # Delete all files just to be sure
    rm -rf *
fi
cd .. # Move up one level

# Set up desired sym link
ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/branch-clone-application-methods avdRvmRoot
ls -l avdRvmRoot
ENDSSH

$DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefault,AVDASCycleDetection,AVDASICD,AVDASDefault --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=25 --retryTrials=true --timeout=180 --baseName=$DC_OUT_DIR --email=true

# Velodrome 

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
ln -nsf workspace/velodrome-clone-app-methods velodromeRvmRoot
ls -l velodromeRvmRoot
ENDSSH

$DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeASInsertPostBarriers,VelodromeASTrackMetadata,VelodromeASCrossThreadEdges,VelodromeASDefault --bench=sor,tsp,montecarlo,moldyn,,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=25 --retryTrials=true --timeout=420 --baseName=$VDROME_OUT_DIR --email=true


