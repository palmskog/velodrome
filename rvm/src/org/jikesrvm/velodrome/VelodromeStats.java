package org.jikesrvm.velodrome;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeStats {
  
  // Related to transactions
  public static final VelodromeThreadSafeCounter numTransactions = new VelodromeThreadSafeCounter("VelodromeNodes");
  public static final VelodromeThreadSafeCounter numRegularTransactions = new VelodromeThreadSafeCounter("VelodromeNumRegularTransactions");
  public static final VelodromeThreadSafeCounter numUnaryTransactions = new VelodromeThreadSafeCounter("VelodromeNumUnaryTransactions");

  // Cross-thread edges
  public static final VelodromeThreadSafeCounter numCrossThreadEdges = new VelodromeThreadSafeCounter("VelodromeNumCrossThreadEdges");

  // Total accesses
  public static final VelodromeThreadSafeCounter numAccessesTotal = new VelodromeThreadSafeCounter("VelodromeNumAccessesTotal");
  public static final VelodromeThreadSafeCounter numAccessesTracked = new VelodromeThreadSafeCounter("VelodromeNumAccessesTracked");
  public static final VelodromeThreadSafeCounter numAccessesAvoidedPre = new VelodromeThreadSafeCounter("VelodromeNumAccessedAvoidedPre");
  public static final VelodromeThreadSafeCounter numAccessesAvoidedPost = new VelodromeThreadSafeCounter("VelodromeNumAccessesAvoidedPost");
  
  // Inner class definitions

  @Uninterruptible
  public static class VelodromeThreadSafeCounter {
    
    private final long[] values;
    final String name;
    
    VelodromeThreadSafeCounter(String name) {
      this.name = name;
      values = Velodrome.recordVelodromeStats() ? new long[RVMThread.MAX_THREADS] : null;
    }
    
    @Inline
    public void inc(long value) {
      if (VM.VerifyAssertions) { VM._assert(Velodrome.recordVelodromeStats()); }
      if (VM.runningVM) {
        // Do not increment dynamic stats if we are not in Harness, but inHarness() is false, why?
        if (MemoryManager.inHarness()) { // Remember to change this in two places
          values[RVMThread.getCurrentThreadSlot()] += value;
        } 
      }
    }
    
    @Inline
    public void inc(RVMThread thread, long value) {
      if (VM.VerifyAssertions) { VM._assert(Velodrome.recordVelodromeStats()); }
      if (VM.runningVM) {
        // Do not increment dynamic stats if we are not in Harness, but inHarness() is false, why?
        if (MemoryManager.inHarness()) { // Remember to change this in two places
          values[thread.getGivenThreadSlot()] += value;
        }
      }
    }
    
    @Inline
    final long total() {
      long total = 0;
      for (long value : values) {
        total += value;
      }
      return total;
    }
    
    final long report() {
      VM.sysWrite("VelodromeStats:", this.name, ": ");
      long total = total(); 
      VM.sysWriteln(total);
      return total;
    }
    
  }
  
}
