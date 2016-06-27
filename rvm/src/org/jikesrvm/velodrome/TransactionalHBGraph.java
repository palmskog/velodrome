package org.jikesrvm.velodrome;

import static org.jikesrvm.ia32.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Context;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.mm.mminterface.Barriers;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.octet.Octet;
import org.jikesrvm.octet.Site;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

@Uninterruptible
public class TransactionalHBGraph {

  /** Special address value used to ensure mutually exclusive access */
  private static final Address LOCK_OUTGOINGEDGE = Address.fromIntZeroExtend(1);
  
  /** Access to this variable should be protected by {@code velodromeEdgeCounterLock} */
  private static int edgeNumber = 0;
  
  private final static int cycleVerbosity = 0; // >= 1 will print cycle information
  
  private static int dfsCounter = 0;
  
  private static TransactionsList stack = TransactionsList.dummyTransaction;

  @Entrypoint 
  @Inline // This probably has no effect
  public static void startTransaction(int site, int methodID) { 
    if (VM.VerifyAssertions) { VM._assert(Velodrome.methodsAsTransactions() || Velodrome.syncBlocksAsTransactions()); }
    RVMThread currentThread = RVMThread.getCurrentThread();
    if (!currentThread.isOctetThread()) { // We do not want to process non-Octet threads
      return;
    }
    
    // Sanity checks
    if (VM.VerifyAssertions) { VM._assert(!currentThread.inTransaction()); }
    Transaction current = currentThread.currentTransaction;
    if (VM.VerifyAssertions) { VM._assert(current.isUnary); } // Current transaction/node should always be a unary transaction
    
    currentThread.setInTransaction();
    
    // Create and add a new regular transaction
    Transaction tx = createTransaction(currentThread, site, methodID, /*isRegularTransaction =*/ true);
    current.next = tx;
    currentThread.currentTransaction = tx;
    
    if (Velodrome.checkStartTransactionInstrumentation()) { VM._assert(checkStartTransactionInstrumentation()); }
    
  }
  
  @UninterruptibleNoWarn
  private static Transaction createTransaction(RVMThread thread, int siteID, int methodID, boolean isRegularTransaction) {
    Transaction tx;
    MemoryManager.startAllocatingInUninterruptibleCode();
    RVMThread.velodromeEdgeCounterLock.lockNoHandshake();
    int count = ++edgeNumber; // For sequential edges
    RVMThread.velodromeEdgeCounterLock.unlock();
    tx = new Transaction(thread, ++thread.numberOfNodes, siteID, isRegularTransaction, methodID, count);
    MemoryManager.stopAllocatingInUninterruptibleCode();
    return tx;
  }
  
  @NoInline
  private static void printTransaction(int site, int methodID, boolean isStart) {
    RVMThread currentThread = RVMThread.getCurrentThread();
    if (!currentThread.isOctetThread()) { // We are not bothered about non-Octet threads
      return;
    }
    VM.sysWrite(isStart ? "startTransaction(): " : "endTransaction(): ");
    VM.sysWrite("Thread id: ", currentThread.octetThreadID);
    VM.sysWrite(" Trans id: ", currentThread.currentTransaction.transactionID);
    VM.sysWrite(", In Trans: ", currentThread.inTransaction());
    VM.sysWrite(", Method id:", methodID);
    VM.sysWrite(", Site:", site);
    if (Velodrome.needsSites()) {
      if (VM.VerifyAssertions) { VM._assert(site >= 0); } // Site should be valid for method start/end
      Site site1 = Site.lookupSite(site);
      if (VM.VerifyAssertions) { VM._assert(site1 != null); }
      site1.sysWriteln();
    } else {
      VM.sysWriteln();
    }
  }
  
  @Entrypoint 
  @Inline // This probably has no effect
  public static void endTransaction(int site, int methodID) {
    if (VM.VerifyAssertions) { VM._assert(Velodrome.methodsAsTransactions() || Velodrome.syncBlocksAsTransactions()); }
    RVMThread currentThread = RVMThread.getCurrentThread();
    if (!currentThread.isOctetThread()) { // We do not want to process non-Octet threads
      return;
    }
    
    // Perform sanity checks
    if (VM.VerifyAssertions) {
      VM._assert(Velodrome.methodsAsTransactions() || Velodrome.syncBlocksAsTransactions());
      VM._assert(currentThread.inTransaction());
    }
    if (Velodrome.checkStartTransactionInstrumentation()) { VM._assert(checkStartTransactionInstrumentation()); }
    
    Transaction current = currentThread.currentTransaction;
    if (VM.VerifyAssertions) { VM._assert(current.methodID == methodID); }
    
    currentThread.resetInTransaction();
    
    Transaction last = createTransaction(currentThread, site, methodID, /*isRegularTransaction =*/ false);
    current.next = last;
    currentThread.currentTransaction = last;
  }
  
