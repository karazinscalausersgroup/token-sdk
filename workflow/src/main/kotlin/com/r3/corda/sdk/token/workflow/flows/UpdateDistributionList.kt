package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.workflow.utilities.addPartyToDistributionList
import com.r3.corda.sdk.token.workflow.utilities.getDistributionList
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SignedData
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.utilities.unwrap

object UpdateDistributionList {

    @CordaSerializable
    data class DistributionListUpdate(val oldParty: Party, val newParty: Party, val linearId: UniqueIdentifier)

    // TODO Also ineffective if we are heavily using confidential identities
    class Initiator<T : EvolvableTokenType>(
            val tokenPointer: TokenPointer<T>,
            val oldParty: Party,
            val newParty: Party
    ) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val evolvableToken = tokenPointer.pointer.resolve(serviceHub).state.data
            val distributionListUpdate = DistributionListUpdate(oldParty, newParty, evolvableToken.linearId)
            val maintainers = evolvableToken.maintainers
            val maintainersSessions = maintainers.map { initiateFlow(it) }
            // Collect signatures from old and new parties
            // TODO surely there is some helper function for those three lines below?
            val updateBytes = distributionListUpdate.serialize()
            val ourSig = serviceHub.keyManagementService.sign(updateBytes.bytes, oldParty.owningKey)
            val signedUpdate = SignedData(updateBytes, ourSig)
            // TODO new party signs the update?
//            val newPartySession = initiateFlow(newParty)
//            subFlow()
            // TODO this is naive quick fix approach for now, should use data dist groups
            maintainersSessions.forEach {
                it.send(signedUpdate)
            }
        }
    }

    @InitiatedBy(UpdateDistributionList.Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val distListUpdate = otherSession.receive<SignedData<DistributionListUpdate>>().unwrap {
                val update = it.verified()
                // Check that request is signed by the oldParty.
                check(update.oldParty.owningKey == it.sig.by)
                // TODO add same check for new party
                // Check that the request comes from that party.
                check(update.oldParty == otherSession.counterparty)
                update
            }
            // Check that newParty is well known party.
            serviceHub.identityService.wellKnownPartyFromAnonymous(distListUpdate.newParty)
                    ?: throw IllegalArgumentException("Don't know about party: ${distListUpdate.newParty}")

            // TODO Slightly ineffective, perform direct query for pairs party, linearId
            val distributionList = getDistributionList(serviceHub, distListUpdate.linearId).map { it.party }.toSet()
            check(distListUpdate.oldParty in distributionList) {
                "Party ${distListUpdate.oldParty} is not in distribution list for ${distListUpdate.linearId}"
            }
            if (distListUpdate.newParty !in distributionList) {
                // Add new party to the dist list for this token.
                serviceHub.addPartyToDistributionList(distListUpdate.newParty, distListUpdate.linearId)
            }
            // TODO What about old party?
            // Remove entry (oldParty, linearId)? what if they have more assets dependent on that token id? for example lots of fungible etc
        }
    }
}

// maybe use the tx? but issuer doesn't have to see the tx
object DistributionListSign {
    class Initiator() : FlowLogic<SignedData<UpdateDistributionList.DistributionListUpdate>>() {
        @Suspendable
        override fun call(): SignedData<UpdateDistributionList.DistributionListUpdate> {
            //check that one of the parties is ours
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    class Responder() : FlowLogic<SignedData<UpdateDistributionList.DistributionListUpdate>>() {
        @Suspendable
        override fun call(): SignedData<UpdateDistributionList.DistributionListUpdate> {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}