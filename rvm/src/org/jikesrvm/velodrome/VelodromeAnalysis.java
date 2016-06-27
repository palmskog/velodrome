package org.jikesrvm.velodrome;

import static org.jikesrvm.ia32.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Context;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.baseline.ia32.OctetBaselineInstr;
import org.jikesrvm.compilers.baseline.ia32.VelodromeBaselineInstr;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.OctetOptInstr;
import org.jikesrvm.compilers.opt.OctetOptSelection;
import org.jikesrvm.compilers.opt.RedundantBarrierRemover;
import org.jikesrvm.compilers.opt.RedundantBarrierRemover.AnalysisLevel;
import org.jikesrvm.compilers.opt.VelodromeOptSelection;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.VelodromeOptInstr;
import org.jikesrvm.octet.ClientAnalysis;
import org.jikesrvm.octet.Octet;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

@Uninterruptible
public class VelodromeAnalysis extends ClientAnalysis {

  @Interruptible
  @Override
  protected void boot() {
    Velodrome.boot();
  }
  
  @Interruptible
  public void handleThreadTerminationEarly() {
    // Velodrome: LATER: This assertion fails for few benchmarks: avrora9 
    if (VM.VerifyAssertions) { VM._assert(!RVMThread.getCurrentThread().inTransaction()); }
    super.handleThreadTerminationEarly();
  }
  
  @Override
  public void handleThreadTerminationLate() {
    super.handleThreadTerminationLate();
  }
  
  @Inline
  @Override
  protected boolean needsFieldInfo() {
    return true;
  }
  
  @Inline
  @Override
  protected boolean useFieldOffset() {
    return true;
  }

  /** Support overriding/customizing barrier insertion in the baseline compiler */
  @Interruptible
  @Override
  public OctetBaselineInstr newBaselineInstr() {
    return new VelodromeBaselineInstr();
  }
  
  /** Support overriding/customizing the choice of which instructions the opt compiler should instrument */
  @Interruptible
  @Override
  public OctetOptSelection newOptSelect() {
    return new VelodromeOptSelection();
  }

  /** Support overriding/customizing barrier insertion in the opt compiler */
  @Interruptible
  @Override
  public OctetOptInstr newOptInstr(boolean lateInstr, RedundantBarrierRemover redundantBarrierRemover) {
    return new VelodromeOptInstr(lateInstr, redundantBarrierRemover);
  }
  
  @Interruptible
  @Override
  public RedundantBarrierRemover newRedundantBarriersAnalysis() {
    return new RedundantBarrierRemover(AnalysisLevel.NONE);
  }
  
  /** Let the client analysis specify the chooseBarrier. */
  @Override
  public NormalMethod chooseBarrier(NormalMethod method, boolean isRead, boolean isField, boolean isResolved, boolean isStatic, boolean hasRedundantBarrier, boolean isSpecialized) {
    return VelodromeInstrDecisions.chooseVelodromeBarrier(isRead, isField, isResolved, isStatic);
  }
  
  // Velodrome has disabled RBA. So, I guess the following has no effect.
  @Override
  public boolean instrInstructionHasRedundantBarrier(Instruction inst) { 
    return false; 
  }

  @Override
  public boolean supportsIrBasedBarriers() {
    return false;
  }

  @Override
  public boolean incRequestCounterForImplicitProtocol() {
    return false;
  }
  
  /** Compute the static context that needs to be used for a method */
  // We could possibly invoke this method at places where the isNonAtomic value is checked, 
  // since that is wrong. The actual context would depend on the call stack. 
  public static int walkStackToFindContext() {
    RVMThread currentThread = RVMThread.getCurrentThread();
    if (VM.VerifyAssertions) { VM._assert(currentThread.isOctetThread()); }
    int resolvedContext = Context.INVALID_CONTEXT;
    Address fp = Magic.getFramePointer();
    boolean atLeastOneAppMethod = false;
    // Search for the topmost application frame/method
    while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      int compiledMethodId = Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
        CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);
        RVMMethod method = compiledMethod.getMethod();
        if (!method.isNative() && Octet.shouldInstrumentMethod(method)) {
          resolvedContext = method.getStaticContext();
          atLeastOneAppMethod = true;
          break;
        }
      }
      fp = Magic.getCallerFramePointer(fp);
    }
    if (!atLeastOneAppMethod) { // First application method called
      resolvedContext = Context.NONTRANS_CONTEXT; 
    }
    return resolvedContext;
  }

}
