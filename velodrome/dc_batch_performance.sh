#!/bin/bash

DATE=`date +"%m-%d-%y"`
VELODROME_DIR=$DATE"_velodrome_performance"
DC_SINGLERUN_DIR=$DATE"_dc_singlerun_perf_sunshine"
DC_MULTIRUN_ICD_DIR=$DATE"_dc_multirun_icd_perf_sunshine"
DC_MULTIRUN_PCD_DIR=$DATE"_dc_multirun_pcd_perf_sunshine"

if [[ $# -ne 1 ]]; then
    echo "Usage: sh <script_name> <local | remote>"
    exit 1
fi

DOEXP="doexp-"$1
USER_PATH="/home/biswass/"
AVD_DIR="avdRvmRoot"
AVD_PATH=$USER_PATH$AVD_DIR

echo $AVD_PATH

cd $USER_PATH

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

$DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefault,AVDASTransactionInstrumentation,AVDASSlowPathHooks,AVDASCrossThreadEdge,AVDASCycleDetection,AVDASICD,AVDASDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch9-fixed,sunflow9,xalan9,pmd9,luindex9,avrora9,jython9 --tasks=sync,build,exp,product --workloadSize=small --trial=15 --retryTrials=true --timeout=180 --baseName=$DC_SINGLERUN_DIR --jikesArgs=-X:gc:eagerMmapSpaces=true

#$DOEXP --project=avd --buildPrefix=FullAdaptive --config=AVDASDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch9-fixed,sunflow9,xalan9,pmd9,luindex9,avrora9,jython9 --tasks=sync,build,exp --workloadSize=small --trial=1 --retryTrials=true --timeout=180

# Now execute DoubleChecker ICD in multi-run mode

# Delete contents of local AVD directory
if [ -d "$AVD_DIR" ]; then
    cd $AVD_DIR
    # Delete all files just to be sure
    rm -rf *
else 
    echo "AVD directory should have been present"
    exit 1
fi
cd .. # Move up one level

# Set up desired sym link
ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/multirun-icd-doublechecker avdRvmRoot
ls -l avdRvmRoot
ENDSSH

$DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefault,AVDTransactionInstrumentation,AVDSlowPathHooks,AVDCrossThreadEdge,AVDCycleDetection --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch9-fixed,sunflow9,xalan9,pmd9,luindex9,avrora9,jython9 --tasks=sync,build,exp,product --workloadSize=small --trial=15 --retryTrials=true --timeout=180 --baseName=$DC_MULTIRUN_ICD_DIR --jikesArgs=-X:gc:eagerMmapSpaces=true

#$DOEXP --project=avd --buildPrefix=FullAdaptive --config=AVDCycleDetection --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch9-fixed,sunflow9,xalan9,pmd9,luindex9,avrora9,jython9 --tasks=sync,build,exp --workloadSize=small --trial=1 --retryTrials=true --timeout=120

# Now execute DoubleChecker PCD in multi-run mode

# Delete contents of local AVD directory
if [ -d "$AVD_DIR" ]; then
    cd $AVD_DIR
    # Delete all files just to be sure
    rm -rf *
else 
    echo "AVD directory should have been present"
    exit 1
fi
cd .. # Move up one level

# Set up desired sym link
ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/multirun-pcd-doublechecker avdRvmRoot
ls -l avdRvmRoot
ENDSSH

$DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefault,AVDTransactionInstrumentation,AVDSlowPathHooks,AVDCrossThreadEdge,AVDCycleDetection,AVDICD,AVDDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch9-fixed,sunflow9,xalan9,pmd9,luindex9,avrora9,jython9 --tasks=sync,build,exp,product --workloadSize=small --trial=15 --retryTrials=true --timeout=180 --baseName=$DC_MULTIRUN_PCD_DIR --jikesArgs=-X:gc:eagerMmapSpaces=true

#$DOEXP --project=avd --buildPrefix=FullAdaptive --config=AVDDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch9-fixed,sunflow9,xalan9,pmd9,luindex9,avrora9,jython9 --tasks=sync,build,exp --workloadSize=small --trial=1 --retryTrials=true --timeout=120

# Helper functions



