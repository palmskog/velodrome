package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASTransactionInstrumentation extends VelodromeAtomicitySpecifications {

  @Pure
  @Override
  public boolean methodsAsTransactions() {
    return true;
  }
  
  @Pure
  @Override
  public boolean inlineStartEndTransactions() {
    return true;
  }
  
}
