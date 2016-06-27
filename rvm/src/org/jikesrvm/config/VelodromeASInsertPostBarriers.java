package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASInsertPostBarriers extends VelodromeASInsertBarriers {

  @Pure
  @Override
  public boolean insertPostBarriers() {
    return true;
  }
  
}
