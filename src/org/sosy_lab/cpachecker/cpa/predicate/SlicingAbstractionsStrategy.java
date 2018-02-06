/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState.getPredicateState;
import static org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState.mkAbstractionState;
import static org.sosy_lab.cpachecker.cpa.predicate.SlicingAbstractionsUtils.buildPathFormula;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGLogger;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.SLARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.predicates.AbstractionFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions;
import org.sosy_lab.java_smt.api.SolverException;

/**
 * This is an implementation of the Slicing Abstractions idea as RefinementStrategy like in the
 * papers: "Slicing Abstractions" (doi:10.1007/978-3-540-75698-9_2) "Splitting via Interpolants"
 * (doi:10.1007/978-3-642-27940-9_13)
 */
@Options(prefix = "cpa.predicate.slicingabstractions")
public class SlicingAbstractionsStrategy extends RefinementStrategy implements StatisticsProvider {

  private class Stats implements Statistics {

    private final Timer coverTime = new Timer();
    private final Timer argUpdate = new Timer();
    private final Timer copyEdges = new Timer();
    private final Timer sliceEdges = new Timer();
    private final Timer calcReached = new Timer();

    @Override
    public String getName() {
      return "Slicing Abstractions";
    }

    @Override
    public void printStatistics(PrintStream out, Result pResult, UnmodifiableReachedSet pReached) {
      out.println("  Computing abstraction of itp:       " + impact.abstractionTime);
      out.println("  Checking whether itp is new:        " + impact.itpCheckTime);
      out.println("  Coverage checks:                    " + coverTime);
      out.println("  ARG update:                         " + argUpdate);
      out.println("    Copy edges:                       " + copyEdges);
      out.println("    Slice edges:                      " + sliceEdges);
      out.println("    Recalculate ReachedSet:           " + calcReached);
      out.println();
      out.println("Number of abstractions during refinements:  " + impact.abstractionTime.getNumberOfIntervals());

      SlicingAbstractionsStrategy.this.printStatistics(out);
    }
  }

  @Option(secure=true, name="minimalslicing",
      description="Only slices the minimal amount of edges to guarantuee progress")
  private boolean minimalSlicing = false;

  @Option(secure=true, name="optimizeslicing",
      description="Reduces the amount of solver calls by directely slicing some edges" +
                  "that are mathematically proven to be infeasible in any case")
  private boolean optimizeSlicing = true;

  @Option(
    secure = true,
    description = "Whether to remove parts fo the ARG from which no target state is reachable"
  )
  private boolean removeSafeRegions = true;

  @Option(
    secure = true,
    description = "Whether to perform dynamic block encoding as part of each refinement iteration"
  )
  private boolean dynamicBlockEncoding = false;

  private final Stats stats = new Stats();

  private final BooleanFormulaManagerView bfmgr;
  private final PredicateAbstractionManager predAbsMgr;
  private final ImpactUtility impact;
  private final PathFormulaManager pfmgr;
  private final Solver solver;
  private final ARGLogger argLogger;

  // During the refinement of a single path,
  // a reference to the abstraction of the last state we have seen
  // (we sometimes needs this to refer to the previous block).
  private AbstractionFormula lastAbstraction = null;
  // There might be edges in the first refinement that are already infeasible.
  // This boolean is for keeping track of whether these were already sliced:
  private boolean initialSliceDone = false;
  // As long as the abstraction states form a tree, states do not need to be split.
  // This boolean is for keeping track of when this shortcut is allowed:
  private Boolean mayShortcutSlicing = null;

  private HashMap<ARGState,ARGState> forkedStateMap;

  public SlicingAbstractionsStrategy(final Configuration config, final Solver pSolver,
      final PredicateAbstractionManager pPredAbsMgr,
      final PathFormulaManager pPathFormulaManager) throws InvalidConfigurationException {
    super(pSolver);

    bfmgr = pSolver.getFormulaManager().getBooleanFormulaManager();
    predAbsMgr = pPredAbsMgr;
    impact = new ImpactUtility(config, pSolver.getFormulaManager(), pPredAbsMgr);
    pfmgr = pPathFormulaManager;
    solver = pSolver;
    argLogger = new ARGLogger(config);

    config.inject(this);
  }

