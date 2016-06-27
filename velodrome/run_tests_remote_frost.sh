#!/bin/bash

DATE=`date +"%m-%d-%y"`

# Run EXP remotely
DOEXP="doexp-remote"

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
TRIALS=25

cd $USER_PATH

# DoubleChecker single run mode
dc_single_run() {
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
ln -nsf workspace/dc-single-run-inlining avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_single_run_final_perf_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDASSlowPathHooks,AVDASCrossThreadEdge,AVDASCycleDetection,AVDASICD,AVDASDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
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

    DC_OUT_DIR=$DATE"_dc_first_run_final_perf_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDCrossThreadEdge,AVDCycleDetection --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
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

    DC_OUT_DIR=$DATE"_dc_second_run_perf_final_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDICD,AVDDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=180 --baseName=$DC_OUT_DIR --email=true
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

    VDROME_OUT_DIR=$DATE"_velodrome_sound_final_perf_frost"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeASTrackMetadata,VelodromeASCrossThreadEdges,VelodromeASDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=360 --baseName=$VDROME_OUT_DIR --email=true
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

    VDROME_OUT_DIR=$DATE"_velodrome_unsound_final_perf_frost"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeUnsynchronizedAccesses,VelodromeSynchronizedWrites --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=240 --baseName=$VDROME_OUT_DIR --email=true
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

    DC_OUT_DIR=$DATE"_dc_single_run_arrays_final_perf_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,AVDASICDNoCycle,AVDASICDWithArraysNoCycle --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
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

    VDROME_OUT_DIR=$DATE"_velodrome_sound_arrays_final_perf_frost"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeASDefaultNoCycle,VelodromeASDefaultWithArraysNoCycle --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=360 --baseName=$VDROME_OUT_DIR --email=true
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

    DC_OUT_DIR=$DATE"_dc_second_run_always_instr_unaries_perf_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDDefault,AVDDefaultAlwaysInstrumentUnaries --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=180 --baseName=$DC_OUT_DIR --email=true
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
ln -nsf workspace/dc-single-run-inlining avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_single_run_iterative_refinement_perf_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,AVDASDefault,AVDIterativeRefinementStart,AVDIterativeRefinementHalfway  --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=180 --baseName=$DC_OUT_DIR --email=true
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

    VDROME_OUT_DIR=$DATE"_velodrome_sound_second_run_final_perf_frost"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=360 --baseName=$VDROME_OUT_DIR --email=true
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

    # small
    DC_OUT_DIR=$DATE"_dc_single_run_static_race_small_final_perf_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,PessimisticBarriers,AVDASDefault,OctetDefaultStaticRaceFiltering,PessimisticBarriersStaticRaceFiltering,AVDASDefaultWithStaticRace --bench=eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=420 --baseName=$DC_OUT_DIR --email=true

    # large
    DC_OUT_DIR=$DATE"_dc_single_run_static_race_large_final_perf_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,OctetDefaultForAVD,PessimisticBarriers,OctetDefaultStaticRaceFiltering,PessimisticBarriersStaticRaceFiltering --bench=eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build --workloadSize=large --trial=$TRIALS --retryTrials=true --timeout=420 --baseName=$DC_OUT_DIR --email=true

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

    # small
    VDROME_OUT_DIR=$DATE"_velodrome_sound_static_race_small_final_perf_frost"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,PessimisticBarriers,VelodromeASDefault,PessimisticBarriersStaticRaceFiltering,VelodromeASDefaultWithStaticRace --bench=eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build --trial=$TRIALS --retryTrials=true --timeout=420 --baseName=$VDROME_OUT_DIR --email=true

    # large
    VDROME_OUT_DIR=$DATE"_velodrome_sound_static_race_large_final_perf_frost"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=large --config=BaseConfig,OctetDefaultForAVD,PessimisticBarriers,OctetDefaultStaticRaceFiltering,PessimisticBarriersStaticRaceFiltering --bench=eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build --trial=$TRIALS --retryTrials=true --timeout=420 --baseName=$VDROME_OUT_DIR --email=true

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

    # small
    OCTET_OUT_DIR=$DATE"_octet_pessimistic_barriers_static_race_perf_small_frost"
    $DOEXP --project=octet --buildPrefix=FastAdaptive --workloadSize=small --tasks=sync,build,exp,product --config=BaseConfig,OctetDefault,PessimisticBarriers,OctetDefaultStaticRaceFiltering,PessimisticBarriersStaticRaceFiltering --bench=eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9,pseudojbb2000,pseudojbb2005 --trial=$TRIALS --retryTrials=true --timeout=420 --baseName=$OCTET_OUT_DIR --email=true

    # large
    OCTET_OUT_DIR=$DATE"_octet_pessimistic_barriers_static_race_perf_large_frost"
    $DOEXP --project=octet --buildPrefix=FastAdaptive --workloadSize=large --tasks=sync,build,exp,product --config=BaseConfig,OctetDefault,PessimisticBarriers,OctetDefaultStaticRaceFiltering,PessimisticBarriersStaticRaceFiltering --bench=eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9,pseudojbb2000,pseudojbb2005 --trial=$TRIALS --retryTrials=true --timeout=420 --baseName=$OCTET_OUT_DIR --email=true

}

