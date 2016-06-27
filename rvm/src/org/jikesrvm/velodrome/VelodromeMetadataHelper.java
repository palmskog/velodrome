package org.jikesrvm.velodrome;

import org.jikesrvm.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.mm.mminterface.Barriers;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.octet.OctetBarriers;
import org.jikesrvm.octet.Site;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

@Uninterruptible
public class VelodromeMetadataHelper implements Constants {

  /** Lock bit (LSB) to protect access to field metadata */
  private static final Word LOCK_BIT = Word.one(); // 000...001
  private static final Word LOCK_MASK = Word.max().minus(Word.one()); // 111...110
  
  public static Object lockFieldMetadata(Address objAddr, Offset writeOffset, boolean isStatic) {
    Word oldValue = objAddr.loadWord(writeOffset);
    do {
      do {
        oldValue = objAddr.prepareWord(writeOffset);
      } while (oldValue.and(LOCK_BIT).EQ(LOCK_BIT));    
    } while (!objAddr.attempt(oldValue, oldValue.or(LOCK_BIT), writeOffset));
    if (VM.VerifyAssertions) { 
      VM._assert(!RVMThread.getCurrentThread().betweenPreAndPost);
      RVMThread.getCurrentThread().betweenPreAndPost = true;
      RVMThread.getCurrentThread().lockedMetadata = objAddr.loadWord(writeOffset);
    }
    
    return oldValue.toAddress().toObjectReference().toObject();
  }
  