  @Override
  protected void startRefinementOfPath() {
    checkState(lastAbstraction == null);
    lastAbstraction = predAbsMgr.makeTrueAbstractionFormula(null);
    forkedStateMap = new HashMap<>();
    mayShortcutSlicing = true;
  }

  /**
   * For each interpolant, we strengthen the corresponding state by
   * conjunctively adding the interpolant to its state formula.
   * This is all implemented in
   * {@link ImpactUtility#strengthenStateWithInterpolant(BooleanFormula, ARGState, AbstractionFormula)}.
   */
  @Override
  protected boolean performRefinementForState(BooleanFormula itp,
      ARGState s) throws SolverException, InterruptedException {
    checkArgument(!bfmgr.isTrue(itp));
    checkArgument(!bfmgr.isFalse(itp));
    checkState(forkedStateMap != null);

    PredicateAbstractState original = getPredicateState(s);
    PredicateAbstractState copiedPredicateState = mkAbstractionState(
        original.getPathFormula(),
        original.getAbstractionFormula(),
        original.getAbstractionLocationsOnPath()
    );
    boolean stateChanged = impact.strengthenStateWithInterpolant(
                                                       itp, s, lastAbstraction);
    // we only split if the state has actually changed
    ARGState newState;
    if (stateChanged
        && (!optimizeSlicing || !mayShortcutSlicing
            || SlicingAbstractionsUtils.calculateIncomingSegments(s).keySet().size() > 1)) {
      mayShortcutSlicing = false;
      //splitting the state:
      newState = s.forkWithReplacements(Collections.singleton(copiedPredicateState));
      forkedStateMap.put(s,newState);

      //Now we strengthen the splitted state with negated interpolant:
      BooleanFormula negatedItp = bfmgr.not(itp);
      impact.strengthenStateWithInterpolant(negatedItp,newState,lastAbstraction);
    }

    // Get the abstraction formula of the current state
    // (whether changed or not) to have it ready for the next call to this method).
    lastAbstraction = getPredicateState(s).getAbstractionFormula();

    return !stateChanged; // Careful: this method requires negated return value.
  }

