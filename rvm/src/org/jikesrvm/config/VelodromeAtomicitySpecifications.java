package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeAtomicitySpecifications extends VelodromeAddPerFieldMetadata {

  @Pure
  @Override
  public boolean isPerformanceRun() {
    return true;
  }
  
}
