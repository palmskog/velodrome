package org.jikesrvm.velodrome;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

/** This class tracks the last read access information for a field/static per thread */
@Uninterruptible
public class ReadHashMapElement {

  Transaction transaction;
  int lastReadSiteID;
  ReadHashMapElement next;
  
  public ReadHashMapElement(Transaction n, int siteID) {
    transaction = n;
    lastReadSiteID = siteID;
    next = null;
  }
  
  public ReadHashMapElement(Transaction n, int siteID, ReadHashMapElement nx) {
    transaction = n;
    lastReadSiteID =  siteID;
    next = nx;
  }
  
  public void setNext(ReadHashMapElement next) {
    this.next = next;
  }
  
  @Inline
  public Transaction getTransaction() {
    return transaction;
  }

  public void setTransaction(Object object) {
    if (VM.VerifyAssertions) { VM._assert(object != null); }
    transaction = (Transaction) object;
  }
  
}
