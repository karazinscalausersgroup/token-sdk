package com.r3.corda.sdk.token.contracts.states

import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party

/** Contains common [NonFungibleToken] functionality. */
abstract class AbstractToken<T : TokenType> : ContractState {

    /** The [AbstractParty] which is currently holding (some amount of) tokens. */
    abstract val holder: AbstractParty

    /** The current [holder] is always the sole participant. */
    override val participants: List<AbstractParty> get() = listOf(holder)

    /** The [TokenType]. */
    abstract val tokenType: TokenType

    /** The [IssuedTokenType]. */
    abstract val issuedTokenType: IssuedTokenType<T>

    /** The issuer [Party]. */
    abstract val issuer: Party

    /**
     * Converts [holder] into a more friendly string. It uses only the x500 organisation for [Party] objects and
     * shortens the public key for [AnonymousParty]s to the first 16 characters.
     * */
    protected val holderString: String
        get() {
            return (holder as? Party)?.name?.organisation ?: holder.owningKey.toStringShort().substring(0, 16)
        }

    /** For creating a copy of an existing [AbstractToken] with a new holder. */
    abstract fun withNewHolder(newHolder: AbstractParty): AbstractToken<T>
}