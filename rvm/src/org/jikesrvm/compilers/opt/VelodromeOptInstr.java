package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Context;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.hir2lir.ConvertToLowLevelIR;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Prologue;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Return;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.octet.CFGVisualization;
import org.jikesrvm.octet.InstrDecisions;
import org.jikesrvm.octet.Octet;
import org.jikesrvm.octet.Site;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.util.HashSetRVM;
import org.jikesrvm.velodrome.Velodrome;

public class VelodromeOptInstr extends OctetOptInstr implements Operators, OptConstants {

  public VelodromeOptInstr(boolean late, RedundantBarrierRemover redundantBarrierRemover) {
    super(late, redundantBarrierRemover);
  }
  
  @Override
  public String getName() {
    return "Velodrome instrumentation";
  }
  
  // Class to store the <current instr, post instr> information, a tuple indicates that 
  // the post instr needs to be inserted after current instr in the FCFG.
  public static final class InstrInfoWrapper {
    Instruction currInstr;
    Instruction postInstr;
    
    public void addInstrInfo(Instruction inst, Instruction post) {
      this.currInstr = inst;
      this.postInstr = post;
    }
  }
  
  @Override
  public void perform(IR ir) {

    if(ir.options.VISUALIZE_CFG) {
      CFGVisualization cfg = new CFGVisualization(ir, "beforeVelodrome");
      cfg.visualizeCFG(ir);
    }

    if (VM.VerifyAssertions) {
      slowPathsInstrumented = new HashSetRVM<Instruction>();
    }

    HashSetRVM<Instruction> callsToInline = null;
    HashSetRVM<InstrInfoWrapper> postInstrInfo = new HashSetRVM<InstrInfoWrapper>();

    if (inliningType == InliningType.JIKES_INLINER) {
      ir.gc.resync(); // resync generation context; needed since Jikes inlining may occur
      callsToInline = new HashSetRVM<Instruction>();
    }

    if (verbose) {
      System.out.println("Method before: " + ir.getMethod());
      ir.printInstructions();
    }

    for (Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst = inst.nextInstructionInCodeOrder()) {
      if (inst.isPossibleSharedMemoryAccess() || (Octet.getClientAnalysis().instrInstructionHasRedundantBarrier(inst))) {
        instrumentInst(inst, callsToInline, ir, postInstrInfo);
        inst.clearAsPossibleSharedMemoryAccess();
      } else {
        instrumentOtherInstTypes(inst, callsToInline, ir, postInstrInfo);
      }
    }

    // We insert post instructions for transaction exit and read/write barriers
    if (Velodrome.insertPostBarriers() || (Velodrome.methodsAsTransactions() 
        || (Velodrome.syncBlocksAsTransactions() && ir.getMethod().isSynchronized()))) {
      for (InstrInfoWrapper instr : postInstrInfo) {
        Instruction curInst = instr.currInstr;
        Instruction postInstr = instr.postInstr;
        if (VM.VerifyAssertions) { VM._assert(testPostCall(postInstr, curInst, ir)); }
        curInst.insertAfter(postInstr);
      }
    }

    if (verbose) {
      System.out.println("Method after: " + ir.getMethod());
      ir.printInstructions();
    }

    if (inliningType == InliningType.JIKES_INLINER) {
      for (Instruction call : callsToInline) {
        Instruction inst = call.nextInstructionInCodeOrder();
        // Setting no callee exceptions might be incorrect for analyses that
        // want to throw exceptions out of the slow path, so allow analyses to override this.
        // Octet: TODO: trying this -- does it help STM?
        inline(call, ir, !Octet.getClientAnalysis().barriersCanThrowExceptions());
        // Make optimistic RBA safe by executing barriers for recently acquired objects.
        if (!inst.hasRedundantBarrier() || !Octet.getConfig().isFieldSensitiveAnalysis()) {
          makeRedundantBarriersSafe(inst, null, ir);
        }
      }
    }

    // Check that all instructions were instrumented.  While it seems like iterating over
    // all instructions above should accomplish this, the facts that instructions get inserted --
    // and particularly because BBs get split -- can cause problems, particularly if
    // the "next" instruction is determined prior to inserting barrier code (which is avoided above).
    if (VM.VerifyAssertions) {
      if (!Octet.getConfig().isFieldSensitiveAnalysis()) {
        for (Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst = inst.nextInstructionInCodeOrder()) {
          VM._assert(!inst.isPossibleSharedMemoryAccess());
        }
      }
    }
    if(ir.options.VISUALIZE_CFG) {
      CFGVisualization cfgAfter = new CFGVisualization(ir, "afterVelodrome");
      cfgAfter.visualizeCFG(ir);
    }
  }  
  
