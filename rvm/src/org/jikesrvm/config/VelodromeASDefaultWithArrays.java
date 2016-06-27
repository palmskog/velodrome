package org.jikesrvm.config;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASDefaultWithArrays extends VelodromeASDefault {

  public VelodromeASDefaultWithArrays() {
    // Post barriers should be enabled if arrays are instrumented
    if (VM.VerifyAssertions) { VM._assert(instrumentArrays() ? insertPostBarriers() : true); }
  }
  
  @Pure
  @Override
  public boolean instrumentArrays() {
    return true;
  }
  
}
