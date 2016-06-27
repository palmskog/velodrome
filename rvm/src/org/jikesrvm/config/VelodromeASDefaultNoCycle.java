package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASDefaultNoCycle extends VelodromeASDefault {

  @Pure
  @Override
  public boolean invokeCycleDetection() {
    return false;
  }
  
}