  void instrumentInst(Instruction inst, HashSetRVM<Instruction> callsToInline, IR ir, HashSetRVM<InstrInfoWrapper> instrumentedInsts) {
    if (GetField.conforms(inst) || PutField.conforms(inst)) {
      if (VM.VerifyAssertions) { VM._assert(!lateInstr); }
      instrumentScalarAccess(inst, callsToInline, ir, instrumentedInsts);
    } else if (ALoad.conforms(inst) || AStore.conforms(inst)) {
      // Conditional instrumentation of array accesses
      if (Velodrome.instrumentArrays()) {
        instrumentArrayAccess(inst, callsToInline, ir, instrumentedInsts);
      }
    } else if (GetStatic.conforms(inst) || PutStatic.conforms(inst)) {
      if (VM.VerifyAssertions) { VM._assert(!lateInstr); }
      instrumentStaticAccess(inst, callsToInline, ir, instrumentedInsts);
    } else if (Load.conforms(inst) || Store.conforms(inst)) {
      if (VM.VerifyAssertions) { VM._assert(lateInstr); }
      Operand tempRef = Load.conforms(inst) ? Load.getAddress(inst) : Store.getAddress(inst);
      boolean isStatic = tempRef.isIntConstant() && tempRef.asIntConstant().value == Magic.getTocPointer().toInt();
      if (isStatic) {
        instrumentStaticAccess(inst, callsToInline, ir, instrumentedInsts);
      } else {
        instrumentScalarAccess(inst, callsToInline, ir, instrumentedInsts);
      }
    } else {
      if (VM.VerifyAssertions) { VM._assert(false); }
    }
  }

  /**
   * Velodrome wants to instrument few additional instructions over Octet
   * @param inst
   * @param callsToInline
   * @param ir
   */
  public void instrumentOtherInstTypes(Instruction inst, HashSetRVM<Instruction> callsToInline, IR ir,
      HashSetRVM<InstrInfoWrapper> instrumentedInstrs) {
    // position attribute may not always be set for non-read/write instructions, so cannot directly use
    // OctetOptSelection::shouldInstrumentInstPosition(), but then again getSite() will fail if position is not set
    
    if (OctetOptSelection.shouldInstrumentInstPosition(inst, ir)) {
      if (Velodrome.methodsAsTransactions() || (Velodrome.syncBlocksAsTransactions() && inst.position.getMethod().isSynchronized())) {
        NormalMethod method = inst.position.getMethod();
        
        if (Prologue.conforms(inst)) { // Method entry
          // Insert the debug method first
          if (Velodrome.checkMethodContextAtProlog() && Context.isApplicationPrefix(inst.position.getMethod().getDeclaringClass().getTypeRef())) {
            insertVerifyApplicationContext(inst);
          }
          // Insert at all prologs, this call instruction should get sandwiched between
          // the prolog and the "just above" context debug call 
          if (Context.isTRANSContext(method.getStaticContext()) && Context.isNONTRANSContext(method.getResolvedContext())) {
            instrumentTransactionEntry(inst, callsToInline, ir);
          }
        } else if (Return.conforms(inst)) { // Method exit
          if (Context.isTRANSContext(method.getStaticContext()) && Context.isNONTRANSContext(method.getResolvedContext())) {
            instrumentTransactionExit(inst, callsToInline, ir, instrumentedInstrs);
          }
        }
        
      } 
      if (Velodrome.syncBlocksAsTransactions()) {
        if (inst.getOpcode() == MONITORENTER_opcode ) { // Monitor entry
          instrumentTransactionEntry(inst, callsToInline, ir);
        } else if (inst.getOpcode() == MONITOREXIT_opcode) { // Monitor exit
          instrumentTransactionExit(inst, callsToInline, ir, instrumentedInstrs);
        }
      }
    }
  }
  
