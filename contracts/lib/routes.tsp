import "@typespec/http";

using TypeSpec.Http;

namespace WrkflwApi;

@route("/flows")
interface FlowsApi {
  @get
  @doc("List flows submitted by the caller (submitter My Submissions view)")
  list(): FlowSummary[];

  @post
  @doc("Submit a document for approval — only initiator-group members may start (FR-002)")
  submit(@body body: SubmitDocumentRequest):
    | { @statusCode statusCode: 201; @body body: FlowStatus }
    | { @statusCode statusCode: 403 }
    | { @statusCode statusCode: 404 }
    | { @statusCode statusCode: 422 };
}

@route("/flows/{flowId}")
interface FlowInstancesApi {
  @get
  @doc("Get flow status, pending tasks, responsible groups, and audit history")
  getStatus(@path @format("uuid") flowId: string):
    | { @statusCode statusCode: 200; @body body: FlowStatusWithHistory }
    | { @statusCode statusCode: 404 };
}

@route("/worklists/group")
interface GroupWorklistApi {
  @get
  @doc("Unclaimed tasks actionable by the caller's group(s) (FR-016)")
  list(): TaskSummary[];
}

@route("/worklists/mine")
interface MyWorklistApi {
  @get
  @doc("Tasks the caller personally owns (FR-017)")
  list(): TaskSummary[];
}

@route("/tasks/{taskId}/claim")
interface TaskClaimApi {
  @post
  @doc("Claim a pending task — caller must be in the candidate group and task must be PENDING (FR-008)")
  claim(@path @format("uuid") taskId: string):
    | { @statusCode statusCode: 200; @body body: TaskSummary }
    | { @statusCode statusCode: 403 }
    | { @statusCode statusCode: 404 }
    | { @statusCode statusCode: 409 };
}

@route("/tasks/{taskId}/release")
interface TaskReleaseApi {
  @post
  @doc("Release a claimed task back to the group (FR-009)")
  release(@path @format("uuid") taskId: string):
    | { @statusCode statusCode: 200; @body body: TaskSummary }
    | { @statusCode statusCode: 403 }
    | { @statusCode statusCode: 404 }
    | { @statusCode statusCode: 409 };
}

@route("/tasks/{taskId}/decision")
interface TaskDecisionApi {
  @post
  @doc("Record an approve/reject decision on an owned task (FR-011, FR-012)")
  decide(@path @format("uuid") taskId: string, @body body: DecisionRequest):
    | { @statusCode statusCode: 200; @body body: FlowStatus }
    | { @statusCode statusCode: 403 }
    | { @statusCode statusCode: 404 }
    | { @statusCode statusCode: 409 }
    | { @statusCode statusCode: 422 };
}
