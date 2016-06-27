package org.jikesrvm.octet;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;

import org.jikesrvm.Callbacks;
import org.jikesrvm.Callbacks.ExitMonitor;
import org.jikesrvm.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.Time;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.util.LinkedListRVM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.unboxed.Address;

/**
 * Octet: statistics and debugging.
 * 
 * In this file stats and counters are used interchangeably to refer to the same
 * idea. E.g., static stats means stats that are collected with a static counter.
 * 
 * @author Mike Bond
 * @author Meisam
 * 
 */
@Uninterruptible
public class Stats implements Constants {

  private static final LinkedListRVM<Stat> all = new LinkedListRVM<Stat>();

  // measuring the communication algorithm:
  static final UnsyncHistogram logTimeCommunicateRequests = new UnsyncHistogram("logTimeCommunicateRequests", true, 1L<<32);
  static final UnsyncHistogram logTimeSlowPath = new UnsyncHistogram("logTimeSlowPath", true, 1L<<32);
  static final UnsyncHistogram threadsWaitedFor = new UnsyncHistogram("threadsWaitedFor", false, 128);
  static final UnsyncHistogram logWaitIter = new UnsyncHistogram("waitIter", true, 1L<<16);
  static final UnsyncHistogram logSlowPathIter = new UnsyncHistogram("slowPathIter", true, 1L<<32);
  static final UnsyncHistogram logSlowPathObjectSize = new UnsyncHistogram("logSlowPathObjectSize", true, 1L<<32);

  static final ThreadSafeCounter readSharedCounterAvoidsSendRequestCall = new ThreadSafeCounter("readSharedCounterAvoidsSendRequestCall", true);
  static final ThreadSafeCounter readSharedCounterAvoidsActualSendRequest = new ThreadSafeCounter("readSharedCounterAvoidsActualSendRequest", true);
  static final ThreadSafeCounter rdExToRdShButOldThreadDied = new ThreadSafeCounter("rdExToRdShButOldThreadDied", true);

  static final PerSiteCounter slowPathsEntered       = new PerSiteCounter("slowPathsEntered");
  static final PerSiteCounter slowPathsExitedEarly   = new PerSiteCounter("slowPathsExitedEarly");
  static final PerSiteCounter fastPathsEntered       = new PerSiteCounter("fastPathsEntered");
  static final PerSiteCounter conflictingTransitions = new PerSiteCounter("conflictingTransitions");

  public static final ThreadSafeCounter Alloc      = new ThreadSafeCounter("Alloc", false);
  static final ThreadSafeCounter Uninit_Init       = new ThreadSafeCounter("Uninit_Init", false);
  static final ThreadSafeCounter WrEx_WrEx_Same    = new ThreadSafeCounter("WrEx_WrEx_Same", false);
  static final ThreadSafeCounter WrEx_WrEx_Diff    = new ThreadSafeCounter("WrEx_WrEx_Diff", false);
  static final ThreadSafeCounter WrEx_RdEx         = new ThreadSafeCounter("WrEx_RdEx", false);
  static final ThreadSafeCounter RdEx_WrEx_Same    = new ThreadSafeCounter("RdEx_WrEx_Same", false);
  static final ThreadSafeCounter RdEx_WrEx_Diff    = new ThreadSafeCounter("RdEx_WrEx_Diff", false);
  static final ThreadSafeCounter RdEx_RdEx         = new ThreadSafeCounter("RdEx_RdEx", false);
  static final ThreadSafeCounter RdEx_RdSh         = new ThreadSafeCounter("RdEx_RdSh", false);
  static final ThreadSafeCounter RdSh_WrEx         = new ThreadSafeCounter("RdSh_WrEx", false);
  static final ThreadSafeCounter RdSh_RdSh_NoFence = new ThreadSafeCounter("RdSh_RdSh_NoFence", false);
  static final ThreadSafeCounter RdSh_RdSh_Fence   = new ThreadSafeCounter("RdSh_RdSh_Fence", false);

