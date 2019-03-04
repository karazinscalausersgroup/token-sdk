package com.r3.corda.sdk.token.contracts.commands

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TypeOnlyCommandData

/**
 * A standard set of commands for creating and updating [EvolvableTokenType]s. Additional commands can be added
 * which implement [EvolvableTokenTypeCommand]. For example, a stock token will require a "stock split" command,
 * which would implement the [EvolvableTokenTypeCommand] interface.
 *
 * Note, that for the time being, [EvolvableTokenType]s cannot be removed from the ledger. Doing so would break
 * the evolvable token model. Given that [EvolvableTokenType]s are not storage hungry, this is an acceptable
 * trade-off - they can just persist on the ledger even if they are not required any more.
 */
interface EvolvableTokenTypeCommand : CommandData

class Create : EvolvableTokenTypeCommand, TypeOnlyCommandData()
class Update : EvolvableTokenTypeCommand, TypeOnlyCommandData()