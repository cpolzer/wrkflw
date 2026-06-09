-- V2__seed_document_approval.sql
-- Seed the document-approval flow definition
-- Submitted → (APPROVE) → FinalReview → (APPROVE) → Approved[terminal]
-- Any REJECT → ReworkRequested → resubmit SUBMIT → back to Submitted
-- Abandon → Rejected[terminal]

INSERT INTO flow_definition (key, version, initial_state, initiator_group_id, states, transitions)
VALUES (
    'document-approval',
    1,
    'Submitted',
    'authors',
    '[
        {"name": "Submitted", "type": "HUMAN_TASK", "candidateGroupId": "reviewers"},
        {"name": "FinalReview", "type": "HUMAN_TASK", "candidateGroupId": "senior-reviewers"},
        {"name": "ReworkRequested", "type": "HUMAN_TASK", "candidateGroupId": "authors"},
        {"name": "Approved", "type": "TERMINAL", "terminalOutcome": "APPROVED"},
        {"name": "Rejected", "type": "TERMINAL", "terminalOutcome": "REJECTED"}
    ]'::jsonb,
    '[
        {"from": "Submitted", "trigger": "APPROVE", "to": "FinalReview"},
        {"from": "Submitted", "trigger": "REJECT", "to": "ReworkRequested"},
        {"from": "FinalReview", "trigger": "APPROVE", "to": "Approved"},
        {"from": "FinalReview", "trigger": "REJECT", "to": "ReworkRequested"},
        {"from": "ReworkRequested", "trigger": "SUBMIT", "to": "Submitted"},
        {"from": "Submitted", "trigger": "REJECT", "to": "Rejected"},
        {"from": "FinalReview", "trigger": "REJECT", "to": "Rejected"}
    ]'::jsonb
);
