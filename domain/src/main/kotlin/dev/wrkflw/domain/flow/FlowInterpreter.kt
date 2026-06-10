package dev.wrkflw.domain.flow

data class FlowInterpreterResult(
    val nextState: String?,
    val terminalOutcome: String?,
    val isTerminal: Boolean,
) {
    companion object {
        fun advance(
            stateName: String,
            trigger: Trigger,
            definition: FlowDefinition,
        ): FlowInterpreterResult {
            val transition =
                definition.transitions.find {
                    it.from == stateName && it.trigger == trigger
                }

            if (transition == null) {
                throw InvalidTransitionException(
                    "No transition from '$stateName' with trigger $trigger in definition ${definition.key.value}",
                )
            }

            val isTerminal = definition.isTerminal(transition.to)
            return FlowInterpreterResult(
                nextState = if (isTerminal) null else transition.to,
                terminalOutcome = if (isTerminal) definition.terminalOutcome(transition.to) else null,
                isTerminal = isTerminal,
            )
        }

        fun initialState(definition: FlowDefinition): String = definition.initialState
    }
}

class InvalidTransitionException(
    message: String,
) : IllegalStateException(message)
