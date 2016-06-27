package org.jikesrvm.config;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeASCrossThreadEdges extends VelodromeASMiscHeader {

  @Pure
  @Override
  public boolean createCrossThreadEdges() {
    return true;
  }
  
}
