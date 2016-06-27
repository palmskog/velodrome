/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.mm.mmtk;

import static org.jikesrvm.classloader.RVMType.REFARRAY_OFFSET_ARRAY;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.mm.mminterface.DebugUtil;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.JavaHeaderConstants;
import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.velodrome.ReadHashMap;
import org.jikesrvm.velodrome.ReadHashMapElement;
import org.jikesrvm.velodrome.Velodrome;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.ParallelCollector;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.generational.Gen;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.OffsetArray;
import org.vmmagic.unboxed.Word;

@Uninterruptible public final class ObjectModel extends org.mmtk.vm.ObjectModel implements org.mmtk.utility.Constants,
                                                                                           org.jikesrvm.Constants {

  @Override
  protected Offset getArrayBaseOffset() { return JavaHeaderConstants.ARRAY_BASE_OFFSET; }

  @Override
  @Inline
  public ObjectReference copy(ObjectReference from, int allocator) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(from);
    // Velodrome: Included this assertion
    if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(tib != null); }
    RVMType type = Magic.objectAsType(tib.getType());

    if (type.isClassType())
      return copyScalar(from, tib, type.asClass(), allocator);
    else
      return copyArray(from, tib, type.asArray(), allocator);
  }

  @Inline
  private ObjectReference copyScalar(ObjectReference from, TIB tib, RVMClass type, int allocator) {
    int bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), type);
    int align = org.jikesrvm.objectmodel.ObjectModel.getAlignment(type, from.toObject());
    int offset = org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type, from);
    CollectorContext context = VM.activePlan.collector();
    allocator = context.copyCheckAllocator(from, bytes, align, allocator);
    Address region = MemoryManager.allocateSpace(context, bytes, align, offset,
                                                allocator, from);
    Object toObj = org.jikesrvm.objectmodel.ObjectModel.moveObject(region, from.toObject(), bytes, type);
    ObjectReference to = ObjectReference.fromObject(toObj);
    context.postCopy(to, ObjectReference.fromObject(tib), bytes, allocator);
    return to;
  }

  @Inline
  private ObjectReference copyArray(ObjectReference from, TIB tib, RVMArray type, int allocator) {
    int elements = Magic.getArrayLength(from.toObject());
    int bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), type, elements);
    int align = org.jikesrvm.objectmodel.ObjectModel.getAlignment(type, from.toObject());
    int offset = org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type, from);
    CollectorContext context = VM.activePlan.collector();
    allocator = context.copyCheckAllocator(from, bytes, align, allocator);
    Address region = MemoryManager.allocateSpace(context, bytes, align, offset,
                                                allocator, from);
    Object toObj = org.jikesrvm.objectmodel.ObjectModel.moveObject(region, from.toObject(), bytes, type);
    ObjectReference to = ObjectReference.fromObject(toObj);
    context.postCopy(to, ObjectReference.fromObject(tib), bytes, allocator);
    if (type == RVMType.CodeArrayType) {
      // sync all moved code arrays to get icache and dcache in sync
      // immediately.
      int dataSize = bytes - org.jikesrvm.objectmodel.ObjectModel.computeHeaderSize(Magic.getObjectType(toObj));
      org.jikesrvm.runtime.Memory.sync(to.toAddress(), dataSize);
    }
    return to;
  }

  /**
   * Return the size of a given object, in bytes
   *
   * @param object The object whose size is being queried
   * @return The size (in bytes) of the given object.
   */
  static int getObjectSize(ObjectReference object) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(object);
    RVMType type = Magic.objectAsType(tib.getType());

    if (type.isClassType())
      return org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(object.toObject(), type.asClass());
    else
      return org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(object.toObject(), type.asArray(), Magic.getArrayLength(object.toObject()));
  }

  /**
   * @param region The start (or an address less than) the region that was reserved for this object.
   */
  @Override
  @Inline
  public Address copyTo(ObjectReference from, ObjectReference to, Address region) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(from);
    RVMType type = tib.getType();
    int bytes;

    boolean copy = (from != to);

    if (copy) {
      if (type.isClassType()) {
        RVMClass classType = type.asClass();
        bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), classType);
        org.jikesrvm.objectmodel.ObjectModel.moveObject(from.toObject(), to.toObject(), bytes, classType);
      } else {
      RVMArray arrayType = type.asArray();
        int elements = Magic.getArrayLength(from.toObject());
        bytes = org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(from.toObject(), arrayType, elements);
        org.jikesrvm.objectmodel.ObjectModel.moveObject(from.toObject(), to.toObject(), bytes, arrayType);
      }
    } else {
      bytes = getCurrentSize(to);
    }

    Address start = org.jikesrvm.objectmodel.ObjectModel.objectStartRef(to);
    Allocator.fillAlignmentGap(region, start);

    return start.plus(bytes);
  }

  @Override
  public ObjectReference getReferenceWhenCopiedTo(ObjectReference from, Address to) {
    return ObjectReference.fromObject(org.jikesrvm.objectmodel.ObjectModel.getReferenceWhenCopiedTo(from.toObject(), to));
  }

  @Override
  public Address getObjectEndAddress(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getObjectEndAddress(object.toObject());
  }

  @Override
  public int getSizeWhenCopied(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.bytesRequiredWhenCopied(object.toObject());
  }

  @Override
  public int getAlignWhenCopied(ObjectReference object) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(object);
    RVMType type = tib.getType();
    if (type.isArrayType()) {
      return org.jikesrvm.objectmodel.ObjectModel.getAlignment(type.asArray(), object.toObject());
    } else {
      return org.jikesrvm.objectmodel.ObjectModel.getAlignment(type.asClass(), object.toObject());
    }
  }

  @Override
  public int getAlignOffsetWhenCopied(ObjectReference object) {
    TIB tib = org.jikesrvm.objectmodel.ObjectModel.getTIB(object);
    RVMType type = tib.getType();
    if (type.isArrayType()) {
      return org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type.asArray(), object);
    } else {
      return org.jikesrvm.objectmodel.ObjectModel.getOffsetForAlignment(type.asClass(), object);
    }
  }

  @Override
  public int getCurrentSize(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.bytesUsed(object.toObject());
  }

  @Override
  public ObjectReference getNextObject(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getNextObject(object);
  }

  @Override
  public ObjectReference getObjectFromStartAddress(Address start) {
    return org.jikesrvm.objectmodel.ObjectModel.getObjectFromStartAddress(start);
  }

  @Override
  public byte [] getTypeDescriptor(ObjectReference ref) {
    Atom descriptor = Magic.getObjectType(ref).getDescriptor();
    return descriptor.toByteArray();
  }

  @Override
  @Inline
  public int getArrayLength(ObjectReference object) {
    return Magic.getArrayLength(object.toObject());
  }

  @Override
  public boolean isArray(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getObjectType(object.toObject()).isArrayType();
  }

  @Override
  public boolean isPrimitiveArray(ObjectReference object) {
    Object obj = object.toObject();
    return (obj instanceof long[]   ||
            obj instanceof int[]    ||
            obj instanceof short[]  ||
            obj instanceof byte[]   ||
            obj instanceof double[] ||
            obj instanceof float[]);
  }

  /**
   * Tests a bit available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param idx the index of the bit
   */
  public boolean testAvailableBit(ObjectReference object, int idx) {
    return org.jikesrvm.objectmodel.ObjectModel.testAvailableBit(object.toObject(), idx);
  }

  /**
   * Sets a bit available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param idx the index of the bit
   * @param flag <code>true</code> to set the bit to 1,
   * <code>false</code> to set it to 0
   */
  public void setAvailableBit(ObjectReference object, int idx,
                                     boolean flag) {
    org.jikesrvm.objectmodel.ObjectModel.setAvailableBit(object.toObject(), idx, flag);
  }

  @Override
  public boolean attemptAvailableBits(ObjectReference object,
                                             Word oldVal, Word newVal) {
    return org.jikesrvm.objectmodel.ObjectModel.attemptAvailableBits(object.toObject(), oldVal,
                                               newVal);
  }

  @Override
  public Word prepareAvailableBits(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.prepareAvailableBits(object.toObject());
  }

  @Override
  public void writeAvailableByte(ObjectReference object, byte val) {
    org.jikesrvm.objectmodel.ObjectModel.writeAvailableByte(object.toObject(), val);
  }

  @Override
  public byte readAvailableByte(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.readAvailableByte(object.toObject());
  }

  @Override
  public void writeAvailableBitsWord(ObjectReference object, Word val) {
    org.jikesrvm.objectmodel.ObjectModel.writeAvailableBitsWord(object.toObject(), val);
  }

  @Override
  public Word readAvailableBitsWord(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.readAvailableBitsWord(object.toObject());
  }

  /* AJG: Should this be a variable rather than method? */
  @Override
  public Offset GC_HEADER_OFFSET() {
    return org.jikesrvm.objectmodel.ObjectModel.GC_HEADER_OFFSET;
  }

  @Override
  @Inline
  public Address objectStartRef(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.objectStartRef(object);
  }

  @Override
  public Address refToAddress(ObjectReference object) {
    return org.jikesrvm.objectmodel.ObjectModel.getPointerInMemoryRegion(object);
  }

  @Override
  @Inline
  public boolean isAcyclic(ObjectReference typeRef) {
    TIB tib = Magic.addressAsTIB(typeRef.toAddress());
    RVMType type = tib.getType();
    return type.isAcyclicReference();
  }

  @Override
  public void dumpObject(ObjectReference object) {
    DebugUtil.dumpRef(object);
  }
  
  // Velodrome: Trace lock access metadata
  @Override
  public void traceLockMetadata(ObjectReference object, TraceLocal trace) {
    if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(NOT_REACHED); }
    if (Velodrome.addMiscHeader()) {
      Address slot = object.toAddress().plus(MiscHeader.VELODROME_OFFSET);
      trace.processEdge(object, slot);
    }
  }
  
  // Velodrome: Add lock metadata slot to weak reference queue
  @Override
  public void traceLockMetadata(ObjectReference object) {
    if (Velodrome.addMiscHeader()) {
      Address slot = object.toAddress().plus(MiscHeader.VELODROME_OFFSET);
      if (!slot.loadObjectReference().isNull()) {
        TraceLocal trace = ((ParallelCollector) VM.activePlan.collector()).getCurrentTrace();
        trace.metadataSlots.insert(slot);
      }
    }
  }
  
  // Velodrome: Add all object-level metadata references to the weak reference queue
  // for later processing
  @Override
  public void traceObjectLevelMetadata(ObjectReference object) {
    TraceLocal trace = ((ParallelCollector) VM.activePlan.collector()).getCurrentTrace();
    Address slot;
    if (Velodrome.addMiscHeader()) {
      slot = object.toAddress().plus(MiscHeader.VELODROME_OFFSET);
      if (!slot.loadObjectReference().isNull()) {
        trace.metadataSlots.insert(slot);
      }
    }
    if (Velodrome.instrumentArrays()) {
      slot = object.toAddress().plus(MiscHeader.VELODROME_WRITE_OFFSET);
      if (!slot.loadObjectReference().isNull()) {
        trace.metadataSlots.insert(slot);
      }
      slot = object.toAddress().plus(MiscHeader.VELODROME_READ_OFFSET);
      if (!slot.loadObjectReference().isNull()) {
        trace.metadataSlots.insert(slot);
      }
    }
  }

  // Velodrome: Add metadata reference addresses to a queue for later processing
  @Override
  public void addMetadataSlotsToQueue(ObjectReference objRef) {
    if (Velodrome.addPerFieldVelodromeMetadata()) {
      TraceLocal trace = ((ParallelCollector) VM.activePlan.collector()).getCurrentTrace();
      if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(trace.isLive(objRef)); }
      if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(DebugUtil.validRef(objRef)); }
      // The gray object "objRef" should have already moved by now, so it cannot be in the nursery anymore
      if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(!Gen.inNursery(objRef)); }

      RVMType type = org.jikesrvm.objectmodel.ObjectModel.getObjectType(objRef.toObject());
      int[] velodromeOffsets = type.getVelodromeMetadataOffsets();
      if (velodromeOffsets == null) {
        return;
      }
      if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(velodromeOffsets.length % 2 == 0); }
      
      Address addr = null;
      if (velodromeOffsets != REFARRAY_OFFSET_ARRAY) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(type.isClassType() || (type.isArrayType() && !type.asArray().getElementType().isReferenceType()));
        for (int i = 0; i < velodromeOffsets.length; i++) {
          addr = objRef.toAddress().plus(velodromeOffsets[i]);
          if (VM.VERIFY_ASSERTIONS) {
            if (!DebugUtil.validRef(addr.loadObjectReference())) {
              VM.assertions._assert(NOT_REACHED); }
          }
          if (!addr.loadObjectReference().isNull()) { 
            // push() adds to the head of the deque, while insert() adds it to the tail. pop() dequeues elements from the head.
            // It seems an insert() will be more efficient than a push().
            trace.metadataSlots.insert(addr);
          }
        }
      } else {
        if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(NOT_REACHED); }
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(type.isArrayType() && type.asArray().getElementType().isReferenceType());
        for(int i=0; i < org.jikesrvm.objectmodel.ObjectModel.getArrayLength(objRef.toObject()); i++) {
          addr = objRef.toAddress().plus(i << SizeConstants.LOG_BYTES_IN_ADDRESS);
          if (VM.VERIFY_ASSERTIONS) {
            if (!DebugUtil.validRef(addr.loadObjectReference())) {
              VM.assertions._assert(NOT_REACHED); }
          }
          if (!addr.loadObjectReference().isNull()) {
            // push() adds to the head of the deque, while insert() adds it to the tail. pop() dequeues elements from the head.
            // It seems an insert() will be more efficient than a push().
            trace.metadataSlots.insert(addr);
          }
        }
      }
      // Probably not necessary, since the local buffer would be automatically flushed when it is full
      //VM.activePlan.collector().metadataSlots.flushLocal(); 
    }
  }
  
  // Velodrome: Treat metadata references as weak references. This method is only invoked for full heap GCs. 
  // For a full heap GC, we don't expect metadata references that point to read maps to be live. 
  /** @param mdSlot This is the address of a particular metadata slot (either write or read) for a field in an object or 
   *                could be for statics */
  @Override
  public void traceMetadataReferencesDuringFullHeap(TraceLocal trace, Address mdSlot) {
    if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(Velodrome.addPerFieldVelodromeMetadata()); }
    if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(!VM.activePlan.global().isCurrentGCNursery()); }
    ObjectReference oldObjRef = mdSlot.loadObjectReference(); // This is the referent
    
    // It seems that mdSlots are getting added to the weak reference queue multiple times, so in that case, it is 
    // possible that mdSlot has already been processed and nulled out
    if (oldObjRef.isNull()) {
      return;
    }
    
    if (trace.isLive(oldObjRef)) {
      ObjectReference newObjRef = trace.getForwardedReference(oldObjRef); // This is the new referent
      if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(!newObjRef.isNull()); }
      if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(DebugUtil.validRef(newObjRef)); }
      
      if (newObjRef.toAddress().NE(oldObjRef.toAddress())) {
        VM.activePlan.global().storeObjectReference(mdSlot, newObjRef);
      }
      return;      
    }
    
    // 1. Object reference is a transaction, or
    // 2. Object reference is a read map (shouldn't already be marked live)    
    
    // Expecting the TIB to be well formed since the object is probably not yet touched or moved
    Address tib = oldObjRef.toAddress().loadAddress(JavaHeader.getTibOffset());
    if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(tib.NE(Address.zero())); }
    boolean isReadmap = tib.EQ(Velodrome.tibForReadHashMap);
    
    if (!isReadmap) { // Metadata slot points to a transaction which is not already marked live
      VM.activePlan.global().storeObjectReference(mdSlot, ObjectReference.nullReference());
      return;
    } 
    
    // Metadata slot points to a read map
    
    // Iterate over all the elements in the read map and check for liveness of each reference. 
    ReadHashMap map = (ReadHashMap) oldObjRef.toObject();
    boolean oneBucketLive = false; 
    for (int j = 0; j < ReadHashMap.INITIAL_NUMBER_THREADS; j++) {
      ReadHashMapElement rdMapElem = map.getBucketHead(j); // tmp is the start pointer to the bucket indexed by j
      ReadHashMapElement next = null;
      while (rdMapElem != null) {
        ObjectReference tRef = ObjectReference.fromObject(rdMapElem.getTransaction());
        next = map.getNext(rdMapElem);
        if (!trace.isLive(tRef)) { // Reference object is dead
          // Velodrome: TODO: Why does this assertion fail, mostly for xalan9 and eclipse6?
          map.remove(map.getKey(rdMapElem), false);
        } else {
          oneBucketLive = true;
        }
        rdMapElem = next;
      }
    }
    
    if (!oneBucketLive) { // Not even a single bucket has a live transaction
      VM.activePlan.global().storeObjectReference(mdSlot, ObjectReference.nullReference());
      return;
    }
    
    // This should add the read map reference to the gray list. A later CLOSURE phase should then trace all 
    // objects reachable from the read map reference. 
    ObjectReference newObjRef = trace.traceObject(oldObjRef, false);
    if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(DebugUtil.validRef(newObjRef)); }
    if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(newObjRef.toAddress().loadAddress(JavaHeader.getTibOffset()).EQ(Velodrome.tibForReadHashMap)); }
    VM.activePlan.global().storeObjectReference(mdSlot, newObjRef);
  }
  
  public boolean checkForReadmap(ObjectReference newObject, ObjectReference oldObject) {
    if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(!newObject.isNull()); }
    Address tib = newObject.toAddress().loadAddress(JavaHeader.getTibOffset());
    if (tib.EQ(Velodrome.tibForReadHashMap)) {
      Address dMap = ObjectReference.fromObject(Velodrome.dummyMap).toAddress();
      if (VM.VERIFY_ASSERTIONS && oldObject.toAddress().NE(dMap)) { VM.assertions._assert(false); }
      return true;
    }
    return false;
  }
  
  /**
   * @param source Source object whose slot {@code slot} is being traced
   * @param slot Actual slot of {@code source} being traced
   * @param newObj Object reference stored at {@code slot}
   */
  public boolean checkForReadmapDuringTracing(ObjectReference source, Address slot, ObjectReference newObj) {
    if (!newObj.isNull()) {
      Address tib = newObj.toAddress().loadAddress(JavaHeader.getTibOffset());
      if (tib.EQ(Velodrome.tibForReadHashMap)) {
        RVMType type = org.jikesrvm.objectmodel.ObjectModel.getObjectType(source.toObject());
        org.jikesrvm.VM.sysWriteln("RVM Type:", type.getDescriptor());
        return true;
      }
    }
    return false;
  }
  
  /**
   * @param slot Address of root reference
   * @param newObj Object reference stored at {@code slot} after being traced
   * @param oldObj Object reference stored at {@code slot} before being traced
   */
  public boolean checkForReadmapFromRoot(Address slot, ObjectReference newObj, ObjectReference oldObj) {
    if (!newObj.isNull()) {
      Address tib = newObj.toAddress().loadAddress(JavaHeader.getTibOffset());
      if (tib.EQ(Velodrome.tibForReadHashMap)) {
        return true;
      }
    }
    return false;
  }  
  
  public void flushRememberedSets() {
    Selected.Mutator.get().flushRememberedSets();
  }
  
  // This is called during nursery GC to just update the contents of metadata reference slots if the referent is live and 
  // has moved. If the referent is live, we expect that its TIB either be of Transaction or ReadHashMap type.
  // We don't need to recursively check for liveness and moving of objects internal to the read map, since a CLOSURE should 
  // be performed after our Velodrome phase.
  // For a nursery GC, it is possible that metadata references that point to read maps be live because of inter-generational 
  // pointers.
  public void updateMetadataSlotsDuringNursery(TraceLocal trace, Address mdSlot) {
    if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(VM.activePlan.global().isCurrentGCNursery()); }
    ObjectReference oldObjRef = mdSlot.loadObjectReference(); // This is the referent
    if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(!oldObjRef.isNull()); } // We don't add null references to the queue

    ObjectReference newObjRef;
    if (trace.isLive(oldObjRef)) {
      newObjRef = trace.getForwardedReference(oldObjRef); // This is the new referent
      if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(!newObjRef.isNull()); }
      if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(DebugUtil.validRef(newObjRef)); }
      if (VM.VERIFY_ASSERTIONS) {
        Address tib = newObjRef.toAddress().loadAddress(JavaHeader.getTibOffset());
        VM.assertions._assert(tib.EQ(Velodrome.tibForReadHashMap) || tib.EQ(Velodrome.tibForTransaction));
      }
      if (newObjRef.toAddress().NE(oldObjRef.toAddress())) {
        VM.activePlan.global().storeObjectReference(mdSlot, newObjRef);
      }
    } else {
      VM.activePlan.global().storeObjectReference(mdSlot, ObjectReference.nullReference());
    }
  }
  
  @Override
  public boolean testOctetThreadsBeforeGCStarts() {
    boolean flag = true;
    for (int i = RVMThread.numThreads - 1; i >= 0; i--) {
      Magic.sync();
      RVMThread t = RVMThread.threads[i];
      if (!t.isOctetThread()) {
        continue;
      }
      if (t.betweenPreAndPost) {
        org.jikesrvm.VM.sysWrite("Thread id:", t.octetThreadID);
        org.jikesrvm.VM.sysWriteln(" between pre and post");
        org.jikesrvm.VM.sysWrite("Locked object reference:");
        org.jikesrvm.VM.sysWriteln(t.lockedMetadata);
        t.dump();
        if (t.contextRegisters != null && !t.ignoreHandshakesAndGC()) {
          RVMThread.dumpStack(t.contextRegisters.getInnermostFramePointer());
        }
        flag = false;
      }
    }
    return flag;
  }
  
  public boolean validRef(ObjectReference objRef) {
    return DebugUtil.validRef(objRef);
  }
  
  public boolean addPerFieldVelodromeMetadata() {
    return Velodrome.addPerFieldVelodromeMetadata();
  }
  
  public int getNumStaticMetadataSlots() {
    return Velodrome.jtocMetadataReferencesIndex;
  }
  
  public void traceStaticMetadataSlots() {
    TraceLocal trace = ((ParallelCollector) VM.activePlan.collector()).getCurrentTrace();
    // This thread as a collector
    final CollectorContext cc = RVMThread.getCurrentThread().getCollectorContext();
    // The number of collector threads
    final int numberOfCollectors = cc.parallelWorkerCount();
    int numberOfSlots = VM.objectModel.getNumStaticMetadataSlots();
    // The size to give each thread
    final int chunkSize = (numberOfSlots / numberOfCollectors);
    // The number of this collector thread (1...n)
    final int threadOrdinal = cc.parallelWorkerOrdinal();
    
    // Start and end of statics region to be processed
    final int start = threadOrdinal * chunkSize;
    final int leftovers = numberOfSlots - numberOfCollectors * chunkSize;
    final int end = (threadOrdinal + 1 == numberOfCollectors) ? (threadOrdinal + 1) * chunkSize - 1 + leftovers 
                                                              : (threadOrdinal + 1) * chunkSize - 1;
    OffsetArray array = Velodrome.jtocMetadataReferences;
    ObjectReference oldObj;
    ObjectReference newObj;
    
    for (int i = start; i <= end; i++) {
      Offset off = array.get(i);
      Address addr = Magic.getJTOC().plus(off);
      oldObj = addr.loadObjectReference();
      if (!oldObj.isNull()) {
        if (trace.isLive(oldObj)) {
          newObj = trace.getForwardedReference(oldObj);
          if (VM.VERIFY_ASSERTIONS) {
            Address tib = newObj.toAddress().loadAddress(JavaHeader.getTibOffset());
            VM.assertions._assert(tib.EQ(Velodrome.tibForTransaction) || tib.EQ(Velodrome.tibForReadHashMap)); 
          }
          VM.activePlan.global().storeObjectReference(addr, newObj);
        } else {
          VM.activePlan.global().storeObjectReference(addr, ObjectReference.nullReference());
        }
      }
    }
    
  }
  
}