  /**
   * Insert instrumentation at method entry, or monitor entry
   * @param inst
   * @param callsToInline
   * @param ir
   */
  void instrumentTransactionEntry(Instruction inst, HashSetRVM<Instruction> callsToInline, IR ir) {
    int methodID = inst.position.getMethod().getId();
    int siteID = InstrDecisions.passSite() ? Site.getSite(inst) : 0;
    NormalMethod barrierMethod = Entrypoints.velodromeStartTransactionMethod;
    Instruction barrierCall = Call.create2(CALL, 
                                            null, 
                                            IRTools.AC(barrierMethod.getOffset()), 
                                            MethodOperand.STATIC(barrierMethod), 
                                            IRTools.IC(siteID),
                                            IRTools.IC(methodID));
    barrierCall.bcIndex = inst.bcIndex;
    barrierCall.position = inst.position;
    inst.insertAfter(barrierCall); // Insert after the prolog instruction
    if (Velodrome.inlineStartEndTransactions()) {
      inlineInstrs(barrierCall, inst, callsToInline);
    }
  }
  
  /** Insert call to debug method to verify contexts */
  void insertVerifyApplicationContext(Instruction inst) {
    NormalMethod barrierMethod = Entrypoints.velodromeCheckMethodContextAtPrologMethod;
    Instruction barrierCall = Call.create0(CALL, null, IRTools.AC(barrierMethod.getOffset()), MethodOperand.STATIC(barrierMethod));
    barrierCall.bcIndex = inst.bcIndex;
    barrierCall.position = inst.position;
    inst.insertAfter(barrierCall);
  }

  // Inline if using Jikes inliner
  private void inlineInstrs(Instruction barrierCall, Instruction inst, HashSetRVM<Instruction> callsToInline) {
    // Velodrome: LATER: Is it necessary to check for frequency?
    if (inliningType == InliningType.JIKES_INLINER && !inst.getBasicBlock().getInfrequent()) {
      callsToInline.add(barrierCall);
    }    
  }

  /**
   * Insert instrumentation before method exit, monitor exit
   * @param inst
   * @param callsToInline
   * @param ir
   */
  void instrumentTransactionExit(Instruction inst, HashSetRVM<Instruction> callsToInline, IR ir, HashSetRVM<InstrInfoWrapper> instrumentedInstrs) {
    int methodID = inst.position.getMethod().getId();
    int siteID = InstrDecisions.passSite() ? Site.getSite(inst) : 0;
    NormalMethod barrierMethod = Entrypoints.velodromeEndTransactionMethod;
    Instruction barrierCall = Call.create2(CALL, 
                                            null, 
                                            IRTools.AC(barrierMethod.getOffset()), 
                                            MethodOperand.STATIC(barrierMethod), 
                                            IRTools.IC(siteID),
                                            IRTools.IC(methodID));
    barrierCall.bcIndex = inst.bcIndex;
    barrierCall.position = inst.position;
    // Velodrome: TODO: Do not insert barrierCall into the instruction stream immediately. Why?
//    InstrInfoWrapper wrapper = new InstrInfoWrapper();
//    wrapper.addInstrInfo(inst, barrierCall);
//    instrumentedInstrs.add(wrapper);
    inst.insertBefore(barrierCall); // Insert before the return instruction
    if (Velodrome.inlineStartEndTransactions()) {
      inlineInstrs(barrierCall, inst, callsToInline);
    }
  }
  
  /** <ol> 
   * <li> Pass Velodrome metadata write and read offsets for all. 
   * <li> Pass the element size for arrays. 
   * </ol>*/
  @Override
  void passExtra(Instruction inst, FieldReference fieldRef, Instruction barrier) {    
    // param: Velodrome metadata write and read offsets, note that zero is a valid offset
    int writeOffset = Velodrome.UNITIALIZED_OFFSET;
    int readOffset = Velodrome.UNITIALIZED_OFFSET;
    if (Velodrome.addPerFieldVelodromeMetadata()) {
      if (fieldRef != null && fieldRef.getResolvedField() != null) {
        RVMField field = fieldRef.getResolvedField();
        if (field.hasVelodromeMetadataOffset()) {
          writeOffset = field.getWriteMetadataOffset().toInt();
          readOffset = field.getReadMetadataOffset().toInt();
        }
      }
    }
    // We always pass the metadata offsets
    addParam(barrier, IRTools.IC(writeOffset));
    addParam(barrier, IRTools.IC(readOffset));

    if (fieldRef == null) { // fieldRef is null for array accesses
      boolean isRead = ALoad.conforms(inst);
      LocationOperand loc = isRead ? ALoad.getLocation(inst) : AStore.getLocation(inst);
      if (VM.VerifyAssertions) { VM._assert(loc.isArrayAccess()); }
      Operand ref = isRead ? ALoad.getArray(inst) : AStore.getArray(inst);
      TypeReference elemType = ref.getType().getArrayElementType();
      addParam(barrier, IRTools.IC(elemType.getMemoryBytes()));
    }
  }

