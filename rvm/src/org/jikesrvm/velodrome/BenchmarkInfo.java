package org.jikesrvm.velodrome;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

/** This class is to abstract manipulating benchmark information 
 *  DaCapo 2006-MR2         -         1
 *  DaCapo 9.12-bach        -         2
 *  Java Grande             -         3
 *  ETHZ                    -         4
 *  PseudoJbb               -         5
 *  Miscellaneous           -         6
 *  Microbenchmarks         -         9
 *  
 *  Please note that this class can perform no meaningful validity checks on the inputs.
 * */
@Uninterruptible
public final class BenchmarkInfo {

  /*****************************************************************************************/
  // DaCapo 2006
  static final int ECLIPSE6 = 11;
  static final int HSQLDB6 = 12;
  static final int XALAN6 = 13;
  static final int LUSEARCH6 = 14;
  
  // DaCapo 2009
  static final int AVRORA9 = 21;
  static final int LUINDEX9 = 22;
  static final int LUSEARCH9_FIXED = 23;
  static final int PMD9 = 24;
  static final int SUNFLOW9 = 25;
  static final int XALAN9 = 26;
  static final int JYTHON9 = 27;
  
  // Java Grande
  static final int MOLDYN = 31;
  static final int MONTECARLO = 32;
  static final int RAYTRACER = 33;

  // ETH Zurich
  static final int ELEVATOR = 41;
  static final int PHILO = 42;
  static final int SOR = 43;
  static final int HEDC = 44;
  static final int TSP = 45;
  
  // Pseudojbb
  static final int PSEUDOJBB2000 = 51;
  static final int PSEUDOJBB2005 = 52;
  
  // Misc
  static final int RAJA = 61;
  
  // Micro
  static final int MICRO = 101;
  
  static final int INVALID = -1;
  /*****************************************************************************************/
  
  private Atom name;
  private int id;
  
  public BenchmarkInfo() {
    name = Atom.findOrCreateAsciiAtom(VM.benchmarkName);
    if (VM.VerifyAssertions) { VM._assert(name != null && name.length() > 0); }
    
    // DaCapo 2006
    if (VM.benchmarkName.equalsIgnoreCase("ECLIPSE6")) { id = ECLIPSE6; }
    else if (VM.benchmarkName.equalsIgnoreCase("HSQLDB6")) { id = HSQLDB6; }
    else if (VM.benchmarkName.equalsIgnoreCase("LUSEARCH6")) { id = LUSEARCH6; }
    else if (VM.benchmarkName.equalsIgnoreCase("XALAN6")) { id = XALAN6; }
    
    // DaCapo 2009
    else if (VM.benchmarkName.equalsIgnoreCase("AVRORA9")) { id = AVRORA9; }
    else if (VM.benchmarkName.equalsIgnoreCase("JYTHON9")) { id = JYTHON9; }
    else if (VM.benchmarkName.equalsIgnoreCase("LUINDEX9")) { id = LUINDEX9; }
    else if (VM.benchmarkName.equalsIgnoreCase("LUSEARCH9-FIXED")) { id = LUSEARCH9_FIXED; }
    else if (VM.benchmarkName.equalsIgnoreCase("PMD9")) { id = PMD9; }
    else if (VM.benchmarkName.equalsIgnoreCase("SUNFLOW9")) { id = SUNFLOW9; }
    else if (VM.benchmarkName.equalsIgnoreCase("XALAN9")) { id = XALAN9; }
    
    // Java Grande
    else if (VM.benchmarkName.equalsIgnoreCase("MOLDYN")) { id = MOLDYN; }
    else if (VM.benchmarkName.equalsIgnoreCase("MONTECARLO")) { id = MONTECARLO; }
    else if (VM.benchmarkName.equalsIgnoreCase("RAYTRACER")) { id = RAYTRACER; }
    
    // ETH Zurich
    else if (VM.benchmarkName.equalsIgnoreCase("ELEVATOR")) { id = ELEVATOR; }
    else if (VM.benchmarkName.equalsIgnoreCase("PHILO")) { id = PHILO; }
    else if (VM.benchmarkName.equalsIgnoreCase("HEDC")) { id = HEDC; }
    else if (VM.benchmarkName.equalsIgnoreCase("SOR")) { id = SOR; }
    else if (VM.benchmarkName.equalsIgnoreCase("TSP")) { id = TSP; }
    
    // Pseudojbb
    else if (VM.benchmarkName.equalsIgnoreCase("PSEUDOJBB2000")) { id = PSEUDOJBB2000; }
    else if (VM.benchmarkName.equalsIgnoreCase("PSEUDOJBB2005")) { id = PSEUDOJBB2005; }
    
    // Misc
    else if (VM.benchmarkName.equalsIgnoreCase("RAJA")) { id = RAJA; }
    
    // Micro
    else { id = MICRO; }
  }
  
  @Inline
  Atom getName() {
    return name;
  }
  
  @Inline
  public int getId() {
    return id;
  }

  @Inline
  boolean isDaCapoBenchmark() {
    return (id >= 11 && id < 30);
  }
  
  @Inline
  boolean isMicroBenchmark() {
    return (id >= 101);
  }
  
}
