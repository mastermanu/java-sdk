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

import io.temporal.api.command.v1.Command;
import io.temporal.api.command.v1.SignalExternalWorkflowExecutionCommandAttributes;
import io.temporal.api.enums.v1.CommandType;
import io.temporal.api.history.v1.HistoryEvent;

class SignalCommandStateMachine extends CommandStateMachineBase {

  private SignalExternalWorkflowExecutionCommandAttributes attributes;

  private boolean canceled;

  public SignalCommandStateMachine(
      CommandId id, SignalExternalWorkflowExecutionCommandAttributes attributes) {
    super(id);
    this.attributes = attributes;
  }

  /** Used for unit testing */
  SignalCommandStateMachine(
      CommandId id,
      SignalExternalWorkflowExecutionCommandAttributes attributes,
      CommandState state) {
    super(id, state);
    this.attributes = attributes;
  }

  @Override
  public Command getCommand() {
    switch (state) {
      case CREATED:
        return createSignalExternalWorkflowExecutionCommand();
      default:
        return null;
    }
  }

  @Override
  public boolean isDone() {
    return state == CommandState.COMPLETED || canceled;
  }

  @Override
  public boolean cancel(Runnable immediateCancellationCallback) {
    stateHistory.add("cancel");
    boolean result = false;
    switch (state) {
      case CREATED:
      case INITIATED:
        state = CommandState.COMPLETED;
        if (immediateCancellationCallback != null) {
          immediateCancellationCallback.run();
        }
        result = true;
        break;
      case COMMAND_SENT:
        state = CommandState.CANCELED_BEFORE_INITIATED;
        if (immediateCancellationCallback != null) {
          immediateCancellationCallback.run();
        }
        result = true;
        break;
      default:
        failStateTransition();
    }
    canceled = true;
    stateHistory.add(state.toString());
    return result;
  }

  @Override
  public void handleInitiatedEvent(HistoryEvent event) {
    stateHistory.add("handleInitiatedEvent");
    switch (state) {
      case COMMAND_SENT:
        state = CommandState.INITIATED;
        break;
      case CANCELED_BEFORE_INITIATED:
        // No state change
        break;
      default:
        failStateTransition();
    }
    stateHistory.add(state.toString());
  }

  @Override
  public void handleInitiationFailedEvent(HistoryEvent event) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void handleStartedEvent(HistoryEvent event) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void handleCompletionEvent() {
    stateHistory.add("handleCompletionEvent");
    switch (state) {
      case COMMAND_SENT:
      case INITIATED:
      case CANCELED_BEFORE_INITIATED:
        state = CommandState.COMPLETED;
        break;
      case COMPLETED:
        // No state change
        break;
      default:
        failStateTransition();
    }
    stateHistory.add(state.toString());
  }

  @Override
  public void handleCancellationInitiatedEvent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void handleCancellationFailureEvent(HistoryEvent event) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void handleCancellationEvent() {
    throw new UnsupportedOperationException();
  }

  private Command createSignalExternalWorkflowExecutionCommand() {
    return Command.newBuilder()
        .setSignalExternalWorkflowExecutionCommandAttributes(attributes)
        .setCommandType(CommandType.COMMAND_TYPE_SIGNAL_EXTERNAL_WORKFLOW_EXECUTION)
        .build();
  }
}
