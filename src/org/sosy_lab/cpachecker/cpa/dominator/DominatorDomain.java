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
package org.sosy_lab.cpachecker.cpa.dominator;

import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

public class DominatorDomain implements AbstractDomain {

  @Override
  public boolean isLessOrEqual(AbstractState pElement1, AbstractState pElement2) {

    assert pElement1 instanceof DominatorState && pElement2 instanceof DominatorState
        : "States given that are no dominator states! "
            + pElement1.getClass().getName()
            + ", "
            + pElement2.getClass().getName();

    return ((DominatorState) pElement1).isLessOrEqual((DominatorState) pElement2);
  }

  @Override
  public AbstractState join(AbstractState pElement1, AbstractState pElement2) {
    assert pElement1 instanceof DominatorState && pElement2 instanceof DominatorState
        : "States given that are no dominator states! "
            + pElement1.getClass().getName()
            + ", "
            + pElement2.getClass().getName();

    return ((DominatorState) pElement1).join((DominatorState) pElement2);
  }
}
