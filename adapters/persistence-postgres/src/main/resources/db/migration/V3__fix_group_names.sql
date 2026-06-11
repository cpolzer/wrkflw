-- V3__fix_group_names.sql
-- Align group IDs in flow_definition with Keycloak group names.
-- V2 seeded with placeholder names (authors, reviewers, senior-reviewers);
-- Keycloak realm defines: initiators, legal-reviewers.

UPDATE flow_definition
SET
    initiator_group_id = 'initiators',
    states = jsonb_set(
        jsonb_set(
            jsonb_set(
                states,
                '{0,candidateGroupId}', '"legal-reviewers"'
            ),
            '{1,candidateGroupId}', '"legal-reviewers"'
        ),
        '{2,candidateGroupId}', '"initiators"'
    )
WHERE key = 'document-approval';
