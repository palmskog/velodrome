package org.jikesrvm.config;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASDefaultWithArraysNoCycle extends VelodromeASDefaultNoCycle {
  
  public VelodromeASDefaultWithArraysNoCycle() {
    // Post barriers should be enabled if arrays are instrumented
    if (VM.VerifyAssertions) { VM._assert(instrumentArrays() ? insertPostBarriers() : true); }
    if (VM.VerifyAssertions) { VM._assert(!invokeCycleDetection()); }
  }

  @Pure
  @Override
  public boolean instrumentArrays() {
    return true;
  }
  
}
