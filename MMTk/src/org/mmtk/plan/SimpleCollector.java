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
package org.mmtk.plan;

import org.mmtk.plan.generational.Gen;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

/**
 * This class (and its sub-classes) implement <i>per-collector thread</i>
 * behavior and state.<p>
 *
 * MMTk assumes that the VM instantiates instances of CollectorContext
 * in thread local storage (TLS) for each thread participating in
 * collection.  Accesses to this state are therefore assumed to be
 * low-cost during mutator time.<p>
 *
 * @see CollectorContext
 */
@Uninterruptible
public abstract class SimpleCollector extends ParallelCollector {

  /****************************************************************************
   * Instance fields
   */

  /** Used for sanity checking. */
  protected final SanityCheckerLocal sanityLocal = new SanityCheckerLocal();

  /****************************************************************************
   *
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == Simple.PREPARE) {
      // Nothing to do
      return;
    }

    if (phaseId == Simple.STACK_ROOTS) {
      VM.scanning.computeThreadRoots(getCurrentTrace());
      return;
    }

    if (phaseId == Simple.ROOTS) {
      VM.scanning.computeGlobalRoots(getCurrentTrace());
      VM.scanning.computeStaticRoots(getCurrentTrace());
      if (Plan.SCAN_BOOT_IMAGE) {
        VM.scanning.computeBootImageRoots(getCurrentTrace());
      }
      return;
    }

    if (phaseId == Simple.SOFT_REFS) {
      if (primary) {
        if (Options.noReferenceTypes.getValue())
          VM.softReferences.clear();
        else
          VM.softReferences.scan(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.WEAK_REFS) {
      if (primary) {
        if (Options.noReferenceTypes.getValue())
          VM.weakReferences.clear();
        else
          VM.weakReferences.scan(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.FINALIZABLE) {
      if (primary) {
        if (Options.noFinalizer.getValue())
          VM.finalizableProcessor.clear();
        else
          VM.finalizableProcessor.scan(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.PHANTOM_REFS) {
      if (primary) {
        if (Options.noReferenceTypes.getValue())
          VM.phantomReferences.clear();
        else
          VM.phantomReferences.scan(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.FORWARD_REFS) {
      if (primary && !Options.noReferenceTypes.getValue() &&
          VM.activePlan.constraints().needsForwardAfterLiveness()) {
        VM.softReferences.forward(getCurrentTrace(),global().isCurrentGCNursery());
        VM.weakReferences.forward(getCurrentTrace(),global().isCurrentGCNursery());
        VM.phantomReferences.forward(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.FORWARD_FINALIZABLE) {
      if (primary && !Options.noFinalizer.getValue() &&
          VM.activePlan.constraints().needsForwardAfterLiveness()) {
        VM.finalizableProcessor.forward(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.COMPLETE) {
      // Nothing to do
      return;
    }

    if (phaseId == Simple.RELEASE) {
      // Nothing to do
      return;
    }
    
    // Velodrome: Added a phase for processing metadata references
    if (phaseId == Gen.VELODROME_METADATA) {
      TraceLocal trace = ((ParallelCollector) VM.activePlan.collector()).getCurrentTrace();
      if (!global().isCurrentGCNursery()) {
        while (!trace.metadataSlots.isEmpty()) {
          Address mdSlot = trace.metadataSlots.pop();
          VM.objectModel.traceMetadataReferencesDuringFullHeap(trace, mdSlot);
        }
      } else {
        while (!trace.metadataSlots.isEmpty()) {
          Address mdSlot = trace.metadataSlots.pop();
          VM.objectModel.updateMetadataSlotsDuringNursery(trace, mdSlot);
        }
      } 
      VM.objectModel.traceStaticMetadataSlots(); // Scan statics
      
      // Velodrome: LATER: Is this required? Mike says that only the mutator generates remset entries.
      // Flush out any remset entries generated during the above activities
      VM.objectModel.flushRememberedSets();
      if (VM.VERIFY_ASSERTIONS) { VM.assertions._assert(trace.metadataSlots.isFlushed()); }
      return;
    }

    if (Options.sanityCheck.getValue() && sanityLocal.collectionPhase(phaseId, primary)) {
      return;
    }

    Log.write("Per-collector phase "); Log.write(Phase.getName(phaseId));
    Log.writeln(" not handled.");
    VM.assertions.fail("Per-collector phase not handled!");
  }

  /****************************************************************************
   *
   * Miscellaneous.
   */

  /** @return The active global plan as a <code>Simple</code> instance. */
  @Inline
  private static Simple global() {
    return (Simple) VM.activePlan.global();
  }
}
