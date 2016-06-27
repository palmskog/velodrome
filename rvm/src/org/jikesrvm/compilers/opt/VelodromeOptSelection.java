package org.jikesrvm.compilers.opt;

import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.compilers.opt.escape.FI_EscapeSummary;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.velodrome.Velodrome;
import org.jikesrvm.velodrome.VelodromeInstrDecisions;

public class VelodromeOptSelection extends OctetOptSelection {
  
  public VelodromeOptSelection() {
    
  }
  
  @Override
  public String getName() {
    return "Velodrome read and write selection";
  }

  @Override
  void processInst(Instruction inst, FI_EscapeSummary escapeSummary, IR ir) {
    boolean shouldInstrument = false;
    if (GetField.conforms(inst) || PutField.conforms(inst)) {
      shouldInstrument = shouldInstrumentInstPosition(inst, ir) && shouldInstrumentScalarAccess(inst, escapeSummary);
    } else if (ALoad.conforms(inst) || AStore.conforms(inst)) {
      // Conditional instrumentation of array accesses
      if (Velodrome.instrumentArrays()) {
        shouldInstrument = shouldInstrumentInstPosition(inst, ir) && shouldInstrumentArrayAccess(inst, escapeSummary);
      }
    } else if (GetStatic.conforms(inst) || PutStatic.conforms(inst)) {
      shouldInstrument = shouldInstrumentInstPosition(inst, ir) && shouldInstrumentStaticAccess(inst);
    }
    if (shouldInstrument) {
      inst.markAsPossibleSharedMemoryAccess();
    }
  }

  @Override
  boolean shouldInstrumentScalarAccess(Instruction inst, FI_EscapeSummary escapeSummary) {
    // are we early or late in the compilation process?  use a different strategy in each case
    boolean isRead = GetField.conforms(inst);
    LocationOperand loc = isRead ? GetField.getLocation(inst) : PutField.getLocation(inst);
    Operand ref = isRead ? GetField.getRef(inst) : PutField.getRef(inst);
    FieldReference fieldRef = loc.getFieldRef();
    RVMField field = fieldRef.peekResolvedField();
    boolean isResolved = (field != null);
    boolean mightHaveMetadata;
    if (isResolved) {
      mightHaveMetadata = VelodromeInstrDecisions.objectOrFieldHasVelodromeMetadata(field);
    } else {
      mightHaveMetadata = VelodromeInstrDecisions.objectOrFieldMightHaveVelodromeMetadata(fieldRef);
    }
    if (mightHaveMetadata) {
      if (mightEscape(ref, escapeSummary)) {
        return true;
      } else {
        // Helps with debugging
        // inst.markThreadLocal();
      }
    }
    return false;
  }

  @Override
  boolean shouldInstrumentStaticAccess(Instruction inst) {
    boolean isRead = GetStatic.conforms(inst);
    LocationOperand loc = isRead ? GetStatic.getLocation(inst) : PutStatic.getLocation(inst);
    FieldReference fieldRef = loc.getFieldRef();
    RVMField field = fieldRef.peekResolvedField();
    boolean isResolved = (field != null);
    boolean mightHaveMetadata;
    if (isResolved) {
      mightHaveMetadata = VelodromeInstrDecisions.staticFieldHasVelodromeMetadata(field);
    } else {
      mightHaveMetadata = VelodromeInstrDecisions.staticFieldMightHaveVelodromeMetadata(fieldRef);
    }
    if (mightHaveMetadata) {
      return true;
    }
    return false;
  }

}