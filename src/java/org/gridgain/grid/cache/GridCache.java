// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.cache.store.*;
import org.gridgain.grid.lang.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * <img id="callout_img" src="{@docRoot}/img/callout_blue.gif"><span id="callout_blue">Start Here</span>&nbsp;
 * Main <b>Data Grid</b> APIs.
 * <h1 class="header">Rich API</h1>
 * This API extends {@link GridCacheProjection} API which contains vast majority of cache functionality
 * and documentation. In addition to {@link GridCacheProjection} functionality this API provides:
 * <ul>
 * <li>
 *  Various {@code 'loadCache(..)'} methods to load cache either synchronously or asynchronously.
 *  These methods don't specify any keys to load, and leave it to the underlying storage to load cache
 *  data based on the optionally passed in arguments.
 * </li>
 * <li>
 *  Methods like {@code 'tx{Un}Synchronize(..)'} witch allow to get notifications for transaction state changes.
 *  This feature is very useful when integrating cache transactions with some other in-house transactions.
 * </li>
 * </li>
 * <li>Method {@link #metrics()} to provide metrics for the whole cache.</li>
 * <li>Method {@link #configuration()} to provide cache configuration bean.</li>
 * <li>Method {@link #randomEntry()} to retrieve random entry from cache.</li>
 * <li>Method {@link #overflowSize()} to get the size of the swap storage.</li>
 * </ul>
 * <h1 class="header">Named Data Structures</h1>
 * Cache provides some types of named structures such as {@link GridCacheAtomicLong},
 * {@link GridCacheAtomicReference}, {@link GridCacheAtomicStamped}, and {@link GridCacheAtomicSequence}.
 * All instances of these structures must have unique names in cache regardless of their type.
 * <h1 class="header">Null Keys or Values</h1>
 * Neither {@code null} keys or values are allowed to be stored in cache. If a {@code null} value
 * happens to be in cache (e.g. after invalidation or remove), then cache will treat this case
 * as there is no value at all.
 * <p>
 * All API method with {@link Nullable @Nullable} annotation on method parameters
 * or return values either accept or may return a {@code null} value. Parameters that do not
 * have this annotation cannot be {@code null} and invoking method with a {@code null} parameter
 * in this case will result in {@link NullPointerException}.
 * <h1 class="header">Allowed Discovery SPIs</h1>
 * When working with distributed cache, proper node ordering is required on startup. For that
 * reason cache can be used only with implementations of {@link org.gridgain.grid.spi.discovery.GridDiscoverySpi}
 * that are annotated with {@link org.gridgain.grid.spi.discovery.GridDiscoverySpiOrderSupport} annotation.
 * User can also relax this annotation and can manually ensure that nodes are started sequentially (not concurrently).
 * To do that, {@link GridSystemProperties#GG_NO_DISCO_ORDER} must be provided at startup.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 * @param <K> Cache key type.
 * @param <V> Cache value type.
 */
public interface GridCache<K, V> extends GridCacheProjection<K, V> {
    /**
     * Gets configuration bean for this cache.
     *
     * @return Configuration bean for this cache.
     */
    public GridCacheConfiguration configuration();

    /**
     * Registers transactions synchronizations for all transactions started by this cache.
     * Use it whenever you need to get notifications on transaction lifecycle and possibly change
     * its course. It is also particularly useful when integrating cache transactions
     * with some other in-house transactions.
     *
     * @param syncs Transaction synchronizations to register.
     */
    public void txSynchronize(@Nullable GridCacheTxSynchronization... syncs);

    /**
     * Removes transaction synchronizations.
     *
     * @param syncs Transactions synchronizations to remove.
     * @see #txSynchronize(GridCacheTxSynchronization...)
     */
    public void txUnsynchronize(@Nullable GridCacheTxSynchronization... syncs);

    /**
     * Gets registered transaction synchronizations.
     *
     * @return Registered transaction synchronizations.
     * @see #txSynchronize(GridCacheTxSynchronization...)
     */
    public Collection<GridCacheTxSynchronization> txSynchronizations();

    /**
     * Gets metrics (statistics) for this cache.
     *
     * @return Cache metrics.
     */
    public GridCacheMetrics metrics();

    /**
     * Gets metrics (statistics) for all queries executed in this cache. Metrics
     * are grouped by query clause (e.g. SQL clause), query type, and return value.
     * <p>
     * Note that only the last {@code 1000} query metrics are kept. This should be
     * enough for majority of the applications, as generally applications have
     * significantly less than {@code 1000} different queries that are executed.
     * <p>
     * Note that in addition to query metrics, you can also enable query tracing by setting
     * {@code "org.gridgain.cache.queries"} logging category to {@code DEBUG} level.
     *
     * @return Queries metrics or {@code null} if a query manager is not provided.
     */
    @Nullable public Collection<GridCacheQueryMetrics> queryMetrics();

    /**
     * Gets size (in bytes) of all entries swapped to disk.
     *
     * @return Size (in bytes) of all entries swapped to disk.
     * @throws GridException In case of error.
     */
    public long overflowSize() throws GridException;

    /**
     * Delegates to {@link GridCacheStore#loadAll(String,GridInClosure2,Object...)} method
     * to load state from the underlying persistent storage. The loaded values
     * will then be given to the optionally passed in predicate, and, if the predicate returns
     * {@code true}, will be stored in cache. If predicate is {@code null}, then
     * all loaded values will be stored in cache.
     * <p>
     * Note that this method does not receive keys as a parameter, so it is up to
     * {@link GridCacheStore} implementation to provide all the data to be loaded.
     * <p>
     * This method is not transactional and may end up loading a stale value into
     * cache if another thread has updated the value immediately after it has been
     * loaded. It is mostly useful when pre-loading the cache from underlying
     * data store before start, or for read-only caches.
     *
     * @param ttl Time to live for loaded entries ({@code 0} for infinity).
     * @param p Optional predicate (may be {@code null}). If provided, will be used to
     *      filter values to be put into cache.
     * @param args Optional user arguments to be passed into
     *      {@link GridCacheStore#loadAll(String, GridInClosure2, Object...)} method.
     * @throws GridException If loading failed.
     */
    public void loadCache(@Nullable GridPredicate2<K, V> p, long ttl, @Nullable Object... args) throws GridException;

    /**
     * Asynchronously delegates to {@link GridCacheStore#loadAll(String, GridInClosure2, Object...)} method
     * to reload state from the underlying persistent storage. The reloaded values
     * will then be given to the optionally passed in predicate, and if the predicate returns
     * {@code true}, will be stored in cache. If predicate is {@code null}, then
     * all reloaded values will be stored in cache.
     * <p>
     * Note that this method does not receive keys as a parameter, so it is up to
     * {@link GridCacheStore} implementation to provide all the data to be loaded.
     * <p>
     * This method is not transactional and may end up loading a stale value into
     * cache if another thread has updated the value immediately after it has been
     * loaded. It is mostly useful when pre-loading the cache from underlying
     * data store before start, or for read-only caches.
     *
     * @param p Optional predicate (may be {@code null}). If provided, will be used to
     *      filter values to be put into cache.
     * @param ttl Time to live for loaded entries ({@code 0} for infinity).
     * @param args Optional user arguments to be passed into
     *      {@link GridCacheStore#loadAll(String,GridInClosure2,Object...)} method.
     * @return Future to be completed whenever loading completes.
     */
    public GridFuture<?> loadCacheAsync(@Nullable GridPredicate2<K, V> p, long ttl, @Nullable Object... args);

    /**
     * Gets a random entry out of cache. In the worst cache scenario this method
     * has complexity of <pre>O(S * N/64)</pre> where {@code N} is the size of internal hash
     * table and {@code S} is the number of hash table buckets to sample, which is {@code 5}
     * by default. However, if the table is pretty dense, with density factor of {@code N/64},
     * which is true for near fully populated caches, this method will generally perform significantly
     * faster with complexity of O(S) where {@code S = 5}.
     * <p>
     * Note that this method is not available on {@link GridCacheProjection} API since it is
     * impossible (or very hard) to deterministically return a number value when pre-filtering
     * and post-filtering is involved (e.g. projection level predicate filters).
     *
     * @return Random entry, or {@code null} if cache is empty.
     */
    @Nullable public GridCacheEntry<K, V> randomEntry();

    /**
     * Will get a sequence from cache or create one with initial value of
     * {@code 0} if it has not been created yet. This method is analogous to
     * calling {@link #atomicSequence(String, long, boolean)} sequence(name, 0, false)}.
     *
     * @param name Sequence name.
     * @return Sequence.
     * @throws GridException If sequence could not be fetched or created.
     */
    public GridCacheAtomicSequence atomicSequence(String name) throws GridException;

    /**
     * Will get an atomic sequence from cache and create one if it has not been created yet.
     *
     * @param name Sequence name.
     * @param initVal Initial value for sequence. If sequence already cached, {@code initVal} will be ignored.
     * @param persistent If {@code true} sequence will be put to the storage, otherwise won't.
     *      Note that user must implement storage himself.
     * @return Sequence for the given name.
     * @throws GridException If sequence could not be fetched or created.
     */
    public GridCacheAtomicSequence atomicSequence(String name, long initVal, boolean persistent) throws GridException;

    /**
     * Remove sequence from cache.
     *
     * @param name Sequence name.
     * @return {@code True} if sequence has been removed, {@code false} otherwise.
     * @throws GridException If remove failed.
     */
    public boolean removeAtomicSequence(String name) throws GridException;

    /**
     * Will get a atomic long from cache or create one with initial value of
     * {@code 0} if it has not been created yet. This method is analogous to
     * calling {@link #atomicLong(String,long,boolean) atomicLong(name, 0, false)}.
     *
     * @param name Atomic long name.
     * @return Atomic long for the given name.
     * @throws GridException If atomic long could not be fetched or created.
     */
    public GridCacheAtomicLong atomicLong(String name) throws GridException;

    /**
     * Will get a atomic long from cache and create one if it has not been created yet.
     *
     * @param name Name of atomic long.
     * @param initVal Initial value for atomic long. If atomic long already cached, {@code initVal}
     *        will be ignored.
     * @param persistent If {@code true} atomic long will be put to the storage, otherwise won't.
     *      Note that user must implement storage himself.
     * @return Atomic long.
     * @throws GridException If atomic long could not be fetched or created.
     */
    public GridCacheAtomicLong atomicLong(String name, long initVal, boolean persistent) throws GridException;

    /**
     * Remove atomic long from cache.
     *
     * @param name Name of atomic long.
     * @return {@code True} if atomic long has been removed, {@code false} otherwise.
     * @throws GridException If removing failed.
     */
    public boolean removeAtomicLong(String name) throws GridException;

    /**
     * Will get a named queue from cache and create one if it has not been created yet.
     * If queue is present in cache already, queue properties will not be changed.
     * This method is analogous to calling {@link #queue(String,GridCacheQueueType, int, boolean)}
     * queue(name, FIFO, 0 , true)}.
     *
     * @param name Name of queue.
     * @return Queue.
     * @throws GridException If removing failed.
     */
    public <T> GridCacheQueue<T> queue(String name) throws GridException;

    /**
     * Will get a named queue from cache and create one if it has not been created yet.
     * If queue is present in cache already, queue properties will not be changed.
     * This method is analogous to calling {@link #queue(String,GridCacheQueueType, int, boolean)}
     * queue(name, type, 0, true)}.
     *
     * @param name Name of queue.
     * @param type Type of queue.
     * @return Queue.
     * @throws GridException If removing failed.
     */
    public <T> GridCacheQueue<T> queue(String name, GridCacheQueueType type) throws GridException;

    /**
     * Will get a named queue from cache and create one if it has not been created yet.
     * If queue is present in cache already, queue properties will not be changed.
     * This method is analogous to calling {@link #queue(String,GridCacheQueueType, int, boolean)}
     * queue(name, type, capacity, true)}.
     *
     * @param name Name of queue.
     * @param type Type of queue.
     * @param cap Capacity of queue, {@code 0} for unbounded queue.
     * @return Queue.
     * @throws GridException If removing failed.
     */
    public <T> GridCacheQueue<T> queue(String name, GridCacheQueueType type, int cap) throws GridException;

    /**
     * Will get a named queue from cache and create one if it has not been created yet.
     * If queue is present in cache already, queue properties will not be changed. Use
     * collocation for {@link GridCacheMode#PARTITIONED} caches if you have lots of relatively
     * small queues as it will make fetching, querying, and iteration a lot faster. If you have
     * few very large queues, then you should consider turning off collocation as they simply
     * may not fit in a single node's memory. However note that in this case
     * to get a single element off the queue all nodes may have to be queried.
     *
     * @param name Name of queue.
     * @param type Type of queue.
     * @param cap Capacity of queue, {@code 0} for unbounded queue.
     * @param collocated If {@code true} then all items within the same queue will be collocated on the same node.
     *      Otherwise elements of the same queue maybe be cached on different nodes. If you have lots of relatively
     *      small queues, then you should use collocation. If you have few large queues, then you should turn off
     *      collocation. This parameter works only for {@link GridCacheMode#PARTITIONED} cache.
     * @return Queue with given properties.
     * @throws GridException If remove failed.
     */
    public <T> GridCacheQueue<T> queue(String name, GridCacheQueueType type, int cap, boolean collocated)
        throws GridException;

    /**
     * Remove queue from cache. Internally one transaction will be created for all elements
     * in the queue. If you anticipate that queue may be large, then it's better to use
     * {@link #removeQueue(String, int)} which allows to specify batch size. In that case
     * transaction will be split into multiple transactions which will have upto {@code batchSize}
     * elements in it.
     *
     * @param name Name queue.
     * @return {@code True} if queue has been removed and false if it's not cached.
     * @throws GridException If remove failed.
     */
    public boolean removeQueue(String name) throws GridException;

    /**
     * Remove queue from cache. Internally multiple transactions will be created
     * with no more than {@code batchSize} elements in them. For larger queues, this
     * method is preferrable over {@link #removeQueue(String)} which will create only
     * one transaction for the whole operation.
     *
     * @param name Name queue.
     * @param batchSize Batch size.
     * @return {@code True} if queue has been removed and false if it's not cached.
     * @throws GridException If remove failed.
     */
    public boolean removeQueue(String name, int batchSize) throws GridException;

    /**
     * Will get a atomic reference from cache or create one with initial value of
     * {@code null} if it has not been created yet. This method is analogous to
     * calling {@link #atomicReference(String,Object, boolean)} atomicReference(name, null, false)}.
     *
     * @param name Atomic reference name.
     * @return Atomic reference.
     * @throws GridException If atomic reference could not be fetched or created.
     */
    public <T> GridCacheAtomicReference<T> atomicReference(String name) throws GridException;

    /**
     * Will get a atomic reference from cache and create one if it has not been created yet.
     *
     * @param name Atomic reference name.
     * @param initVal Initial value for atomic reference. If atomic reference already cached,
     *      {@code initVal} will be ignored.
     * @param persistent If {@code true} atomic reference will be put to the storage, otherwise won't.
     *      Note that user must implement storage himself.
     * @return Atomic reference for the given name.
     * @throws GridException If atomic reference could not be fetched or created.
     */
    public <T> GridCacheAtomicReference<T> atomicReference(String name, T initVal, boolean persistent)
        throws GridException;

    /**
     * Remove atomic reference from cache.
     *
     * @param name Atomic reference name.
     * @return {@code True} if atomic reference has been removed, {@code false} otherwise.
     * @throws GridException If remove failed.
     */
    public boolean removeAtomicReference(String name) throws GridException;

    /**
     * Will get a atomic stamped from cache or create one with initial value of
     * {@code null} if it has not been created yet. This method is analogous to
     * calling {@link #atomicStamped(String,Object,Object)} atomicStamped(name, null, null)}.
     *
     * @param name Atomic stamped name.
     * @return Atomic stamped.
     * @throws GridException If atomic stamped could not be fetched or created.
     */
    public <T, S> GridCacheAtomicStamped<T, S> atomicStamped(String name) throws GridException;

    /**
     * Will get a atomic stamped from cache and create one if it has not been created yet.
     *
     * @param name Atomic stamped name.
     * @param initVal Initial value for atomic stamped. If atomic stamped already cached,
     *      {@code initVal} will be ignored.
     * @param initStamp Initial stamp for atomic stamped. If atomic stamped already cached,
     *      {@code initStamp} will be ignored.
     * @return Atomic stamped for the given name.
     * @throws GridException If atomic stamped could not be fetched or created.
     */
    public <T, S> GridCacheAtomicStamped<T, S> atomicStamped(String name, T initVal, S initStamp) throws GridException;

    /**
     * Remove atomic stamped from cache.
     *
     * @param name Atomic stamped name.
     * @return {@code True} if atomic stamped has been removed, {@code false} otherwise.
     * @throws GridException If remove failed.
     */
    public boolean removeAtomicStamped(String name) throws GridException;

    /**
     * Gets or creates count down latch. If count down latch is not found in cache,
     * it is created using provided name and count parameter.
     *
     * @param name Name of the latch.
     * @param cnt Count for new latch creation.
     * @param autoDel {@code True} to automatically delete latch from cache
     *      when its count reaches zero.
     * @return Count down latch for the given name.
     * @throws GridException If operation failed.
     */
    public GridCacheCountDownLatch countDownLatch(String name, int cnt, boolean autoDel) throws GridException;

    /**
     * Gets count down latch. If count down latch is not found in cache
     * {@code null} is returned.
     *
     * @param name Name of the latch.
     * @return Count down latch for the given name or {@code null}.
     * @throws GridException If operation failed.
     */
    @Nullable public GridCacheCountDownLatch countDownLatch(String name) throws GridException;

    /**
     * Removes count down latch from cache.
     *
     * @param name Name of the latch.
     * @return Count down latch for the given name.
     * @throws GridException If operation failed.
     */
    public boolean removeCountDownLatch(String name) throws GridException;

    /**
     * Runs DGC procedure on demand using
     * {@link GridCacheConfiguration#getDgcSuspectLockTimeout()} to identify suspect locks.
     * <p>
     * Method blocks current thread until locks are examined and all DGC requests are sent
     * to remote nodes.
     * <p>
     * DGC does not remove locks if {@link GridCacheConfiguration#isDgcRemoveLocks()}
     * is set to {@code false}.
     */
    public void dgc();

    /**
     * Runs DGC procedure on demand using provided parameter to identify suspect locks.
     * <p>
     * Method blocks current thread until locks are examined and all DGC requests are sent
     * to remote nodes and (if {@code global} is {@code true}) all nodes running this cache
     * will get signal to start GC procedure.
     *
     * @param suspectLockTimeout Custom suspect lock timeout (should be greater than or equal to 0).
     * @param global If {@code true} then GC procedure will start on all nodes having this cache.
     * @param rmvLocks If {@code false} then DGC does not remove locks, just report them to log.
     */
    public void dgc(long suspectLockTimeout, boolean global, boolean rmvLocks);
}
