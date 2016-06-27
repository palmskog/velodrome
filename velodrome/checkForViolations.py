#!/usr/bin/python

import sys 
import os

dir_name = ""
bench_name = ""
exp_output_dir = "exp-output"

def parse_arguments():
    '''Test validity of input arguments'''
    if len(sys.argv) != 3:
        print "Wrong number of arguments"
        print "Usage:python <xxx.py> <directory> <benchmark>"
        sys.exit(1)
    global dir_name
    global bench_name
    dir_name = sys.argv[1]
    bench_name = sys.argv[2]    

def change_dir():
    '''Switch working directory to the one requested by the user'''
    home = os.path.expanduser("~")
    path = home + "/" + exp_output_dir + "/" + dir_name + "/" + bench_name
    if os.path.exists(path) == False:
        print "Invalid path"
        sys.exit(1)
    if os.path.isdir(path) == False:
        print "Invalid directory"
        sys.exit(1)
    os.chdir(path)

def main():
    print 'Hello world!'
    parse_arguments()
    change_dir()
    # Current working directory should now correspond to the 
    # given benchmark

if __name__ == '__main__':
    main()