  static final ThreadSafeSumCounter sameState      = new ThreadSafeSumCounter("sameState", Alloc, Uninit_Init, WrEx_WrEx_Same, RdEx_RdEx, RdSh_RdSh_NoFence);
  static final ThreadSafeSumCounter upgrading      = new ThreadSafeSumCounter("upgrading", RdEx_WrEx_Same, RdEx_RdSh, RdSh_RdSh_Fence);
  static final ThreadSafeSumCounter conflicting    = new ThreadSafeSumCounter("conflicting", WrEx_WrEx_Diff, WrEx_RdEx, RdEx_WrEx_Diff, RdSh_WrEx);
  static final ThreadSafeSumCounter total          = new ThreadSafeSumCounter("total", Alloc, Uninit_Init, WrEx_WrEx_Same, RdEx_RdEx, RdSh_RdSh_NoFence, RdEx_WrEx_Same, RdEx_RdSh, RdSh_RdSh_Fence, WrEx_WrEx_Diff, WrEx_RdEx, RdEx_WrEx_Diff, RdSh_WrEx);

  // ratio counters
  static final ThreadSafeRatioCounter Alloc_Ratio             = new ThreadSafeRatioCounter("Alloc_Ratio", Alloc, total);
  static final ThreadSafeRatioCounter Uninit_Init_Ratio       = new ThreadSafeRatioCounter("Uninit_Init_Ratio", Uninit_Init, total);
  static final ThreadSafeRatioCounter WrEx_WrEx_Same_Ratio    = new ThreadSafeRatioCounter("WrEx_WrEx_Same_Ratio", WrEx_WrEx_Same, total);
  static final ThreadSafeRatioCounter WrEx_WrEx_Diff_Ratio    = new ThreadSafeRatioCounter("WrEx_WrEx_Diff_Ratio", WrEx_WrEx_Diff, total);
  static final ThreadSafeRatioCounter WrEx_RdEx_Ratio         = new ThreadSafeRatioCounter("WrEx_RdEx_Ratio", WrEx_RdEx, total);
  static final ThreadSafeRatioCounter RdEx_WrEx_Same_Ratio    = new ThreadSafeRatioCounter("RdEx_WrEx_Same_Ratio", RdEx_WrEx_Same, total);
  static final ThreadSafeRatioCounter RdEx_WrEx_Diff_Ratio    = new ThreadSafeRatioCounter("RdEx_WrEx_Diff_Ratio", RdEx_WrEx_Diff, total);
  static final ThreadSafeRatioCounter RdEx_RdEx_Ratio         = new ThreadSafeRatioCounter("RdEx_RdEx_Ratio", RdEx_RdEx, total);
  static final ThreadSafeRatioCounter RdEx_RdSh_Ratio         = new ThreadSafeRatioCounter("RdEx_RdSh_Ratio", RdEx_RdSh, total);
  static final ThreadSafeRatioCounter RdSh_WrEx_Ratio         = new ThreadSafeRatioCounter("RdSh_WrEx_Ratio", RdSh_WrEx, total);
  static final ThreadSafeRatioCounter RdSh_RdSh_NoFence_Ratio = new ThreadSafeRatioCounter("RdSh_RdSh_NoFence_Ratio", RdSh_RdSh_NoFence, total);
  static final ThreadSafeRatioCounter RdSh_RdSh_Fence_Ratio   = new ThreadSafeRatioCounter("RdSh_RdSh_Fence_Ratio", RdSh_RdSh_Fence, total);

  static final ThreadSafeRatioCounter sameState_Ratio      = new ThreadSafeRatioCounter("sameState_Ratio", sameState, total);
  static final ThreadSafeRatioCounter upgrading_Ratio      = new ThreadSafeRatioCounter("upgrading_Ratio", upgrading, total);
  static final ThreadSafeRatioCounter conflicting_Ratio    = new ThreadSafeRatioCounter("conflicting_Ratio", conflicting, total);
  static final ThreadSafeRatioCounter total_Ratio          = new ThreadSafeRatioCounter("total_Ratio", total, total); // should report 1.00

  // Counting threads
  public static final SpecialUnsyncCounter threadsLive = new SpecialUnsyncCounter("threadsLive");
  
