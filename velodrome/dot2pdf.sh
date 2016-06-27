#!/bin/bash

cd
for i in `ls *.graph`
do
  OUTFILE=$i+".pdf"
  dot -Tpdf -o $OUTFILE $i
done

