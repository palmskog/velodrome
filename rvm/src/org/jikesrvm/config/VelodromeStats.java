package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeStats extends VelodromeASDefault {

  @Pure
  @Override
  public boolean recordVelodromeStats() {
    return true;
  }
  
}
