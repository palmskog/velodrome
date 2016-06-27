package org.jikesrvm.config;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASDefaultInlineOnlyBarriers extends VelodromeASDefault {

  public VelodromeASDefaultInlineOnlyBarriers() {
    if (VM.VerifyAssertions) { VM._assert(inlineBarriers()); }
  }
  
  @Pure
  @Override
  public boolean inlineStartEndTransactions() {
    return false;
  }
  
}
