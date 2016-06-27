package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASTrackMetadata extends VelodromeASInsertPostBarriers {

  @Pure
  @Override
  public boolean trackLastAccess() {
    return true;
  }
  
}
