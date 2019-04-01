package com.r3.corda.sdk.token.workflow.selection

import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.utilities.contextLogger
import rx.Observable
import java.lang.RuntimeException
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.function.Predicate

@CordaService
class VaultWatcherService(appServiceHub: AppServiceHub? = null) {

    companion object {
        val LOG = contextLogger()
    }

    private val unlocker = Executors.newSingleThreadScheduledExecutor()

    //owner -> tokenClass -> tokenIdentifier -> List
    private val cache: ConcurrentMap<PublicKey, ConcurrentMap<Class<*>, ConcurrentMap<String, ConcurrentMap<StateAndRef<FungibleToken<TokenType>>, Boolean>>>> = ConcurrentHashMap()

    //will be used to stop adding and removing AtomicMarkableReference for every single consume / add event.

    init {
        if (appServiceHub != null) {
            val pageSize = 1000
            var currentPage = DEFAULT_PAGE_NUM;
            var (existingStates, observable) = appServiceHub.vaultService.trackBy(FungibleToken::class.java, PageSpecification(pageNumber = currentPage, pageSize = pageSize))
            val listOfThings = mutableListOf<StateAndRef<FungibleToken<TokenType>>>()
            while (existingStates.states.isNotEmpty()) {
                listOfThings.addAll(existingStates.states as Iterable<StateAndRef<FungibleToken<TokenType>>>)
                existingStates = appServiceHub.vaultService.queryBy(FungibleToken::class.java, PageSpecification(pageNumber = ++currentPage, pageSize = pageSize))
            }

            listOfThings.forEach(::addTokenToCache)
            (observable as Observable<Vault.Update<FungibleToken<TokenType>>>).doOnNext(::onVaultUpdate)
        }
    }

    fun onVaultUpdate(t: Vault.Update<FungibleToken<TokenType>>) {
        t.consumed.forEach(::removeTokenFromCache)
        t.produced.forEach(::addTokenToCache)
    }

    fun removeTokenFromCache(it: StateAndRef<FungibleToken<*>>) {
        val (owner, type, typeId) = processToken(it.state.data)
        val tokenSet = getTokenSet(owner, type, typeId)
        if (tokenSet.remove(it) != true) {
            LOG.warn("Received a consumption event for a token ${it.ref} which was either not present in the token cache, or was not locked")
        }
    }

    fun addTokenToCache(stateAndRef: StateAndRef<FungibleToken<out TokenType>>) {
        val token = stateAndRef.state.data
        val (owner, type, typeId) = processToken(token)
        val tokensForTypeInfo = getTokenSet(owner, type, typeId)
        val existingMark = tokensForTypeInfo.putIfAbsent(stateAndRef as StateAndRef<FungibleToken<TokenType>>, false)
        existingMark?.let {
            LOG.warn("Attempted to overwrite existing token ${stateAndRef.ref} during cache initialization, this suggests incorrect vault behaviours")
        }
    }

    fun unlockToken(stateAndRef: StateAndRef<FungibleToken<TokenType>>){
        val token = stateAndRef.state.data
        val (owner, type, typeId) = processToken(token)
        val tokensForTypeInfo = getTokenSet(owner, type, typeId)
        tokensForTypeInfo.replace(stateAndRef, true, false)
    }

    fun selectTokens(
            owner: PublicKey,
            amountRequested: Amount<IssuedTokenType<TokenType>>,
            predicate: Predicate<StateAndRef<FungibleToken<TokenType>>>? = null
    ): MutableList<StateAndRef<FungibleToken<TokenType>>> {
        val set = getTokenSet(owner, amountRequested.token.tokenType.tokenClass, amountRequested.token.tokenType.tokenIdentifier)
        val lockedTokens = mutableListOf<StateAndRef<FungibleToken< TokenType>>>()
        var amountLocked: Amount<IssuedTokenType<TokenType>> = amountRequested.copy(quantity = 0)
        for (tokenStateAndRef in set.keys) {
            //does the token satisfy the (optional) predicate?
            if (predicate?.test(tokenStateAndRef) != false) {
                //if so, race to lock the token, expected oldValue = false
                if (set.replace(tokenStateAndRef, false, true)) {
                    //we won the race to lock this token
                    lockedTokens.add(tokenStateAndRef)
                    val token = tokenStateAndRef.state.data
                    amountLocked += token.amount
                    if (amountLocked >= amountRequested) {
                        break
                    }
                }
            }
        }
        if (amountLocked < amountRequested) {
            throw InsufficientBalanceException("Could not find enough tokens to satisfy token request")
        }
        return lockedTokens

    }

    private fun processToken(token: FungibleToken<*>): Triple<PublicKey, Class<*>, String> {
        val owner = token.holder.owningKey
        val type = token.amount.token.tokenType.tokenClass
        val typeId = token.amount.token.tokenType.tokenIdentifier
        return Triple(owner, type, typeId)
    }

    private fun getTokenSet(
            owner: PublicKey,
            type: Class<*>,
            typeId: String
    ): ConcurrentMap<StateAndRef<FungibleToken<TokenType>>, Boolean> {
        return cache.computeIfAbsent(owner) {
            ConcurrentHashMap()
        }.computeIfAbsent(type) {
            ConcurrentHashMap()
        }.computeIfAbsent(typeId) {
            ConcurrentHashMap()
        }
    }
}


class InsufficientBalanceException(message: String) : RuntimeException(message)