  // stats for RBA
  public static final ThreadSafeCounter sharedAccesses = new ThreadSafeCounter("Shared_Accesses", true); 
  public static final ThreadSafeCounter redundantBarriers = new ThreadSafeCounter("Redundant_Barriers", true);
  public static final UnsyncHistogram optimisticRbaStaticLoopSize = new UnsyncHistogram("optimisticRbaStaticLoopSize", true, 1L<<32);

  static {
    if (Octet.getConfig().stats()) {
      Callbacks.addExitMonitor(new ExitMonitor() {
        //@Override
        public void notifyExit(int value) {

          System.out.println("BEGIN .gv");
          System.out.println("  Null -> WrEx [ style=dotted label = \"" + pct(Alloc.total() + Uninit_Init.total(), total.total()) + "%\" ]");
          System.out.println("  WrEx -> WrEx [ style=dotted label = \"" + pct(WrEx_WrEx_Same.total(), total.total()) + "%\" ]");
          System.out.println("  WrEx -> WrEx [ style=solid  label = \"" + pct(WrEx_WrEx_Diff.total(), total.total()) + "%\" headport=sw ]");
          System.out.println("  WrEx -> RdEx [ style=solid  label = \"" + pct(WrEx_RdEx.total(), total.total()) + "%\" ]");
          System.out.println("  RdEx -> WrEx [ style=dashed label = \"" + pct(RdEx_WrEx_Same.total(), total.total()) + "%\" ]");
          System.out.println("  RdEx -> WrEx [ style=solid  label = \"" + pct(RdEx_WrEx_Diff.total(), total.total()) + "%\" ]");
          System.out.println("  RdEx -> RdEx [ style=dotted label = \"" + pct(RdEx_RdEx.total(), total.total()) + "%\" ]");
          System.out.println("  RdEx -> RdSh [ style=dashed label = \"" + pct(RdEx_RdSh.total(), total.total()) + "%\" ]");
          System.out.println("  RdSh -> WrEx [ style=solid  label = \"" + pct(RdSh_WrEx.total(), total.total()) + "%\" ]");
          System.out.println("  RdSh -> RdSh [ style=dotted label = \"" + pct(RdSh_RdSh_NoFence.total(), total.total()) + "%\" ]");
          System.out.println("  RdSh -> RdSh [ style=dashed label = \"" + pct(RdSh_RdSh_Fence.total(), total.total()) + "%\" ]");
          System.out.println();
          System.out.println("Null1 -> Null2 [style=dotted label=\"Same state: "  + commas(sameState.total()) + "\" ]");
          System.out.println("Null3 -> Null4 [style=dashed label=\"Upgrading: "   + commas(upgrading.total()) + "\" ]");
          System.out.println("Null5 -> Null6 [style=solid  label=\"Conflicting: " + commas(conflicting.total()) + "\" ]");
          System.out.println("END .gv");

          System.out.println();
          
          System.out.println("BEGIN tabular");
          System.out.println(commas(sameState.total()) + " & " + commas(upgrading.total()) + " & " + commas(conflicting.total()));
          
          System.out.print(pct(Alloc.total() + Uninit_Init.total(), total.total()));
          for (ThreadSafeCounter counter : new ThreadSafeCounter[] { WrEx_WrEx_Same, RdEx_RdEx, RdSh_RdSh_NoFence,
                                                                     RdEx_WrEx_Same, RdEx_RdSh, RdSh_RdSh_Fence,
                                                                     WrEx_WrEx_Diff, WrEx_RdEx, RdEx_WrEx_Diff, RdSh_WrEx } ) {
            System.out.print(" & " + pct(counter.total(), total.total()));
          }
          System.out.println(" \\");
          System.out.println("END tabular");

        }
      });
    }
  }
  
  @Interruptible
  static String pct(long x, long total) {
    double fraction = (double)x / total;
    double pct = fraction * 100;
    return new DecimalFormat("0.0000000000000").format(pct); 
  }
  
  @Interruptible
  public static String commas(long n) {
    if (n < 0) {
      return "-" + commas(-n);
    } else if (n < 1000) {
      return String.valueOf(n);
    } else {
      return commas(n / 1000) + "," + String.valueOf((n % 1000) + 1000).substring(1);
    }
  }
  
