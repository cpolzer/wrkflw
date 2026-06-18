namespace WrkflwApi;

union FlowRunStatus {
  running: "RUNNING",
  completed: "COMPLETED",
}

union TaskRunStatus {
  pending: "PENDING",
  claimed: "CLAIMED",
  completed: "COMPLETED",
}

union DecisionOutcome {
  approve: "APPROVE",
  reject: "REJECT",
}

model SubmitDocumentRequest {
  definitionKey: string;
  @doc("Reference/identifier of the document")
  documentRef: string;
}

model DecisionRequest {
  outcome: DecisionOutcome;
  comment?: string;
}

model TaskSummary {
  @format("uuid") taskId: string;
  @format("uuid") flowId: string;
  stateName: string;
  candidateGroupId: string;
  status: TaskRunStatus;
  ownerId: string | null;
}

model FlowStatus {
  @format("uuid") flowId: string;
  definitionKey: string;
  currentState: string;
  status: FlowRunStatus;
  terminalOutcome: string | null;
  pendingTasks: TaskSummary[];
}

model AuditEntry {
  type: string;
  actorId: string | null;
  occurredAt: utcDateTime;
  detail: Record<unknown>;
}

model FlowStatusWithHistory extends FlowStatus {
  history: AuditEntry[];
}

model FlowSummary {
  @format("uuid") flowId: string;
  definitionKey: string;
  currentState: string;
  status: FlowRunStatus;
  terminalOutcome: string | null;
  documentRef: string;
  submitterId: string;
  updatedAt: utcDateTime;
}
