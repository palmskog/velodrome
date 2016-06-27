package org.jikesrvm.velodrome;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.jikesrvm.Callbacks;
import org.jikesrvm.Callbacks.ExitMonitor;
import org.jikesrvm.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMember;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.mm.mminterface.Barriers;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.octet.Octet;
import org.jikesrvm.scheduler.SpinLock;
import org.jikesrvm.util.HashSetRVM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.OffsetArray;

/*
 * Velodrome: LATER: Check for 
 * 2) emit_aastore() does not currently have unlock metadata instrumentation. It can possibly done by 
 *    duplicating the contents of the SP.
 * 3) Instead of emitting a call to unlock metadata, we can emit the instructions directly in baseline and 
 *    opt compilers.
 * 6) Arrays are not supported for now, need to make changes in the compilers to support arrays.
 * 7) has velodrome offset check can be required for inheritance cases where the base class is the library. The 
 *    per-field metadata in that case will not be laid out, but we would want it for derived application classes
 * 10) Maintain the stack while recursing during dfs.
 * 13) Runtimefinal - Using a static reference to TIB object has an additional load from the JTOC. 
 *     However, Runtimefinal seems to work only for booleans.
 * 15) Post-barrier call instructions are currently not being inlined
 * 18) Test the FCFG graph.
 * 19) Plenty of cycles for the start node in each thread. And the same cycle is probably reported multiple times.
 * 20) Can we avoid cycle detection from transactions that are part of the dacapo harness?
 * 21) Use Raja benchmark, check REDCard paper.
 * 
 * */

@Uninterruptible
public final class Velodrome implements Constants {

  static BenchmarkInfo bench = null;

  // Sync these uses with DoubleChecker
  static final int OCTET_FINALIZER_THREAD_ID = 0;
  static final int DACAPO_DRIVER_THREAD_OCTET_ID = 1;
  static final int JAVA_GRANDE_DRIVER_THREAD_OCTET_ID = 1;
  static final int ELEVATOR_DRIVER_THREAD_OCTET_ID = 1;
  static final int TSP_DRIVER_THREAD_OCTET_ID = 1;
  
  public static final int START_TRANSACTION_ID = 1;
  
  public static final int UNITIALIZED_OFFSET = RVMMember.NO_OFFSET;
  
  private static final int NUM_METADATA_FIELDS = 2; // One for write, one for read (in order)
  public static final int LOG_FIELD_SIZE = LOG_BYTES_IN_WORD; // 2
  public static final int FIELD_SIZE = 1 << LOG_FIELD_SIZE; // 4
  
  // Velodrome: TODO: Check if using untraced works. Static fields would get added to the JTOC and would anyway
  // be treated as roots.
  @Untraced
  private static Transaction dummyTrans = new Transaction();
  public static Address tibForTransaction;
  @Untraced
  public static ReadHashMap dummyMap = null;
  public static Address tibForReadHashMap = null;

  public static final Atom transactionDescriptor = Atom.findOrCreateAsciiAtom("Lorg/jikesrvm/velodrome/Transaction");
  public static final Atom readMapDescriptor = Atom.findOrCreateAsciiAtom("Lorg/jikesrvm/velodrome/ReadHashMap;");

  /** String ends with a / (e.g., /home/biswass/) */
  public static String homePrefix = System.getProperty("user.home") + "/";
  public static String directoryName = homePrefix + "velodrome-output/"; 
  private static String directoryPrefix = homePrefix + "velodromeRvmRoot/velodrome/";
  public static final HashSetRVM<Atom> notTransactions = new HashSetRVM<Atom>();
  
  public static final Atom method = Atom.findOrCreateAsciiAtom("getNextToken");
  public static final Atom parent = Atom.findOrCreateAsciiAtom("execute");
  public static final Atom methodClass = Atom.findOrCreateAsciiAtom("Lorg/eclipse/jdt/internal/compiler/parser/Scanner;");
  public static final Atom parentClass = Atom.findOrCreateAsciiAtom("Lorg/apache/xalan/templates/ElemLiteralResult;");
  
