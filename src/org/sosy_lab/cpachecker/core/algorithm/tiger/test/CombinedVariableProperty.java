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
package org.sosy_lab.cpachecker.core.algorithm.tiger.test;

import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;


public abstract class CombinedVariableProperty extends VariableProperty {

  public enum Combinators {
    ADD,
    SUBSTRACT,
    MULTIPLY
  }

  protected List<String> combinedVariables;

  protected Combinators combinator;

  public CombinedVariableProperty(List<String> combinedVariables, Combinators combinator,
      Comparators pComp, GoalPropertyType pInOrOut) {
    super(pComp, pInOrOut);
    this.combinedVariables = combinedVariables;
    this.combinator = combinator;
  }

  protected BigInteger combineVariables(Map<String, BigInteger> listToCheck) {
    List<BigInteger> combinedValues = Lists.newLinkedList();

    for (String var : combinedVariables) {
      BigInteger value = listToCheck.get(var);
      combinedValues.add(value);
    }

    BigInteger cominedValue = combinedValues.get(0);
    combinedValues.remove(cominedValue);

    for (BigInteger integer : combinedValues) {
      switch (combinator) {
        case ADD: {
          cominedValue = cominedValue.add(integer);
          break;
        }
        case SUBSTRACT: {
          cominedValue = cominedValue.subtract(integer);
          break;
        }
        case MULTIPLY: {
          cominedValue = cominedValue.multiply(integer);
          break;
        }
        default:
          break;
      }
    }
    return cominedValue;
  }

  @Override
  public abstract boolean checkProperty(Map<String, BigInteger> pListToCheck,
      GoalPropertyType pInOrOut);

}