  // Create WAW output dependence edge
  @Inline
  public static boolean createWrWrEdge(Transaction source, int sourceSiteID, Transaction dest, int destSiteID) {
    return createEdgeHelper(source, sourceSiteID, dest, destSiteID);
  }

  // Create WAR anti dependence edge
  @Inline
  public static boolean createRdWrEdge(Transaction lastRead, int sourceSiteID, Transaction currentWrite, int destSiteID) {
    return createEdgeHelper(lastRead, sourceSiteID, currentWrite, destSiteID);
  }
  
  // Create possibly many WAR anti dependence edges
  public static void createRdWrEdges(ReadHashMap readMap, Transaction currentWrite, int siteID) {
    if (VM.VerifyAssertions) { VM._assert(readMap != null); }
    if (readMap != null) {
      if (VM.VerifyAssertions) { VM._assert(readMap.length == ReadHashMap.INITIAL_NUMBER_THREADS); }
      for (int i = 0; i < readMap.length; i++) {
        ReadHashMapElement tmp = readMap.getBucketHead(i); // tmp is the start pointer to the bucket indexed by i
        while (tmp != null) {
          if (tmp.transaction.octetThread != currentWrite.octetThread) {
            createEdgeHelper(tmp.transaction, tmp.lastReadSiteID, currentWrite, siteID);
          }
          tmp = readMap.getNext(tmp);
        }
      }
    }
  }

  // Create RAW true dependence edge
  @Inline
  public static boolean createWrRdEdge(Transaction lastWrite, int sourceSiteID, Transaction currentRead, int destSiteID) {
    return createEdgeHelper(lastWrite, sourceSiteID, currentRead, destSiteID);
  }
  
  @Inline
  public static boolean createLockReleaseAcquireEdge(Transaction release, Transaction acquire) {
    return createEdgeHelper(release, -1, acquire, -1);
  }
  
  /** Create a cross-thread edge between nodes {@code source --> dest} 
   *  At the moment, we do not create any cross-thread edges involving the driver thread for DaCapo benchmarks.
   * */
  private static boolean createEdgeHelper(Transaction source, int sourceSiteID, Transaction dest, int destSiteID) {
    if (VM.VerifyAssertions) { VM._assert(source != null && dest != null); }
    if (VM.VerifyAssertions) { VM._assert(Velodrome.addPerFieldVelodromeMetadata()); }
    // This is especially problematic for avrora9
    if (Velodrome.bench.isDaCapoBenchmark() && 
        (source.octetThread.octetThreadID == Velodrome.OCTET_FINALIZER_THREAD_ID || dest.octetThread.octetThreadID == Velodrome.OCTET_FINALIZER_THREAD_ID)) {
      return false;
    }
    if (Velodrome.bench.getId() == BenchmarkInfo.TSP && 
        (source.octetThread.octetThreadID == Velodrome.TSP_DRIVER_THREAD_OCTET_ID || dest.octetThread.octetThreadID == Velodrome.TSP_DRIVER_THREAD_OCTET_ID)) {
      return false;
    }
    // We avoid cycle detection from the driver thread in DaCapo, which is currently Thread 1. Sync changes with AVD.
    if (Velodrome.bench.isDaCapoBenchmark() 
        && (source.octetThread.octetThreadID == Velodrome.DACAPO_DRIVER_THREAD_OCTET_ID || dest.octetThread.octetThreadID == Velodrome.DACAPO_DRIVER_THREAD_OCTET_ID)) {
      return false;
    }
    if (source.octetThread == dest.octetThread || isCrossThreadEdgeAlreadyPresent(source, dest)) {
      return false; // No cross-thread dependence
    }
    return createEdge(source, sourceSiteID, dest, destSiteID);
  }
  
