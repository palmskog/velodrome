package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class PessimisticBarriersStaticRaceFiltering extends PessimisticBarriers {

  @Pure
  @Override
  public boolean  enableStaticRaceDetection() { 
    return true; 
  }
  
}
