package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASDefaultNoInline extends VelodromeASDefault {

  @Pure
  @Override
  public boolean inlineStartEndTransactions() {
    return false;
  }

  @Pure
  @Override
  public boolean inlineBarriers() {
    return false;
  }

}