  /** Create a cross-thread edge between nodes {@code source --> dest} */
  private static boolean createEdge(Transaction source, int sourceSiteID, Transaction dest, int destSiteID) {
    if (VM.VerifyAssertions) { VM._assert(dest.next == null); } // dest should be an ongoing transaction
    Address oldHead;
    do {
      do {
        oldHead = ObjectReference.fromObject(source).toAddress().prepareAddress(Entrypoints.velodromeOutgoingEdgesField.getOffset());
      } while (oldHead.EQ(LOCK_OUTGOINGEDGE));      
      if (ObjectReference.fromObject(source).toAddress().attempt(oldHead, LOCK_OUTGOINGEDGE, Entrypoints.velodromeOutgoingEdgesField.getOffset())) {
        break;
      }
    } while(true);
    // We how have exclusive access to the outgoing list
    TransactionsList oldListHead = (TransactionsList) oldHead.toObjectReference().toObject();
    if (VM.VerifyAssertions) { VM._assert(oldListHead != null); } 
    RVMThread.velodromeEdgeCounterLock.lockNoHandshake();
    int edgeCount = ++edgeNumber;
    RVMThread.velodromeEdgeCounterLock.unlock();
    TransactionsList newListHead = TransactionsList.createTransactionsListNode(dest, edgeCount); 
    newListHead.next = oldListHead;
    // An MFENCE is required over here
    Magic.fence();
    boolean result = false;
    Address temp = ObjectReference.fromObject(source).toAddress().prepareAddress(Entrypoints.velodromeOutgoingEdgesField.getOffset());
    if (VM.VerifyAssertions) { VM._assert(temp.EQ(LOCK_OUTGOINGEDGE)); }
    Object fakeObject = LOCK_OUTGOINGEDGE.toObjectReference().toObject();
    if (Barriers.NEEDS_OBJECT_PUTFIELD_BARRIER) {
      result = Barriers.objectTryCompareAndSwap(source, Entrypoints.velodromeOutgoingEdgesField.getOffset(), 
          fakeObject, newListHead);
    } else {
      result = ObjectReference.fromObject(source).toAddress().attempt(LOCK_OUTGOINGEDGE, 
          ObjectReference.fromObject(newListHead).toAddress(), Entrypoints.velodromeOutgoingEdgesField.getOffset());
    }
    if (VM.VerifyAssertions) { VM._assert(result, "Accessing the list is expected to be mutually exclusive"); }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numCrossThreadEdges.inc(1L);
    }
    
