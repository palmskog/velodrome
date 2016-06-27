#!/bin/bash

DATE=`date +"%m-%d-%y"`

# Run EXP locally
DOEXP="doexp-local"

# Set up directory paths
USER=`whoami`
USER_PATH="/home/"$USER
AVD_DIR="avdRvmRoot"
AVD_PATH=$USER_PATH$AVD_DIR
VDROME_DIR="velodromeRvmRoot"
VDROME_PATH=$USER_PATH$VDROME_DIR
OCTET_DIR="octetRvmRoot"
OCTET_PATH=$USER_PATH$OCTET_DIR

# Run 25 trials usually
TRIALS=10

cd $USER_PATH

# DoubleChecker single run mode
dc_single_run() {
	ln -nsf workspace/dc-single-run-final avdRvmRoot
	ls -l avdRvmRoot
    DC_OUT_DIR=$DATE"_dc_single_run_final_perf_rain"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDASSlowPathHooks,AVDASCrossThreadEdge,AVDASCycleDetection,AVDASICD,AVDASDefault --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
}

# DoubleChecker first run in the multi-run mode
dc_first_run() {
    # Delete contents of local AVD directory
    if [ -d "$AVD_DIR" ]; then
	cd $AVD_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/dc-first-run-final avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_first_run_final_perf_rain"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDCrossThreadEdge,AVDCycleDetection --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
}

# DoubleChecker second run in the multi-run mode
dc_second_run() {
    # Delete contents of local AVD directory
    if [ -d "$AVD_DIR" ]; then
	cd $AVD_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/dc-second-run-final avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_second_run_perf_final_rain"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDICD,AVDDefault --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=180 --baseName=$DC_OUT_DIR --email=true
}

# Velodrome sound version
velodrome_sound() {
    # Delete contents of local VDROME directory
    if [ -d "$VDROME_DIR" ]; then 
	cd $VDROME_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/velodrome-sound-final velodromeRvmRoot
ls -l velodromeRvmRoot
ENDSSH

    VDROME_OUT_DIR=$DATE"_velodrome_sound_final_perf_rain"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeASTrackMetadata,VelodromeASCrossThreadEdges,VelodromeASDefault --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=360 --baseName=$VDROME_OUT_DIR --email=true
}

# Velodrome unsound version
velodrome_unsound() {
    # Delete contents of local VDROME directory
    if [ -d "$VDROME_DIR" ]; then 
	cd $VDROME_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/velodrome-unsound-final velodromeRvmRoot
ls -l velodromeRvmRoot
ENDSSH

    VDROME_OUT_DIR=$DATE"_velodrome_unsound_final_perf_rain"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeUnsynchronizedAccesses,VelodromeSynchronizedWrites --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=240 --baseName=$VDROME_OUT_DIR --email=true
}

# DoubleChecker with arrays
dc_single_run_arrays() {
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
ln -nsf workspace/dc-single-run-final avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_single_run_arrays_final_perf_rain"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,AVDASICDNoCycle,AVDASICDWithArraysNoCycle --bench=sor,tsp,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
}

# Velodrome sound version with arrays
velodrome_sound_arrays() {
    # Delete contents of local VDROME directory
    if [ -d "$VDROME_DIR" ]; then 
	cd $VDROME_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/velodrome-sound-final velodromeRvmRoot
ls -l velodromeRvmRoot
ENDSSH

    VDROME_OUT_DIR=$DATE"_velodrome_sound_arrays_final_perf_rain"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeASDefaultNoCycle,VelodromeASDefaultWithArraysNoCycle --bench=sor,tsp,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=360 --baseName=$VDROME_OUT_DIR --email=true
}

# DoubleChecker second run in the multi-run mode with always unary instrumentation turned on
dc_second_run_always_unaries() {
    # Delete contents of local AVD directory
    if [ -d "$AVD_DIR" ]; then
	cd $AVD_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/dc-second-run-final avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_second_run_always_instr_unaries_perf_rain"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDDefault,AVDDefaultAlwaysInstrumentUnaries --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=180 --baseName=$DC_OUT_DIR --email=true
}

