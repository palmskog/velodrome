package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeAddPerFieldMetadata extends VelodromeBase {

  @Pure
  @Override
  public boolean addPerFieldVelodromeMetadata() {
    return true;
  }
  
}
