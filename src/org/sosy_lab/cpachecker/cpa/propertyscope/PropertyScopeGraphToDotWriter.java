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
package org.sosy_lab.cpachecker.cpa.propertyscope;

import org.sosy_lab.cpachecker.cfa.export.DOTBuilder;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.ShadowCFAEdgeFactory.ShadowCFANode;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.propertyscope.PropertyScopeGraph.ScopeEdge;
import org.sosy_lab.cpachecker.cpa.propertyscope.PropertyScopeGraph.ScopeNode;
import org.sosy_lab.cpachecker.cpa.propertyscope.ScopeLocation.Reason;
import org.sosy_lab.cpachecker.util.AbstractStates;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PropertyScopeGraphToDotWriter {

  private final PropertyScopeGraph graph;

  public PropertyScopeGraphToDotWriter(PropertyScopeGraph pGraph) {
    graph = pGraph;
  }

  public static void write(PropertyScopeGraph graph, Appendable sb) throws IOException {
    new PropertyScopeGraphToDotWriter(graph).write(sb);
  }

  public void write(Appendable sb) throws IOException {
    sb // preface
        .append("digraph \"PSARG_")
        .append(graph.getScopeReasons().stream()
            .map(Reason::name).collect(Collectors.joining("_")))
        .append("\" {\n")
        .append("node [style=\"filled\" shape=\"box\" color=\"white\"  ")
        //.append("width=0 height=0 margin=0")
        .append("]\n");

    // specify nodes

    for (ScopeNode scopeNode : graph.getNodes().values()) {
      sb.append(scopeNode.getId()).append(" [");
      bulidNodeParams(scopeNode, sb);
      sb.append("]");

      sb.append(";\n");
    }

    // specify edges
    for (ScopeEdge scopeEdge : graph.getEdges().values()) {
      sb
          .append(scopeEdge.getStart().getId()).append(" -> ").append(scopeEdge.getEnd().getId())
          .append(" [");

      buildEdgeParams(scopeEdge, sb);

      sb
          .append("]")
          .append(";\n");
    }

    // end of graph
    sb.append("}\n");


  }

  private void bulidNodeParams(ScopeNode scopeNode, Appendable sb) throws IOException {
    sb.append("label=\"");
    ARGState currentElement = scopeNode.getArgState();
    sb.append(Objects.toString(currentElement.getStateId()));

    Iterable<CFANode> locs = AbstractStates.extractLocations(currentElement);
    if (locs != null) {
      for (CFANode loc : AbstractStates.extractLocations(currentElement)) {
        sb.append("@");
        sb.append(loc.toString());
        sb.append(" r ");
        sb.append(Objects.toString(loc.getReversePostorderId()));
        if (loc instanceof ShadowCFANode) {
          sb.append(" ~ weaved ");
        }
        sb.append("\\n");
        sb.append(loc.getFunctionName());
        if (loc instanceof FunctionEntryNode) {
          sb.append(" entry");
        } else if (loc instanceof FunctionExitNode) {
          sb.append(" exit");
        }
        sb.append("\\n");
      }
    } else {
      sb.append("\\n");
    }

    for (Reason reason : scopeNode.getScopeReasons()) {
      sb.append(reason.name()).append("\\n");
    }

    sb.append("\"");

    if(scopeNode.isPartOfScope()) {
      sb.append(",color=\"cornflowerblue\"");

    } else {
      sb.append(",color=\"grey\"");
    }
  }

  private void buildEdgeParams(ScopeEdge scopeEdge, Appendable sb) throws IOException {
    if (scopeEdge.getIrrelevantARGStates() == 0) {
      List<CFAEdge> edges =
          scopeEdge.getStart().getArgState().getEdgesToChild(scopeEdge.getEnd().getArgState());

      if (edges.isEmpty()) { // there is no direct edge between the nodes, use a dummy-edge
        sb.append("style=\"bold\" color=\"blue\" label=\"dummy edge\"");

      } else { // edge exists, use info from edge

        boolean hasWeavedTrans = false;
        for (CFAEdge edge : edges) {
          if (edge.getPredecessor() instanceof ShadowCFANode) {
            hasWeavedTrans = true;
            break;
          }
        }

        if (hasWeavedTrans) {
          sb.append("color=\"green\" ");
        }

        sb.append("label=\"");
        if (edges.size() > 1) {
          sb
              .append("Lines ")
              .append(Objects.toString(edges.get(0).getLineNumber()))
              .append(" - ")
              .append(Objects.toString(edges.get(edges.size() - 1).getLineNumber()));
        } else {
          sb.append("Line ").append(Objects.toString(edges.get(0).getLineNumber()));
        }
        sb.append(": \\l");

        for (CFAEdge edge : edges) {
          sb.append(edge.getDescription().replaceAll("\n", " ").replace('"', '\''));
          sb.append("\\l");
        }
        sb.append("\"");
      }
    } else {
      sb
          .append("label=\"")
          .append("Irrelevant: ")
          .append(Objects.toString(scopeEdge.getIrrelevantARGStates()))
          .append("\\l");

      if (!scopeEdge.getPassedFunctions().isEmpty()) {
        sb.append("\\l");
        for (String func : scopeEdge.getPassedFunctions()) {
          sb.append(func).append("\\l");
        }
      }
      sb.append("\",");
      sb.append("color=\"#55007f\",fontcolor=\"#55007f\"");
    }

  }
}
