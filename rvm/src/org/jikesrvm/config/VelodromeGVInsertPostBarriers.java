package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeGVInsertPostBarriers extends VelodromeGVInsertBarriers {

  @Pure
  @Override
  public boolean insertPostBarriers() {
    return true;
  }
  
}
