package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeCustom2 extends VelodromeASDefault {
  
  @Pure
  @Override
  public boolean invokeCycleDetection() {
    return false;
  }

}