#!/bin/bash

if [[ $# -ne 1 ]]; then
    echo "Usage: sh <script_name> <benchmark_name>"
    exit 1
fi

FS="/"
# Use argument to read benchmark name
BENCH_NAME=$1
DIR_PATH="/exp-output/temp/"$BENCH_NAME

# START
if [[ ! -d "$HOME$DIR_PATH" ]]; then
    echo "Directory $HOME$DIR_PATH does not exist!"
    exit 1
fi

cd $HOME$DIR_PATH

# Get total number of nodes
grep -r "AVDNodes" . | cut -d ' ' -f 2 | awk '{sum += $1} END {print sum/10}'
# Get total number of regular transactions
grep -r "AVDNumRegularTransactions" . | cut -d ' ' -f 2 | awk '{sum += $1} END {print sum/10}'
# Get total number of unary transactions
grep -r "AVDNumUnaryTransactions" . | cut -d ' ' -f 2 | awk '{sum += $1} END {print sum/10}'
# Get total number of Phase 1 edges
grep -r "AVDPhase1Edges" . | cut -d ' ' -f 2 | awk '{sum += $1} END {print sum/10}'
# Get total number of Phase 1 cycles
grep -r "AVDPhase1Cycles" . | cut -d ' ' -f 2 | awk '{sum += $1} END {print sum/10}'
# Get total number of instrumented instructions in Phase 1
grep -r "AVDNumTotalPhase1Instructions" . | cut -d ' ' -f 2 | awk '{sum += $1} END {print sum/10}'
# Get total number of instructions logged in Phase 1
grep -r "AVDNumPhase1InstructionsLogged" . | cut -d ' ' -f 2 | awk '{sum += $1} END {print sum/10}'


