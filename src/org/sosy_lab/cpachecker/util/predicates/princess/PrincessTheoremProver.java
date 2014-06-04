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
package org.sosy_lab.cpachecker.util.predicates.princess;

import ap.SimpleAPI;
import ap.parser.IExpression;
import ap.parser.IFormula;
import org.sosy_lab.common.time.NestedTimer;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.counterexample.Model;
import org.sosy_lab.cpachecker.util.predicates.AbstractionManager.RegionCreator;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.ProverEnvironment;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;
import org.sosy_lab.cpachecker.util.predicates.interfaces.RegionManager.RegionBuilder;
import scala.collection.JavaConversions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sosy_lab.cpachecker.util.predicates.princess.PrincessUtil.castToFormula;

public class PrincessTheoremProver extends PrincessAbstractProver implements ProverEnvironment {

  private final List<IExpression> assertedTerms = new ArrayList<>();
  protected final ShutdownNotifier shutdownNotifier;

  public PrincessTheoremProver(PrincessFormulaManager pMgr, ShutdownNotifier pShutdownNotifier) {
    super(pMgr);
    this.shutdownNotifier = checkNotNull(pShutdownNotifier);
  }

  @Override
  public Model getModel() {
    return PrincessModel.createModel(stack, assertedTerms);
  }

  @Override
  public void pop() {
    assertedTerms.remove(assertedTerms.size()-1); // remove last term
    stack.pop(1);
  }

  @Override
  public void push(BooleanFormula f) {
    final IFormula t = castToFormula(mgr.getTerm(f));
    assertedTerms.add(t);
    stack.push(1);
    stack.assertTerm(t);
  }

  @Override
  public List<BooleanFormula> getUnsatCore() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AllSatResult allSat(Collection<BooleanFormula> formulas,
                             RegionCreator rmgr, Timer solveTime, NestedTimer enumTime) throws InterruptedException {
    checkNotNull(rmgr);
    checkNotNull(solveTime);
    checkNotNull(enumTime);
    checkArgument(!formulas.isEmpty());

    // create new allSatResult
    SmtInterpolAllSatCallback result = new SmtInterpolAllSatCallback(rmgr, solveTime, enumTime);

    solveTime.start();
    try {
      stack.push(1);
      while (stack.hasNextModel()) {
        shutdownNotifier.shutdownIfNecessary();
        SimpleAPI.PartialModel model = stack.getModel();
        result.callback(model);
      }
      shutdownNotifier.shutdownIfNecessary();
      stack.pop(1);

    } finally {
      if (solveTime.isRunning()) {
        solveTime.stop();
      } else {
        enumTime.stopOuter();
      }
    }

    return result;
  }

  /**
   * callback used to build the predicate abstraction of a formula
   */
  class SmtInterpolAllSatCallback implements AllSatResult {
    private final RegionCreator rmgr;
    private final RegionBuilder builder;

    private final Timer solveTime;
    private final NestedTimer enumTime;
    private Timer regionTime = null;

    private int count = 0;

    private Region formula = null;

    public SmtInterpolAllSatCallback(RegionCreator rmgr, Timer pSolveTime, NestedTimer pEnumTime) {
      this.rmgr = rmgr;
      this.solveTime = pSolveTime;
      this.enumTime = pEnumTime;
      builder = rmgr.newRegionBuilder(shutdownNotifier);
    }

    @Override
    public int getCount() {
      return count;
    }

    @Override
    public Region getResult() throws InterruptedException {
      if (formula == null) {
        enumTime.startBoth();
        try {
          formula = builder.getResult();
          builder.close();
        } finally {
          enumTime.stopBoth();
        }
      }
      return formula;
    }

    public void callback(SimpleAPI.PartialModel model) {
      if (count == 0) {
        solveTime.stop();
        enumTime.startOuter();
        regionTime = enumTime.getCurentInnerTimer();
      }

      regionTime.start();

      // the abstraction is created simply by taking the disjunction
      // of all the models found by all_sat, and storing them in a BDD
      // first, let's create the BDD corresponding to the model
      builder.startNewConjunction();

      for (SimpleAPI.ModelLocation mloc : JavaConversions.asJavaCollection(model.definedLocations())) {
        System.out.println(mloc);
        if (mloc instanceof SimpleAPI.ConstantLoc) {
         /* if (isNot(mloc)) {
            t = getArg(t, 0);
            builder.addNegativeRegion(rmgr.getPredicate(encapsulate(t)));
          } else {
            builder.addPositiveRegion(rmgr.getPredicate(encapsulate(t)));
          }*/
        }
      }
      builder.finishConjunction();

      count++;

      regionTime.stop();
    }

    private BooleanFormula encapsulate(IExpression pT) {
      return mgr.encapsulateBooleanFormula(pT);
    }
  }
}
