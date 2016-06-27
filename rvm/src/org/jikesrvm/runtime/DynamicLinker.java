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
package org.jikesrvm.runtime;

import static org.jikesrvm.ia32.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;

import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.ArchitectureSpecific.DynamicLinkerHelper;
import org.jikesrvm.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.Context;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.octet.Octet;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.DynamicBridge;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

// Octet: Static cloning: Various changes in this class to support multiple resolved methods for every method reference.

/**
 * Implement lazy compilation.
 */
@DynamicBridge
public class DynamicLinker implements Constants {

  /**
   * Resolve, compile if necessary, and invoke a method.
   * <pre>
   *  Taken:    nothing (calling context is implicit)
   *  Returned: does not return (method dispatch table is updated and method is executed)
   * </pre>
   */
  @Entrypoint
  static void lazyMethodInvoker() {
    DynamicLink dl = DL_Helper.resolveDynamicInvocation();
    MethodReference methodRef = dl.methodRef();
    
    Address callingFrame = Magic.getCallerFramePointer(Magic.getFramePointer());
    int callingCompiledMethodId = Magic.getCompiledMethodID(callingFrame);
    CompiledMethod ccm = CompiledMethods.getCompiledMethod(callingCompiledMethodId);
    
    // Velodrome: Context: Walk stack to find caller, note if it is application
    RVMThread currentThread = RVMThread.getCurrentThread();
    int resolvedContext = ccm.method.getStaticContext();
    if (Context.isApplicationPrefix(methodRef.getType())) {
      // I guess it is not necessary to do this for non-Octet threads, since they will probably not execute application 
      // code
      if (currentThread.isOctetThread()) {
        Address fp = Magic.getFramePointer();
        boolean atLeastOneAppMethod = false;
        // Search for the topmost application frame/method
        while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
          int compiledMethodId = Magic.getCompiledMethodID(fp);
          if (compiledMethodId != INVISIBLE_METHOD_ID) {
            CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);
            RVMMethod method = compiledMethod.getMethod();
            if (!method.isNative() && Octet.shouldInstrumentMethod(method)) {
              resolvedContext = method.getStaticContext();
              atLeastOneAppMethod = true;
              break;
            }
          }
          fp = Magic.getCallerFramePointer(fp);
        }
        if (!atLeastOneAppMethod) { // First application method called
          resolvedContext = Context.NONTRANS_CONTEXT; 
        }
      }
    }
    
    RVMMethod targMethod = DL_Helper.resolveMethodRef(dl, resolvedContext);
    
    // Velodrome: Context: This is bad, but "methodRef" can be a library type, while the targMethod is an application method
    if (currentThread.isOctetThread()) {
      if (Context.isApplicationPrefix(targMethod.getDeclaringClass().getTypeRef()) && 
          !Context.isApplicationPrefix(methodRef.getType())) {
        Address fp = Magic.getFramePointer();
        boolean atLeastOneAppMethod = false;
        // Search for the topmost application frame/method
        while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
          int compiledMethodId = Magic.getCompiledMethodID(fp);
          if (compiledMethodId != INVISIBLE_METHOD_ID) {
            CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);
            RVMMethod method = compiledMethod.getMethod();
            if (!method.isNative() && Octet.shouldInstrumentMethod(method)) {
              resolvedContext = method.getStaticContext();
              atLeastOneAppMethod = true;
              break;
            }
          }
          fp = Magic.getCallerFramePointer(fp);
        }
        if (!atLeastOneAppMethod) { // First application method called
          resolvedContext = Context.NONTRANS_CONTEXT; 
        }
      }
      targMethod = DL_Helper.resolveMethodRef(dl, resolvedContext); // Recompute 
    }

    DL_Helper.compileMethod(dl, targMethod);
    CodeArray code = targMethod.getCurrentEntryCodeArray();
    Magic.dynamicBridgeTo(code);                   // restore parameters and invoke
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);  // does not return here
  }

  /**
   * Report unimplemented native method error.
   * <pre>
   *  Taken:    nothing (calling context is implicit)
   *  Returned: does not return (throws UnsatisfiedLinkError)
   * </pre>
   */
  @Entrypoint
  static void unimplementedNativeMethod() {
    DynamicLink dl = DL_Helper.resolveDynamicInvocation();
    RVMMethod targMethod = DL_Helper.resolveMethodRef(dl, Context.VM_CONTEXT);
    throw new UnsatisfiedLinkError(targMethod.toString());
  }

  /**
   * Report a magic SysCall has been mistakenly invoked
   */
  @Entrypoint
  static void sysCallMethod() {
    DynamicLink dl = DL_Helper.resolveDynamicInvocation();
    RVMMethod targMethod = DL_Helper.resolveMethodRef(dl, Context.VM_CONTEXT);
    throw new UnsatisfiedLinkError(targMethod.toString() + " which is a SysCall");
  }

  /**
   * Helper class that does the real work of resolving method references
   * and compiling a lazy method invocation.  In separate class so
   * that it doesn't implement DynamicBridge magic.
   */
  private static class DL_Helper {

    /**
     * Discover method reference to be invoked via dynamic bridge.
     * <pre>
     * Taken:       nothing (call stack is examined to find invocation site)
     * Returned:    DynamicLink that describes call site.
     * </pre>
     */
    @NoInline
    static DynamicLink resolveDynamicInvocation() {

      // find call site
      //
      VM.disableGC();
      Address callingFrame = Magic.getCallerFramePointer(Magic.getFramePointer());
      Address returnAddress = Magic.getReturnAddressUnchecked(callingFrame);
      callingFrame = Magic.getCallerFramePointer(callingFrame);
      int callingCompiledMethodId = Magic.getCompiledMethodID(callingFrame);
      CompiledMethod callingCompiledMethod = CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      Offset callingInstructionOffset = callingCompiledMethod.getInstructionOffset(returnAddress);
      VM.enableGC();

      // obtain symbolic method reference
      //
      DynamicLink dynamicLink = new DynamicLink();
      callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);

      return dynamicLink;
    }

    /**
     * Resolve method ref into appropriate RVMMethod
     * <pre>
     * Taken:       DynamicLink that describes call site.
     * Returned:    RVMMethod that should be invoked.
     * </pre>
     */
    @NoInline
    static RVMMethod resolveMethodRef(DynamicLink dynamicLink, int context) {
      // resolve symbolic method reference into actual method
      //
      MethodReference methodRef = dynamicLink.methodRef();
      if (dynamicLink.isInvokeSpecial()) {
        return methodRef.resolveInvokeSpecial(context);
      } else if (dynamicLink.isInvokeStatic()) {
        return methodRef.resolve(context);
      } else {
        // invokevirtual or invokeinterface
        VM.disableGC();
        Object targetObject = DynamicLinkerHelper.getReceiverObject();
        VM.enableGC();
        RVMClass targetClass = Magic.getObjectType(targetObject).asClass();
        RVMMethod targetMethod = targetClass.findVirtualMethod(methodRef.getName(), methodRef.getDescriptor(), context);
        if (targetMethod == null) {
          throw new IncompatibleClassChangeError(targetClass.getDescriptor().classNameFromDescriptor());
        }
        return targetMethod;
      }
    }

    /**
     * Compile (if necessary) targetMethod and patch the appropriate disaptch tables
     * @param targetMethod the RVMMethod to compile (if not already compiled)
     */
    @NoInline
    static void compileMethod(DynamicLink dynamicLink, RVMMethod targetMethod) {

      RVMClass targetClass = targetMethod.getDeclaringClass();

      // if necessary, compile method
      //
      if (!targetMethod.isCompiled()) {
        targetMethod.compile();

        // If targetMethod is a virtual method, then eagerly patch tib of declaring class.
        // (we need to do this to get the method test used by opt to work with lazy compilation).
        if (!(targetMethod.isObjectInitializer() || targetMethod.isStatic())) {
          targetClass.updateTIBEntry(targetMethod);
        }
      }

      // patch appropriate dispatch table
      //
      if (targetMethod.isObjectInitializer() || targetMethod.isStatic()) {
        targetClass.updateJTOCEntry(targetMethod);
      } else if (dynamicLink.isInvokeSpecial()) {
        targetClass.updateTIBEntry(targetMethod);
      } else {
        VM.disableGC();
        Object targetObject = DynamicLinkerHelper.getReceiverObject();
        VM.enableGC();
        RVMClass recvClass = (RVMClass) Magic.getObjectType(targetObject);
        recvClass.updateTIBEntry(targetMethod);
      }
    }
  }
}
