#!/bin/bash

# WHAT DOES THIS SCRIPT DO?
# This script takes a benchmark name as input, finds all output.txt files in exp-output, and then 
# extracts some strings from the output files and logs to another output file.

# This work can be done more cheaply

if [[ $# -ne 1 ]]; then
    echo "Usage: sh <script_name> <benchmark_name>"
    exit 1
fi

FS="/"
# Use argument to read benchmark name
BENCH_NAME=$1
DIR_PATH="/exp-output/temp/"$BENCH_NAME
SEARCH_STRING="Cycle"
OUTPUT_FILE=$HOME$FS"Desktop"$FS$BENCH_NAME

# Delete output file if it already exists
if [[ -e "$OUTPUT_FILE" ]]; then
    rm $OUTPUT_FILE
fi

function processFile(){
    local file_name=$1
    echo "FILE NAME: "$file_name
    # Why is this not working?
    echo "Searching $file_name" >> $OUTPUT_FILE
    echo `grep -F "$SEARCH_STRING" $file_name` >> $OUTPUT_FILE
    # Now add newlines for each output
    sed -i "s|ITERATION:|\nITERATION:|g" $OUTPUT_FILE
}

# recurse() is to be called with a directory path as the parameter
# $1 is the first parameter
function recurse(){
    local dir_name=$1
    cd $dir_name
    # We are now in $dir_name
    # If there are no .txt files, the wild card will expand to *.txt, the following line avoids this problem
    shopt -s nullglob
    local file
    # We are bothered with only "output.txt"
    for file in *.txt
    do
	if [[ $file = "output.txt" ]]; then
	    local file_name=$dir_name$FS$file
	    processFile $file_name
	fi
    done
    # Iterate over directories if any
    local dir
    for dir in `ls`
    do
	if [[ -d $dir ]]; then
 	    local child_dir=$dir_name$FS$dir
	    recurse $child_dir
	fi
    done
    cd .. # Move back to the parent
}

# START
if [[ ! -d "$HOME$DIR_PATH" ]]; then
    echo "Directory $HOME$DIR_PATH does not exist!"
    exit 1
fi

#cd ~/$DIR_PATH

#for file in `ls`
#do 
#    if [[ -d $file ]]; then
#	top_dir_name=`pwd`$FS$file
#	recurse $top_dir_name
#    fi
#done

for file in `find $HOME$DIR_PATH -name output.txt`
do
    processFile $file
done
echo "OUTPUT WRITTEN TO "$OUTPUT_FILE