  void instrumentScalarAccess(Instruction inst, HashSetRVM<Instruction> callsToInline, IR ir, 
      HashSetRVM<InstrInfoWrapper> instrumentedInstrs) {
    boolean isRead;
    LocationOperand loc;
    Operand ref = null;
    if (!lateInstr) {
      isRead = GetField.conforms(inst);
      loc = isRead ? GetField.getLocation(inst) : PutField.getLocation(inst);
      ref = isRead ? GetField.getRef(inst) : PutField.getRef(inst);
    } else {
      isRead = Load.conforms(inst);
      loc = isRead ? Load.getLocation(inst) : Store.getLocation(inst);
      if (VM.VerifyAssertions) { VM._assert(loc != null && loc.isFieldAccess()); }
      Operand tempRef = isRead ? Load.getAddress(inst) : Store.getAddress(inst);
      if (VM.VerifyAssertions) { VM._assert(!(tempRef.isIntConstant() && tempRef.asIntConstant().value == Magic.getTocPointer().toInt())); }
      ref = tempRef;
    }
    FieldReference fieldRef = loc.getFieldRef();
    RVMField field = fieldRef.peekResolvedField();
    boolean isResolved = (field != null);
    if (VM.VerifyAssertions && isResolved) { VM._assert(!field.isStatic()); }

    int fieldInfo = 0;
    if (InstrDecisions.passFieldInfo()) {
      // Octet: TODO: we could still call the resolved barrier if doing late instrumentation,
      // since the offset will be in a virtual register
      if (isResolved && InstrDecisions.useFieldOffset()) {
        fieldInfo = getFieldOffset(field);
      } else {
        fieldInfo = fieldRef.getId();
      }
    }
    NormalMethod barrierMethod = Octet.getClientAnalysis().chooseBarrier(ir.getMethod(), isRead, true, isResolved, false, inst.hasRedundantBarrier(), isSpecializedMethod(ir));
    Instruction barrierCall = Call.create2(CALL,
                                            null,
                                            IRTools.AC(barrierMethod.getOffset()),
                                            MethodOperand.STATIC(barrierMethod),
                                            ref.copy(),
                                            IRTools.IC(fieldInfo));
    barrierCall.position = inst.position;
    barrierCall.bcIndex = inst.bcIndex;
    // Octet: LATER: try this
    /*
    if (!Octet.getClientAnalysis().barriersCanThrowExceptions()) {
      barrierCall.markAsNonPEI();
    }
     */
    finishParams(inst, fieldRef, barrierCall);
    insertBarrier(barrierCall, inst, isRead, ref, field, isResolved, callsToInline, ir);
    
    // Velodrome: Inserting instrumentation to release metadata lock
    if (Velodrome.insertPostBarriers()) {
      Operand base = ref.copy();
      // First make a backup of the object reference
      if (isRead) { // Getfield
        // This code block assumes early assertion, this assertion helps in sanity check
        if (VM.VerifyAssertions) { VM._assert(!lateInstr); }
        if (GetField.getResult(inst).isRegister() && GetField.getRef(inst).isRegister()) { // LHS is a register operand
          if (GetField.getResult(inst).getRegister() == GetField.getRef(inst).asRegister().getRegister()) {
            // Register is same on LHS and RHS
            RegisterOperand t = ir.regpool.makeTempAddress();
            Instruction backupObjRef = Move.create(REF_MOVE, t, ref.copy());
            backupObjRef.position = inst.position;
            backupObjRef.bcIndex = inst.bcIndex;
            inst.insertBefore(backupObjRef);
            base = t;
          }
        }
      }

      NormalMethod postBarrierMethod = (isResolved) ? Entrypoints.velodromeUnlockMetadataForResolvedFieldMethod 
                                                    : Entrypoints.velodromeUnlockMetadataForUnresolvedFieldMethod;
      int writeOffset = Velodrome.UNITIALIZED_OFFSET;
      if (isResolved && field.hasVelodromeMetadataOffset()) {
        writeOffset = field.getWriteMetadataOffset().toInt();
      } else {
        writeOffset = fieldRef.getId();
      }      
      int siteID = InstrDecisions.passSite() ? Site.getSite(inst) : 0;
      int read = (isRead) ? 1 : 0;
      Instruction postBarrierCall = Call.create4(CALL,
                                                  null,
                                                  IRTools.AC(postBarrierMethod.getOffset()),
                                                  MethodOperand.STATIC(postBarrierMethod),
                                                  base.copy(),
                                                  IRTools.IC(writeOffset),
                                                  IRTools.IC(siteID),
                                                  IRTools.IC(read));
      postBarrierCall.position = inst.position;
      postBarrierCall.bcIndex = inst.bcIndex;
      InstrInfoWrapper wrapper = new InstrInfoWrapper();
      wrapper.addInstrInfo(inst, postBarrierCall);
      instrumentedInstrs.add(wrapper);
    }
  }
  