  @Interruptible
  public static String commas(ThreadSafeCounter counter) {
    return commas(counter.total());
  }
  
  // measuring how often and where communication gets blocked:
  // Octet: LATER: consider other ways we might avoid blocking sometimes, in favor of checking (e.g., if code could be in a loop for a while but won't actually block)
  public static final ThreadSafeCounter blockCommEntrypoint = new ThreadSafeCounter("blockCommEntrypoint", false);
  public static final ThreadSafeCounter blockCommReceiveResponses = new ThreadSafeCounter("blockCommReceiveResponses", false);
  public static final ThreadSafeCounter blockCommBeginPairHandshake = new ThreadSafeCounter("blockCommBeginPairHandshake", false);
  public static final ThreadSafeCounter blockCommThreadBlock = new ThreadSafeCounter("blockCommThreadBlock", false);
  public static final ThreadSafeCounter blockCommEnterJNIFromCallIntoNative = new ThreadSafeCounter("blockCommEnterJNIFromCallIntoNative", false);
  public static final ThreadSafeCounter blockCommEnterJNIFromJNIFunctionCall = new ThreadSafeCounter("blockCommEnterJNIFromJNIFunctionCall", false);
  public static final ThreadSafeCounter blockCommEnterNative = new ThreadSafeCounter("blockCommEnterNative", false);
  public static final ThreadSafeCounter blockCommTerminate = new ThreadSafeCounter("blockCommTerminate", false);
  public static final ThreadSafeCounter blockCommYieldpoint = new ThreadSafeCounter("blockCommYieldpoint", false);
  public static final ThreadSafeCounter blockCommHoldsLock = new ThreadSafeCounter("blockCommHoldsLock", false);
  public static final ThreadSafeCounter blockCommLock = new ThreadSafeCounter("blockCommLock", false);
  
  static {
    Callbacks.addExitMonitor(new ExitMonitor() {
      //@Override
      public void notifyExit(int value) {
        for (Stat stat : all) {
          stat.report();
        }
      }
    });
  }
  
  @Uninterruptible
  static abstract class Stat {
    public static final String SEPARATOR = ": ";
    public static final String LINE_PREFIX = "STATS" + SEPARATOR;
    final String name;
    /**
     * Determines if stats that we want to collect with this object is compile
     * time stats or runtime stats.
     */
    protected final boolean staticStat;

    Stat(String name, boolean runtimeStats) {
      // if counter names contain spaces in their names, EXP cannot parse the results.
      if (VM.VerifyAssertions) {
        VM._assert(name == null || !name.contains(" "), "Counter name contains space character in it.");
      }
      this.name = name;
      this.staticStat = runtimeStats;
      if (Octet.getConfig().stats()) {
        if (name != null) {
          synchronized (all) {
            all.add(this);
          }
        }
      }
    }
    @Interruptible
    abstract void report();
    
    @Interruptible
    protected String outputLinePrefix() {
      return LINE_PREFIX + this.getClass().getName() + SEPARATOR;
    }
  }
  
  public static final class PerSiteCounter extends Stat {
    final UnsyncCounter[] counters;
    public PerSiteCounter(String name) {
      super(name, true);
      if (Octet.getConfig().stats()) {
        counters = new UnsyncCounter[Site.MAX_SITES];
        for (int i= 0; i < Site.MAX_SITES; i++) {
          counters[i] = new UnsyncCounter(null);
        }
      } else {
        counters = null;
      }
    }
    @Uninterruptible
    @Inline
    public void inc(int siteID) {
      if (Octet.getConfig().stats()) {
        counters[siteID].inc();
      }
    }
    @Interruptible
    @Override
    public void report() {
      Integer[] siteIDs = new Integer[Site.MAX_SITES];
      for (int i = 0; i < Site.MAX_SITES; i++) {
        siteIDs[i] = i;
      }
      Arrays.sort(siteIDs, new Comparator<Integer>() {
        public int compare(Integer siteID1, Integer siteID2) {
          long total1 = counters[siteID1].getValue();
          long total2 = counters[siteID2].getValue();
          if (total1 > total2) {
            return -1;
          } else if (total1 < total2) {
            return 1;
          } else {
            return 0;
          }
        }
      });
      long grandTotal = 0;
      for (UnsyncCounter counter : counters) {
        grandTotal += counter.getValue();
      }
      System.out.print(outputLinePrefix());
      System.out.println(name + " = " + grandTotal);
      for (int i = 0; i < 25; i++) {
        int siteID = siteIDs[i];
        System.out.print(outputLinePrefix());
        System.out.println("  " + Site.lookupSite(siteID) + " = " + counters[siteID].getValue());
      }
    }
  }
  