# DoubleChecker single run during stages of iterative refinement (very start, and approximately halfway) 
dc_single_run_iterative_refinement() {
    # Delete contents of local AVD directory
    if [ -d "$AVD_DIR" ]; then
	cd $AVD_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/dc-single-run-final avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_single_run_iterative_refinement_perf_rain"
$DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDASDefault,AVDIterativeRefinementStart,AVDIterativeRefinementHalfway  --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=180 --baseName=$DC_OUT_DIR --email=true
}

# Velodrome sound version for the second run
velodrome_sound_second_run() {
    # Delete contents of local VDROME directory
    if [ -d "$VDROME_DIR" ]; then 
	cd $VDROME_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/velodrome-sound-second-run velodromeRvmRoot
ls -l velodromeRvmRoot
ENDSSH

    VDROME_OUT_DIR=$DATE"_velodrome_sound_second_run_final_perf_rain"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeDefault --bench=sor,tsp,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=360 --baseName=$VDROME_OUT_DIR --email=true
}

# DoubleChecker single run mode with static race detection
dc_single_run_with_static_race() {
    # Delete contents of local AVD directory
    if [ -d "$AVD_DIR" ]; then
	cd $AVD_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/dc-single-run-final avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_single_run_static_race_final_perf_rain"
    #    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDASDefault,OctetDefaultStaticRaceFiltering,AVDASDefaultWithStaticRace --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDASDefault,OctetDefaultStaticRaceFiltering,AVDASDefaultWithStaticRace --bench=eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
}

# Velodrome sound version with static race detection
velodrome_sound_with_static_race() {
    # Delete contents of local VDROME directory
    if [ -d "$VDROME_DIR" ]; then 
	cd $VDROME_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/velodrome-sound-final velodromeRvmRoot
ls -l velodromeRvmRoot
ENDSSH

    VDROME_OUT_DIR=$DATE"_velodrome_sound_static_race_final_perf_rain"
    #    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeASDefault,VelodromeASDefaultWithStaticRace --bench=sor,tsp,montecarlo,moldyn,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=360 --baseName=$VDROME_OUT_DIR --email=true
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeASDefault,VelodromeASDefaultWithStaticRace --bench=eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=360 --baseName=$VDROME_OUT_DIR --email=true
}

# Octet optimistic/pessimistic barriers with static race detection
octet_with_static_race() {
    # Delete contents of local Octet directory
    if [ -d "$OCTET_DIR" ]; then 
	cd $OCTET_DIR
	# Delete all files just to be sure
	rm -rf *
    fi
    cd # Switch to $HOME

    # Set up desired sym link
    ssh biswass@rain.cse.ohio-state.edu <<'ENDSSH'
bash -s
cd $USER_PATH
ln -nsf workspace/octetRvmRoot octetRvmRoot
ls -l octetRvmRoot
ENDSSH

    OCTET_OUT_DIR=$DATE"_octet_pessimistic_barriers_static_race_perf_rain"
    doexp-remote --project=octet --buildPrefix=FastAdaptive --workloadSize=small --tasks=sync,build,exp,product --config=BaseConfig,OctetDefault,PessimisticBarriers,OctetDefaultStaticRaceFiltering,PessimisticBarriersStaticRaceDetection --bench=eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --trial=$TRIALS --retryTrials=true --timeout=360 --baseName=$OCTET_OUT_DIR --email=true
}

# DoubleChecker single run mode custom configurations
dc_single_run_custom() {
	ln -nsf workspace/dc-single-run-final avdRvmRoot
	ls -l avdRvmRoot
    DC_OUT_DIR=$DATE"_dc_single_run_final_custom_perf_rain"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDASTransactionInstrumentation,AVDASSlowPathHooks,AVDASCrossThreadEdge --bench=sor,tsp,montecarlo,moldyn,raytracer,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
}

# Experiment execution is controlled from here

#dc_single_run
#dc_first_run
#dc_second_run
#velodrome_sound
#velodrome_unsound
#dc_single_run_arrays
#velodrome_sound_arrays
#dc_second_run_always_unaries
#dc_single_run_iterative_refinement
#velodrome_sound_second_run
#dc_single_run_with_static_race
#velodrome_sound_with_static_race
#octet_with_static_race
dc_single_run_custom