  /** Insert instrumentation to unlock metadata after the actual access */
  private boolean testPostCall(Instruction postBarrier, Instruction inst, IR ir) {
    RVMMethod target = Call.getMethod(postBarrier).getTarget();
    int numParams = target.getParameterTypes().length;
    if (Call.getNumberOfParams(postBarrier) != numParams) {
      System.out.println(postBarrier);
      System.out.println(inst);
      VM.sysFail("Bad match");
      return false;
    }
    // If in LIR, the call needs to be lowered to LIR
    if (lateInstr) {
      ConvertToLowLevelIR.callHelper(postBarrier, ir);
    }
    return true;
  }
  
  void instrumentStaticAccess(Instruction inst, HashSetRVM<Instruction> callsToInline, IR ir, HashSetRVM<InstrInfoWrapper> instrumentedInstrs) {
    boolean isRead;
    LocationOperand loc;
    if (!lateInstr) {
      isRead = GetStatic.conforms(inst);
      loc = isRead ? GetStatic.getLocation(inst) : PutStatic.getLocation(inst);
    } else {
      isRead = Load.conforms(inst);
      loc = isRead ? Load.getLocation(inst) : Store.getLocation(inst);
      if (VM.VerifyAssertions) { VM._assert(loc.isFieldAccess()); }
      Operand tempRef = isRead ? Load.getAddress(inst) : Store.getAddress(inst);
      if (VM.VerifyAssertions) { VM._assert(tempRef.isIntConstant() && tempRef.asIntConstant().value == Magic.getTocPointer().toInt()); }
    }
    FieldReference fieldRef = loc.getFieldRef();
    RVMField field = fieldRef.peekResolvedField();
    boolean isResolved = (field != null);
    if (VM.VerifyAssertions && isResolved) { VM._assert(field.isStatic()); }

    int fieldInfo = 0;
    if (InstrDecisions.passFieldInfo()) {
      if (isResolved && InstrDecisions.useFieldOffset()) {
        fieldInfo = getFieldOffset(field);
      } else {
        fieldInfo = fieldRef.getId();
      }
    }

    NormalMethod barrierMethod = Octet.getClientAnalysis().chooseBarrier(ir.getMethod(), isRead, true, isResolved, true, inst.hasRedundantBarrier(), isSpecializedMethod(ir));
    Instruction barrierCall;
    barrierCall = Call.create1(CALL,
                                null,
                                IRTools.AC(barrierMethod.getOffset()),
                                MethodOperand.STATIC(barrierMethod),
                                IRTools.IC(fieldInfo)); // need to pass the field ID even if field info isn't needed,
                                                        // since it's needed to get the metadata offset    
    barrierCall.position = inst.position;
    barrierCall.bcIndex = inst.bcIndex;
    // Octet: LATER: try this
    /*
    if (!Octet.getClientAnalysis().barriersCanThrowExceptions()) {
      barrierCall.markAsNonPEI();
    }
     */
    finishParams(inst, fieldRef, barrierCall);
    insertBarrier(barrierCall, inst, isRead, null, field, isResolved, callsToInline, ir);
    
    // Velodrome: Inserting instrumentation to release metadata lock
    if (Velodrome.insertPostBarriers()) {
      NormalMethod postBarrierMethod = isResolved ? Entrypoints.velodromeUnlockMetadataForStaticResolvedMethod
                                                  : Entrypoints.velodromeUnlockMetadataForStaticUnresolvedMethod;
      Instruction postBarrierCall;
      int info = 0;
      int siteID = InstrDecisions.passSite() ? Site.getSite(inst) : 0;
      int read = (isRead) ? 1 : 0 ;
      if (isResolved) {
        int writeOffset = Velodrome.UNITIALIZED_OFFSET;;
        if (Velodrome.addPerFieldVelodromeMetadata()) {
          writeOffset = field.getWriteMetadataOffset().toInt();
        }
        if (InstrDecisions.passFieldInfo() && InstrDecisions.useFieldOffset()) {
          info = getFieldOffset(field);
        }
        postBarrierCall = Call.create4(CALL, 
                                        null, 
                                        IRTools.AC(postBarrierMethod.getOffset()), 
                                        MethodOperand.STATIC(postBarrierMethod), 
                                        IRTools.IC(writeOffset), 
                                        IRTools.IC(info),
                                        IRTools.IC(siteID),
                                        IRTools.IC(read));
      } else {
        info = fieldRef.getId();
        postBarrierCall = Call.create3(CALL, 
                                        null, 
                                        IRTools.AC(postBarrierMethod.getOffset()), 
                                        MethodOperand.STATIC(postBarrierMethod), 
                                        IRTools.IC(info),
                                        IRTools.IC(siteID),
                                        IRTools.IC(read));
      }
      postBarrierCall.position = inst.position;
      postBarrierCall.bcIndex = inst.bcIndex;
      InstrInfoWrapper wrapper = new InstrInfoWrapper();
      wrapper.addInstrInfo(inst, postBarrierCall);
      instrumentedInstrs.add(wrapper);
    }    
  }
  