  @Entrypoint
  public static void unlockResolvedFieldMetadata(Object o, int velodromeOffset, int siteID, int read) {
    if (VM.VerifyAssertions) { VM._assert(Velodrome.insertPostBarriers()); }
    if (VM.VerifyAssertions) { VM._assert(MemoryManager.validRef(ObjectReference.fromObject(o))); }
    boolean isRead = (read == 1) ? true : false;
    Address objAddr = ObjectReference.fromObject(o).toAddress();
    if (velodromeOffset != Velodrome.UNITIALIZED_OFFSET) {
      if (VM.VerifyAssertions) { VM._assert(velodromeOffset >= 0); }
      Offset vOffset = Offset.fromIntSignExtend(velodromeOffset);
      // An MFENCE is required over here
      Magic.fence();
      Address oldValue = objAddr.prepareAddress(vOffset);
      // Assert that the metadata is indeed locked
      // Velodrome: LATER: eclipse6 fails here
      if (VM.VerifyAssertions) { VM._assert(oldValue.toWord().and(LOCK_BIT).EQ(Word.one())); }
      Address newValue = oldValue.toWord().and(LOCK_MASK).toAddress();
      Transaction currTx = RVMThread.getCurrentThread().currentTransaction;
      if (Velodrome.useGenerationalBarriers()) {
        if (isRead) {
          Barriers.objectFieldWrite(o, newValue.toObjectReference().toObject(), vOffset, 0);
        } else { // This is the new write
          Barriers.objectFieldWrite(o, currTx, vOffset, 0);
        }
      } else {
        if (isRead) {
          objAddr.store(newValue, vOffset);
        } else {
          objAddr.store(ObjectReference.fromObject(currTx), vOffset);
        }
      }
      if (VM.VerifyAssertions) {
        VM._assert(RVMThread.getCurrentThread().betweenPreAndPost);
        RVMThread.getCurrentThread().betweenPreAndPost = false;
      }
    } else if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesAvoidedPost.inc(1L);
    }
  }
  
  @Entrypoint
  public static void unlockUnresolvedFieldMetadata(Object o, int fieldID, int siteID, int read) {
    if (VM.VerifyAssertions) { VM._assert(Velodrome.insertPostBarriers()); }
    if (VM.VerifyAssertions) { VM._assert(MemoryManager.validRef(ObjectReference.fromObject(o))); }
    boolean isRead = (read == 1) ? true : false;
    Address objAddr = ObjectReference.fromObject(o).toAddress();
    RVMField field = OctetBarriers.handleUnresolvedField(fieldID);
    if (field.hasVelodromeMetadataOffset()) {
      Offset writeOffset = field.getWriteMetadataOffset();
      if (VM.VerifyAssertions) { VM._assert(writeOffset.toInt() >= 0); }
      Word oldValue = objAddr.prepareWord(writeOffset);
      // An MFENCE is required over here
      Magic.fence();
      // Assert that the metadata is indeed locked
      if (VM.VerifyAssertions) { VM._assert(oldValue.and(LOCK_BIT).EQ(Word.one())); }
      Address newValue = oldValue.and(LOCK_MASK).toAddress();
      if (Velodrome.useGenerationalBarriers()) {
        if (isRead) {
          Barriers.objectFieldWrite(objAddr.toObjectReference().toObject(), newValue.toObjectReference().toObject(), writeOffset, 0);
        } else { // This is the new write
          Transaction currTx = RVMThread.getCurrentThread().currentTransaction;
          Barriers.objectFieldWrite(objAddr.toObjectReference().toObject(), currTx, writeOffset, 0);
        }
      } else {
        if (isRead) {
          objAddr.store(newValue, writeOffset);
        } else { // This is the new write
          Transaction currTx = RVMThread.getCurrentThread().currentTransaction;
          objAddr.store(ObjectReference.fromObject(currTx), writeOffset);
        }
      }
      if (VM.VerifyAssertions) {
        VM._assert(RVMThread.getCurrentThread().betweenPreAndPost);
        RVMThread.getCurrentThread().betweenPreAndPost = false;
      }
    } else if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesAvoidedPost.inc(1L);
    }
  }
  
  @Entrypoint
  public static void unlockMetadataForStaticResolved(Offset velodromeOffset, int fieldID, int siteID, int read) {
    if (VM.VerifyAssertions) { VM._assert(velodromeOffset.toInt() < 0); }
    boolean isRead = (read == 1) ? true : false;
    // Using Magic.getJTOC() seems to be causing problems during opt compilation
    Address objAddr = Magic.getTocPointer();
    Word oldValue = objAddr.loadWord(velodromeOffset);
    // An MFENCE is required over here
    Magic.fence();
    // Assert that the metadata is indeed locked
    if (VM.VerifyAssertions) { VM._assert(oldValue.and(LOCK_BIT).EQ(LOCK_BIT)); }
    if (Velodrome.useGenerationalBarriers()) {
      if (isRead) {
        Address newValue = oldValue.and(LOCK_MASK).toAddress();
        Barriers.objectFieldWrite(objAddr.toObjectReference().toObject(), newValue.toObjectReference().toObject(), velodromeOffset, 0);
      } else { // This is the new write
        Transaction currTx = RVMThread.getCurrentThread().currentTransaction;
        Barriers.objectFieldWrite(objAddr.toObjectReference().toObject(), currTx, velodromeOffset, 0);
      }
    } else {
      if (isRead) {
        Address newValue = oldValue.and(LOCK_MASK).toAddress();
        objAddr.store(newValue, velodromeOffset);
      } else { // This is the new write
        Transaction currTx = RVMThread.getCurrentThread().currentTransaction;
        objAddr.store(ObjectReference.fromObject(currTx), velodromeOffset);
      }
    }
    if (VM.VerifyAssertions) {
      VM._assert(RVMThread.getCurrentThread().betweenPreAndPost);
      RVMThread.getCurrentThread().betweenPreAndPost = false;
    }
  }
  
  @Entrypoint
  public static void unlockMetadataForStaticUnresolved(int fieldID, int siteID, int read) {
    boolean isRead = (read == 1) ? true : false;
    // Using Magic.getJTOC() seems to be causing problems during opt compilation
    Address objAddr = Magic.getTocPointer();
    RVMField field = OctetBarriers.handleUnresolvedField(fieldID);
    if (field.hasVelodromeMetadataOffset()) { 
      Offset writeOffset = field.getWriteMetadataOffset();
      if (VM.VerifyAssertions) { VM._assert(writeOffset.toInt() < 0); }
      // An MFENCE is required over here
      Magic.fence();
      Word oldValue = objAddr.prepareWord(writeOffset);
      // Assert that the metadata is indeed locked
      if (VM.VerifyAssertions) { VM._assert(oldValue.and(LOCK_BIT).EQ(Word.one())); }
      if (Velodrome.useGenerationalBarriers()) {
        if (isRead) {
          Address newValue = oldValue.and(LOCK_MASK).toAddress();
          Barriers.objectFieldWrite(objAddr.toObjectReference().toObject(), newValue.toObjectReference().toObject(), writeOffset, 0);
        } else { // This is the new write
          Transaction currTx = RVMThread.getCurrentThread().currentTransaction;
          Barriers.objectFieldWrite(objAddr.toObjectReference().toObject(), currTx, writeOffset, 0);
        }
      } else {
        if (isRead) {
          Address newValue = oldValue.and(LOCK_MASK).toAddress();
          objAddr.store(newValue, writeOffset);
        } else { // This is the new write
          Transaction currTx = RVMThread.getCurrentThread().currentTransaction;
          objAddr.store(ObjectReference.fromObject(currTx), writeOffset);
        }
      }
      if (VM.VerifyAssertions) {
        VM._assert(RVMThread.getCurrentThread().betweenPreAndPost);
        RVMThread.getCurrentThread().betweenPreAndPost = false;
      }
    } else if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesAvoidedPost.inc(1L);
    }
  }
   
  public static void updateLastWrite(Address objAddr, Offset writeOffset, Offset readOffset, Transaction lastWrite, int siteID) {
    RVMThread currentThread = RVMThread.getCurrentThread();
    Transaction currTx = currentThread.currentTransaction;
    if (Velodrome.createCrossThreadEdges()) {
      // Always attempt to create WAR anti dependence edges
      ObjectReference readMetadata = objAddr.loadObjectReference(readOffset);
      if (!readMetadata.isNull()) {
        Address tib = ObjectReference.fromObject(readMetadata).toAddress().loadAddress(JavaHeader.getTibOffset());
        if (tib.EQ(Velodrome.tibForTransaction)) { // Single read information
          Transaction lastRead = (Transaction) readMetadata.toObject();
          TransactionalHBGraph.createRdWrEdge(lastRead, lastRead.siteID, currTx, siteID);
        } else { // Read map
          ReadHashMap map = (ReadHashMap) readMetadata.toObject();
          TransactionalHBGraph.createRdWrEdges(map, currTx, siteID);
        }
      } else { // No read information, nothing to do
      }
      // Create WAW output dependence edge
      if (lastWrite != null && currTx != lastWrite) {
        if (VM.VerifyAssertions && currentThread == lastWrite.octetThread) {
          VM._assert(currTx.transactionID > lastWrite.transactionID); 
        } 
        TransactionalHBGraph.createWrWrEdge(lastWrite, lastWrite.siteID, currTx, siteID);
      }
    }
    // Velodrome: LATER: Do we require a generational store here? Probably not.
    if (Velodrome.useGenerationalBarriers()) {
      Barriers.objectFieldWrite(objAddr.toObjectReference().toObject(), ObjectReference.nullReference().toObject(), readOffset, 0);
    } else {
      objAddr.store(ObjectReference.nullReference(), readOffset); // Clear all reads
    }
    // We update the current write in the post barrier
    // Test that the write metadata lock bit is still set
    if (VM.VerifyAssertions) { VM._assert(objAddr.loadAddress(writeOffset).toWord().and(LOCK_BIT).EQ(LOCK_BIT)); }
  }
  
  /** This is the first read to an object (maybe after a write). This is indicated by the readMetadata offset being 
   *  equal to zero. */
  public static void setOnlyRead(Address objAddr, Offset writeOffset, Offset readOffset, Transaction lastWrite, int siteID) {
    RVMThread currentThread = RVMThread.getCurrentThread();
    Transaction currTx = currentThread.currentTransaction;
    if (Velodrome.createCrossThreadEdges() && lastWrite != null) {
      // Always attempt to create RAW true dependence edge
      TransactionalHBGraph.createWrRdEdge(lastWrite, lastWrite.siteID, currTx, siteID);
    }
    if (Velodrome.useGenerationalBarriers()) {
      Barriers.objectFieldWrite(objAddr.toObjectReference().toObject(), currTx, readOffset, 0);
    } else {
      objAddr.store(ObjectReference.fromObject(currTx), readOffset);
    }
    // Test that the write metadata lock bit is still set
    if (VM.VerifyAssertions) { VM._assert(objAddr.loadAddress(writeOffset).toWord().and(LOCK_BIT).EQ(LOCK_BIT)); }
  }
  
  /** If the read metadata offset already contains a data, then it is expected to be a reference to a Transaction. The
   *  current read is then the second read. In such cases, the single reference is updated to a Read map. */
  public static void upgradeToReadHashMap(Address objAddr, Offset writeOffset, Offset readOffset, Transaction lastRead, 
      Transaction lastWrite, int siteID) {
    if (VM.VerifyAssertions) { VM._assert(lastRead != null); }
    RVMThread currentThread = RVMThread.getCurrentThread();
    Transaction currentTx = currentThread.currentTransaction;
    if (lastRead.octetThread != currentThread) {
      if (Velodrome.createCrossThreadEdges() && lastWrite != null && lastWrite.octetThread != lastRead.octetThread) {
        if (VM.VerifyAssertions) {
          // No cross-thread edges are created for driver threads in DaCapo and Tsp
          if (Velodrome.bench.getId() == BenchmarkInfo.TSP &&
              (lastWrite.octetThread.octetThreadID != Velodrome.TSP_DRIVER_THREAD_OCTET_ID &&
              lastRead.octetThread.octetThreadID != Velodrome.TSP_DRIVER_THREAD_OCTET_ID)) {
            VM._assert(TransactionalHBGraph.isCrossThreadEdgeAlreadyPresent(lastWrite, lastRead));
          }
          if (Velodrome.bench.isDaCapoBenchmark() && 
              (lastWrite.octetThread.octetThreadID != Velodrome.DACAPO_DRIVER_THREAD_OCTET_ID 
              && lastRead.octetThread.octetThreadID != Velodrome.DACAPO_DRIVER_THREAD_OCTET_ID)) {
            VM._assert(TransactionalHBGraph.isCrossThreadEdgeAlreadyPresent(lastWrite, lastRead)); 
          }
          if (Velodrome.bench.getId() != BenchmarkInfo.TSP && !Velodrome.bench.isDaCapoBenchmark()) {
            VM._assert(TransactionalHBGraph.isCrossThreadEdgeAlreadyPresent(lastWrite, lastRead)); 
          }
        }
        // Always attempt to create RAW true dependence edge
        TransactionalHBGraph.createWrRdEdge(lastWrite, lastWrite.siteID, currentTx, siteID);
      }
      ReadHashMap map = ReadHashMap.newReadHashMap();
      ReadHashMapElement elem1 = ReadHashMap.newReadHashMapElement(lastRead, lastRead.siteID);
      map.put(elem1);
      Transaction currTx = RVMThread.getCurrentThread().currentTransaction;
      ReadHashMapElement elem2 = ReadHashMap.newReadHashMapElement(currTx, siteID);
      map.put(elem2);
      if (Velodrome.useGenerationalBarriers()) {
        Barriers.objectFieldWrite(objAddr.toObjectReference().toObject(), map, readOffset, 0);
      } else {
        objAddr.store(ObjectReference.fromObject(map), readOffset);
      }
    } else { // Same thread performing the second read, so no need to upgrade to a map immediately
      if (VM.VerifyAssertions) { VM._assert(currentTx.transactionID >= lastRead.transactionID); }
      if (currentTx.transactionID > lastRead.transactionID) {
        setOnlyRead(objAddr, writeOffset, readOffset, lastWrite, siteID);
      } else { // Same transaction        
        if (Velodrome.createCrossThreadEdges() && VM.VerifyAssertions && lastWrite != null && lastWrite.octetThread != currentThread) {
          VM._assert(currentTx == lastRead);
          // No cross-thread edges are created for driver threads in DaCapo and Tsp
          if (Velodrome.bench.getId() == BenchmarkInfo.TSP && 
              (lastWrite.octetThread.octetThreadID != Velodrome.TSP_DRIVER_THREAD_OCTET_ID &&
              lastRead.octetThread.octetThreadID != Velodrome.TSP_DRIVER_THREAD_OCTET_ID)) {
            VM._assert(TransactionalHBGraph.isCrossThreadEdgeAlreadyPresent(lastWrite, lastRead));
          }
          if (Velodrome.bench.isDaCapoBenchmark() && 
              (lastWrite.octetThread.octetThreadID != Velodrome.DACAPO_DRIVER_THREAD_OCTET_ID 
              && lastRead.octetThread.octetThreadID != Velodrome.DACAPO_DRIVER_THREAD_OCTET_ID)) {
            VM._assert(TransactionalHBGraph.isCrossThreadEdgeAlreadyPresent(lastWrite, lastRead)); 
          }
          if (Velodrome.bench.getId() != BenchmarkInfo.TSP && !Velodrome.bench.isDaCapoBenchmark()) {
            VM._assert(TransactionalHBGraph.isCrossThreadEdgeAlreadyPresent(lastWrite, lastRead)); 
          }
        }
      }
    }
    // Test that the write metadata lock bit is still set
    if (VM.VerifyAssertions) { VM._assert(objAddr.loadAddress(writeOffset).toWord().and(LOCK_BIT).EQ(LOCK_BIT)); }
  }
  
  public static void appendCurrentRead(Address objAddr, Offset writeOffset, Offset readOffset, Transaction lastWrite, int siteID) {
    RVMThread currentThread = RVMThread.getCurrentThread();
    Transaction currTx = currentThread.currentTransaction;
    if (Velodrome.createCrossThreadEdges() && lastWrite != null) {
      // Always attempt to create RAW true dependence edge
      TransactionalHBGraph.createWrRdEdge(lastWrite, lastWrite.siteID, currTx, siteID);
    }
    if (VM.VerifyAssertions) { 
      Address tib = objAddr.loadAddress(readOffset).loadAddress(JavaHeader.getTibOffset()); 
      VM._assert(!tib.EQ(Velodrome.tibForTransaction));
    } 
    ReadHashMap map = (ReadHashMap) objAddr.loadObjectReference(readOffset).toObject();
    ReadHashMapElement elem1 = ReadHashMap.newReadHashMapElement(currTx, siteID);
    map.put(elem1);
    // Test that the write metadata lock bit is still set
    if (VM.VerifyAssertions) { VM._assert(objAddr.loadAddress(writeOffset).toWord().and(LOCK_BIT).EQ(LOCK_BIT)); }
  }
  
  /** Track lock acquire */
  // Since the lock is already acquired, therefore we can safely perform the read of the lock metadata
  public static void trackLockAcquire(Object o) {
    Transaction current = RVMThread.getCurrentThread().currentTransaction;
    // Create edge from last release 
    Address addr = ObjectReference.fromObject(o).toAddress().loadAddress(MiscHeader.VELODROME_OFFSET);
    if (addr.NE(Address.zero())) {
      Transaction lastTransaction = (Transaction) addr.toObjectReference().toObject();
      TransactionalHBGraph.createLockReleaseAcquireEdge(lastTransaction, current);
    } else { // This is the first acquire 
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTotal.inc(1L);
      VelodromeStats.numAccessesTracked.inc(1L);
    }
  }

  /** Track lock release */
  // Since the lock is still acquired, therefore we can safely write to the lock metadata
  public static void trackLockRelease(Object o) {
    Transaction current = RVMThread.getCurrentThread().currentTransaction;
    if (Velodrome.useGenerationalBarriers()) {
      Barriers.objectFieldWrite(o, current, MiscHeader.VELODROME_OFFSET, 0);
    } else {
      ObjectReference.fromObject(o).toAddress().store(ObjectReference.fromObject(current), MiscHeader.VELODROME_OFFSET);
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTotal.inc(1L);
      VelodromeStats.numAccessesTracked.inc(1L);
    }
  }
  
  @Entrypoint
  public static void arrayPostBarrier(Object o, int index, int siteID, int read) {
    if (VM.VerifyAssertions) { VM._assert(Velodrome.insertPostBarriers() && Velodrome.instrumentArrays()); }
    if (VM.VerifyAssertions) { VM._assert(MemoryManager.validRef(ObjectReference.fromObject(o))); }
    boolean isRead = (read == 1) ? true : false;
    Address objAddr = ObjectReference.fromObject(o).toAddress();
    Offset writeOffset = MiscHeader.VELODROME_WRITE_OFFSET;
    // An MFENCE is required over here
    Magic.fence();
    Address oldValue = objAddr.prepareAddress(writeOffset);
    // Assert that the metadata is indeed locked
    if (VM.VerifyAssertions) { VM._assert(oldValue.toWord().and(LOCK_BIT).EQ(Word.one())); }
    Address newValue = oldValue.toWord().and(LOCK_MASK).toAddress();
    Transaction currTx = RVMThread.getCurrentThread().currentTransaction;
    if (Velodrome.useGenerationalBarriers()) {
      if (isRead) {
        Barriers.objectFieldWrite(o, newValue.toObjectReference().toObject(), writeOffset, 0);
      } else { // This is the new write
        Barriers.objectFieldWrite(o, currTx, writeOffset, 0);
      }
    } else {
      if (isRead) {
        objAddr.store(newValue, writeOffset);
      } else {
        objAddr.store(ObjectReference.fromObject(currTx), writeOffset);
      }
    }
    if (VM.VerifyAssertions) {
      VM._assert(RVMThread.getCurrentThread().betweenPreAndPost);
      RVMThread.getCurrentThread().betweenPreAndPost = false;
    }
  }
  
}