  /**
   * After a path was strengthened, we need to take care of the coverage relation.
   * We also remove the infeasible part from the ARG,
   * and re-establish the coverage invariant (i.e., that states on the path
   * are either covered or cannot be covered).
   */
  @Override
  protected void finishRefinementOfPath(ARGState infeasiblePartOfART,
      List<ARGState> changedElements, ARGReachedSet pReached,
      List<ARGState> abstractionStatesTrace, boolean pRepeatedCounterexample)
      throws CPAException, InterruptedException {
    checkState(lastAbstraction != null);
    lastAbstraction = null;

    stats.argUpdate.start();

    stats.copyEdges.start();
    for (ARGState w : changedElements) {
      pReached.removeCoverageOf(w);
      if (forkedStateMap.containsKey(w)) {
        ARGState forkedState = forkedStateMap.get(w);
        SlicingAbstractionsUtils.copyEdges(forkedState,w,pReached);
        pReached.addForkedState(forkedState,w);
      }
    }
    stats.copyEdges.stop();
    // save root of the ARG BEFORE slicing. After slicing, there might be
    // several root states, but we need the true root state for recalculateReachedSet()!
    Set<ARGState> rootStates = ARGUtils.getRootStates(pReached.asReachedSet());
    ARGState rootState;
    if (rootStates.size() == 1) {
      rootState = Iterables.get(rootStates, 0);
    } else {
      // TODO: refactor so that the caller provides the full abstractionStatesTrace including the root state.
      // Then handling more than one root state would be no problem.
      throw new CPAException(
          String.format(
              "More than one root state present!(%s)",
              rootStates.stream().map(x -> x.getStateId()).collect(Collectors.toList())));
    }

    argLogger.log("in refinement before slicing!", pReached.asReachedSet().asCollection());
    stats.sliceEdges.start();
    // optimization: Slice all edges only on first iteration
    // After that we only need to slice edges of the states we split
    if (!initialSliceDone) {
      @SuppressWarnings("unchecked")
      List<ARGState> all = (List<ARGState>)(List<? extends AbstractState>)pReached.asReachedSet().asCollection().stream().
          filter(x->getPredicateState(x).isAbstractionState()).collect(Collectors.toList());
      sliceEdges(all, infeasiblePartOfART, abstractionStatesTrace, rootState);
      initialSliceDone = true;
    } else {
      sliceEdges(changedElements, infeasiblePartOfART, abstractionStatesTrace, rootState);
    }
    stats.sliceEdges.stop();

    argLogger.log("in refinement after slicing!", pReached.asReachedSet().asCollection());
    // We do not have a tree, so this does not make sense anymore:
    //pReached.removeInfeasiblePartofARG(infeasiblePartOfART);
    // Instead we use a different method:
    stats.calcReached.start();
    // pReached.recalculateReachedSet(rootState);
    // pReached.removeSafeRegions();
    pReached.recalculateReachedSet(rootState);

    stats.calcReached.stop();

    // This way we can check if startRefinementOfPath is called
    // before performRefinementForState:
    forkedStateMap.clear();
    mayShortcutSlicing = null;

    if (dynamicBlockEncoding) {
      boolean changed = true;
      while (changed) {
        changed = performDynamicBlockEncoding(pReached);
        if (changed) {
          pReached.recalculateReachedSet(rootState);
          @SuppressWarnings("unchecked")
          List<ARGState> all =
              (List<ARGState>)
                  (List<? extends AbstractState>)
                      pReached
                          .asReachedSet()
                          .asCollection()
                          .stream()
                          .filter(x -> getPredicateState(x).isAbstractionState())
                          .collect(Collectors.toList());
          sliceEdges(all, null, null, null);
        }
      }
      pReached.recalculateReachedSet(rootState);
    }

    if (removeSafeRegions) {
      pReached.removeSafeRegions();
    }

    argLogger.log("in refinement after pruning!", pReached.asReachedSet().asCollection());

    stats.argUpdate.stop();

    // optimization: instead of closing all ancestors of v,
    // close only those that were strengthened during refine
    stats.coverTime.start();
    try {
      for (ARGState w : changedElements) {
          if (w.isDestroyed()) {
            break; // all further elements are unreachable anyway
          }
          if (pReached.tryToCover(w)) {
            break; // all further elements are covered anyway
          }
      }
    } finally {
      stats.coverTime.stop();
    }


  }

  private static boolean performDynamicBlockEncoding(ARGReachedSet pArgReachedSet) {
    boolean changed = false;
    for (AbstractState state : new ArrayList<>(pArgReachedSet.asReachedSet().asCollection())) {
      ARGState currentState = (ARGState) state;
      PredicateAbstractState predState =
          PredicateAbstractState.getPredicateState(currentState);
      assert predState != null;
      if (predState.isAbstractionState() && !blk(currentState)) {
        changed = true;
        PredicateAbstractState replacement =
            PredicateAbstractState.mkNonAbstractionState(
                predState.getPathFormula(),
                predState.getAbstractionFormula(),
                predState.getAbstractionLocationsOnPath());
        ARGState newState = currentState.forkWithReplacements(Collections.singleton(replacement));
        currentState.replaceInARGWith(newState);
        pArgReachedSet.addForkedState(newState, (ARGState) state);
        if (newState instanceof SLARGState) {
          // check for incoming edges that do not have a suitable outgoing edge for their successor
          // location. E.g.: A-{1~>2}->B-{3~>4}->C
          // transfer from 1~>2 will be removed
          removeIncomingEdgesWithLocationMismatch(newState);

          // now do the same the other way around (check for outgoing edges that do not have a
          removeOutgoingEdgesWithLocationMismatch(newState);
        }
      } else if (predState.isAbstractionState() && !((SLARGState) state).isInit()) {
        // here it is only sound to check for outgoing edges that do not have a suitable incoming
        // edge
        removeOutgoingEdgesWithLocationMismatch(state);
      }
    }
    return changed;
  }

