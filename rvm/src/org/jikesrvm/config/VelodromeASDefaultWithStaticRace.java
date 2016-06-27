package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASDefaultWithStaticRace extends VelodromeASDefault {

  @Pure
  @Override
  public boolean enableStaticRaceDetection() { 
    return true; 
  }
  
}
