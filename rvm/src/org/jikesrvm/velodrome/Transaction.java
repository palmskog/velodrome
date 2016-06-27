package org.jikesrvm.velodrome;

import org.jikesrvm.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.octet.Site;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class Transaction implements Constants {

  /** A unique id for a transaction/node. Root node has id 0. */
  public int transactionID;
  /* Does this node correspond to an actual method? */
  public boolean isUnary;
  /** Stores the site id corresponding to the line in the source file. */
  public int siteID;
  
  /** Thread reference */
  public RVMThread octetThread;
  
  public int methodID; // Unary transactions and dummy start/end have method id = -1
  
  /** Pointer to the next node in the list. */
  public Transaction next;
  public int incomingEdgeCount;
  
  /** List of outgoing edges */
  @Entrypoint
  public TransactionsList outgoingEdges = TransactionsList.dummyTransaction;
  public int sizeOfOutgoingEdges;
  
  public int visitedValue;
  
  public Transaction() {
    this(null, false, /*incomingEdge = */ -1);
  }
  
  /** 
   * Called to create the root (dummy) node only. This has a trans id 1.
   * Note that it is possible for <code>t</code> to be different from RVMThread.getCurrentThread(), if this 
   * ctor is called from RVMThread ctor
   */
  public Transaction(RVMThread t, boolean isRegular, int incomingEdge) {
    this(t, 1,
        -1, isRegular, -1, incomingEdge);
  }
  
  /** This ctor creates a node that represents a transaction. 
   * Note that it is possible for t to be different from RVMThread.getCurrentThread(), if this
   * ctor is called from RVMThread ctor
   * */
  public Transaction(RVMThread thread, int transID, int site, boolean isRegular, int method, int incomingEdge) {
    octetThread = thread;
    if (VM.VerifyAssertions && thread != null) { VM._assert(thread.isOctetThread()); }
    siteID = site;
    methodID = method;
    transactionID = transID;   
    this.isUnary = !isRegular;
    next= null;
    incomingEdgeCount = incomingEdge;
    sizeOfOutgoingEdges = 0;
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numTransactions.inc(1L);
      if (isRegular) {
        VelodromeStats.numRegularTransactions.inc(1L);
      } else {
        VelodromeStats.numUnaryTransactions.inc(1L);
      }
    }
  }
  
  @NoInline
  public static void printTransaction(Transaction tx) {
    VM.sysWrite("Thread:", tx.octetThread.octetThreadID);
    VM.sysWrite(" Trans:", tx.transactionID);
    if (tx.isUnary) {
      VM.sysWrite(" Unary");
    } else {
      VM.sysWrite(" Regular");
    }
    VM.sysWrite(" Site id:", tx.siteID, " ");
    if (Velodrome.needsSites() && tx.siteID >= 0) { // Root/dummy transaction could log accesses and has site id -1
      Site site = Site.lookupSite(tx.siteID);
      if (VM.VerifyAssertions) { VM._assert(site != null); }
      site.sysWriteln();
    } else {
      if (VM.VerifyAssertions) { VM._assert(tx.transactionID == 1); }
      VM.sysWriteln(" Site id is null");
    }
  }
  
}