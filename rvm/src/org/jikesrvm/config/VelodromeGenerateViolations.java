package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

/** This class is the base for using the exclusion list */
@Uninterruptible
public class VelodromeGenerateViolations extends VelodromeAddPerFieldMetadata {

  @Pure
  @Override
  public boolean isPerformanceRun() {
    return false;
  }
  
}
