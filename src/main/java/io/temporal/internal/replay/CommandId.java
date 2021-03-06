/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.internal.replay;

import java.util.Objects;

class CommandId {

  private final CommandTarget commandTarget;

  private final long commandEventId;

  CommandId(CommandTarget commandTarget, long commandEventId) {
    this.commandEventId = commandEventId;
    this.commandTarget = Objects.requireNonNull(commandTarget);
  }

  CommandTarget getCommandTarget() {
    return commandTarget;
  }

  long getCommandEventId() {
    return commandEventId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !(o instanceof CommandId)) {
      return false;
    }

    CommandId that = (CommandId) o;

    if (commandEventId != that.commandEventId) {
      return false;
    }
    return commandTarget == that.commandTarget;
  }

  @Override
  public int hashCode() {
    int result = commandTarget.hashCode();
    result = 31 * result + (int) (commandEventId ^ (commandEventId >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "CommandId{"
        + "commandTarget="
        + commandTarget
        + ", commandEventId="
        + commandEventId
        + '}';
  }
}
