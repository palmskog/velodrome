package org.jikesrvm.velodrome;

import org.jikesrvm.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.octet.OctetBarriers;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;

// Currently, Velodrome is not instrumenting libraries. The "has velodrome offset" check can be required for 
// inheritance cases where the base class is the library. The per-field metadata in such a scenario will not 
// be laid out for the base class, but we would want it for derived application classes.
// Another issue is that however, there could accesses to public fields of library classes in
// application code which will also not have Velodrome offsets w/o library instrumentation. Example, 
// Elevator accesses StreamTokenizer.nval and sval in Elevator::readNum()
// Another example: org.eclipse.osgi.framework.internal.core.BundleURLConnection::connect()

@Uninterruptible
public class VelodromeBarriers implements Constants {
  
  @Entrypoint
  public static final void fieldReadBarrierResolved(Object o, int fieldOffset, int siteID, int writeOffset, int readOffset) {
    if (writeOffset != Velodrome.UNITIALIZED_OFFSET) {
      if (VM.VerifyAssertions && Velodrome.addPerFieldVelodromeMetadata()) { VM._assert(writeOffset >= 0 && readOffset >= 0); }
      readObject(o, fieldOffset, siteID, Offset.fromIntSignExtend(writeOffset), Offset.fromIntSignExtend(readOffset));
    } else if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesAvoidedPre.inc(1L);
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTotal.inc(1L);
    }
  }
  
  @Entrypoint
  public static final void fieldWriteBarrierResolved(Object o, int fieldOffset, int siteID, int writeOffset, int readOffset) {
    if (writeOffset != Velodrome.UNITIALIZED_OFFSET) {
      if (VM.VerifyAssertions && Velodrome.addPerFieldVelodromeMetadata()) { VM._assert(writeOffset >= 0 && readOffset >= 0); }
      writeObject(o, fieldOffset, siteID, Offset.fromIntSignExtend(writeOffset), Offset.fromIntSignExtend(readOffset));
    } else if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesAvoidedPre.inc(1L);
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTotal.inc(1L);
    }
  }
  
  @Entrypoint
  public static final void fieldReadBarrierStaticResolved(int fieldOffset, int siteID, int writeOffset, int readOffset) {
    // Metadata offsets are stored in negative offsets so that the GC does not trace them as roots while scanning the JTOC
    if (VM.VerifyAssertions && Velodrome.addPerFieldVelodromeMetadata()) { VM._assert(writeOffset < 0 && readOffset < 0); }
    readStatic(fieldOffset, siteID, Offset.fromIntSignExtend(writeOffset), Offset.fromIntSignExtend(readOffset));
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTotal.inc(1L);
    }
  }
  
  @Entrypoint
  public static final void fieldWriteBarrierStaticResolved(int fieldOffset, int siteID, int writeOffset, int readOffset) {
    // Metadata offsets are stored in negative offsets so that the GC does not trace them as roots while scanning the JTOC
    if (VM.VerifyAssertions && Velodrome.addPerFieldVelodromeMetadata()) { VM._assert(writeOffset < 0 && readOffset < 0); }
    writeStatic(fieldOffset, siteID, Offset.fromIntSignExtend(writeOffset), Offset.fromIntSignExtend(readOffset));
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTotal.inc(1L);
    }
  }
  
  // Octet: TODO: field might not actually be resolved here if inserted in the opt compiler during early instrumentation!
  
  // Octet: TODO: can we resolve a field?  is that interruptible?  how does that work in Jikes?
  
  // Velodrome: A call to a unresolved field in the barriers will have their velodrome offset
  // initialized. However, it may not be initialized during compilation.
  
  @Entrypoint
  public static final void fieldReadBarrierUnresolved(Object o, int fieldID, int siteID, int writeOffset, int readOffset) {
    int fieldOffset = OctetBarriers.getFieldInfo(fieldID);
    RVMField field = OctetBarriers.handleUnresolvedField(fieldID);
    if (Velodrome.addPerFieldVelodromeMetadata() && field.hasVelodromeMetadataOffset()) {
      writeOffset = field.getWriteMetadataOffset().toInt();
      readOffset = field.getReadMetadataOffset().toInt();
      if (VM.VerifyAssertions && Velodrome.addPerFieldVelodromeMetadata()) { VM._assert(writeOffset >= 0 && readOffset >= 0); }
      readObject(o, fieldOffset, siteID, Offset.fromIntSignExtend(writeOffset), Offset.fromIntSignExtend(readOffset));
    } else if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesAvoidedPre.inc(1L);
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTotal.inc(1L);
    }
  }
  
  @Entrypoint
  public static final void fieldWriteBarrierUnresolved(Object o, int fieldID, int siteID, int writeOffset, int readOffset) {
    int fieldOffset = OctetBarriers.getFieldInfo(fieldID);
    RVMField field = OctetBarriers.handleUnresolvedField(fieldID);
    if (Velodrome.addPerFieldVelodromeMetadata() && field.hasVelodromeMetadataOffset()) {
      writeOffset = field.getWriteMetadataOffset().toInt();
      readOffset = field.getReadMetadataOffset().toInt();
      if (VM.VerifyAssertions && Velodrome.addPerFieldVelodromeMetadata()) { VM._assert(writeOffset >= 0 && readOffset >= 0); }
      writeObject(o, fieldOffset, siteID, Offset.fromIntSignExtend(writeOffset), Offset.fromIntSignExtend(readOffset));
    } else if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesAvoidedPre.inc(1L);
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTotal.inc(1L);
    }
  }
  
  // Octet: LATER: the hasMetadataOffset checks are necessary because some fields --
  // in particular, *final* fields -- might get instrumentation added for them at
  // "unresolved static" accesses, but their resolved fields won't have a metadata offset.
  
  @Entrypoint
  public static final void fieldReadBarrierStaticUnresolved(int fieldID, int siteID, int writeOffset, int readOffset) {
    RVMField field = OctetBarriers.handleUnresolvedField(fieldID);
    // Velodrome: I guess the above Octet comment explains the need for the hasVelodromeMetadataOffset() check.
    if (Velodrome.addPerFieldVelodromeMetadata() && field.hasVelodromeMetadataOffset()) {
      int fieldOffset = OctetBarriers.getFieldInfo(field, fieldID);
      writeOffset = field.getWriteMetadataOffset().toInt();
      readOffset = field.getReadMetadataOffset().toInt();
      if (VM.VerifyAssertions && Velodrome.addPerFieldVelodromeMetadata()) { VM._assert(writeOffset < 0 && readOffset < 0); }
      readStatic(fieldOffset, siteID, Offset.fromIntSignExtend(writeOffset), Offset.fromIntSignExtend(readOffset));
    } else if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesAvoidedPre.inc(1L);
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTotal.inc(1L);
    }
  }
  
  @Entrypoint
  public static final void fieldWriteBarrierStaticUnresolved(int fieldID, int siteID, int writeOffset, int readOffset) {
    RVMField field = OctetBarriers.handleUnresolvedField(fieldID);
    if (Velodrome.addPerFieldVelodromeMetadata() && field.hasVelodromeMetadataOffset()) {
      int fieldOffset = OctetBarriers.getFieldInfo(field, fieldID);
      writeOffset = field.getWriteMetadataOffset().toInt();
      readOffset = field.getReadMetadataOffset().toInt();
      if (VM.VerifyAssertions && Velodrome.addPerFieldVelodromeMetadata()) { VM._assert(writeOffset < 0 && readOffset < 0); }
      writeStatic(fieldOffset, siteID, Offset.fromIntSignExtend(writeOffset), Offset.fromIntSignExtend(readOffset));
    } else if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesAvoidedPre.inc(1L);
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTotal.inc(1L);
    }
  }
  
  @Entrypoint
  public static final void arrayReadBarrier(Object o, int arrayIndex, int siteID, int writeOffset, int readOffset, int arrayElementSize) {
    int arraySlotOffset = Offset.fromIntSignExtend(arrayIndex * arrayElementSize).toInt();
    if (VM.VerifyAssertions) { VM._assert(writeOffset == Integer.MIN_VALUE + 1); }
    Offset wOffset = MiscHeader.VELODROME_WRITE_OFFSET;
    if (VM.VerifyAssertions) { VM._assert(readOffset == Integer.MIN_VALUE + 1); }
    Offset rOffset = MiscHeader.VELODROME_READ_OFFSET;
    readObject(o, arraySlotOffset, siteID, wOffset, rOffset);
  }
  
  @Entrypoint
  public static final void arrayWriteBarrier(Object o, int arrayIndex, int siteID, int writeOffset, int readOffset, int arrayElementSize) {
    int arraySlotOffset = Offset.fromIntSignExtend(arrayIndex * arrayElementSize).toInt();
    if (VM.VerifyAssertions) { VM._assert(writeOffset == Integer.MIN_VALUE + 1); }
    Offset wOffset = MiscHeader.VELODROME_WRITE_OFFSET;
    if (VM.VerifyAssertions) { VM._assert(readOffset == Integer.MIN_VALUE + 1); }
    Offset rOffset = MiscHeader.VELODROME_READ_OFFSET;    
    writeObject(o, arraySlotOffset, siteID, wOffset, rOffset);
  }
  
  // Velodrome: TODO: Decide whether to inline readObject()/readStatic() methods

  static final boolean readObject(Object o, int fieldOffset, int siteID, Offset writeOffset, Offset readOffset) {
    if (Velodrome.insertPostBarriers()) { // Lock the metadata iff postbarriers are enabled
      Address objAddr = ObjectReference.fromObject(o).toAddress();
      Transaction lastWrite = (Transaction) VelodromeMetadataHelper.lockFieldMetadata(objAddr, writeOffset, /* isStatic*/ false);
      if (Velodrome.trackLastAccess()) {
        ObjectReference readMetadataRef = objAddr.loadObjectReference(readOffset);
        RVMThread currentThread = RVMThread.getCurrentThread();
        if (!readMetadataRef.isNull()) {
          Address tib = readMetadataRef.toAddress().loadAddress(JavaHeader.getTibOffset());
          if (VM.VerifyAssertions) { VM._assert(!tib.isZero()); }
          Transaction currentTx = currentThread.currentTransaction;
          if (tib.EQ(Velodrome.tibForTransaction)) { // This is the second read, need to upgrade to a read map
            Transaction lastRead = (Transaction) readMetadataRef.toObject();
            if (lastRead != currentTx) {
              VelodromeMetadataHelper.upgradeToReadHashMap(objAddr, writeOffset, readOffset, lastRead, lastWrite, siteID);
            }
          } else { // Should be a read map
            if (VM.VerifyAssertions) { VM._assert(tib.EQ(Velodrome.tibForReadHashMap)); }
            ReadHashMap map = (ReadHashMap) objAddr.loadObjectReference(readOffset).toObject();
            if (!map.contains(currentThread.octetThreadID, currentTx.transactionID)) {
              VelodromeMetadataHelper.appendCurrentRead(objAddr, writeOffset, readOffset, lastWrite, siteID);
            }
          }
        } else { // First read
          VelodromeMetadataHelper.setOnlyRead(objAddr, writeOffset, readOffset, lastWrite, siteID);
        }
      }
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTracked.inc(1L);
    }
    return true;
  }
  
  static final boolean writeObject(Object o, int fieldOrIndexInfo, int siteID, Offset writeOffset, Offset readOffset) {
    if (Velodrome.insertPostBarriers()) { // Lock the metadata iff postbarriers are enabled
      Address objAddr = ObjectReference.fromObject(o).toAddress();
      Transaction lastWrite = (Transaction) VelodromeMetadataHelper.lockFieldMetadata(objAddr, writeOffset, false);
      if (Velodrome.trackLastAccess()) {
        if (lastWrite != RVMThread.getCurrentThread().currentTransaction) {
          VelodromeMetadataHelper.updateLastWrite(objAddr, writeOffset, readOffset, lastWrite, siteID);
        }
      }
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTracked.inc(1L);
    }
    return true;
  }
  
  static final boolean readStatic(int fieldOffset, int siteID, Offset writeOffset, Offset readOffset) {
    if (Velodrome.insertPostBarriers()) { // Lock the metadata iff postbarriers are enabled
      Transaction lastWrite = (Transaction) VelodromeMetadataHelper.lockFieldMetadata(Magic.getTocPointer(), writeOffset, true);
      if (Velodrome.trackLastAccess()) {
        Address readMetadata = Magic.getTocPointer().loadAddress(readOffset);
        RVMThread currentThread = RVMThread.getCurrentThread();
        if (readMetadata.NE(Address.zero())) {
          Address tib = ObjectReference.fromObject(readMetadata).toAddress().loadAddress(JavaHeader.getTibOffset());
          Transaction currentTx = currentThread.currentTransaction;
          if (tib.EQ(Velodrome.tibForTransaction)) { // This is the second read, need to upgrade to a read map
            Transaction lastRead = (Transaction) readMetadata.toObjectReference().toObject();
            if (lastRead != currentTx) {
              VelodromeMetadataHelper.upgradeToReadHashMap(Magic.getTocPointer(), writeOffset, readOffset, lastRead, lastWrite, siteID);
            }
          } else { // Should be a read map
            ReadHashMap map = (ReadHashMap) Magic.getTocPointer().loadObjectReference(readOffset).toObject();
            if (!map.contains(currentThread.octetThreadID, currentTx.transactionID)) {
              VelodromeMetadataHelper.appendCurrentRead(Magic.getTocPointer(), writeOffset, readOffset, lastWrite, siteID);
            }
          }
        } else { // First read
          VelodromeMetadataHelper.setOnlyRead(Magic.getTocPointer(), writeOffset, readOffset, lastWrite, siteID);
        }
      }
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTracked.inc(1L);
    }
    return true;
  }

  static final boolean writeStatic(int fieldOffset, int siteID, Offset writeOffset, Offset readOffset) {
    if (Velodrome.insertPostBarriers()) { // Lock the metadata iff postbarriers are enabled
      Transaction lastWrite = (Transaction) VelodromeMetadataHelper.lockFieldMetadata(Magic.getTocPointer(), writeOffset, true);
      if (Velodrome.trackLastAccess()) {
        if (lastWrite != RVMThread.getCurrentThread().currentTransaction) {
          VelodromeMetadataHelper.updateLastWrite(Magic.getTocPointer(), writeOffset, readOffset, lastWrite, siteID);
        }
      }
    }
    if (Velodrome.recordVelodromeStats()) {
      VelodromeStats.numAccessesTracked.inc(1L);
    }
    return true;
  }

}