  private static void removeIncomingEdgesWithLocationMismatch(ARGState state) {
    Set<CFANode> locations = ((SLARGState) state).getOutgoingLocations();
    List<ARGState> toRemove = new ArrayList<>();
    for (ARGState parent : state.getParents()) {
      CFAEdge edge = parent.getEdgeToChild(state);
      if (edge != null && !locations.contains(edge.getSuccessor())) {
        toRemove.add(parent);
      }
    }
    for (ARGState parent : toRemove) {
      state.removeParent(parent);
    }
  }

  private static void removeOutgoingEdgesWithLocationMismatch(AbstractState state) {
    Set<CFANode> locations = ((SLARGState) state).getIncomingLocations();
    List<ARGState> toRemove = new ArrayList<>();
    for (ARGState child : ((ARGState) state).getChildren()) {
      CFAEdge edge = ((ARGState) state).getEdgeToChild(child);
      if (edge != null && !locations.contains(edge.getPredecessor())) {
        toRemove.add(child);
      }
    }
    for (ARGState child : toRemove) {
      child.removeParent((ARGState) state);
    }
  }

  private static boolean blk(ARGState pState) {

    // if it is the root state, return true:
    if (pState.getParents().size() == 0) {
      return true;
    }
    // if it is a target state, return true:
    if (pState.isTarget()) {
      return true;
    }
    // if it is part of multiple incoming blocks, return true:
    if (SlicingAbstractionsUtils.calculateStartStates(pState).size() > 1) {
      return true;
    }
    // it is a loop head, return true:
    if (SlicingAbstractionsUtils.calculateOutgoingSegments(pState).containsKey(pState)) {
      return true;
    }
    if (pState instanceof SLARGState) {
      // if not all EdgeSets from parents to pState are singletons, return true:
      if (!pState.getParents().stream().map(parent -> ((SLARGState)parent).getEdgeSetToChild(pState)).allMatch(EdgeSet::isSingleton)) {
        return true;
      }
      // if not all EdgeSets from pState to children are singletons, return true:
      if (!pState
          .getChildren()
          .stream()
          .map(child -> ((SLARGState) pState).getEdgeSetToChild(child))
          .allMatch(EdgeSet::isSingleton)) {
        return true;
      }
    }
    if (pState.getChildren().size() > 1 || pState.getParents().size() > 1) {
      return true;
    }
    return false;
  }