  @Uninterruptible
  public static class UnsyncCounter extends Stat {
    protected long value;
    UnsyncCounter(String name) {
      super(name, true);
    }
    @Inline
    public void inc() {
      if (Octet.getConfig().stats()) {
        value++;
      }
    }
    @Interruptible
    @Override
    void report() {
      System.out.print(outputLinePrefix());
      System.out.println(name + " = " + value);
    }
    final long getValue() {
      return value;
    }
  }
  
  @Uninterruptible
  public static class SpecialUnsyncCounter extends UnsyncCounter {
    private long max;
    private long numIncs;
    private long numDecs;
    SpecialUnsyncCounter(String name) {
      super(name);
    }
    @Override
    @Inline
    public void inc() {
      if (Octet.getConfig().stats()) {
        super.inc();
        numIncs++;
        max = value > max ? value : max;
      }
    }
    @Inline
    public void dec() {
      if (Octet.getConfig().stats()) {
        value--;
        numDecs++;
      }
    }
    @Interruptible
    @Override
    final void report() {
      System.out.print(outputLinePrefix());
      System.out.println(name + " = " + value + " (max " + max + ", numIncs " + numIncs + ", numDecs " + numDecs + ")");
    }
  }
  
  @Uninterruptible
  public static class ThreadSafeCounter extends Stat {
    private final long[] values;
    protected long total = -1;

    ThreadSafeCounter(String name, boolean staticStats) {
      super(name, staticStats);
      values = Octet.getConfig().stats() ? new long[RVMThread.MAX_THREADS] : null;
    }

    @Inline
    public void inc() {
      if (Octet.getConfig().stats()) {
        if (VM.runningVM) {
          // do not increment dynamic stats if we are not in Harness
          if (staticStat || (!staticStat && MemoryManager.inHarness())) {
            values[RVMThread.getCurrentThreadSlot()]++;
          }
        } else if (staticStat){
          incDuringBootImageBuild();
        }
      }
    }
    @NoInline
    @UninterruptibleNoWarn // Needed because of synchronization, but it's okay because we know the VM won't be running when it calls this method.
    private void incDuringBootImageBuild() {
      if (VM.VerifyAssertions) { VM._assert(!VM.runningVM); }
      // Synchronize in case multiple compiler threads are building the boot image (I think that happens).
      synchronized (this) {
        // Note that thread slot 0 isn't actually used by any thread,
        // so we can just use this slot to represent all increments during the boot image build.
        values[0]++;
      }
    }
    @Interruptible
    @Override
    final void report() {
      System.out.print(outputLinePrefix());
      System.out.println(name + " = " + total());
    }
    
    @Uninterruptible
    final long total() {
      // if not computed yet, compute it
      if (total == -1) {
        updateTotal();
      }
      return total ;
    }

    @Uninterruptible
    protected void updateTotal() {
      total = 0;
      for (long value : values) {
        total += value;
      }
    }
  }

  /**
   * 
   * This counter can be used for collecting stats that can be computed by
   * adding several other stats For example number of total accesses can be
   * computed by adding total number of reads with total number of writes.
   * If we already have a counter for reads and another counter for writes,
   * then we can use them to construct a new counter using this class.
   * 
   * @author Meisam
   * 
   */
  @Uninterruptible
  public static final class ThreadSafeSumCounter extends ThreadSafeCounter {

    private ThreadSafeCounter[] threadSafeCounters;

    public ThreadSafeSumCounter(String name, ThreadSafeCounter ... threadSafeCounters) {
      super(name, false); // A sum counter is not really a runtime counter
      this.threadSafeCounters = threadSafeCounters;
    }

