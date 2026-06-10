import type { WorkflowDefinition } from './models'

// Hardcoded fallback until GET /definitions backend endpoint is implemented.
// Remove this file and replace with an API call once the endpoint ships.
export const DOCUMENT_APPROVAL_DEFINITION: WorkflowDefinition = {
  definitionId: 'document-approval',
  name: 'Document Approval',
  initiatorGroup: 'initiators',
  fields: [
    { name: 'title', label: 'Document Title', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea', required: true },
    {
      name: 'priority',
      label: 'Priority',
      type: 'select',
      required: true,
      options: ['low', 'medium', 'high'],
    },
  ],
}

export const AVAILABLE_DEFINITIONS: WorkflowDefinition[] = [DOCUMENT_APPROVAL_DEFINITION]

export function getDefinition(id: string): WorkflowDefinition | undefined {
  return AVAILABLE_DEFINITIONS.find((d) => d.definitionId === id)
}