  private void sliceEdges(final List<ARGState> pChangedElements,
      ARGState pInfeasiblePartOfART, List<ARGState> pAbstractionStatesTrace, ARGState rootState)
      throws InterruptedException, CPAException {
    final List<ARGState> allChangedStates;
    //get the corresponding forked states:
    allChangedStates = pChangedElements.stream()
        .map(x -> forkedStateMap.get(x))
        .filter(x -> x != null)
        .filter(x -> !pChangedElements.contains(x))
        .collect(Collectors.toList());
    allChangedStates.addAll(pChangedElements);

    List<ARGState> priorAbstractionStates = new ArrayList<>();
    for (ARGState currentState : allChangedStates) {
      for (ARGState s : SlicingAbstractionsUtils.calculateStartStates(currentState)) {
        if (!priorAbstractionStates.contains(s) && ! allChangedStates.contains(s)) {
          priorAbstractionStates.add(s);
        }
      }
    }
    allChangedStates.addAll(priorAbstractionStates);

    // for minimalSlicing, there could be the case that no states got changed
    // in the refinement, but still some edges in the error path are infeasible
    // Therefore we need to make sure that allChangedStates at least contains
    // all abstraction states on the error path:
    if (minimalSlicing) {
      allChangedStates.addAll(pAbstractionStatesTrace.stream().
          filter(x->!allChangedStates.contains(x)).
          collect(Collectors.toList()));
    }

    for (ARGState currentState : allChangedStates) {
      Map<ARGState, List<ARGState>> segmentMap = SlicingAbstractionsUtils.calculateOutgoingSegments(currentState);
      Map<ARGState, Boolean> infeasibleMap = new HashMap<>();
      Set<ARGState> segmentStateSet = new HashSet<>();
      for (Map.Entry<ARGState,List<ARGState>> entry : segmentMap.entrySet()) {
        ARGState key = entry.getKey();
        List<ARGState> segment = entry.getValue();
        boolean infeasible;
        if (currentState instanceof SLARGState) {
          infeasible = checkSymbolicEdge(currentState, key, segment);
        } else {
          infeasible =
              checkEdge(
                  currentState,
                  key,
                  segment,
                  pAbstractionStatesTrace,
                  rootState,
                  pInfeasiblePartOfART,
                  pChangedElements);
        }

        infeasibleMap.put(key, infeasible);
        segmentStateSet.addAll(segment);
      }
      for (Map.Entry<ARGState, Boolean> entry : infeasibleMap.entrySet()) {
        ARGState key = entry.getKey();
        boolean isInfeasible = entry.getValue();
        List<ARGState> segment = segmentMap.get(key);
        if (!isInfeasible) {
          segmentStateSet.removeAll(segment);
        } else {
          if (key.getParents().contains(currentState)) {
            // checking for segement.size()==0 would not be enough, because we could also
            // have this:
            // 1-->A-->2
            // |       ^
            // \-------|
            key.removeParent(currentState); // this removes 1->2 in the above example
          }
          if (!Collections.disjoint(key.getParents(), segment)) {
            // Consider the following case, where abstraction states have numbers and
            // non-abstractions
            // states are shown as letters:
            // 1-->A-->2
            //     \-->3
            // if 1~>3 is infeasible, but 1~>2 is not, we cannot remove A, so we need to cut A->3:
            for (ARGState s :
                Sets.intersection(Sets.newHashSet(key.getParents()), Sets.newHashSet(segment))) {
              key.removeParent(s); // this is the cut of A->3 in the example of the comment above
            }
          }
        }
      }

      for (ARGState toRemove : segmentStateSet) {
        toRemove.removeFromARG();
      }
    }
  }

  private boolean checkEdgeSet(SLARGState startState, SLARGState endState)
      throws InterruptedException, CPAException {
    assert startState.getChildren().contains(endState);
    EdgeSet edgeSet = startState.getEdgeSetToChild(endState);
    boolean infeasible = true;
    CFAEdge savedEdge = edgeSet.choose();
    for (CFAEdge cfaEdge : edgeSet.getEdges()) {
      edgeSet.select(cfaEdge);
      if (isInfeasibleEdge(startState, endState, ImmutableList.of())) {
        edgeSet.removeEdge(cfaEdge);
      } else {
        infeasible = false;
      }
    }
    if (!infeasible && edgeSet.getEdges().contains(savedEdge)) {
      edgeSet.select(savedEdge);
    }
    return infeasible;
  }

  private boolean checkEdge(ARGState startState, ARGState endState,
      List<ARGState> segmentList, final List<ARGState> abstractionStatesTrace, ARGState rootState,
      ARGState pInfeasiblePartOfART, List<ARGState> pChangedElements)
          throws InterruptedException, CPAException {
    boolean infeasible = false;
    final boolean mustBeInfeasible = mustBeInfeasible(startState, endState,
        abstractionStatesTrace, rootState, pInfeasiblePartOfART, pChangedElements);

    if (mustBeInfeasible && optimizeSlicing) {
      infeasible = true;
    } else if (minimalSlicing) {
      if (!optimizeSlicing) {
        assert (!mustBeInfeasible || isInfeasibleEdge(startState, endState, segmentList)) : "Edge "
            + startState.getStateId() + " -> " + endState.getStateId() + " must be infeasible!";
      }
      infeasible = mustBeInfeasible;
    } else {
      infeasible = isInfeasibleEdge(startState, endState, segmentList);
      // Assert that mustBeInfeasible => infeasible holds:
      assert (!mustBeInfeasible || infeasible) : "Edge " + startState.getStateId() + " -> "
          + endState.getStateId() + " must be infeasible!";
    }

    assert !(startState instanceof SLARGState)
        || ((SLARGState) startState).getEdgeSetToChild(endState) == null
        || ((SLARGState) startState).getEdgeSetToChild(endState).getEdges().size() > 0
        || infeasible;
    return infeasible;
  }

