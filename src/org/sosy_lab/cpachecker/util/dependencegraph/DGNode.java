/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.dependencegraph;

import com.google.common.collect.ImmutableSet;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;

/** Node of a dependence graph. Represents a {@link CFAEdge}. */
public class DGNode implements Serializable {

  private static final long serialVersionUID = -3772112168117340401L;

  private CFAEdge cfaEdge;

  private Set<DGEdge> outgoingEdges = new HashSet<>();
  private Set<DGEdge> incomingEdges = new HashSet<>();

  public DGNode(final CFAEdge pCfaEdge) {
    cfaEdge = pCfaEdge;
  }

  public Set<DGEdge> getOutgoingEdges() {
    return ImmutableSet.copyOf(outgoingEdges);
  }

  public Set<DGEdge> getIncomingEdges() {
    return ImmutableSet.copyOf(incomingEdges);
  }

  public void addIncomingEdge(final DGEdge pIncomingEdge) {
    incomingEdges.add(pIncomingEdge);
  }

  public void addOutgoingEdge(final DGEdge pOutgoingEdge) {
    outgoingEdges.add(pOutgoingEdge);
  }

  public CFAEdge getCfaEdge() {
    return cfaEdge;
  }

  public boolean representsSameCfaEdge(final DGNode pO) {
    return cfaEdge.equals(pO.cfaEdge);
  }

  @Override
  public boolean equals(Object pO) {
    if (this == pO) {
      return true;
    }
    if (pO == null || getClass() != pO.getClass()) {
      return false;
    }
    DGNode dgNode = (DGNode) pO;
    return cfaEdge.equals(dgNode.cfaEdge);
  }

  @Override
  public int hashCode() {
    return Objects.hash(cfaEdge);
  }

  @Override
  public String toString() {
    return cfaEdge.toString();
  }
}
