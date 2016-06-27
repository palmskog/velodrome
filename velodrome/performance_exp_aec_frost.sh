#!/bin/bash

#
# This script is to do performance tests for the AEC submission. This script runs five implementations one by 
# one and generates graphs for each of them. The benchmark names and the configurations being run in each test 
# corresponds closely to the results reported in the first version of the paper. The code base revision on which 
# these tests are being run also correspond to versions used for the initial submission. We have made some 
# enhancements to the code since the submission, but we do not use the newer revisions so that the results generated 
# hopefully closely match the ones reported in the initial submission. 
#
# Please feel free to contact the corresponding author at biswass@cse.ohio-state.edu with any questions or feedback.

DATE=`date +"%m-%d-%y"`

# Run EXP either in local mode or remote mode

#if [[ $# -ne 1 ]]; then
#    echo "Usage: sh <script_name> <local | remote>"
#    exit 1
#fi

#DOEXP="doexp-"$1
DOEXP="doexp-"$1

# Set up directory paths
USER=`whoami`
USER_PATH="/home/"$USER
AVD_DIR="avdRvmRoot"
AVD_PATH=$USER_PATH$AVD_DIR
VDROME_DIR="velodromeRvmRoot"
VDROME_PATH=$USER_PATH$VDROME_DIR

cd $USER_PATH

# DoubleChecker Single run mode

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
ln -nsf workspace/branch-clone-application-methods-aec avdRvmRoot
ls -l avdRvmRoot
ENDSSH
DC_OUT_DIR=$DATE"_dc_single_run_perf_aec_frost"
$DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefault,AVDASICD,AVDASDefault --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=10 --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true

# DoubleChecker First run in the multi-run mode

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
ln -nsf workspace/first-run-doublechecker-aec avdRvmRoot
ls -l avdRvmRoot
ENDSSH
DC_OUT_DIR=$DATE"_dc_first_run_perf_aec_frost"
$DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefault,AVDCycleDetection --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=10 --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true

# DoubleChecker Second run in the multi-run mode

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
ln -nsf workspace/second-run-doublechecker-aec avdRvmRoot
ls -l avdRvmRoot
ENDSSH
DC_OUT_DIR=$DATE"_dc_second_run_perf_aec_frost"
$DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefault,AVDICD,AVDDefault --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=10 --retryTrials=true --timeout=180 --baseName=$DC_OUT_DIR --email=true


# Velodrome sound version

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
ln -nsf workspace/velodrome-clone-app-methods-aec velodromeRvmRoot
ls -l velodromeRvmRoot
ENDSSH
VDROME_OUT_DIR=$DATE"_velodrome_sound_perf_aec_frost"
$DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeASDefault --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=10 --retryTrials=true --timeout=420 --baseName=$VDROME_OUT_DIR --email=true


# Velodrome unsound version

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
ln -nsf workspace/velodrome-unsound-aec velodromeRvmRoot
ls -l velodromeRvmRoot
ENDSSH
VDROME_OUT_DIR=$DATE"_velodrome_unsound_perf_aec_frost"
$DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeUnsynchronizedAccesses,VelodromeSynchronizedWrites --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=10 --retryTrials=true --timeout=240 --baseName=$VDROME_OUT_DIR --email=true


