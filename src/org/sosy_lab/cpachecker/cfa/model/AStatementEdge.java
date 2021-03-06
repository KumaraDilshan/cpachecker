/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cfa.model;

import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import com.google.common.base.Optional;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;


public class AStatementEdge extends AbstractCFAEdge {

  private static final long serialVersionUID = 2639832981364107114L;
  protected final AStatement statement;

  protected AStatementEdge(String pRawStatement, AStatement pStatement,
      FileLocation pFileLocation, CFANode pPredecessor, CFANode pSuccessor) {

    super(pRawStatement, pFileLocation, pPredecessor, pSuccessor);
    statement = pStatement;
  }

  @Override
  public CFAEdgeType getEdgeType() {
    return CFAEdgeType.StatementEdge;
  }

  public AStatement getStatement() {
    return statement;
  }

  @Override
  public Optional<? extends AStatement> getRawAST() {
    return Optional.of(statement);
  }

  @Override
  public String getCode() {
    return statement.toASTString();
  }

}