    if (Velodrome.invokeCycleDetection()) {
      if (checkForCycle(source, sourceSiteID, dest, destSiteID, edgeCount)) {
        if (VM.VerifyAssertions) { VM._assert(!dest.isUnary); }
        if (cycleVerbosity > 0) {
          VM.sysWriteln("*********************************************************************************");
          VM.sysWrite("Cycle detected:");
          if (cycleVerbosity > 2) {
            Transaction.printTransaction(dest);
            VM.sysWrite(" --->  ");
            Transaction.printTransaction(source);
            VM.sysWrite("Source site");
            if (destSiteID >= 0) {
              Site.lookupSite(destSiteID).sysWriteln();
            } else {
              VM.sysWriteln("Source site id is -1");
            }
            VM.sysWrite(" --->  ");
            if (sourceSiteID >= 0) {
              Site.lookupSite(sourceSiteID).sysWriteln();
            } else {
              VM.sysWriteln("Dest site id is -1");
            }
          }
          VM.sysWrite("Culprit Transaction: ");;
          Transaction.printTransaction(dest);
          VM.sysWriteln("*********************************************************************************");
        }
      }
    }
    return result;
  }
  
  /** Returns true if there is already a cross-thread edge of the form {@code source --> dest} */
  static boolean isCrossThreadEdgeAlreadyPresent(Transaction source, Transaction dest) {
    if (VM.VerifyAssertions) { VM._assert(MemoryManager.validRef(ObjectReference.fromObject(source))); }
    Address oldHead;
    do {
      oldHead = ObjectReference.fromObject(source).toAddress().loadAddress(Entrypoints.velodromeOutgoingEdgesField.getOffset());
    } while (oldHead.EQ(LOCK_OUTGOINGEDGE));
    if (VM.VerifyAssertions) { VM._assert(MemoryManager.validRef(oldHead.toObjectReference())); }
    TransactionsList start = (TransactionsList) oldHead.toObjectReference().toObject();
    if (VM.VerifyAssertions) { VM._assert(start != null); }    
    while (start.transaction !=  null) {
      if (start.transaction == dest) {
        if (VM.VerifyAssertions) { VM._assert(start.transaction.transactionID == dest.transactionID && start.transaction.octetThread == dest.octetThread); }
        return true;
      }
      start = start.next;
    }
    return false;
  }
  
  /** We want to check for possible cycles between {@code source} and {@code dest} after an 
   *  edge from {@code source --> dest} has been added. To do that, we start exploring from {@code dest} and
   *  see if we can reach {@code source}. If we can, then we say that there is a cycle from transaction {@code dest} */
  private static boolean checkForCycle(Transaction source, int sourceSiteID, Transaction dest, int destSiteID, int currentEdgeCount) {
    if (dest.transactionID == Velodrome.START_TRANSACTION_ID || dest.isUnary) { 
      return false;
    }
    RVMThread.velodromeCycleLock.lockNoHandshake();
    if (VM.VerifyAssertions) { VM._assert(dfsCounter % 3 == 0); } 
    
    boolean flag = dfs(source, dest, dest, dfsCounter, currentEdgeCount, true);

    dfsCounter += 3;
    RVMThread.velodromeCycleLock.unlock();
    return flag;
  }

  /** (firstCall == true) ==> (current == dest) */
  private static boolean dfs(Transaction source, Transaction dest, Transaction current, int dCounter, int eCounter, boolean firstCall) {
    if (VM.VerifyAssertions) { VM._assert(dest.next == null); } // dest should be an ongoing transaction
    
    if (current == source && !firstCall) { // Cycle detected
      if (VM.VerifyAssertions) { VM._assert(current.octetThread == source.octetThread); }
      return true;
    }
    
    int WHITE = dCounter; // value of white
    int GRAY = dCounter + 1; // value of gray
    int BLACK = dCounter + 2; // value of black
    current.visitedValue = GRAY;
    
    // Consider cross-thread successors of current
    Address listHead = ObjectReference.fromObject(current).toAddress().plus(Entrypoints.velodromeOutgoingEdgesField.getOffset());
    do {
      listHead = ObjectReference.fromObject(current).toAddress().prepareAddress(Entrypoints.velodromeOutgoingEdgesField.getOffset());
    } while (listHead.EQ(LOCK_OUTGOINGEDGE));
    if (VM.VerifyAssertions) { VM._assert(listHead != null); }
    if (VM.VerifyAssertions) { VM._assert(MemoryManager.validRef(listHead.toObjectReference())); }
    TransactionsList start = (TransactionsList) listHead.toObjectReference().toObject();
    if (VM.VerifyAssertions) { VM._assert(start != null); }
    // Iterate over outgoing edges
    while (start.transaction != null) {
      Transaction temp = start.transaction;
      if (VM.VerifyAssertions) { VM._assert(temp.visitedValue <= BLACK); }
      if (VM.VerifyAssertions) { VM._assert(start.edgeNumber != eCounter); }
      if (firstCall) {
        if (start.edgeNumber < eCounter) { // Traverse along an older edge
          if (temp.visitedValue == GRAY) { // Cycle detected
            return true;
          } else if (temp.visitedValue <= WHITE) {
            return dfs(source, dest, temp, dCounter, start.edgeNumber, false);
          }
        } else { // No need to traverse along a more recent edge
        }
      } else {
        if (start.edgeNumber > eCounter) { // Traverse along an increasing cycle
          if (temp.visitedValue == GRAY) { // Cycle detected
            return true;
          } else if (temp.visitedValue <= WHITE) {
            return dfs(source, dest, temp, dCounter, start.edgeNumber, false);
          }
        } else { // No need to traverse along older edges
        }
      }
      start = start.next;
    }
    
    // Traverse along the sequential edge
    if (current.next != null) { // If current == dest, then dest.next should be null 
      Transaction temp = current.next;
      if (VM.VerifyAssertions) { VM._assert(temp.visitedValue <= BLACK); }
      if (temp.incomingEdgeCount > eCounter) { // Traverse along an increasing cycle
        if (temp.visitedValue == GRAY) { // Cycle detected
          return true;
        } else if (temp.visitedValue <= WHITE) {
          return dfs(source, dest, temp, dCounter, temp.incomingEdgeCount, false);
        }
      } else { // No need to traverse along older edges
      }
    }
    
    // Make current black
    current.visitedValue = BLACK;
    return false;
  }
  
  /**
   * Here we decrease transaction depth during stack unwinding. The caller method takes
   * care of the fact to avoid processing exceptions ***on behalf of VM*** and for native methods
   */
  @Inline
  public static void handleExceptionDuringStackUnwinding(RVMMethod method) {
    // This is no longer required with encapsulating the method calls. This is required when we need to accurately maintain
    // the transaction depth without encapsulating the method calls.
    RVMThread currentThread = RVMThread.getCurrentThread();
    
    // Velodrome: LATER: This is the config we are bothered with for uncaught exceptions for now.
    // What do we do for exceptions from within sync blocks?
    if (!Velodrome.methodsAsTransactions() || !currentThread.isOctetThread()) {
      return;
    }
    
    // Sanity check
    if (VM.VerifyAssertions) { VM._assert(currentThread.inTransaction()); }
    // We are assuming exceptions are from regular transactions
    if (VM.VerifyAssertions) { VM._assert(!currentThread.currentTransaction.isUnary); }
    
    currentThread.resetInTransaction();

    // Add a unary node/transaction immediately after an actual method ends
    Transaction current = currentThread.currentTransaction;
    Transaction last = createTransaction(currentThread, current.siteID, current.methodID, /*isRegularTransaction =*/ false);
    current.next = last;
    currentThread.currentTransaction = last;
  }
  
  /*
   *  Debugging methods
   */
  
  @NoInline
  @UninterruptibleNoWarn
  public static final boolean checkStartTransactionInstrumentation() {
    Address fp = Magic.getFramePointer();
    fp = Magic.getCallerFramePointer(fp);
    int depth = 0;
    while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      int compiledMethodId = Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
        CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);
        RVMMethod method = compiledMethod.getMethod();
        if (!method.isNative() && Octet.shouldInstrumentMethod(method)) {
          if (Context.isTRANSContext(method.getStaticContext()) && Context.isNONTRANSContext(method.getResolvedContext())) {
            depth++;
          }
        }
      }
      fp = Magic.getCallerFramePointer(fp);
    }
    if (depth != 1) {
      RVMThread currentThread = RVMThread.getCurrentThread();
      VM.sysWriteln("Current thread:", currentThread.octetThreadID, "Trans:", currentThread.currentTransaction.transactionID);
      VM.sysWriteln("depth:", depth);
      RVMThread.dumpStack();
      VM.sysFail("Mismatch in transaction depth");
    }
    return true;
  }
  
  @Entrypoint
  public static final void checkMethodContextAtProlog() {
    if (VM.VerifyAssertions) { VM._assert(Velodrome.checkMethodContextAtProlog()); }
    RVMThread currentThread = RVMThread.getCurrentThread();
    if (VM.VerifyAssertions) { VM._assert(currentThread.isOctetThread()); }
    Address fp = Magic.getFramePointer();
    int compiledMethodId = Magic.getCompiledMethodID(Magic.getCallerFramePointer(fp));
    if (compiledMethodId != INVISIBLE_METHOD_ID) {
      CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);
      RVMMethod method = compiledMethod.getMethod();
      if (VM.VerifyAssertions) { VM._assert(Context.isApplicationPrefix(method.getDeclaringClass().getTypeRef())); }
      if (VM.VerifyAssertions) {
        if (currentThread.inTransaction()) {
          if (method.getStaticContext() != Context.TRANS_CONTEXT) {
            VM.sysWriteln("Current Octet thread id:", currentThread.octetThreadID);
            VM.sysWriteln("Method name:", method.getName());
            VM.sysWriteln("Class name:", method.getDeclaringClass().getDescriptor());
            VM.sysWriteln("Static context:", method.getStaticContext());
            VM.sysWriteln("Resolved context:", method.getResolvedContext());
            VM.sysFail("Static context of called method is not TRANS");
          }
        } else {
          if (method.getStaticContext() != Context.NONTRANS_CONTEXT) {
            VM.sysWriteln("Current Octet thread id:", currentThread.octetThreadID);
            VM.sysWriteln("Method name:", method.getName());
            VM.sysWriteln("Class name:", method.getDeclaringClass().getDescriptor());
            VM.sysWriteln("Static context:", method.getStaticContext());
            VM.sysWriteln("Resolved context:", method.getResolvedContext());
            VM.sysFail("Static context of called method is not NONTRANS");
          }
        }
      }
    }
  }

}