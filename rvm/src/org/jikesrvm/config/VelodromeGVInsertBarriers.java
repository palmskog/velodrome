package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeGVInsertBarriers extends VelodromeGVTransactionInstrumentation {

  @Pure
  @Override
  public boolean insertBarriers() { 
    return true; 
  }
  
}
