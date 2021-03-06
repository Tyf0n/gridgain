// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed.dht;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheTxState.*;
import static org.gridgain.grid.kernal.processors.cache.GridCacheOperation.*;

/**
 * Replicated user transaction.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
public class GridDhtTxLocal<K, V> extends GridCacheTxLocalAdapter<K, V> implements GridCacheMappedVersion {
    /** */
    private UUID nearNodeId;

    /** Near future ID. */
    private GridUuid nearFutId;

    /** Near future ID. */
    private GridUuid nearMiniId;

    /** Near future ID. */
    private GridUuid nearFinFutId;

    /** Near future ID. */
    private GridUuid nearFinMiniId;

    /** Near XID. */
    private GridCacheVersion nearXidVer;

    /** Near mappings. */
    private Map<UUID, GridDistributedTxMapping<K, V>> nearMap =
        new ConcurrentHashMap<UUID, GridDistributedTxMapping<K, V>>(16, 0.75f, 1);

    /** DHT mappings. */
    private Map<UUID, GridDistributedTxMapping<K, V>> dhtMap =
        new ConcurrentHashMap<UUID, GridDistributedTxMapping<K, V>>(16, 0.75f, 1);

    /** Future. */
    @GridToStringExclude
    private final AtomicReference<GridDhtTxPrepareFuture<K, V>> prepFut =
        new AtomicReference<GridDhtTxPrepareFuture<K, V>>();

    /** Mapped flag. */
    private AtomicBoolean mapped = new AtomicBoolean();

    /** */
    private boolean syncCommit;

    /** */
    private boolean syncRollback;

    /** */
    private long dhtThreadId;

    /** */
    private boolean explicitLock;

    /** Initialize to {@code true} to be safe. */
    private boolean needsCompletedVers = true;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtTxLocal() {
        // No-op.
    }

    /**
     * @param nearNodeId Near node ID that initiated transaction.
     * @param nearXidVer Near transaction ID.
     * @param nearFutId Near future ID.
     * @param nearMiniId Near mini future ID.
     * @param nearThreadId Near thread ID.
     * @param implicit Implicit flag.
     * @param implicitSingle Implicit-with-single-key flag.
     * @param cctx Cache context.
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param invalidate Invalidation policy.
     * @param syncCommit Synchronous commit flag.
     * @param syncRollback Synchronous rollback flag.
     * @param explicitLock Explicit lock flag.
     */
    GridDhtTxLocal(
        UUID nearNodeId,
        GridCacheVersion nearXidVer,
        GridUuid nearFutId,
        GridUuid nearMiniId,
        long nearThreadId,
        boolean implicit,
        boolean implicitSingle,
        GridCacheContext<K, V> cctx,
        GridCacheTxConcurrency concurrency,
        GridCacheTxIsolation isolation,
        long timeout,
        boolean invalidate,
        boolean syncCommit,
        boolean syncRollback,
        boolean explicitLock) {
        super(cctx, cctx.versions().onReceivedAndNext(nearNodeId, nearXidVer), implicit, implicitSingle,
            concurrency, isolation, timeout, invalidate, false, false);

        assert cctx != null;
        assert nearNodeId != null;
        assert nearFutId != null;
        assert nearMiniId != null;
        assert nearXidVer != null;

        this.nearNodeId = nearNodeId;
        this.nearXidVer = nearXidVer;
        this.nearFutId = nearFutId;
        this.nearMiniId = nearMiniId;
        this.syncCommit = syncCommit;
        this.syncRollback = syncRollback;
        this.explicitLock = explicitLock;

        threadId = nearThreadId;

        dhtThreadId = Thread.currentThread().getId();
    }

    /** {@inheritDoc} */
    @Override public boolean dht() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public UUID eventNodeId() {
        return nearNodeId;
    }

    /** {@inheritDoc} */
    @Override public Collection<UUID> masterNodeIds() {
        assert nearNodeId != null;

        return Collections.singleton(nearNodeId);
    }

    /** {@inheritDoc} */
    @Override public UUID otherNodeId() {
        assert nearNodeId != null;

        return nearNodeId;
    }

    /** {@inheritDoc} */
    @Override public Collection<UUID> nodeIds() {
        Collection<UUID> ids = new GridLeanSet<UUID>();

        Collections.addAll(ids, nearNodeId, cctx.nodeId());

        ids.addAll(dhtMap.keySet());
        ids.addAll(nearMap.keySet());

        return ids;
    }

    /**
     * @param needsCompletedVers {@code True} if needs completed versions.
     */
    public void needsCompletedVersions(boolean needsCompletedVers) {
        this.needsCompletedVers = needsCompletedVers;
    }

    /** {@inheritDoc} */
    @Override public boolean needsCompletedVersions() {
        return needsCompletedVers;
    }

    /**
     * @return Explicit lock flag.
     */
    boolean explicitLock() {
        return explicitLock;
    }

    /**
     * @return DHT thread ID.
     */
    long dhtThreadId() {
        return dhtThreadId;
    }

    /**
     * @return Near node.
     */
    UUID nearNodeId() {
        return nearNodeId;
    }

    /**
     * @return Near XID.
     */
    public GridCacheVersion nearXidVersion() {
        return nearXidVer;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion mappedVersion() {
        return nearXidVer;
    }

    /**
     * @return Near future ID.
     */
    GridUuid nearFutureId() {
        return nearFutId;
    }

    /**
     * @return Near future mini ID.
     */
    GridUuid nearMiniId() {
        return nearMiniId;
    }

    /**
     * @return Near future ID.
     */
    public GridUuid nearFinishFutureId() {
        return nearFinFutId;
    }

    /**
     * @param nearFinFutId Near future ID.
     */
    public void nearFinishFutureId(GridUuid nearFinFutId) {
        this.nearFinFutId = nearFinFutId;
    }

    /**
     * @return Near future mini ID.
     */
    public GridUuid nearFinishMiniId() {
        return nearFinMiniId;
    }

    /**
     * @param nearFinMiniId Near future mini ID.
     */
    public void nearFinishMiniId(GridUuid nearFinMiniId) {
        this.nearFinMiniId = nearFinMiniId;
    }

    /** {@inheritDoc} */
    @Override public boolean syncCommit() {
        return syncCommit;
    }

    /** {@inheritDoc} */
    @Override public boolean syncRollback() {
        return syncRollback;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTxEx<K, V>> future() {
        return prepFut.get();
    }

    /** {@inheritDoc} */
    @Override protected boolean isSingleUpdate() {
        // Transaction updates should only happen from near transaction.
        return false;
    }

    /** {@inheritDoc} */
    @Override protected boolean isBatchUpdate() {
        // Transaction updates should only happen from near transaction.
        return false;
    }

    /**
     * Map explicit locks.
     */
    private void mapExplicitLocks() {
        if (!mapped.get()) {
            Map<GridNode, List<GridDhtCacheEntry<K, V>>> dhtEntryMap = null;
            Map<GridNode, List<GridDhtCacheEntry<K, V>>> nearEntryMap = null;

            for (GridCacheTxEntry<K, V> e : allEntries()) {
                assert e.cached() != null;

                if (e.cached() == null || e.cached().obsolete()) {
                    GridCacheEntryEx<K, V> cached = cctx.cache().entryEx(e.key());

                    e.cached(cached, cached.keyBytes());
                }

                while (true) {
                    try {
                        // Map explicit locks.
                        if (e.explicitVersion() != null && !e.explicitVersion().equals(xidVer)) {
                            if (dhtEntryMap == null)
                                dhtEntryMap = new GridLeanMap<GridNode, List<GridDhtCacheEntry<K, V>>>();

                            if (nearEntryMap == null)
                                nearEntryMap = new GridLeanMap<GridNode, List<GridDhtCacheEntry<K, V>>>();

                            cctx.dhtMap(nearNodeId, topologyVersion(),
                                (GridDhtCacheEntry<K, V>)e.cached(), log, dhtEntryMap, nearEntryMap);
                        }

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignore) {
                        GridCacheEntryEx<K, V> cached = cctx.cache().entryEx(e.key());

                        e.cached(cached, cached.keyBytes());
                    }
                }
            }

            if (!F.isEmpty(dhtEntryMap))
                addDhtMapping(dhtEntryMap);

            if (!F.isEmpty(nearEntryMap))
                addNearMapping(nearEntryMap);

            mapped.set(true);
        }
    }

    /**
     * @return DHT map.
     */
    Map<UUID, GridDistributedTxMapping<K, V>> dhtMap() {
        mapExplicitLocks();

        return dhtMap;
    }

    /**
     * @return Near map.
     */
    Map<UUID, GridDistributedTxMapping<K, V>> nearMap() {
        mapExplicitLocks();

        return nearMap;
    }

    /**
     * @param nodeId Node ID.
     * @return Mapping.
     */
    GridDistributedTxMapping<K, V> dhtMapping(UUID nodeId) {
        return dhtMap.get(nodeId);
    }

    /**
     * @param nodeId Node ID.
     * @return Mapping.
     */
    GridDistributedTxMapping<K, V> nearMapping(UUID nodeId) {
        return nearMap.get(nodeId);
    }

    /**
     * @param mappings Mappings to add.
     */
    void addDhtMapping(Map<GridNode, List<GridDhtCacheEntry<K, V>>> mappings) {
        addMapping(mappings, dhtMap);
    }

    /**
     * @param mappings Mappings to add.
     */
    void addNearMapping(Map<GridNode, List<GridDhtCacheEntry<K, V>>> mappings) {
        addMapping(mappings, nearMap);
    }

    /**
     * @param nodeId Node ID.
     * @return {@code True} if mapping was removed.
     */
    boolean removeMapping(UUID nodeId) {
        return removeMapping(nodeId, null, dhtMap) | removeMapping(nodeId, null, nearMap);
    }

    /**
     * @param nodeId Node ID.
     * @param entry Entry to remove.
     * @return {@code True} if was removed.
     */
    boolean removeDhtMapping(UUID nodeId, GridCacheEntryEx<K, V> entry) {
        return removeMapping(nodeId, entry, dhtMap);
    }

    /**
     * @param nodeId Node ID.
     * @param entry Entry to remove.
     * @return {@code True} if was removed.
     */
    boolean removeNearMapping(UUID nodeId, GridCacheEntryEx<K, V> entry) {
        return removeMapping(nodeId, entry, nearMap);
    }

    /**
     * @param nodeId Node ID.
     * @param entry Entry to remove.
     * @param map Map to remove from.
     * @return {@code True} if was removed.
     */
    private boolean removeMapping(UUID nodeId, @Nullable GridCacheEntryEx<K, V> entry,
        Map<UUID, GridDistributedTxMapping<K, V>> map) {
        if (entry != null) {
            if (log.isDebugEnabled())
                log.debug("Removing mapping for entry [nodeId=" + nodeId + ", entry=" + entry + ']');

            GridCacheTxEntry<K, V> txEntry = txMap.get(entry.key());

            if (txEntry == null)
                return false;

            GridDistributedTxMapping<K, V> m = map.get(nodeId);

            boolean ret = m != null && m.removeEntry(txEntry);

            if (m != null && m.empty())
                map.remove(nodeId);

            return ret;
        }
        else
            return map.remove(nodeId) != null;
    }

    /**
     * @param mappings Entry mappings.
     * @param map Transaction mappings.
     */
    private void addMapping(Map<GridNode, List<GridDhtCacheEntry<K, V>>> mappings,
        Map<UUID, GridDistributedTxMapping<K, V>> map) {
        for (Map.Entry<GridNode, List<GridDhtCacheEntry<K, V>>> mapping : mappings.entrySet()) {
            GridNode n = mapping.getKey();

            for (GridDhtCacheEntry<K, V> entry : mapping.getValue()) {
                GridCacheTxEntry<K, V> txEntry = txMap.get(entry.key());

                if (txEntry != null) {
                    GridDistributedTxMapping<K, V> m = map.get(n.id());

                    if (m == null)
                        map.put(n.id(), m = new GridDistributedTxMapping<K, V>(cctx.rich().rich(n)));

                    m.add(txEntry);
                }
            }
        }
    }


    /** {@inheritDoc} */
    @Override public void addInvalidPartition(int part) {
        assert false : "DHT transaction encountered invalid partition [part=" + part + ", tx=" + this + ']';
    }


    /**
     * Adds reader to cached entry.
     *
     * @param msgId Message ID.
     * @param cached Cached entry.
     * @param entry Transaction entry.
     * @return {@code True} if reader was added as a result of this call.
     */
    @Nullable private GridFuture<Boolean> addReader(long msgId, GridDhtCacheEntry<K, V> cached,
        GridCacheTxEntry<K, V> entry) {
        // Don't add local node as reader.
        if (!cctx.nodeId().equals(nearNodeId)) {
            while (true) {
                try {
                    return cached.addReader(nearNodeId, msgId);
                }
                catch (GridCacheEntryRemovedException ignore) {
                    if (log.isDebugEnabled())
                        log.debug("Got removed entry when adding to DHT local transaction: " + cached);

                    cached = cctx.dht().entryExx(entry.key());
                }
            }
        }

        return null;
    }

    /**
     * @param msgId Message ID.
     * @param e Entry to add.
     * @return Future for active transactions for the time when reader was added.
     * @throws GridException If failed.
     */
    @Nullable public GridFuture<Boolean> addEntry(long msgId, GridCacheTxEntry<K, V> e) throws GridException {
        init();

        assert state() == GridCacheTxState.ACTIVE : "Invalid tx state for adding entry [msgId=" + msgId + ", e=" + e +
            ", tx=" + this + ']';

        e.unmarshal(cctx, cctx.deploy().globalLoader());

        checkInternal(e.key());

        assert state() == GridCacheTxState.ACTIVE : "Invalid tx state for adding entry: " + e;

        try {
            GridCacheTxEntry<K, V> entry = txMap.get(e.key());

            if (entry != null) {
                entry.op(e.op()); // Absolutely must set operation, as default is DELETE.
                entry.value(e.value());
                entry.ttl(e.ttl());
                entry.expireTime(e.expireTime());
                entry.filters(e.filters());
            }
            else {
                entry = e.cleanCopy(cctx);

                while (true) {
                    GridDhtCacheEntry<K, V> cached = cctx.dht().entryExx(entry.key(), topologyVersion());

                    try {
                        // Set key bytes to avoid serializing in future.
                        cached.keyBytes(entry.keyBytes());

                        entry.cached(cached, entry.keyBytes());

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry when adding to dht tx (will retry): " + cached);
                    }
                }

                GridCacheVersion explicit = entry.explicitVersion();

                if (explicit != null) {
                    GridCacheVersion dhtVer = cctx.mvcc().mappedVersion(explicit);

                    if (dhtVer == null)
                        throw new GridException("Failed to find dht mapping for explicit entry version: " + entry);

                    entry.explicitVersion(dhtVer);
                }

                txMap.put(entry.key(), entry);

                if (log.isDebugEnabled())
                    log.debug("Added entry to transaction: " + entry);
            }

            return addReader(msgId, cctx.dht().entryExx(entry.key()), entry);
        }
        catch (GridDhtInvalidPartitionException ex) {
            addInvalidPartition(ex.partition());

            return new GridFinishedFuture<Boolean>(cctx.kernalContext(), true);
        }
    }

    /**
     * @param keys Keys.
     * @param msgId Message ID.
     * @param implicit Implicit flag.
     * @param read Read flag.
     * @return Lock future.
     */
    @SuppressWarnings( {"IfMayBeConditional"})
    public GridFuture<GridCacheReturn<V>> lockAllAsync(Collection<? extends K> keys, long msgId,
        boolean implicit, final boolean read) {
        try {
            checkValid(CU.<K, V>empty());
        }
        catch (GridException e) {
            return new GridFinishedFuture<GridCacheReturn<V>>(cctx.kernalContext(), e);
        }

        final GridCacheReturn<V> ret = new GridCacheReturn<V>(false);

        if (F.isEmpty(keys))
            return new GridFinishedFuture<GridCacheReturn<V>>(cctx.kernalContext(), ret);

        init();

        try {
            Set<K> skipped = null;

            GridCompoundFuture<Boolean, Boolean> txFut = null;

            // Enlist locks into transaction.
            for (K key : keys) {
                if (key == null)
                    continue;

                GridCacheTxEntry<K, V> txEntry = entry(key);

                // First time access.
                if (txEntry == null) {
                    GridDhtCacheEntry<K, V> cached = cctx.dht().entryExx(key, topologyVersion());

                    cached.unswap();

                    txEntry = addEntry(NOOP, null, cached, CU.<K, V>empty());

                    txEntry.cached(cached, txEntry.keyBytes());

                    GridFuture<Boolean> f = addReader(msgId, cached, txEntry);

                    if (f != null) {
                        if (txFut == null)
                            txFut = new GridCompoundFuture<Boolean, Boolean>(cctx.kernalContext(), CU.boolReducer());

                        txFut.add(f);
                    }
                }
                else {
                    if (skipped == null)
                        skipped = new GridLeanSet<K>();

                    skipped.add(key);
                }
            }

            if (txFut != null)
                txFut.markInitialized();

            assert pessimistic();

            // Acquire locks only after having added operation to the write set.
            // Otherwise, during rollback we will not know whether locks need
            // to be rolled back.
            // Loose all skipped and previously locked (we cannot reenter locks here).
            final Collection<? extends K> passedKeys = skipped != null ? F.view(keys, F.notIn0(skipped)) : keys;

            if (log.isDebugEnabled())
                log.debug("Lock keys: " + passedKeys);

            if (txFut == null || txFut.isDone())
                return obtainLockAsync(ret, passedKeys, read, skipped);
            else {
                final Set<K> skip = skipped;

                // Wait for active transactions to complete.
                return new GridEmbeddedFuture<GridCacheReturn<V>, Boolean>(
                    txFut,
                    new C2<Boolean, Exception, GridFuture<GridCacheReturn<V>>>() {
                        @Override public GridFuture<GridCacheReturn<V>> apply(Boolean b, Exception e) {
                            if (e != null)
                                throw new GridClosureException(e);

                            return obtainLockAsync(ret, passedKeys, read, skip);
                        }
                    },
                    cctx.kernalContext());
            }
        }
        catch (GridException e) {
            setRollbackOnly();

            return new GridFinishedFuture<GridCacheReturn<V>>(cctx.kernalContext(), e);
        }
    }

    /**
     * @param ret Return value.
     * @param passedKeys Passed keys.
     * @param read {@code True} if read.
     * @param skipped Skipped keys.
     * @return Future for lock acquisition.
     */
    private GridFuture<GridCacheReturn<V>> obtainLockAsync(GridCacheReturn<V> ret,
        final Collection<? extends K> passedKeys, boolean read, final Set<K> skipped) {
        if (log.isDebugEnabled())
            log.debug("Before acquiring transaction lock on keys [passedKeys=" + passedKeys + ", skipped=" +
                skipped + ']');

        if (passedKeys.isEmpty())
            return new GridFinishedFuture<GridCacheReturn<V>>(cctx.kernalContext(), ret);

        GridFuture<Boolean> fut = cctx.cache().txLockAsync(passedKeys,
            lockTimeout(), this, read, /*retval*/false, isolation, isInvalidate(), CU.<K, V>empty());

        return new GridEmbeddedFuture<GridCacheReturn<V>, Boolean>(
            fut,
            new PLC1<GridCacheReturn<V>>(ret) {
                @Override protected GridCacheReturn<V> postLock(GridCacheReturn<V> ret) throws GridException {
                    if (log.isDebugEnabled())
                        log.debug("Acquired transaction lock on keys: " + passedKeys);

                    postLockWrite(passedKeys, skipped, ret, /*remove*/false, /*retval*/false, CU.<K, V>empty());

                    return ret;
                }
            },
            cctx.kernalContext());
    }

    /** {@inheritDoc} */
    // TODO: CODE: review partitioned EC.
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override public boolean finishEC(boolean commit) throws GridException {
        GridException err = null;

        try {
            if (commit)
                state(COMMITTING);
            else
                state(ROLLING_BACK);

            if (commit && !isRollbackOnly()) {
                if (!userCommitEC())
                    return false;
            }
            else
                userRollback();
        }
        catch (GridException e) {
            err = e;

            commit = false;

            // If heuristic error.
            if (!isRollbackOnly())
                invalidate(true);
        }

        if (err != null) {
            state(UNKNOWN);

            throw err;
        }
        else {
            if (!state(commit ? COMMITTED : ROLLED_BACK)) {
                state(UNKNOWN);

                throw new GridException("Invalid transaction state for commit or rollback: " + this);
            }
        }

        return true;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"CatchGenericClass", "ThrowableInstanceNeverThrown"})
    @Override public void finish(boolean commit) throws GridException {
        assert nearFinFutId != null || isInvalidate() || !commit || isSystemInvalidate() :
            "Invalid state [nearFinFutId=" + nearFinFutId + ", isInvalidate=" + isInvalidate() + ", commit=" + commit +
            ", sysInvalidate=" + isSystemInvalidate() + ']';
        assert nearMiniId != null;

        if (log.isDebugEnabled())
            log.debug("Finishing dht local tx [tx=" + this + ", commit=" + commit + "]");

        if (commit) {
            if (!state(COMMITTING)) {
                GridCacheTxState state = state();

                if (state != COMMITTING && state != COMMITTED)
                    throw new GridException("Invalid transaction state for commit [state=" + state() +
                        ", tx=" + this + ']');
                else {
                    if (log.isDebugEnabled())
                        log.debug("Invalid transaction state for commit (another thread is committing): " + this);

                    return;
                }
            }
        }
        else {
            if (!state(ROLLING_BACK)) {
                if (log.isDebugEnabled())
                    log.debug("Invalid transaction state for rollback [state=" + state() + ", tx=" + this + ']');

                return;
            }
        }

        GridException err = null;

        // Commit to DB first. This way if there is a failure, transaction
        // won't be committed.
        try {
            if (commit && !isRollbackOnly())
                userCommit();
            else
                userRollback();
        }
        catch (GridException e) {
            err = e;

            commit = false;

            // If heuristic error.
            if (!isRollbackOnly()) {
                invalidate = true;

                U.warn(log, "Set transaction invalidation flag to true due to error [tx=" + this +
                    ", err=" + err + ']');
            }
        }

        if (err != null) {
            state(UNKNOWN);

            throw err;
        }
        else {
            if (!state(commit ? COMMITTED : ROLLED_BACK)) {
                state(UNKNOWN);

                throw new GridException("Invalid transaction state for commit or rollback: " + this);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridCacheTxEx<K, V>> prepareAsync() {
        GridDhtTxPrepareFuture<K, V> fut = prepFut.get();

        if (fut == null) {
            // Future must be created before any exception can be thrown.
            if (!prepFut.compareAndSet(null, fut = new GridDhtTxPrepareFuture<K, V>(cctx, this)))
                return prepFut.get();
        }
        else
            // Prepare was called explicitly.
            return fut;

        if (!state(PREPARING)) {
            if (setRollbackOnly()) {
                if (timedOut())
                    fut.onError(new GridCacheTxTimeoutException("Transaction timed out and was rolled back: " + this));
                else
                    fut.onError(new GridException("Invalid transaction state for prepare [state=" + state() +
                        ", tx=" + this + ']'));
            }
            else
                fut.onError(new GridCacheTxRollbackException("Invalid transaction state for prepare [state=" + state()
                    + ", tx=" + this + ']'));

            return fut;
        }

        // For pessimistic mode we don't distribute prepare request.
        if (pessimistic()) {
            try {
                userPrepare();

                if (!state(PREPARED)) {
                    setRollbackOnly();

                    fut.onError(new GridException("Invalid transaction state for commit [state=" + state() +
                        ", tx=" + this + ']'));

                    return fut;
                }

                fut.complete();

                return fut;
            }
            catch (GridException e) {
                fut.onError(e);

                return fut;
            }
        }

        try {
            userPrepare();

            // This will attempt to locally commit
            // EVENTUALLY CONSISTENT transactions.
            fut.onPreparedEC();

            // Make sure to add future before calling prepare on it.
            cctx.mvcc().addFuture(fut);

            fut.prepare();
        }
        catch (GridCacheTxTimeoutException e) {
            fut.onError(e);
        }
        catch (GridCacheTxOptimisticException e) {
            fut.onError(e);
        }
        catch (GridException e) {
            setRollbackOnly();

            fut.onError(new GridCacheTxRollbackException("Failed to prepare transaction: " + this, e));

            try {
                rollback();
            }
            catch (GridCacheTxOptimisticException e1) {
                if (log.isDebugEnabled())
                    log.debug("Failed optimistically to prepare transaction [tx=" + this + ", e=" + e1 + ']');

                fut.onError(e);
            }
            catch (GridException e1) {
                U.error(log, "Failed to rollback transaction: " + this, e1);
            }
        }

        return fut;
    }

    /**
     * @param commit Commit flag.
     * @param err Error, if any.
     */
    private void sendFinishReply(boolean commit, @Nullable Throwable err) {
        if (nearFinFutId != null) {
            if (nearNodeId.equals(cctx.localNodeId())) {
                if (log.isDebugEnabled())
                    log.debug("Skipping response sending to local node: " + this);

                return;
            }

            GridNearTxFinishResponse<K, V> res = new GridNearTxFinishResponse<K, V>(nearXidVer, nearFinFutId,
                nearFinMiniId, err);

            try {
                cctx.io().send(nearNodeId, res);
            }
            catch (GridTopologyException ignored) {
                if (log.isDebugEnabled())
                    log.debug("Node left before sending finish response (transaction was committed) [node=" +
                        nearNodeId + ", res=" + res + ']');
            }
            catch (Throwable ex) {
                U.error(log, "Failed to send finish response to node (transaction was " +
                    (commit ? "committed" : "rolledback") + ") [node=" + nearNodeId + ", res=" + res + ']', ex);
            }
        }
        else if (log.isDebugEnabled())
            log.debug("Will not send finish reply because sender node has not sent finish request yet: " + this);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override public GridFuture<GridCacheTx> commitAsync() {
        if (log.isDebugEnabled())
            log.debug("Committing dht local tx: " + this);

        prepareAsync();

        final GridDhtTxFinishFuture<K, V> fut = new GridDhtTxFinishFuture<K, V>(cctx, this, /*commit*/true);

        cctx.mvcc().addFuture(fut);

        if (syncCommit || explicitLock) {
            fut.listenAsync(new CI1<GridFuture<GridCacheTx>>() {
                @Override public void apply(GridFuture<GridCacheTx> f) {
                    Throwable err = null;

                    try {
                        f.get();
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to commit transaction: " + this, e);

                        err = e;
                    }

                    sendFinishReply(true, err);
                }
            });
        }

        prepFut.get().listenAsync(new CI1<GridFuture<GridCacheTxEx<K, V>>>() {
            @Override public void apply(GridFuture<GridCacheTxEx<K, V>> f) {
                try {
                    f.get(); // Check for errors of a parent future.

                    finish(true);

                    fut.finish();
                }
                catch (GridCacheTxOptimisticException e) {
                    if (log.isDebugEnabled())
                        log.debug("Failed optimistically to prepare transaction [tx=" + this + ", e=" + e + ']');

                    fut.onError(e);
                }
                catch (GridException e) {
                    U.error(log, "Failed to prepare transaction: " + this, e);

                    fut.onError(e);
                }
            }
        });

        return fut;
    }

    /** {@inheritDoc} */
    @Override public void rollback() throws GridException {
        try {
            rollbackAsync().get();
        }
        finally {
            cctx.tm().txContextReset();
        }
    }

    /**
     * TODO: Put async rollback on public API.
     * @return Rollback future.
     */
    public GridFuture<GridCacheTx> rollbackAsync() {
        GridDhtTxPrepareFuture<K, V> prepFut = this.prepFut.get();

        final GridDhtTxFinishFuture<K, V> fut = new GridDhtTxFinishFuture<K, V>(cctx, this, /*rollback*/false);

        if (syncRollback) {
            fut.listenAsync(new CI1<GridFuture<GridCacheTx>>() {
                @Override public void apply(GridFuture<GridCacheTx> f) {
                    Throwable err = null;

                    try {
                        f.get();
                    }
                    catch (GridCacheTxOptimisticException e) {
                        if (log.isDebugEnabled())
                            log.debug("Failed optimistically to prepare transaction [tx=" + this + ", e=" + e + ']');

                        fut.onError(e);
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to rollback transaction: " + this, e);

                        err = e;
                    }

                    sendFinishReply(false, err);
                }
            });
        }

        cctx.mvcc().addFuture(fut);

        if (prepFut == null) {
            try {
                finish(false);
            }
            catch (GridCacheTxOptimisticException e) {
                if (log.isDebugEnabled())
                    log.debug("Failed optimistically to prepare transaction [tx=" + this + ", e=" + e + ']');

                fut.onError(e);
            }
            catch (GridException e) {
                U.error(log, "Failed to rollback transaction (will make the best effort to rollback remote nodes): " +
                    this, e);
            }

            fut.finish();
        }
        else {
            prepFut.complete();

            prepFut.listenAsync(new CI1<GridFuture<GridCacheTxEx<K, V>>>() {
                @Override public void apply(GridFuture<GridCacheTxEx<K, V>> f) {
                    try {
                        f.get(); // Check for errors of a parent future.
                    }
                    catch (GridException e) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to prepare or rollback transaction [tx=" + this + ", e=" + e + ']');
                    }

                    try {
                        finish(false);
                    }
                    catch (GridException e) {
                        U.error(log, "Failed to gracefully rollback transaction: " + this, e);

                        fut.onError(e);
                    }

                    fut.finish();
                }
            });
        }

        return fut;
    }

    /** {@inheritDoc} */
    @Override public void addLocalCandidates(K key, Collection<GridCacheMvccCandidate<K>> cands) {
        /* No-op. */
    }

    /** {@inheritDoc} */
    @Override public Map<K, Collection<GridCacheMvccCandidate<K>>> localCandidates() {
        return Collections.emptyMap();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridDhtTxLocal.class, this, "nearNodes", nearMap.keySet(),
            "dhtNodes", dhtMap.keySet(), "super", super.toString());
    }
}
