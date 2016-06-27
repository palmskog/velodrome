package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASDefault extends VelodromeASCrossThreadEdges {

  @Pure
  @Override
  public boolean invokeCycleDetection() {
    return true;
  }
  
}
