package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeGVTransactionInstrumentation extends VelodromeGenerateViolations {

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
