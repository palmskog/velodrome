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
package org.jikesrvm.objectmodel;

import org.jikesrvm.Constants;
import org.jikesrvm.mm.mminterface.MemoryManagerConstants;
import org.jikesrvm.octet.Octet;
import org.jikesrvm.velodrome.Velodrome;

/**
 * Defines other header words not used for
 * core Java language support of memory allocation.
 * Typically these are extra header words used for various
 * kinds of instrumentation or profiling.
 *
 * @see ObjectModel
 */
public interface MiscHeaderConstants extends Constants {

  /*********************
   * Support for GC Tracing; uses either 0 or 3 words of MISC HEADER
   */

  /* amount by which tracing causes headers to grow */ int GC_TRACING_HEADER_WORDS =
      (MemoryManagerConstants.GENERATE_GC_TRACE ? 3 : 0);
  int GC_TRACING_HEADER_BYTES = GC_TRACING_HEADER_WORDS << LOG_BYTES_IN_ADDRESS;

  /** Octet: number of extra header bytes; also used below to calculate NUM_BYTES_HEADER */
  int OCTET_HEADER_BYTES = Octet.getConfig().addHeaderWord() ? BYTES_IN_WORD : 0;
  
  /** Velodrome: Add an extra word to keep track of metadata for locks */
  int VELODROME_HEADER_BYTES = Octet.getConfig().addMiscHeader() ? BYTES_IN_WORD : 0;
  
  /** Velodrome: Adding two extra words to track read/write accesses for arrays */
  int VELODROME_WRITE_METADATA_BYTES = Velodrome.instrumentArrays() ? BYTES_IN_WORD : 0;
  int VELODROME_READ_METADATA_BYTES = Velodrome.instrumentArrays() ? BYTES_IN_WORD : 0;  

  /**
   * How many bytes are used by all misc header fields?
   */
  // Velodrome: Included count for header bytes
  int NUM_BYTES_HEADER = GC_TRACING_HEADER_BYTES + OCTET_HEADER_BYTES + VELODROME_HEADER_BYTES + 
                          VELODROME_WRITE_METADATA_BYTES + VELODROME_READ_METADATA_BYTES; // + YYY_HEADER_BYTES;

}