  /** This array can only grow, it contains offsets of all interesting metadata references. 
   *  Protect accesses to this array with the associated {@code jtocMetadataReferencesLock} lock */
  public static OffsetArray jtocMetadataReferences;
  private static int currentNumSlotsInJtocReferences = 1 << 10;
  private static final int LOG_GROWTH_FACTOR = 1; // 2X
  public static int jtocMetadataReferencesIndex = 0; 
  private static final SpinLock jtocMetadataReferencesLock = new SpinLock();
  
  @Pure
  @Inline
  public static boolean needsSites() {
    return Octet.getConfig().needsSites();
  }
  
  @Pure
  @Inline
  public static boolean methodsAsTransactions() {
    return Octet.getConfig().methodsAsTransactions();
  }
  
  @Pure
  @Inline
  public static boolean syncBlocksAsTransactions() {
    return Octet.getConfig().syncBlocksAsTransactions();
  }
  
  @Pure
  @Inline
  public static boolean createCrossThreadEdges() {
    return Octet.getConfig().createCrossThreadEdges();
  }
  
  @Pure
  @Inline
  public static boolean invokeCycleDetection() {
    return Octet.getConfig().invokeCycleDetection();
  }
  
  @Pure
  @Inline
  public static boolean recordVelodromeStats() {
    return Octet.getConfig().recordVelodromeStats();
  }
  
  @Pure
  @Inline
  public static boolean trackSynchronizationPrimitives() {
    return Octet.getConfig().trackSynchronizationPrimitives();
  }
  
  @Pure
  @Inline
  public static boolean instrumentArrays() {
    return Octet.getConfig().instrumentArrays();
  }

  @Pure
  @Inline
  public static boolean trackThreadSynchronizationPrimitives() {
    return Octet.getConfig().trackThreadSynchronizationPrimitives();
  }
  
  @Pure
  @Inline
  public boolean insertStartEndTransactionBarriers() {
    return Octet.getConfig().insertStartEndTransactionBarriers();
  }
  
  @Pure
  @Inline
  public static boolean inlineStartEndTransactions() {
    return Octet.getConfig().inlineStartEndTransactions();
  }
  
  @Pure
  @Inline
  public static boolean insertBarriers() {
    return Octet.getConfig().insertBarriers();
  }
  
  @Pure
  @Inline
  public static boolean insertPostBarriers() {
    return Octet.getConfig().insertPostBarriers();
  }
  
  @Pure
  @Inline
  public static boolean addPerFieldVelodromeMetadata() {
    return Octet.getConfig().addPerFieldVelodromeMetadata();
  }
  
  @Pure
  @Inline
  public static boolean trackLastAccess() {
    return Octet.getConfig().trackLastAccess();
  }
  
  @Pure
  @Inline
  public static boolean addMiscHeader() {
    return Octet.getConfig().addMiscHeader();
  }
  
  @Pure
  @Inline
  public static boolean isVelodromeEnabled() {
    return Octet.getConfig().isVelodromeEnabled();
  }
  
  @Pure
  @Inline
  public static boolean isPerformanceRun() {
    return Octet.getConfig().isPerformanceRun();
  }
  
  /** Used only for debugging at start and end transaction instrumentation. */
  @Pure
  @Inline
  public static boolean checkStartTransactionInstrumentation() {
    return Octet.getConfig().checkStartTransactionInstrumentation();
  }
  
  /** Used only for debugging static context of methods at every prolog. */
  @Pure
  @Inline
  public static boolean checkMethodContextAtProlog() {
    return Octet.getConfig().checkMethodContextAtProlog();
  }
  