# DoubleChecker single run mode for custom configurations
dc_single_run_custom() {
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
ln -nsf workspace/dc-single-run-inlining avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_single_run_inlining_test_perf_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,AVDASDefault,AVDASDefaultInlineOnlyBarriers,AVDASDefaultInlineOnlyTransactions,AVDASDefaultNoInline --bench=sor,tsp,moldyn,montecarlo,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
#    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,AVDASDefault --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
}

# DoubleChecker second run mode for custom configurations
dc_second_run_custom() {
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

    DC_OUT_DIR=$DATE"_dc_second_run_inlining_perf_mist"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,AVDDefault,AVDDefaultInlineOnlyBarriers,AVDDefaultInlineOnlyTransactions,AVDDefaultNoInline --bench=sor,tsp,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
    #$DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,AVDASDefault --bench=sor,tsp,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
}

# Velodrome sound version for custom configurations
velodrome_sound_custom() {
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

    VDROME_OUT_DIR=$DATE"_velodrome_sound_final_inlining_perf_frost"
    $DOEXP --project=velodrome --buildPrefix=FastAdaptive --workloadSize=small --config=BaseConfig,VelodromeASDefault,VelodromeASDefaultInlineOnlyBarriers,VelodromeASDefaultInlineOnlyTransactions,VelodromeASDefaultNoInline --bench=sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --trial=$TRIALS --retryTrials=true --timeout=360 --baseName=$VDROME_OUT_DIR --email=true
}

# DoubleChecker single run mode where PCD is invoked for all transactions
# A few benchmarks like eclipse6, xalan6, avrora9, xalan9 are left out since they run out of memory
dc_single_run_pcd_for_all() {
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
ln -nsf workspace/dc-single-run-pcd-on-all avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_single_run_pcd_on_all_perf_frost"
	$DOEXP --project=avd --buildPrefix=FastAdaptive --config=BaseConfig,AVDASDefault,AVDASPCDForAll --bench=sor,tsp,moldyn,montecarlo,raytracer,hsqldb6,lusearch6,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9 --tasks=sync,build,exp,product --workloadSize=small --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$DC_OUT_DIR --email=true
}

# Octet custom configurations
octet_custom() {
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

    # small
    OCTET_OUT_DIR=$DATE"_octet_opt_compilation_overhead_perf_small_frost"
    $DOEXP --project=octet --buildPrefix=FastAdaptive --workloadSize=small --tasks=sync,build,exp,product --config=BaseConfig,OctetDefault,OctetForceUseJikesInliner --bench=sor,tsp,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --trial=$TRIALS --retryTrials=true --timeout=120 --baseName=$OCTET_OUT_DIR --email=true

    # large
    OCTET_OUT_DIR=$DATE"_octet_pessimistic_barriers_static_race_perf_large_frost"
    #$DOEXP --project=octet --buildPrefix=FastAdaptive --workloadSize=large --tasks=sync,build --config=BaseConfig,OctetDefault,PessimisticBarriers,OctetDefaultStaticRaceFiltering,PessimisticBarriersStaticRaceFiltering --bench=eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9,pseudojbb2000,pseudojbb2005 --trial=$TRIALS --retryTrials=true --timeout=420 --baseName=$OCTET_OUT_DIR --email=true

}

# DoubleChecker single run mode with stats, ten trials
dc_single_run_stats() {
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
ln -nsf workspace/dc-single-run-inlining avdRvmRoot
ls -l avdRvmRoot
ENDSSH

    DC_OUT_DIR=$DATE"_dc_single_run_final_stats_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=AVDStats --bench=elevator,hedc,philo,sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=10 --retryTrials=true --timeout=420 --baseName=$DC_OUT_DIR --email=true
}

# DoubleChecker second run in the multi-run mode with stats, ten trials
dc_second_run_stats() {
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

    DC_OUT_DIR=$DATE"_dc_second_run_final_stats_frost"
    $DOEXP --project=avd --buildPrefix=FastAdaptive --config=AVDStats --bench=elevator,hedc,philo,sor,tsp,moldyn,montecarlo,raytracer,eclipse6,hsqldb6,lusearch6,xalan6,avrora9,jython9,luindex9,lusearch9-fixed,pmd9,sunflow9,xalan9 --tasks=sync,build,exp,product --workloadSize=small --trial=10 --retryTrials=true --timeout=420 --baseName=$DC_OUT_DIR --email=true
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
#dc_single_run_custom
#dc_second_run_custom
#velodrome_sound_custom
#dc_single_run_pcd_for_all
#octet_custom
#dc_single_run_stats
#dc_second_run_stats