    @Uninterruptible
    protected void updateTotal() {
      total = 0;
      for (int i = 0; i < threadSafeCounters.length; i++) {
        total += threadSafeCounters[i].total();
      }
    }
    
  }

  /**
   * 
   * This counter can be used for collecting ratio of two stats, 
   * for example the ratio of reads to writes.
   * number of total accesses can be
   * computed by adding total number of reads with total number of writes. If we
   * already have a counter for reads and another counter for writes, then we
   * can use them to construct a new counter using this class.
   * 
   * @author Meisam
   * 
   */
  @Uninterruptible
  public static final class ThreadSafeRatioCounter extends Stat {

    private ThreadSafeCounter numeratorCounter;
    private ThreadSafeCounter denumeratorCounter;

    public ThreadSafeRatioCounter(String name, ThreadSafeCounter numerator, ThreadSafeCounter denumerator) {
      super(name, true);
      this.numeratorCounter = numerator;
      this.denumeratorCounter = denumerator;
    }

    @Override
    @Interruptible
    void report() {
      System.out.print(outputLinePrefix());
      System.out.println(name + " = " + ratio());
    }

    @Uninterruptible
    private double ratio() {
      return (100.0 * numeratorCounter.total()) / denumeratorCounter.total();
    }

  }

  @Uninterruptible
  public static final class UnsyncHistogram extends Stat {
    final boolean log2;
    private final int[] data;
    UnsyncHistogram(String name, boolean log2, long maxX) {
      super(name, true);
      this.log2 = log2;
      this.data = Octet.getConfig().stats() ? new int[log2 ? log2(maxX) : (int)maxX] : null;
    }
    @Inline
    public final void incBin(long x) {
      if (Octet.getConfig().stats()) {
        if (log2) {
          data[log2(x)]++;
        } else {
          data[(int)x]++;
        }
      }
    }
    @Inline
    public final void incTime(long startTime) {
      if (Octet.getConfig().stats()) {
        incBin(nsElapsed(startTime));
      }
    }
    @Inline
    public final long total() {
      int i;
      long sum = 0;
      for (i = 0; i < data.length; i++) {
        sum += data[i];
      }
      return sum;
    }
    @Inline
    public final double arithmeticMean() { //Weighted arithmetic mean. Overestimated.
      int lastNonzeroIndex;
      for (lastNonzeroIndex = data.length - 1; lastNonzeroIndex >= 0 && data[lastNonzeroIndex] == 0; lastNonzeroIndex--) { }
      double sum = 0;
      int weight = 0;
      if (lastNonzeroIndex > 0) {
        for (int x = 0; x <= lastNonzeroIndex; x++) {
          sum += data[x] * ((1 << (x + 1)) -1);
          weight += ((1 << (x + 1)) -1);
        }
        return sum / weight;
      }
      return 0;
    }
    @Interruptible
    @Override
    final void report() {
      int lastNonzeroIndex; // don't print any tail of all 0s
      for (lastNonzeroIndex = data.length - 1; lastNonzeroIndex >= 1 && data[lastNonzeroIndex] == 0; lastNonzeroIndex--) { }
      
      for (int x = 0; x <= lastNonzeroIndex; x++) {
        System.out.print(outputLinePrefix());
        System.out.print(name + "[" + x + "] = " + data[x]);
        if (log2) {
          //System.out.print(" (" + (data[x] << x) + ")");
          //Man: I think data[x] is the actual value of the counter, even if it is log-based.
          // data[x]<<x doesn't make sense.
          System.out.printf(" (range: [%d, %d))", 1 << x, 1<<(x+1));
        }
        System.out.println();
      }
    }
    static final int log2(long n) {
      if (n <= 1) {
        return 0;
      } else {
        return 1 + log2(n / 2);
      }
    }
  }

  static final long nsElapsed(long startTime) {
    return (long)(Time.nanoTime() - startTime);
  }

  @UninterruptibleNoWarn
  public static final void tryToPrintStack(Address framePointer) {
    try {
      RVMThread.dumpStack(framePointer);
    } catch (Exception ex) { /* do nothing */ }
  }
}
