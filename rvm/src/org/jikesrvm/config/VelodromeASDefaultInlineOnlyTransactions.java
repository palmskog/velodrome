package org.jikesrvm.config;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASDefaultInlineOnlyTransactions extends VelodromeASDefault {

  public VelodromeASDefaultInlineOnlyTransactions() {
    if (VM.VerifyAssertions) { VM._assert(inlineStartEndTransactions()); }
  }
  
  @Pure
  @Override
  public boolean inlineBarriers() {
    return false;
  }
  
}
