package com.example.agent

import com.example.model.AgentAuditRecord
import com.example.model.AgentCommandProposal
import com.example.model.AgentSettings
import com.example.model.AiProviderType
import com.example.model.ProposalApprovalStatus
import com.example.model.ProposalExecutionStatus
import com.example.security.SecurityManager
import com.example.ssh.SshExecOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Abstraction for executing approved commands over the real Termux SSH channel.
 */
fun interface AgentSshExecutor {
    suspend fun execute(command: String, workingDir: String): Result<SshExecOutput>
}

data class AgentUiSnapshot(
    val isQueryingProvider: Boolean = false,
    val isExecutingCommand: Boolean = false,
    val lastAgentExplanation: String = "",
    val lastProviderError: String? = null,
    val proposals: List<AgentCommandProposal> = emptyList(),
    val auditRecords: List<AgentAuditRecord> = emptyList()
) {
    val isBusy: Boolean get() = isQueryingProvider || isExecutingCommand
}

/**
 * Coordinates the Phase 3 agent workflow:
 * - Agents ONLY propose commands.
 * - Every command requires manual confirmation by default.
 * - Optional auto-approval works ONLY for read-only commands on the user allowlist strictly inside agentWorkingDir.
 * - Full audit trail of proposals, approvals/rejections, executions, and outputs (with secrets redacted).
 * - Immediate STOP button cancels HTTP queries and SSH exec channels.
 */
