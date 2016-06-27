package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeGVTrackMetadata extends VelodromeGVInsertPostBarriers {

  @Pure
  @Override
  public boolean trackLastAccess() {
    return true;
  }
  
}
