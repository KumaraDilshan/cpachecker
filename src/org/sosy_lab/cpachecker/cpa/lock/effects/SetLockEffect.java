/*
 *  CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.lock.effects;

import com.google.common.base.Preconditions;
import org.sosy_lab.cpachecker.cpa.lock.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.lock.LockState.LockStateBuilder;


public class SetLockEffect extends LockEffect {

  private final static SetLockEffect instance = new SetLockEffect();

  final int p;

  private SetLockEffect(int t, LockIdentifier id) {
    super(id);
    p = t;
  }

  private SetLockEffect() {
    this(0, null);
  }

  @Override
  public void effect(LockStateBuilder pBuilder) {
    Preconditions.checkArgument(this != instance, "Temporary instance can not effect");
    Preconditions.checkArgument(target != null, "Lock identifier must be set");
    pBuilder.set(target, p);
  }

  public static SetLockEffect getInstance() {
    return instance;
  }

  public static SetLockEffect createEffectForId(int t, LockIdentifier id) {
    return new SetLockEffect(t, id);
  }

  @Override
  public SetLockEffect cloneWithTarget(LockIdentifier id) {
    return createEffectForId(this.p, id);
  }
}
