package com.r3.corda.sdk.token.contracts.commands

import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.CommandData

/**
 * [TokenCommand]s are linked to groups of input and output tokens, usually by the embeddable token type or some
 * [IssuedTokenType] token type. This needs to be done because if a transaction contains more than one type of token, we
 * need to be able to attribute the correct command to each group. The most simple way to do this is including an
 * [IssuedTokenType] in the command.
 */
interface TokenCommand<T : TokenType> : CommandData {
    val token: IssuedTokenType<T>
}

data class IssueTokenCommand<T : TokenType>(override val token: IssuedTokenType<T>) : TokenCommand<T>
data class MoveTokenCommand<T : TokenType>(override val token: IssuedTokenType<T>) : TokenCommand<T>
data class RedeemTokenCommand<T : TokenType>(override val token: IssuedTokenType<T>) : TokenCommand<T>