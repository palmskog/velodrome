package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeGVDefault extends VelodromeGVCrossThreadEdges {

  @Pure
  @Override
  public boolean invokeCycleDetection() {
    return true;
  }
  
}
