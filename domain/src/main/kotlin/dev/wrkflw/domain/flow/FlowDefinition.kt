package dev.wrkflw.domain.flow

import dev.wrkflw.domain.identity.FlowDefinitionKey
import dev.wrkflw.domain.identity.GroupId

enum class StateType {
    HUMAN_TASK,
    TERMINAL
}

enum class Trigger {
    APPROVE,
    REJECT,
    SUBMIT
}

data class StateDefinition(
    val name: String,
    val type: StateType,
    val candidateGroupId: GroupId? = null,
    val terminalOutcome: String? = null
) {
    init {
        require(name.isNotBlank()) { "State name must not be blank" }
        if (type == StateType.HUMAN_TASK) {
            require(candidateGroupId != null) { "HUMAN_TASK state must have a candidateGroupId" }
        }
        if (type == StateType.TERMINAL) {
            require(terminalOutcome != null) { "TERMINAL state must have a terminalOutcome" }
        }
    }
}

data class TransitionDefinition(
    val from: String,
    val trigger: Trigger,
    val to: String,
    val guard: String? = null
) {
    init {
        require(from.isNotBlank()) { "Transition 'from' must not be blank" }
        require(to.isNotBlank()) { "Transition 'to' must not be blank" }
    }
}

data class FlowDefinition(
    val key: FlowDefinitionKey,
    val version: Int,
    val initialState: String,
    val initiatorGroupId: GroupId,
    val states: List<StateDefinition>,
    val transitions: List<TransitionDefinition>
) {
    init {
        require(version >= 1) { "Version must be >= 1" }
        require(states.isNotEmpty()) { "Must have at least one state" }
        require(transitions.isNotEmpty()) { "Must have at least one transition" }

        val stateNames = states.map { it.name }.toSet()
        require(stateNames.contains(initialState)) {
            "Initial state '$initialState' not found in states: $stateNames"
        }

        val terminalStates = states.filter { it.type == StateType.TERMINAL }
        require(terminalStates.isNotEmpty()) { "Must have at least one terminal state" }

        for (transition in transitions) {
            require(stateNames.contains(transition.from)) {
                "Transition from '${transition.from}' references unknown state"
            }
            require(stateNames.contains(transition.to)) {
                "Transition to '${transition.to}' references unknown state"
            }
        }

        for (state in states) {
            if (state.type == StateType.TERMINAL) {
                require(transitions.none { it.from == state.name }) {
                    "Terminal state '${state.name}' must not have outgoing transitions"
                }
            }
        }
    }

    fun isTerminal(stateName: String): Boolean =
        states.find { it.name == stateName }?.type == StateType.TERMINAL

    fun terminalOutcome(stateName: String): String? =
        states.find { it.name == stateName }?.terminalOutcome

    fun candidateGroup(stateName: String): GroupId? =
        states.find { it.name == stateName }?.candidateGroupId
}