  void instrumentArrayAccess(Instruction inst, HashSetRVM<Instruction> callsToInline, IR ir,
      HashSetRVM<InstrInfoWrapper> instrumentedInstrs) {
    boolean isRead = ALoad.conforms(inst);
    LocationOperand loc = isRead ? ALoad.getLocation(inst) : AStore.getLocation(inst);
    if (VM.VerifyAssertions) { VM._assert(loc.isArrayAccess()); }
    Operand ref = isRead ? ALoad.getArray(inst) : AStore.getArray(inst);
    Operand index = isRead ? ALoad.getIndex(inst) : AStore.getIndex(inst);

    NormalMethod barrierMethod = Octet.getClientAnalysis().chooseBarrier(ir.getMethod(), isRead, false, true, false, inst.hasRedundantBarrier(), isSpecializedMethod(ir));
    Instruction barrierCall = Call.create2(CALL,
                                            null,
                                            IRTools.AC(barrierMethod.getOffset()),
                                            MethodOperand.STATIC(barrierMethod),
                                            ref.copy(),
                                            index.copy());
    barrierCall.position = inst.position;
    barrierCall.bcIndex = inst.bcIndex;
    // Octet: LATER: try this
    /*
    if (!Octet.getClientAnalysis().barriersCanThrowExceptions()) {
      barrierCall.markAsNonPEI();
    }
     */
    
    // Add site id and array element size 
    finishParams(inst, null, barrierCall);
    insertBarrier(barrierCall, inst, isRead, ref, null, true, callsToInline, ir);
    
    // Velodrome: Inserting instrumentation to release metadata lock
    if (Velodrome.insertPostBarriers()) {
      Operand base = ref.copy();
      // First make a backup of the object reference
      if (isRead) { // Getfield
        // This code block assumes early assertion, this assertion helps in sanity check
        if (VM.VerifyAssertions) { VM._assert(!lateInstr); }
        if (GetField.getResult(inst).isRegister() && GetField.getRef(inst).isRegister()) { // LHS is a register operand
          if (GetField.getResult(inst).getRegister() == GetField.getRef(inst).asRegister().getRegister()) {
            // Register is same on LHS and RHS
            RegisterOperand t = ir.regpool.makeTempAddress();
            Instruction backupObjRef = Move.create(REF_MOVE, t, ref.copy());
            backupObjRef.position = inst.position;
            backupObjRef.bcIndex = inst.bcIndex;
            inst.insertBefore(backupObjRef);
            base = t;
          }
        }
      }

      NormalMethod postBarrierMethod = Entrypoints.velodromeArrayPostBarrierMethod; 
      int siteID = InstrDecisions.passSite() ? Site.getSite(inst) : 0;
      int read = (isRead) ? 1 : 0;
      Instruction postBarrierCall = Call.create4(CALL,
                                                  null,
                                                  IRTools.AC(postBarrierMethod.getOffset()),
                                                  MethodOperand.STATIC(postBarrierMethod),
                                                  base.copy(),
                                                  index.copy(),
                                                  IRTools.IC(siteID),
                                                  IRTools.IC(read));
      postBarrierCall.position = inst.position;
      postBarrierCall.bcIndex = inst.bcIndex;
      InstrInfoWrapper wrapper = new InstrInfoWrapper();
      wrapper.addInstrInfo(inst, postBarrierCall);
      instrumentedInstrs.add(wrapper);
    }
    
  }
  
}
