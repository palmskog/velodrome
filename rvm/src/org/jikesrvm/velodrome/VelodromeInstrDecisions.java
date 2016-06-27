package org.jikesrvm.velodrome;

import org.jikesrvm.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.runtime.Entrypoints;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class VelodromeInstrDecisions implements Constants {

  protected static NormalMethod chooseVelodromeBarrier(boolean isRead, boolean isField, boolean isResolved, boolean isStatic) {
    if (isField) { // scalar fields and statics
      if (isRead) {
        if (isResolved) {
          if (isStatic) {
            return Entrypoints.velodromeFieldReadBarrierStaticResolvedMethod;
          } else {
            return Entrypoints.velodromeFieldReadBarrierResolvedMethod;
          }
        } else {
          if (isStatic) {
            return Entrypoints.velodromeFieldReadBarrierStaticUnresolvedMethod;
          } else {
            return Entrypoints.velodromeFieldReadBarrierUnresolvedMethod;
          }          
        }
      } else {
        if (isResolved) {
          if (isStatic) {
            return Entrypoints.velodromeFieldWriteBarrierStaticResolvedMethod;
          } else {
            return Entrypoints.velodromeFieldWriteBarrierResolvedMethod;
          }
        } else {
          if (isStatic) {
            return Entrypoints.velodromeFieldWriteBarrierStaticUnresolvedMethod;
          } else {
            return Entrypoints.velodromeFieldWriteBarrierUnresolvedMethod;
          }
        }
      }
    } else { // Arrays
      if (VM.VerifyAssertions) { VM._assert(isResolved); } // Array accesses can't be unresolved 
      if (isRead) {
        return Entrypoints.velodromeArrayReadBarrierMethod;        
      } else {
        return Entrypoints.velodromeArrayWriteBarrierMethod;
      }
    }
  }  
  
  public static final boolean staticFieldHasVelodromeMetadata(RVMField field) {
    if (Velodrome.addPerFieldVelodromeMetadata()) {
      boolean hasMetadata = field.hasVelodromeMetadataOffset();
      // at least for now, we expect the metadata to provide the same result as for an unresolved field,
      // except that the metadata should also be avoiding final fields
      if (VM.VerifyAssertions) { VM._assert(hasMetadata == (staticFieldMightHaveVelodromeMetadata(field.getMemberRef().asFieldReference()) && !field.isFinal())); }
      return hasMetadata;
    } 
    return false;
  }

  public static boolean staticFieldMightHaveVelodromeMetadata(FieldReference fieldRef) {
    if (Velodrome.addPerFieldVelodromeMetadata()) {
      return Velodrome.shouldAddVelodromeMetadataForStaticField(fieldRef);
    } else {
      return false;
    }
  }
  
  @Inline
  public static boolean objectOrFieldHasVelodromeMetadata(RVMField field) {
    return !field.isFinal() && objectOrFieldMightHaveVelodromeMetadata(field.getMemberRef().asFieldReference()); 
  }

	@Inline
  public static boolean objectOrFieldMightHaveVelodromeMetadata(FieldReference asFieldReference) {
    return Velodrome.addPerFieldVelodromeMetadata();
  }

}