  static {
    
    if (Velodrome.addPerFieldVelodromeMetadata()) {
      jtocMetadataReferences = OffsetArray.create(currentNumSlotsInJtocReferences);
    }
    
    Callbacks.addExitMonitor(new ExitMonitor() {
      public void notifyExit(int value) {  
        if (Velodrome.recordVelodromeStats()) {
          printVelodromeStats();
        }
      }
    });
    
  }
  
  protected static void printVelodromeStats() {
    VM.sysWriteln("/***************************************************************/");
    VelodromeStats.numTransactions.report();
    VelodromeStats.numRegularTransactions.report();
    VelodromeStats.numUnaryTransactions.report();
    VelodromeStats.numCrossThreadEdges.report();
    VelodromeStats.numAccessesTotal.report();
    VelodromeStats.numAccessesTracked.report();
    if (VM.VerifyAssertions) { VM._assert(VelodromeStats.numAccessesAvoidedPre.report() == VelodromeStats.numAccessesAvoidedPost.report()); }
    VM.sysWriteln("/***************************************************************/");
  }
  
  /** Perform activities that are supposed to be carried out during boot time. 
   * 1) Create a reference to Transaction tib
   * 2) Read the set of methods that are not to be treated as transactions
   */
  @Interruptible
  public static void boot() {
    tibForTransaction = ObjectReference.fromObject(dummyTrans).toAddress().loadAddress(JavaHeader.getTibOffset());
    dummyTrans = null;
    
    dummyMap = ReadHashMap.newReadHashMap();
    Velodrome.tibForReadHashMap = ObjectReference.fromObject(Velodrome.dummyMap).toAddress().loadAddress(JavaHeader.getTibOffset());
    dummyMap = null;
    
    bench = new BenchmarkInfo();

    // Prepare the hash set of methods
    if (VM.VerifyAssertions) { VM._assert(methodsAsTransactions()); }
    String prefix = directoryPrefix;
    if (isPerformanceRun()) { // Performance run, or imprecise analysis in multi-run mode
      prefix += "atomicity-specifications/";
    } else {
      prefix += "exclusion-list/";
    }
    readGenericFile(prefix + "methodNames.txt");
    if (!bench.isMicroBenchmark()) {
      readIndividualFile(prefix);
    }
  }
  
  /** This is for all benchmarks excepting DaCapo */
  @Interruptible
  private static void readGenericFile(String path) {
    readFile(new File(path));
  }
  
  /** This method is to read and populate the set of non-transactions. Current invoked only for DaCapo benchmarks. */
  @Interruptible
  static void readIndividualFile(String path) {
    if (VM.VerifyAssertions) { VM._assert(methodsAsTransactions()); }
    String name = "";
    if (bench.getId() == BenchmarkInfo.ELEVATOR) { name = "elevator.txt"; }
    else if (bench.getId() == BenchmarkInfo.PHILO) { name = "philo.txt"; }
    else if (bench.getId() == BenchmarkInfo.SOR) { name = "sor.txt"; }
    else if (bench.getId() == BenchmarkInfo.TSP) { name = "tsp.txt"; }
    else if (bench.getId() == BenchmarkInfo.HEDC) { name = "hedc.txt"; }
    else if (bench.getId() == BenchmarkInfo.MOLDYN) { name = "moldyn.txt"; }
    else if (bench.getId() == BenchmarkInfo.MONTECARLO) { name = "montecarlo.txt"; }
    else if (bench.getId() == BenchmarkInfo.RAYTRACER) { name = "raytracer.txt"; }
    else if (bench.getId() == BenchmarkInfo.HSQLDB6) { name = "hsqldb6.txt"; }
    else if (bench.getId() == BenchmarkInfo.ECLIPSE6) { name = "eclipse6.txt"; }
    else if (bench.getId() == BenchmarkInfo.XALAN6) { name = "xalan6.txt"; }
    else if (bench.getId() == BenchmarkInfo.LUSEARCH6) { name = "lusearch6.txt"; }
    else if (bench.getId() == BenchmarkInfo.AVRORA9) { name = "avrora9.txt"; }
    else if (bench.getId() == BenchmarkInfo.PMD9) { name = "pmd9.txt"; }
    else if (bench.getId() == BenchmarkInfo.LUINDEX9) { name = "luindex9.txt"; }
    else if (bench.getId() == BenchmarkInfo.LUSEARCH9_FIXED) { name = "lusearch9.txt"; }
    else if (bench.getId() == BenchmarkInfo.XALAN9) { name = "xalan9.txt"; }
    else if (bench.getId() == BenchmarkInfo.SUNFLOW9) { name = "sunflow9.txt"; }
    else if (bench.getId() == BenchmarkInfo.JYTHON9) { name = "jython9.txt"; }
    else if (bench.getId() == BenchmarkInfo.RAJA) { name = "raja.txt"; }
    else { if (VM.VerifyAssertions) { VM._assert(NOT_REACHED);} }
    readFile(new File(path + name));
  }

