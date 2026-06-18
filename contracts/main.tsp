import "@typespec/http";
import "@typespec/openapi";
import "@typespec/openapi3";
import "./lib/models.tsp";
import "./lib/routes.tsp";

using TypeSpec.Http;
using TypeSpec.OpenAPI;

@service(#{ title: "wrkflw Document Approval API" })
@info(#{ version: "0.1.0" })
@doc("""
  REST contract for the document-approval workflow engine.
  Actor identity and group memberships are supplied via trusted headers
  (X-Actor-Id, X-Actor-Groups). All command responses reflect the
  authoritative DB state committed before the corresponding Temporal
  signal is sent.
  """)
@server("/api/v1", "Document Approval REST API")
namespace WrkflwApi;
