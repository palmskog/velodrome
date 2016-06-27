package org.jikesrvm.velodrome;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoCheckStore;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.unboxed.ObjectReference;

/** Map from ReadHashMapElement to transactions. Note that each bucket is a set, that is it will contain
 *  one and only one element with a given key. The key is the thread id. */
@Uninterruptible
public final class ReadHashMap {
  
  /** Array of buckets */
  ReadHashMapElement[] data; 

  /** Stores the length of the array, this helps avoid calling {@code length() }. */
  int length; 
  
  public static int INITIAL_NUMBER_THREADS = 4;
  
  private ReadHashMap(int length) {
    this.data = newArray(length);
    this.length = length;
  }

  @UninterruptibleNoWarn
  static ReadHashMap newReadHashMap() {
    ReadHashMap map;
    MemoryManager.startAllocatingInUninterruptibleCode();
    map = new ReadHashMap(INITIAL_NUMBER_THREADS);
    MemoryManager.stopAllocatingInUninterruptibleCode();
    return map;
  }
  
  @UninterruptibleNoWarn
  ReadHashMapElement[] newArray(int length) {
    ReadHashMapElement[] array;
    MemoryManager.startAllocatingInUninterruptibleCode();
    array = new ReadHashMapElement[length];
    MemoryManager.stopAllocatingInUninterruptibleCode();
    return array;
  }
  
  @UninterruptibleNoWarn
  static ReadHashMapElement newReadHashMapElement(Transaction tx, int siteID) {
    ReadHashMapElement elem;
    MemoryManager.startAllocatingInUninterruptibleCode();
    elem = new ReadHashMapElement(tx, siteID);
    MemoryManager.stopAllocatingInUninterruptibleCode();
    return elem;
  }
  
  public int getKey(ReadHashMapElement value) {
    if (VM.VerifyAssertions) { 
      if (value.transaction == null) {
        VM.sysWriteln("Transaction address:", ObjectReference.fromObject(value.transaction).toAddress());
        VM.sysWriteln("Read hash map address:", ObjectReference.fromObject(this).toAddress());
      }
      VM._assert(value.transaction != null); 
    }
    return value.transaction.octetThread.octetThreadID;
  }
  
  /** This method returns the bucket head. 
   *  @param key It is not the index
   *  @return the start pointer to the contents of the bucket indexed by {@code key}. Note that the returned value 
   *  could be {@code null} */
  @NoCheckStore // Velodrome: TODO: This is a load
  public ReadHashMapElement getBucketHead(int key) {
    if (VM.VerifyAssertions) { VM._assert(length == data.length); }
    int index = key % length;
    ReadHashMapElement tmp = data[index];
    return tmp;
  }
  
  @Inline
  ReadHashMapElement get(int key) {
    ReadHashMapElement tmp = getBucketHead(key);
    while (tmp != null && getKey(tmp) != key) {
      tmp = getNext(tmp);
    }
    return tmp;
  }
  
  /** Check if the read map contains an element with key {@code threadID} with a matching
   *  transaction id {@code txID}. We avoid constructing a hash map object. */
  @NoCheckStore
  // Velodrome: This method will contain a checkcast if the type of 'data' if different from a ReadHashMapElement,    
  // because of the array load. The checkcast allows a yieldpoint. This means that GC can happen while executing 
  // this method. That will cause problems since the write metadata at this point is a garbage reference.
  // To avoid the checkcast, we have now used an array of the same type as ReadHashMapElement.
  boolean contains(int threadID, int txID) {
    boolean present = false;
    if (VM.VerifyAssertions) { VM._assert(length == data.length); }
    int key = threadID;
    int index = key % length;
    ReadHashMapElement tmp = data[index];
    if (tmp == null) {
      return false;
    }
    if (getKey(tmp) == key) { // Same thread
      if (VM.VerifyAssertions) { VM._assert(tmp.transaction.transactionID <= txID); }
      if (tmp.transaction.transactionID == txID) { // Tx already present in map
        present = true;
      }
    } else {
      while (getKey(tmp) != key) {
        tmp = getNext(tmp);
        if (tmp == null) {
          return false;
        }
      }
      if (VM.VerifyAssertions) { VM._assert(tmp.transaction.transactionID <= txID); }
      if (tmp.transaction.transactionID == txID) {
        present = true;
      }
    }
    return present;
  }
  
  /** There should be only one value for each {@code RVMThread} in each bucket */
  @Inline
  public void put(ReadHashMapElement value) {
    putInternal(value);
    if (VM.VerifyAssertions) { VM._assert(checkSetPropertyOfBuckets(value)); }
  }
  
  @NoCheckStore
  @Inline
  private void putInternal(ReadHashMapElement value) {
    if (VM.VerifyAssertions) { VM._assert(length == data.length); }
    int key = getKey(value);
    int index = key % length;
    removeIfPresent(key);
    setNext(value, data[index]);
    data[index] = value;
  }
  
  @NoCheckStore
  @NoInline 
  boolean checkSetPropertyOfBuckets(ReadHashMapElement value) {
    int key = getKey(value);
    ReadHashMapElement tmp = getBucketHead(key);
    ReadHashMapElement tmp1 = getNext(tmp);
    while (tmp1 != null) {
      // We should not have another entry in the bucket with the same RVMThread 
      if (tmp1.transaction.octetThread == tmp.transaction.octetThread) {
        return false;
      }
      tmp1 = getNext(tmp1);
    }
    return true;
  }
  
  public ReadHashMapElement getNext(ReadHashMapElement value) {
    return value.next;
  }

  void setNext(ReadHashMapElement value, ReadHashMapElement next) {
    value.next = next;
  }
  
  @Inline
  public void removeIfPresent(int key) {
    removeInternal(key);
  }
  
  @Inline
  public void remove(int key, boolean mustExist) {
    boolean result = removeInternal(key);
    if (VM.VerifyAssertions) {
      if (!result) { VM._assert(!mustExist); }
    }
  }
  
  @NoCheckStore
  @Inline
  private boolean removeInternal(int key) {
    int index = key % length;
    ReadHashMapElement tmp = data[index];
    if (tmp == null) {
      return false;
    }
    if (getKey(tmp) == key) {
      data[index] = getNext(tmp);
    } else {
      ReadHashMapElement prev = tmp;
      while (getKey(tmp) != key) {
        prev = tmp;
        tmp = getNext(tmp);
        if (tmp == null) {
          return false;
        }
      }
      setNext(prev, getNext(tmp));
    }
    return true;
  }
  
}
