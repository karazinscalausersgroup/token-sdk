package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.selection.TokenSelection
import com.r3.corda.sdk.token.workflow.selection.generateMoveNonFungible
import net.corda.core.contracts.Amount
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object MoveToken {

    @InitiatingFlow
    @StartableByRPC
    class Initiator<T : TokenType>(
            val ownedToken: T,
            val holder: AbstractParty,
            val amount: Amount<T>? = null,
            val session: FlowSession? = null
    ) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATE_MOVE : ProgressTracker.Step("Generating tokens move.")
            object SIGNING : ProgressTracker.Step("Signing transaction proposal.")
            object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(GENERATE_MOVE, SIGNING, RECORDING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            val holderParty = serviceHub.identityService.wellKnownPartyFromAnonymous(holder)
                    ?: throw IllegalArgumentException("Called MoveToken flow with anonymous party that node doesn't know about. " +
                            "Make sure that RequestConfidentialIdentity flow is called before.")
            val holderSession = if (session == null) initiateFlow(holderParty) else session

            progressTracker.currentStep = GENERATE_MOVE
            val (builder, keys) = if (amount == null) {
                generateMoveNonFungible(serviceHub.vaultService, ownedToken, holder)
            } else {
                val tokenSelection = TokenSelection(serviceHub)
                tokenSelection.generateMove(TransactionBuilder(), amount, holder)
            }

            progressTracker.currentStep = SIGNING
            // WARNING: At present, the recipient will not be signed up to updates from the token maintainer.
            val stx: SignedTransaction = serviceHub.signInitialTransaction(builder, keys)
            progressTracker.currentStep = RECORDING
            val sessions = if (ourIdentity == holderParty) emptyList() else listOf(holderSession)
            val finalTx = subFlow(FinalityFlow(transaction = stx, sessions = sessions))
            // If it's TokenPointer, then update the distribution lists for token maintainers
            if(ownedToken is TokenPointer<*>) {
                subFlow(UpdateDistributionList.Initiator(ownedToken, ourIdentity, holderParty))
            }
            return finalTx
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call(): Unit {
            // Resolve the issuance transaction.
            if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
                subFlow(ReceiveFinalityFlow(otherSideSession = otherSession, statesToRecord = StatesToRecord.ONLY_RELEVANT))
            }
        }
    }
}