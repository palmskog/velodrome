package org.jikesrvm.config;

import org.jikesrvm.compilers.opt.RedundantBarrierRemover;
import org.jikesrvm.octet.ClientAnalysis;
import org.jikesrvm.velodrome.VelodromeAnalysis;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeBase extends BaseConfig {

  /** Construct the client analysis to use. */
  @Interruptible
  @Override
  public ClientAnalysis constructClientAnalysis() { 
    return new VelodromeAnalysis(); 
  }
  
  @Pure
  @Override
  public boolean needsSites() {
    return true;
  }
  
  @Pure
  @Override
  public boolean insertBarriers() { 
    return false; 
  }
  
  @Pure 
  @Override
  public boolean instrumentLibraries() { 
    return false; 
  }
  
  @Pure
  @Override
  public boolean instrumentArrays() {
    return false;
  }
  
  @Pure 
  public boolean inlineBarriers() { 
    return true; 
  }
  
  @Pure 
  @Override
  public boolean isFieldSensitiveAnalysis() { 
    return true; 
  }
  
  @Interruptible // since enum accesses apparently call interruptible methods
  @Pure
  @Override
  public RedundantBarrierRemover.AnalysisLevel overrideDefaultRedundantBarrierAnalysisLevel() { 
    return RedundantBarrierRemover.AnalysisLevel.NONE;
  }
  
  @Pure
  @Override
  public boolean isVelodromeEnabled() {
    return true;
  }

}