  private boolean checkSymbolicEdge(
      ARGState pStartState, ARGState pEndState, List<ARGState> pSegmentList)
      throws InterruptedException, CPAException {
    boolean edgeSetExists = pStartState.getChildren().contains(pEndState);
    boolean segmentExists = !pSegmentList.isEmpty();
    if (segmentExists && !edgeSetExists) {
      return isInfeasibleEdge(pStartState, pEndState, pSegmentList);
    } else if (!segmentExists && edgeSetExists) {
      return checkEdgeSet((SLARGState) pStartState, (SLARGState) pEndState);
    } else if (segmentExists && edgeSetExists) {
      boolean edgeSetInfeasible = checkEdgeSet((SLARGState) pStartState, (SLARGState) pEndState);
      if (edgeSetInfeasible) {
        pEndState.removeParent(pStartState);
      }
      boolean segmentInfeasible = isInfeasibleEdge(pStartState, pEndState, pSegmentList);
      return edgeSetInfeasible && segmentInfeasible;
    } else {
      throw new RuntimeException("Checking a nonexisting transition in the ARG!");
    }
  }

  private boolean isInfeasibleEdge(ARGState start, ARGState stop, List<ARGState> segmentList)
      throws InterruptedException, CPAException {
    boolean infeasible = false;

    SSAMap startSSAMap = SSAMap.emptySSAMap().withDefault(1);
    PointerTargetSet startPts = PointerTargetSet.emptyPointerTargetSet();
    BooleanFormula formula = buildPathFormula(start, stop, segmentList, startSSAMap, startPts, solver, pfmgr, true).getFormula();
    try (ProverEnvironment thmProver = solver.newProverEnvironment(ProverOptions.GENERATE_MODELS)) {
      thmProver.push(formula);
      if (thmProver.isUnsat()) {
        infeasible = true;
      } else {
        infeasible = false;
      }
    } catch (SolverException  e){
         throw new CPAException("Solver Failure", e);
    }
    return infeasible;
  }

  private boolean mustBeInfeasible(ARGState parent, ARGState child,
      List<ARGState> abstractionStatesTrace, ARGState rootState, ARGState infeasiblePartOfART,
      List<ARGState> pChangedElements) {
    if (rootState == null) {
      return false;
    }
    // Slicing Abstraction guarantees that the edges marked with mustBeInfeasible
    // here MUST be infeasible because of the properties of inductive interpolants

    for (int i = 0; i< abstractionStatesTrace.size()-1; i++) {
      ARGState currentState = abstractionStatesTrace.get(i);
      ARGState nextState = abstractionStatesTrace.get(i+1);
      if (currentState == parent) {
        ARGState s = forkedStateMap.get(nextState);
        if (s == child && pChangedElements.contains(nextState)) {
          return true;
        }
      }
    }

    // root state needs special treatment:
    if (parent == rootState) {
      ARGState firstAfterRoot = abstractionStatesTrace.get(0);
      ARGState s = forkedStateMap.get(firstAfterRoot);
      if (s == child &&  pChangedElements.contains(firstAfterRoot)) {
        return true;
      }
    }

    // beginning of infeasible part at end of trace needs special treatment:
    if (infeasiblePartOfART == child) {
      int i = abstractionStatesTrace.indexOf(infeasiblePartOfART);
      if (i>0) {
        if (abstractionStatesTrace.get(i-1) == parent) {
          return true;
        }
      } else {
        if (parent == rootState) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
  }
}
