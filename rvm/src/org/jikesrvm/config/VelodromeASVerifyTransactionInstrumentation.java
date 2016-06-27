package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASVerifyTransactionInstrumentation extends VelodromeASDefault {

  @Pure
  @Override
  public boolean checkStartTransactionInstrumentation() {
    return true;
  }
  
  @Pure
  @Override
  public boolean checkMethodContextAtProlog() {
    return true;
  }
  
}