class AgentExecutionCoordinator(
    private val scope: CoroutineScope,
    private val llmClient: UnifiedLlmAgentClient,
    private val sshExecutor: AgentSshExecutor,
    private val isSshConnected: () -> Boolean,
    private val onAuditEvent: (levelTag: String, message: String, isWarningOrError: Boolean) -> Unit
) {
    private val _state = MutableStateFlow(AgentUiSnapshot())
    val state: StateFlow<AgentUiSnapshot> = _state.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    fun clearError() {
        _state.update { it.copy(lastProviderError = null) }
    }

    fun submitPrompt(prompt: String, settings: AgentSettings) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return

        if (settings.aiProviderType == AiProviderType.NONE) {
            val msg = "No hay ningún proveedor de IA configurado. Selecciona Anthropic Cloud, OpenAI Cloud o Servidor Local OpenAI (127.0.0.1) en Ajustes."
            _state.update { it.copy(lastProviderError = msg) }
            onAuditEvent("AGENT_CONFIG", msg, true)
            return
        }

        val configErr = UnifiedLlmAgentClient.validateProviderConfiguration(settings)
        if (configErr != null) {
            _state.update { it.copy(lastProviderError = configErr) }
            onAuditEvent("AGENT_CONFIG", configErr, true)
            return
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            _state.update { it.copy(isQueryingProvider = true, lastProviderError = null) }
            try {
                val res = llmClient.queryProvider(cleanPrompt, settings)
                res.onSuccess { response ->
                    val workingDir = AgentCommandPolicy.normalizeWorkingDirectory(settings.agentWorkingDir)
                    val secrets = listOf(settings.claudeApiKey, settings.openAiApiKey, settings.localOpenAiToken)
                    val safeExplanation = SecurityManager.redactSecrets(response.explanationText, secrets)

                    val createdProposals = mutableListOf<AgentCommandProposal>()
                    val autoExecuteQueue = mutableListOf<AgentCommandProposal>()

                    for (cmd in response.proposedCommands) {
                        val decision = AgentCommandPolicy.evaluateProposal(
                            command = cmd,
                            workingDir = workingDir,
                            autoApproveEnabled = settings.agentAutoApproveReadOnly,
                            allowlist = settings.agentReadOnlyAllowlist
                        )
                        val proposal = AgentCommandProposal(
                            providerLabel = response.providerLabel,
                            userPrompt = cleanPrompt,
                            command = cmd,
                            workingDir = workingDir,
                            canAutoApprove = decision.canAutoApprove,
                            policyReason = decision.reason,
                            isReadOnlySafeInWorkspace = decision.isReadOnlySafeInWorkspace,
                            approvalStatus = if (decision.canAutoApprove) {
                                ProposalApprovalStatus.AUTO_APPROVED_READ_ONLY
                            } else {
                                ProposalApprovalStatus.PENDING
                            }
                        )
                        createdProposals.add(proposal)
                        if (decision.canAutoApprove) {
                            autoExecuteQueue.add(proposal)
                            appendAuditRecord(
                                AgentAuditRecord(
                                    id = proposal.id,
                                    providerLabel = proposal.providerLabel,
                                    userPrompt = proposal.userPrompt,
                                    proposedCommand = proposal.command,
                                    workingDir = proposal.workingDir,
                                    decision = ProposalApprovalStatus.AUTO_APPROVED_READ_ONLY,
                                    executionStatus = ProposalExecutionStatus.NOT_EXECUTED,
                                    policyReason = proposal.policyReason
                                ),
                                secrets = secrets
                            )
                            onAuditEvent("AGENT_AUTO_READONLY", "Auto-aprobado lectura: '${proposal.command}' en ${proposal.workingDir}", false)
                        } else {
                            appendAuditRecord(
                                AgentAuditRecord(
                                    id = proposal.id,
                                    providerLabel = proposal.providerLabel,
                                    userPrompt = proposal.userPrompt,
                                    proposedCommand = proposal.command,
                                    workingDir = proposal.workingDir,
                                    decision = ProposalApprovalStatus.PENDING,
                                    executionStatus = ProposalExecutionStatus.NOT_EXECUTED,
                                    policyReason = proposal.policyReason
                                ),
                                secrets = secrets
                            )
                            onAuditEvent("AGENT_PROPOSAL", "Propuesta pendiente de aprobación: '${proposal.command}'", false)
                        }
                    }

                    _state.update {
                        it.copy(
                            isQueryingProvider = false,
                            lastAgentExplanation = safeExplanation,
                            proposals = createdProposals + it.proposals
                        )
                    }

                    for (autoProposal in autoExecuteQueue) {
                        executeApprovedProposalInternal(autoProposal, ProposalApprovalStatus.AUTO_APPROVED_READ_ONLY, secrets)
                    }
                }.onFailure { err ->
                    val errMsg = err.localizedMessage ?: "Error al consultar el proveedor IA."
                    _state.update { it.copy(isQueryingProvider = false, lastProviderError = errMsg) }
                    onAuditEvent("AGENT_ERROR", errMsg, true)
                }
            } catch (ce: CancellationException) {
                _state.update { it.copy(isQueryingProvider = false, lastProviderError = "Consulta detenida por el usuario.") }
                throw ce
            }
        }
    }

    fun approveProposal(proposalId: String, settings: AgentSettings) {
        val proposal = _state.value.proposals.firstOrNull { it.id == proposalId } ?: return
        if (proposal.approvalStatus != ProposalApprovalStatus.PENDING) return

        val secrets = listOf(settings.claudeApiKey, settings.openAiApiKey, settings.localOpenAiToken)
        updateProposalAndAudit(
            id = proposalId,
            approval = ProposalApprovalStatus.APPROVED_MANUAL,
            execStatus = ProposalExecutionStatus.NOT_EXECUTED,
            secrets = secrets
        )
        onAuditEvent("AGENT_APPROVED", "Usuario aprobó comando '${proposal.command}' en ${proposal.workingDir}", false)

        activeJob?.cancel()
        activeJob = scope.launch {
            executeApprovedProposalInternal(proposal, ProposalApprovalStatus.APPROVED_MANUAL, secrets)
        }
    }

    fun rejectProposal(proposalId: String, settings: AgentSettings) {
        val proposal = _state.value.proposals.firstOrNull { it.id == proposalId } ?: return
        if (proposal.approvalStatus != ProposalApprovalStatus.PENDING) return

        val secrets = listOf(settings.claudeApiKey, settings.openAiApiKey, settings.localOpenAiToken)
        updateProposalAndAudit(
            id = proposalId,
            approval = ProposalApprovalStatus.REJECTED,
            execStatus = ProposalExecutionStatus.NOT_EXECUTED,
            output = "Rechazado por el usuario antes de ejecutarse.",
            secrets = secrets
        )
        onAuditEvent("AGENT_REJECTED", "Usuario rechazó propuesta: '${proposal.command}'", false)
    }

    fun stopExecution(settings: AgentSettings) {
        llmClient.cancelActiveCall()
        val jobToCancel = activeJob
        activeJob = null
        jobToCancel?.cancel()

        val secrets = listOf(settings.claudeApiKey, settings.openAiApiKey, settings.localOpenAiToken)
        _state.update { current ->
            val updatedProposals = current.proposals.map { p ->
                if (p.executionStatus == ProposalExecutionStatus.RUNNING) {
                    p.copy(
                        executionStatus = ProposalExecutionStatus.STOPPED_BY_USER,
                        output = p.output.ifBlank { "Ejecución detenida inmediatamente por el usuario." }
                    )
                } else {
                    p
                }
            }
            val updatedAudit = current.auditRecords.map { rec ->
                if (rec.executionStatus == ProposalExecutionStatus.RUNNING) {
                    rec.copy(
                        executionStatus = ProposalExecutionStatus.STOPPED_BY_USER,
                        output = SecurityManager.redactSecrets(
                            rec.output.ifBlank { "Ejecución detenida inmediatamente por el usuario." },
                            secrets
                        )
                    )
                } else {
                    rec
                }
            }
            current.copy(
                isQueryingProvider = false,
                isExecutingCommand = false,
                lastProviderError = if (current.isBusy) "Ejecución detenida inmediatamente por el usuario." else current.lastProviderError,
                proposals = updatedProposals,
                auditRecords = updatedAudit
            )
        }
        onAuditEvent("AGENT_STOP", "Botón PARAR activado: ejecución detenida inmediatamente.", true)
    }

    private suspend fun executeApprovedProposalInternal(
        proposal: AgentCommandProposal,
        approval: ProposalApprovalStatus,
        secrets: List<String>
    ) {
        if (!isSshConnected()) {
            val msg = "No se pudo ejecutar '${proposal.command}': el canal SSH real con Termux está desconectado."
            updateProposalAndAudit(
                id = proposal.id,
                approval = approval,
                execStatus = ProposalExecutionStatus.FAILED,
                exitCode = -1,
                output = msg,
                secrets = secrets
            )
            _state.update { it.copy(isExecutingCommand = false, lastProviderError = msg) }
            onAuditEvent("AGENT_EXEC_FAIL", msg, true)
            return
        }

        _state.update { it.copy(isExecutingCommand = true, lastProviderError = null) }
        updateProposalAndAudit(
            id = proposal.id,
            approval = approval,
            execStatus = ProposalExecutionStatus.RUNNING,
            secrets = secrets
        )

        try {
            val result = sshExecutor.execute(proposal.command, proposal.workingDir)
            result.onSuccess { out ->
                val safeOutput = SecurityManager.redactSecrets(out.output, secrets)
                val execState = if (out.exitCode == 0) ProposalExecutionStatus.COMPLETED else ProposalExecutionStatus.FAILED
                updateProposalAndAudit(
                    id = proposal.id,
                    approval = approval,
                    execStatus = execState,
                    exitCode = out.exitCode,
                    output = safeOutput.ifBlank { "(sin salida, código ${out.exitCode})" },
                    secrets = secrets
                )
                onAuditEvent(
                    "AGENT_SSH_EXEC",
                    "Comando '${proposal.command}' finalizado (exit=${out.exitCode}) en ${out.currentDir}",
                    out.exitCode != 0
                )
            }.onFailure { err ->
                val safeErr = SecurityManager.redactSecrets(err.localizedMessage ?: "Error SSH", secrets)
                updateProposalAndAudit(
                    id = proposal.id,
                    approval = approval,
                    execStatus = ProposalExecutionStatus.FAILED,
                    exitCode = -1,
                    output = safeErr,
                    secrets = secrets
                )
                onAuditEvent("AGENT_EXEC_FAIL", safeErr, true)
            }
        } catch (ce: CancellationException) {
            updateProposalAndAudit(
                id = proposal.id,
                approval = approval,
                execStatus = ProposalExecutionStatus.STOPPED_BY_USER,
                output = "Ejecución SSH interrumpida por el botón PARAR.",
                secrets = secrets
            )
            throw ce
        } finally {
            _state.update { it.copy(isExecutingCommand = false) }
        }
    }

    private fun appendAuditRecord(record: AgentAuditRecord, secrets: List<String>) {
        val safe = record.copy(
            output = SecurityManager.redactSecrets(record.output, secrets),
            userPrompt = SecurityManager.redactSecrets(record.userPrompt, secrets)
        )
        _state.update { current ->
            val updated = listOf(safe) + current.auditRecords.filterNot { it.id == safe.id }
            current.copy(auditRecords = updated.take(100))
        }
    }

    private fun updateProposalAndAudit(
        id: String,
        approval: ProposalApprovalStatus,
        execStatus: ProposalExecutionStatus,
        exitCode: Int? = null,
        output: String? = null,
        secrets: List<String>
    ) {
        val safeOutput = output?.let { SecurityManager.redactSecrets(it, secrets) }
        _state.update { current ->
            val updatedProposals = current.proposals.map { p ->
                if (p.id == id) {
                    p.copy(
                        approvalStatus = approval,
                        executionStatus = execStatus,
                        exitCode = exitCode ?: p.exitCode,
                        output = safeOutput ?: p.output
                    )
                } else {
                    p
                }
            }
            val updatedAudit = current.auditRecords.map { rec ->
                if (rec.id == id) {
                    rec.copy(
                        decision = approval,
                        executionStatus = execStatus,
                        exitCode = exitCode ?: rec.exitCode,
                        output = safeOutput ?: rec.output
                    )
                } else {
                    rec
                }
            }
            current.copy(proposals = updatedProposals, auditRecords = updatedAudit)
        }
    }
}