  /** Read method names from the given {@code file}. */
  @Interruptible
  private static void readFile(File file) {
    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      String line = null;
      try {
        while ((line = reader.readLine()) != null) {      
          if (line.length() == 0 || line.charAt(0) == '#' /*Indicates a comment in the file, ignore*/) { 
            continue;
          }
          String newline = line.substring(0, line.lastIndexOf(":")); // Stripping line and bci information
          notTransactions.add(Atom.findOrCreateAsciiAtom(newline));
        } 
      } catch(IndexOutOfBoundsException e) {
        VM.sysWriteln("Possibly wrong method name format in exclusion file");
        VM.sysWriteln("String:", line);
        VM.sysFail("Possibly wrong method name format in exclusion file");
      } finally {
        reader.close(); // Done with reading the file
      }
    } catch(IOException e) {
      VM.sysWrite("Cannot read atomicity specification from file ");
      VM.sysWriteln(file.getAbsolutePath());
      VM.sysFail("Exiting");
    }
  }

  @Interruptible
  public static Atom constructMethodSignature(RVMMethod method) {
    if (VM.VerifyAssertions) { VM._assert(method != null); }
    if (VM.VerifyAssertions) { VM._assert(method.getDeclaringClass() != null); }
    String str = method.getDeclaringClass().getDescriptor().toString() + "." +
        method.getName().toString() + " " + method.getDescriptor().toString();
    Atom at = Atom.findOrCreateAsciiAtom(str);
    return at;
  }
  
  @Interruptible
  public static Atom constructMethodSignature(MethodReference methodRef) {
    if (VM.VerifyAssertions) { VM._assert(methodRef != null); }
    String str = methodRef.getType().getName().toString() + "." +
        methodRef.getName().toString() + " " + methodRef.getDescriptor().toString();
    Atom at = Atom.findOrCreateAsciiAtom(str);
    return at;
  }  
  
  @Interruptible
  public static Atom constructMethodSignature(MethodOperand methOp) {
    if (VM.VerifyAssertions) { VM._assert(methOp != null); }
    TypeReference tRef = methOp.getMemberRef().getType();
    String str = tRef.getName().toString() + "." + methOp.getMemberRef().getName().toString() + 
        " " + methOp.getMemberRef().getDescriptor().toString();
    Atom at = Atom.findOrCreateAsciiAtom(str);
    return at;
  }
  
  /** Check whether the method referenced by <code>methOp</code> is an RVM method, if yes return true. 
   *  This method does not check MMTk prefixes. */
  @Interruptible
  public static boolean isRVMMethod(NormalMethod method, MethodOperand methOp) {
    if (VM.VerifyAssertions) { VM._assert(method != null); }
    if (VM.VerifyAssertions) { VM._assert(methOp != null); }
    TypeReference tRef = methOp.getMemberRef().getType();
    String str = tRef.getName().toString();
    // Velodrome: LATER: Improve this, make this generic
    return (str.indexOf("Lorg/jikesrvm/") >= 0);
  }

  /** Check whether the method referenced by <code>methOp</code> needs to be instrumented, or not */
  @Interruptible
  public static boolean instrumentCalleeMethod(NormalMethod method, MethodOperand methOp) {
    if (VM.VerifyAssertions) { Octet.shouldInstrumentMethod((RVMMethod)method); }
    if (VM.VerifyAssertions) { VM._assert(methOp != null); }
    TypeReference tRef = methOp.getMemberRef().getType();
    return Octet.shouldInstrumentClass(tRef);
  }
  
  /** Instrument a static field? The parameter is the parent class of the static field. */
  @Inline @Pure
  public static boolean shouldAddVelodromeMetadataForStaticField(FieldReference fieldRef) {
    return Octet.shouldAddMetadataForField(fieldRef);
  }
  
  public static boolean shouldAddPerFieldVelodromeMetadata(RVMField field) {
    if (field.isFinal()) {
      return false;
    }
    return Octet.shouldInstrumentClass(field.getMemberRef().asFieldReference().getType());
  }

  @Inline
  public static int getNumFields(RVMField field) {
    return NUM_METADATA_FIELDS;
  }
  
  /** Determine whether generational write barriers are required for stores. */
  @Inline
  public final static boolean useGenerationalBarriers() {
    return Barriers.NEEDS_OBJECT_PUTFIELD_BARRIER;
  }
  
  /** 
   * @param writeOff Write offset of the metadata reference in the JTOC. This should be negative, since we don't want 
   *                 GC to trace the metadata slots.
   * @param readOff Read offset of the metadata reference in the JTOC. This should be negative, since we don't want
   *                 GC to trace the metadata slots.
   */
  public static void addStaticOffsets(Offset writeOff, Offset readOff) {
    if (VM.VerifyAssertions) { VM._assert(writeOff.sLT(Offset.zero()) && readOff.sLT(Offset.zero())); }
    jtocMetadataReferencesLock.lock();
    if (jtocMetadataReferencesIndex == currentNumSlotsInJtocReferences) { // Array is full
      growOffsetArray(currentNumSlotsInJtocReferences << LOG_GROWTH_FACTOR);
    }
    jtocMetadataReferences.set(jtocMetadataReferencesIndex++, writeOff);
    jtocMetadataReferences.set(jtocMetadataReferencesIndex++, readOff);
    if (VM.VerifyAssertions) { VM._assert(jtocMetadataReferencesIndex % 2 == 0); }
    jtocMetadataReferencesLock.unlock();
  }

  private static void growOffsetArray(int newSize) {
    OffsetArray newArray = createNewOffsetArray(newSize);
    int i = 0;
    Offset value;
    for ( ; i < currentNumSlotsInJtocReferences; i++) {
       value = jtocMetadataReferences.get(i);
       if (VM.VerifyAssertions) { VM._assert(value.sLT(Offset.zero()) ); }
       newArray.set(i, value);
    }
    jtocMetadataReferences = newArray;
    currentNumSlotsInJtocReferences = newSize;
  }
  
  @UninterruptibleNoWarn
  private static OffsetArray createNewOffsetArray(int size) {
    MemoryManager.startAllocatingInUninterruptibleCode();
    OffsetArray newArray = OffsetArray.create(size); 
    MemoryManager.stopAllocatingInUninterruptibleCode();
    return newArray;
  }
  
  // Velodrome offset helper methods. The reference metadata for fields are stored in a way that
  // the read slot comes after the write slot. The reference metadata for statics are stored in primitive slots
  // in the JTOC, i.e., at negative indices. However, there is no guarantee on the ordering of the read 
  // and write slots.
  
  @Inline
  static int getReadOffsetForField(int writeOffset) {
    return (writeOffset + BYTES_IN_ADDRESS);
  }
  
  @Inline
  static Offset getReadOffsetForField(Offset writeOffset) {
    return writeOffset.plus(BYTES_IN_ADDRESS);
  }
  
}