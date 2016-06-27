package org.jikesrvm.velodrome;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

@Uninterruptible
public class TransactionsList {

  public Transaction transaction;
  public TransactionsList next;
  public int edgeNumber;
  
  public static final TransactionsList dummyTransaction = new TransactionsList();
  
  /** Represents a dummy (sentinel) node. */
  public TransactionsList() {
    transaction = null;
    next = null;
    edgeNumber = -1;
  }
  
  public TransactionsList(Transaction tx, int desc) {
    transaction = tx;
    next = null;
    edgeNumber = desc;
  }
  
  @UninterruptibleNoWarn
  public static TransactionsList createTransactionsListNode(Transaction tx, int desc) {
    if (VM.VerifyAssertions) { VM._assert(tx != null); }
    MemoryManager.startAllocatingInUninterruptibleCode();
    TransactionsList temp = new TransactionsList(tx, desc);
    MemoryManager.stopAllocatingInUninterruptibleCode();
    return temp;
  }
  
}