package com.r3.corda.sdk.token.contracts.utilities

import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import java.math.BigDecimal

/** Helpers for composing tokens with issuers, owners and amounts. */

// For parsing amount quantities of tokens that are not wrapped with an issuer. Like so: 1_000.GBP.
fun <T : TokenType> amount(amount: Int, token: T): Amount<T> = amount(amount.toLong(), token)

fun <T : TokenType> amount(amount: Long, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : TokenType> amount(amount: Double, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : TokenType> amount(amount: BigDecimal, token: T): Amount<T> = Amount.fromDecimal(amount, token)

// As above but works with embeddable tokens wrapped with an issuer.
fun <T : TokenType> amount(amount: Int, token: IssuedTokenType<T>): Amount<IssuedTokenType<T>> {
    return amount(amount.toLong(), token)
}

fun <T : TokenType> amount(amount: Long, token: IssuedTokenType<T>): Amount<IssuedTokenType<T>> {
    return Amount.fromDecimal(BigDecimal.valueOf(amount), token)
}

fun <T : TokenType> amount(amount: Double, token: IssuedTokenType<T>): Amount<IssuedTokenType<T>> {
    return Amount.fromDecimal(BigDecimal.valueOf(amount), token)
}

fun <T : TokenType> amount(amount: BigDecimal, token: IssuedTokenType<T>): Amount<IssuedTokenType<T>> {
    return Amount.fromDecimal(amount, token)
}

// For parsing amounts of embeddable tokens that are not wrapped with an issuer. Like so: 1_000 of token.
infix fun <T : TokenType> Int.of(token: T): Amount<T> = amount(this, token)

infix fun <T : TokenType> Long.of(token: T): Amount<T> = amount(this, token)
infix fun <T : TokenType> Double.of(token: T): Amount<T> = amount(this, token)
infix fun <T : TokenType> BigDecimal.of(token: T): Amount<T> = amount(this, token)

// As above but for tokens which are wrapped with an issuer. Like so: 1_000 of issuedToken.
infix fun <T : IssuedTokenType<U>, U : TokenType> Int.of(token: T): Amount<IssuedTokenType<U>> = amount(this, token)

infix fun <T : IssuedTokenType<U>, U : TokenType> Long.of(token: T): Amount<IssuedTokenType<U>> = amount(this, token)
infix fun <T : IssuedTokenType<U>, U : TokenType> Double.of(token: T): Amount<IssuedTokenType<U>> = amount(this, token)
infix fun <T : IssuedTokenType<U>, U : TokenType> BigDecimal.of(token: T): Amount<IssuedTokenType<U>> {
    return amount(this, token)
}

// For wrapping amounts of a token with an issuer: Amount<TokenType> -> Amount<IssuedTokenType<TokenType>>.
infix fun <T : TokenType> Amount<T>.issuedBy(issuer: Party): Amount<IssuedTokenType<T>> = _issuedBy(issuer)

infix fun <T : TokenType> Amount<T>._issuedBy(issuer: Party): Amount<IssuedTokenType<T>> {
    return Amount(quantity, displayTokenSize, uncheckedCast(token.issuedBy(issuer)))
}

// For wrapping tokens with an issuer: TokenType -> IssuedTokenType<TokenType>.
infix fun <T : TokenType> T.issuedBy(issuer: Party): IssuedTokenType<T> = _issuedBy(issuer)

infix fun <T : TokenType> T._issuedBy(issuer: Party): IssuedTokenType<T> = IssuedTokenType(issuer, this)

// For adding ownership information to a TokenType. Wraps amounts of some IssuedTokenType TokenType with an
// FungibleToken state.
infix fun <T : TokenType> Amount<IssuedTokenType<T>>.ownedBy(owner: AbstractParty): FungibleToken<T> = _ownedBy(owner)

infix fun <T : TokenType> Amount<IssuedTokenType<T>>._ownedBy(owner: AbstractParty): FungibleToken<T> {
    return FungibleToken(this, owner)
}

// As above but wraps the token with an NonFungibleToken state.
infix fun <T : TokenType> IssuedTokenType<T>.ownedBy(owner: AbstractParty): NonFungibleToken<T> = _ownedBy(owner)

infix fun <T : TokenType> IssuedTokenType<T>._ownedBy(owner: AbstractParty): NonFungibleToken<T> {
    return NonFungibleToken(this, owner)
}

// Add a notary to an abstract token.
infix fun <T : AbstractToken> T.withNotary(notary: Party): TransactionState<T> = _withNotary(notary)

infix fun <T : AbstractToken> T._withNotary(notary: Party): TransactionState<T> {
    return TransactionState(data = this, notary = notary)
}

// Add a notary to an evolvable token.
infix fun <T : EvolvableTokenType> T.withNotary(notary: Party): TransactionState<T> = _withNotary(notary)

infix fun <T : EvolvableTokenType> T._withNotary(notary: Party): TransactionState<T> {
    return TransactionState(data = this, notary = notary)
}

/** Helpers for summing [Amount]s of [IssuedTokenType]s. */

fun <T : IssuedTokenType<U>, U : TokenType> Iterable<Amount<T>>.sumIssuedTokensOrNull(): Amount<T>? {
    return if (!iterator().hasNext()) null else sumIssuedTokensOrThrow()
}

fun <T : IssuedTokenType<U>, U : TokenType> Iterable<Amount<T>>.sumIssuedTokensOrThrow(): Amount<T> {
    return reduce { left, right -> left + right }
}

fun <T : IssuedTokenType<U>, U : TokenType> Iterable<Amount<T>>.sumIssuedTokensOrZero(token: T): Amount<T> {
    return if (iterator().hasNext()) sumIssuedTokensOrThrow() else Amount.zero(token)
}

/** Helpers for summing [Amount]s of [TokenType]s. */

fun <T : TokenType> Iterable<Amount<T>>.sumTokensOrNull(): Amount<T>? {
    return if (!iterator().hasNext()) null else sumTokensOrThrow()
}

fun <T : TokenType> Iterable<Amount<T>>.sumTokensOrThrow(): Amount<T> {
    return reduce { left, right -> left + right }
}

fun <T : TokenType> Iterable<Amount<T>>.sumTokensOrZero(token: T): Amount<T> {
    return if (iterator().hasNext()) sumTokensOrThrow() else Amount.zero(token)
}

/**
 * Strips the issuer and returns an [Amount] of the raw token directly. This is useful when you are mixing code that
 * cares about specific issuers with code that will accept any, or which is imposing issuer constraints via some
 * other mechanism and the additional type safety is not wanted.
 */
fun <T : TokenType> Amount<IssuedTokenType<T>>.withoutIssuer(): Amount<T> {
    return Amount(quantity, displayTokenSize, token.tokenType)
}