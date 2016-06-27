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
package org.mmtk.harness.lang.ast;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;
import org.mmtk.harness.lang.type.Type;

/**
 * A literal type name, used as an argument to (e.g.) the alloc function.
 */
public class TypeLiteral extends AbstractAST implements Expression {

  /** The type of the literal */
  private final Type type;

  public TypeLiteral(Token t, Type type) {
    super(t);
    this.type = type;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public Type getType() {
    return type;
  }
}
