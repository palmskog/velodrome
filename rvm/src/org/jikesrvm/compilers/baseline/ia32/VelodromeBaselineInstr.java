package org.jikesrvm.compilers.baseline.ia32;

import org.jikesrvm.ArchitectureSpecific.Assembler;
import org.jikesrvm.ArchitectureSpecific.BaselineConstants;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.octet.InstrDecisions;
import org.jikesrvm.octet.Octet;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.velodrome.Velodrome;
import org.jikesrvm.velodrome.VelodromeInstrDecisions;
import org.vmmagic.unboxed.Offset;

public final class VelodromeBaselineInstr extends OctetBaselineInstr implements BaselineConstants {
  
  /** Instrument the beginning of a transaction. */
  static final void insertInstrumentationAtTransactionBegin(NormalMethod method, int biStart, Assembler asm, 
      OctetBaselineInstr octetBaselineInstr, int methodID) {    
    int params = 0;
    params += passSite(method, biStart, asm); // Pass site info
    // Parameter: Method id
    asm.emitPUSH_Imm(methodID);
    params += 1; // For method id
    // make the call
    BaselineCompilerImpl.genParameterRegisterLoad(asm, params);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.velodromeStartTransactionMethod.getOffset()));
  }
  
  /** Instrument the end of a transaction. */
  static final void insertInstrumentationAtTransactionEnd(NormalMethod method, int biStart, Assembler asm, 
      OctetBaselineInstr octetBaselineInstr, int methodID) {
    int params = 0; 
    params += passSite(method, biStart, asm); // Pass site info
    // Parameter: Method id
    asm.emitPUSH_Imm(methodID);
    params += 1; // For method id
    // make the call
    BaselineCompilerImpl.genParameterRegisterLoad(asm, params);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.velodromeEndTransactionMethod.getOffset()));
  }     
  
  /** <ol> 
   * <li> Pass Velodrome metadata offset for all. 
   * <li> Pass the element size for arrays. 
   * </ol>*/
  @Override
  int passExtra(NormalMethod method, int biStart, boolean isField, FieldReference fieldRef, TypeReference type, GPR offsetReg, Assembler asm) {
    // param: Velodrome metadata offset, note that zero is a valid offset
    int writeOffset = Velodrome.UNITIALIZED_OFFSET;
    int readOffset = Velodrome.UNITIALIZED_OFFSET;

    // fieldRef is null for arrays
    if (Velodrome.addPerFieldVelodromeMetadata()) {
      if (fieldRef != null && fieldRef.getResolvedField() != null) {
        // Possible for fields accessed to have no Velodrome metadata offset 
        //if (VM.VerifyAssertions) { VM._assert(fieldRef.getResolvedField().hasVelodromeMetadataOffset()); }
        if (fieldRef.getResolvedField().hasVelodromeMetadataOffset()) {
          writeOffset = fieldRef.getResolvedField().getWriteMetadataOffset().toInt();
          readOffset = fieldRef.getResolvedField().getReadMetadataOffset().toInt();
        }
      }
    }
    asm.emitPUSH_Imm(writeOffset);
    asm.emitPUSH_Imm(readOffset);
    int param = 2;  // We always pass the metadata offsets
    
    // We pass the array element size from here, so that it helps in computing array index offset
    if (!isField) { // Array type
      if (VM.VerifyAssertions) { VM._assert(Velodrome.instrumentArrays()); }
      if (VM.VerifyAssertions) { VM._assert(type != null && fieldRef == null); }
      asm.emitPUSH_Imm(type.getMemoryBytes()); // Can be 1,2,4
      param += 1;
    }
    return param;
  }
  
  /** Barrier for resolved static fields */
  @Override
  boolean insertStaticBarrierResolved(NormalMethod method, int biStart, boolean isRead, RVMField field, Assembler asm) {
    if (shouldInstrument(method, biStart) && VelodromeInstrDecisions.staticFieldHasVelodromeMetadata(field)) {
      NormalMethod barrierMethod = Octet.getClientAnalysis().chooseBarrier(method, isRead, true, true, true, false, isSpecializedMethod(method));
      // Start and finish call      
      int params = startStaticBarrierResolvedCall(field, asm);
      finishCall(method, biStart, true, field.getMemberRef().asFieldReference(), null, null, barrierMethod, params, asm);
      return true;
    }
    return false;
  }
  
  @Override
  int startStaticBarrierResolvedCall(RVMField field, Assembler asm) {
    // param: field info
    int fieldOffset = InstrDecisions.getFieldInfo(field);
    asm.emitPUSH_Imm(fieldOffset);
    return 1;
  }
  
  /** Barrier for unresolved static fields */
  @Override
  boolean insertStaticBarrierUnresolved(NormalMethod method, int biStart, boolean isRead, GPR offsetReg, FieldReference fieldRef, Assembler asm) {
    if (shouldInstrument(method, biStart) && VelodromeInstrDecisions.staticFieldMightHaveVelodromeMetadata(fieldRef)) {
      NormalMethod barrierMethod = Octet.getClientAnalysis().chooseBarrier(method, isRead, true, false, true, false, isSpecializedMethod(method));
      // save offset value on stack
      asm.emitPUSH_Reg(offsetReg); // save value on the stack; also update where we'll find the reference
      // start and finish call      
      int params = startStaticBarrierUnresolvedCall(offsetReg, fieldRef, asm);
      finishCall(method, biStart, true, fieldRef, null, offsetReg, barrierMethod, params, asm);
      // restore offset value from stack
      asm.emitPOP_Reg(offsetReg);
      return true;
    }
    return false;
  }
  
  /** Barrier for resolved non-static fields */
  @Override
  boolean insertFieldBarrierResolved(NormalMethod method, int biStart, boolean isRead, Offset numSlots, RVMField field, Assembler asm) {
    if (shouldInstrument(method, biStart) && VelodromeInstrDecisions.objectOrFieldHasVelodromeMetadata(field)) {
      NormalMethod barrierMethod = Octet.getClientAnalysis().chooseBarrier(method, isRead, true, true, false, false, isSpecializedMethod(method));
      // start and finish call      
      int params = startFieldBarrierResolvedCall(numSlots, field, asm);
      finishCall(method, biStart, true, field.getMemberRef().asFieldReference(), null, null, barrierMethod, params, asm);
      return true;
    }
    return false;
  }
  
  /** Barrier for unresolved non-static fields */
  @Override
  boolean insertFieldBarrierUnresolved(NormalMethod method, int biStart, boolean isRead, Offset numSlots, GPR offsetReg, FieldReference fieldRef, Assembler asm) {
    if (shouldInstrument(method, biStart) && VelodromeInstrDecisions.objectOrFieldMightHaveVelodromeMetadata(fieldRef)) {
      // we can just send isResolved==true because we can get the offset out of the register "offsetReg"
      // But we are instead passing false, so that we can extract the Velodrome metadata offset in the barriers
      NormalMethod barrierMethod = Octet.getClientAnalysis().chooseBarrier(method, isRead, true, false, false, false, isSpecializedMethod(method));
      // save offset value on stack
      asm.emitPUSH_Reg(offsetReg);
      // start and finish call      
      int params = startFieldBarrierUnresolvedCall(numSlots, offsetReg, fieldRef, asm);
      finishCall(method, biStart, true, fieldRef, null, offsetReg, barrierMethod, params, asm);
      // restore offset value from stack
      asm.emitPOP_Reg(offsetReg);
      return true;
    }
    return false;
  }
  
  // It is important to override this method if we are using unresolved barriers 
  @Override
  int startFieldBarrierUnresolvedCall(Offset numSlots, GPR offsetReg, FieldReference fieldRef, Assembler asm) {
    // param: object reference -- add a slot because of the push above
    asm.emitPUSH_RegDisp(SP, numSlots.plus(WORDSIZE));
    // param: field info (either field ID or field offset)
    if (InstrDecisions.passFieldInfo()) {
      // We are ignoring the fact that offsets are what the client analysis has requested for, and instead 
      // we are still passing the field id
      asm.emitPUSH_Imm(fieldRef.getId());
    } else {
      asm.emitPUSH_Imm(0);
    }
    return 2;
  }
  
  /****************** Post read/write barriers  *******************/
  
  static boolean insertPostBarrierForResolvedGetfield(NormalMethod method, int biStart, Assembler asm, RVMField field, GPR reg) {
    if (shouldInstrument(method, biStart) && VelodromeInstrDecisions.objectOrFieldHasVelodromeMetadata(field)) {
      NormalMethod barrierMethod = Entrypoints.velodromeUnlockMetadataForResolvedFieldMethod;
      asm.emitPUSH_Reg(reg); // Object reference
      // Object reference is already expected to be on the stack
      // param: Velodrome write metadata offset, note that zero is a valid offset
      int writeOffset = Velodrome.UNITIALIZED_OFFSET;
      if (field.hasVelodromeMetadataOffset()) {
        writeOffset = field.getWriteMetadataOffset().toInt();
      }
      asm.emitPUSH_Imm(writeOffset);
      int params = 2;
      params += passSite(method, biStart, asm); // Pass site info
      asm.emitPUSH_Imm(1);
      params += 1;
      BaselineCompilerImpl.genParameterRegisterLoad(asm, params);
      asm.emitCALL_Abs(Magic.getTocPointer().plus(barrierMethod.getOffset()));
      return true;
    }
    return false;
  }
  
  static boolean insertPostBarrierForResolvedPutfield(NormalMethod method, int biStart, Assembler asm, RVMField field) {
    if (shouldInstrument(method, biStart) && VelodromeInstrDecisions.objectOrFieldHasVelodromeMetadata(field)) {
      NormalMethod barrierMethod = Entrypoints.velodromeUnlockMetadataForResolvedFieldMethod;
      // object reference is already on the stack, see emit_resolved_putfield
      // param: Velodrome write metadata offset, note that zero is a valid offset
      int writeOffset = Velodrome.UNITIALIZED_OFFSET;
      if (field.hasVelodromeMetadataOffset()) {
        writeOffset = field.getWriteMetadataOffset().toInt();
      }
      asm.emitPUSH_Imm(writeOffset);
      int params = 2;
      params += passSite(method, biStart, asm); // Pass site info
      asm.emitPUSH_Imm(0);
      params += 1;
      BaselineCompilerImpl.genParameterRegisterLoad(asm, params);
      asm.emitCALL_Abs(Magic.getTocPointer().plus(barrierMethod.getOffset()));
      return true;
    }
    return false;
  }
  
  static boolean insertPostBarrierForUnresolvedGetfield(NormalMethod method, int biStart, Assembler asm, 
      FieldReference fieldRef, GPR reg) {
    if (shouldInstrument(method, biStart) && VelodromeInstrDecisions.objectOrFieldMightHaveVelodromeMetadata(fieldRef)) {
      NormalMethod barrierMethod = Entrypoints.velodromeUnlockMetadataForUnresolvedFieldMethod;
      asm.emitPUSH_Reg(reg); // Object reference
      // param: Velodrome metadata offset, note that zero is a valid offset
      if (InstrDecisions.passFieldInfo()) {
        // We are ignoring the fact that offsets are what the client analysis has requested for, and instead 
        // we are still passing the field id
        asm.emitPUSH_Imm(fieldRef.getId());
      } else {
        asm.emitPUSH_Imm(0);
      }
      int params = 2;
      params += passSite(method, biStart, asm); // Pass site info
      asm.emitPUSH_Imm(1);
      params += 1;
      BaselineCompilerImpl.genParameterRegisterLoad(asm, params);
      asm.emitCALL_Abs(Magic.getTocPointer().plus(barrierMethod.getOffset()));
      return true;
    }
    return false;
  }
  
  static boolean insertPostBarrierForUnresolvedPutfield(NormalMethod method, int biStart, Assembler asm, FieldReference fieldRef) {
    if (shouldInstrument(method, biStart) && VelodromeInstrDecisions.objectOrFieldMightHaveVelodromeMetadata(fieldRef)) {
      NormalMethod barrierMethod = Entrypoints.velodromeUnlockMetadataForUnresolvedFieldMethod;
      // object reference is already on the stack, see emit_unresolved_putfield()
      // param: Velodrome metadata offset, note that zero is a valid offset
      if (InstrDecisions.passFieldInfo()) {
        // We are ignoring the fact that offsets are what the client analysis has requested for, and instead 
        // we are still passing the field id
        asm.emitPUSH_Imm(fieldRef.getId());
      } else {
        asm.emitPUSH_Imm(0);
      }
      int params = 2;
      params += passSite(method, biStart, asm); // Pass site info
      asm.emitPUSH_Imm(0);
      params += 1;
      BaselineCompilerImpl.genParameterRegisterLoad(asm, params);
      asm.emitCALL_Abs(Magic.getTocPointer().plus(barrierMethod.getOffset()));
      return true;
    }
    return false;
  }
  
  static boolean insertInstrumentationToUnlockMetadataForStaticUnresolved(NormalMethod method, int biStart, boolean isRead, 
      FieldReference fieldRef, Assembler asm) {
    if (shouldInstrument(method, biStart) && VelodromeInstrDecisions.staticFieldMightHaveVelodromeMetadata(fieldRef)) {
      NormalMethod barrierMethod = Entrypoints.velodromeUnlockMetadataForStaticUnresolvedMethod;
      int fieldInfo = fieldRef.getId();
      asm.emitPUSH_Imm(fieldInfo);
      int params = 1;
      params += passSite(method, biStart, asm);
      if (isRead) {
        asm.emitPUSH_Imm(1);
      } else {
        asm.emitPUSH_Imm(0);
      }
      params += 1;
      BaselineCompilerImpl.genParameterRegisterLoad(asm, params);
      asm.emitCALL_Abs(Magic.getTocPointer().plus(barrierMethod.getOffset()));
      return true;
    }
    return false;
  }
  
  static boolean insertInstrumentationToUnlockMetadataForStaticResolved(NormalMethod method, int biStart, boolean isRead, RVMField field,
      Assembler asm) {
    if (shouldInstrument(method, biStart) && VelodromeInstrDecisions.staticFieldHasVelodromeMetadata(field)) {
      NormalMethod barrierMethod = Entrypoints.velodromeUnlockMetadataForStaticResolvedMethod;
      // param: Velodrome write metadata offset, note that zero is a valid offset
      int writeOffset = field.getWriteMetadataOffset().toInt();
      asm.emitPUSH_Imm(writeOffset);
      int fieldInfo = InstrDecisions.getFieldInfo(field);
      asm.emitPUSH_Imm(fieldInfo);
      int params = 2;
      params += passSite(method, biStart, asm);
      if (isRead) {
        asm.emitPUSH_Imm(1);
      } else {
        asm.emitPUSH_Imm(0);
      }
      params += 1;
      BaselineCompilerImpl.genParameterRegisterLoad(asm, params);
      asm.emitCALL_Abs(Magic.getTocPointer().plus(barrierMethod.getOffset()));
      return true;
    }
    return false;
  }
  
  static boolean insertPostBarrierForArray(NormalMethod method, int biStart, boolean isRead, GPR refReg, GPR idxReg, 
      TypeReference type, Assembler asm) {
    if (shouldInstrument(method, biStart)) {
      NormalMethod barrierMethod = Entrypoints.velodromeArrayPostBarrierMethod;
      asm.emitPUSH_Reg(refReg); // Object reference
      asm.emitPUSH_Reg(idxReg); // Array index
      int params = 2;
      params += passSite(method, biStart, asm);
      if (isRead) {
        asm.emitPUSH_Imm(1);
      } else {
        asm.emitPUSH_Imm(0);
      }
      params += 1;
      BaselineCompilerImpl.genParameterRegisterLoad(asm, params);
      asm.emitCALL_Abs(Magic.getTocPointer().plus(barrierMethod.getOffset()));
      return true;
    }
    return false;
  }

}
