DoubleChecker: Efficient Sound and Precise Atomicity Checking

Swarnendu Biswas, Jipeng Huang, Aritra Sengupta, and Michael Bond
The Ohio State University
April 2014

This document gives a brief description of the project, code, and instructions
to build, and execute the projects.

OVERVIEW

DoubleChecker is a novel sound and precise dynamic atomicity checker whose key
insight lies in its use of two new cooperating dynamic analyses. Its imprecise
analysis tracks inter-thread dependences soundly but imprecisely with
significantly better performance than a fully precise analysis. Its precise
analysis is more expensive but only needs to process parts of execution
identified as potentially involved in atomicity violations. DoubleChecker can
operate in either of two modes: the single-run mode is fully sound, while the
multi-run mode trades accuracy for performance.

We have implemented DoubleChecker and an existing state-of-the-art atomicity
checker called Velodrome in a high-performance Java virtual machine.
DoubleChecker's single-run and multi-run modes significantly outperform
Velodrome, while still providing full soundness and precision. These results
suggest that DoubleChecker's approach is a promising direction for improving the
performance of dynamic atomicity checking over prior work.

The following paper presents DoubleChecker in more detail: 

Swarnendu Biswas, Jipeng Huang, Aritra Sengupta, and Michael
Bond. DoubleChecker: Efficient Sound and Precise Atomicity Checking. PLDI 2014.

This project implements the Velodrome algorithm. The link
to the Velodrome paper is: http://dl.acm.org/citation.cfm?id=1375618. To have a
sound implementation, we encapsulate each program access in small critical
sections to avoid loss of metadata updates due to potential data races.

BUILDING THE PROJECT

Build Velodrome:

Install `gcc-multilib` and `g++-multilib`. In Ubuntu:
    $ apt-get install gcc-multilib g++-multilib

Go to the source directory and run:

    $ ant -Dhost.name=x86_64-linux -Dconfig.name=FastAdaptiveGenImmix -Dconfig.variant=VelodromeASDefault -Dconfig.config-class=org.jikesrvm.config.VelodromeASDefault


EXECUTING THE PROJECT

Before you can execute a project, you need to create a symlink in your $HOME
directory for the project.

    $ cd ; ln -s ${RVMROOT} velodromeRvmRoot

After building, you can run a configuration with this command:

    $ ${RVMROOT}/dist/rvm/${WHATEVER_PREFIX}_${CONFIG_NAME}_${WHATEVER_SUFFIX}/rvm [JVM arguments] [application arguments]

Examples:

Run Velodrome with avrora:

    $ cd ; ln -s ${RVMROOT} velodromeRvmRoot
    $ ${RvmRoot}/dist/FastAdaptiveGenImmix_VelodromeASDefault_x86_64-linux/rvm -X:vm:errorsFatal=true -X:vm:measureCompilation=true -X:vm:measureCompilationPhases=true -X:vm:benchmarkName=avrora9 -Xmx2600M -cp <path-to-dacapo-9.12-bach.jar> Harness -s small -c MMTkCallback -n 1 avrora

Only GenImmix is currently supported for GC changes in DoubleChecker, while all
collectors are supported for Velodrome. It is possible to support other
collectors in DoubleChecker as well.


BRIEF GUIDE TO THE SOURCE CODE

Our implementations build on Jikes RVM 3.1.3. You can use Eclipse to view and/or
modify the source code. Please refer to the Jikes RVM resources on how to set up
Jikes in Eclipse.
 
You can do a global text search (Ctrl+H in Eclipse) and search for "AVD:" to
find all the places where we have modified Jikes RVM 3.1.3 in order to implement
DoubleChecker. To search for Velodrome-related changes, search for the string
"Velodrome:". Octet-specific changes are tagged with "Octet:".

Some key classes:

* VelodromeAnalysis
* VelodromeBarriers
* TransactionalHBGraph
* VelodromeBaselineInstr
