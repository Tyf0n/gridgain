// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.datastructures;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.*;
import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;
import static org.gridgain.grid.kernal.processors.cache.datastructures.GridCacheQueueOperation.*;

/**
 * Queue implementation.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.1c.09042012
 */
public class GridCacheQueueImpl<T> extends AbstractCollection<T> implements GridCacheQueueEx<T>,
    Externalizable {
    /** Deserialization stash. */
    private static final ThreadLocal<GridTuple2<GridCacheContext, String>> stash =
        new ThreadLocal<GridTuple2<GridCacheContext, String>>() {
            @Override protected GridTuple2<GridCacheContext, String> initialValue() {
                return F.t2();
            }
        };

    /** Default value of warning threshold of attempts. */
    public static final int DFLT_ATTEMPT_WARN_THRESHOLD = 5;

    /** Logger. */
    private GridLogger log;

    /** Queue id. */
    private String qid;

    /** Removed flag. */
    private volatile boolean rmvd;

    /** Bounded flag. */
    private volatile boolean bounded;

    /** Queue type. */
    private GridCacheQueueType type;

    /** Maximum queue size. */
    private int cap;

    /** Collocation flag. */
    private boolean collocated;

    /** Queue key. */
    private GridCacheInternalKey key;

    /** Read blocking operations semaphore. */
    @GridToStringExclude
    private volatile Semaphore readSem;

    /** Write blocking operations semaphore. */
    @GridToStringExclude
    private volatile Semaphore writeSem;

    /** Warning threshold in blocking operations.*/
    @GridToStringExclude
    private long blockAttemptWarnThreshold = DFLT_ATTEMPT_WARN_THRESHOLD;

    /** Cache context. */
    private GridCacheContext cctx;

    /** Queue header view. */
    @GridToStringExclude
    private GridCacheProjection<GridCacheInternalKey, GridCacheQueueHeader> queueHdrView;

    /** Queue item view. */
    @GridToStringExclude
    private GridCacheProjection<GridCacheQueueItemKey, GridCacheQueueItem<T>> itemView;

    /** Query factory. */
    @GridToStringExclude
    private GridCacheQueueQueryFactory<T> qryFactory;

    /** Mutex. */
    @GridToStringExclude
    private final Object mux = new Object();

    /** Meta data.*/
    @GridToStringExclude
    private GridMetadataAwareAdapter meta = new GridMetadataAwareAdapter();

    /** Internal error state of the queue. */
    @GridToStringInclude
    private volatile Exception err;

    /**
     * Constructor.
     *
     * @param qid Query Id.
     * @param hdr  Header of queue.
     * @param key Key of queue.
     * @param cctx Cache context.
     * @param queueHdrView Queue headers view.
     * @param itemView  Queue items view.
     * @param qryFactory Query factory.
     */
    public GridCacheQueueImpl(String qid, GridCacheQueueHeader hdr, GridCacheInternalKey key,
        GridCacheContext cctx, GridCacheProjection<GridCacheInternalKey, GridCacheQueueHeader> queueHdrView,
        GridCacheProjection<GridCacheQueueItemKey, GridCacheQueueItem<T>> itemView,
        GridCacheQueueQueryFactory<T> qryFactory) {
        assert qid != null;
        assert hdr != null;
        assert key != null;
        assert cctx != null;
        assert queueHdrView != null;
        assert itemView != null;
        assert qryFactory != null;

        this.qid = qid;
        this.key = key;
        this.cctx = cctx;
        this.queueHdrView = queueHdrView;
        this.itemView = itemView;
        this.qryFactory = qryFactory;
        type = hdr.type();

        readSem = new Semaphore(hdr.size(), true);

        writeSem = new Semaphore(hdr.capacity() - hdr.size(), true);

        cap = hdr.capacity();

        collocated = hdr.collocated();

        bounded = cap < Integer.MAX_VALUE;

        log = cctx.logger(GridCacheQueueImpl.class);
    }

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridCacheQueueImpl() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(GridMetadataAware from) {
        meta.copyMeta(from);
    }

    /** {@inheritDoc} */
    @Override public void copyMeta(Map<String, ?> data) {
        meta.copyMeta(data);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMeta(String name, V val) {
        return meta.addMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V putMetaIfAbsent(String name, V val) {
        return meta.putMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V putMetaIfAbsent(String name, Callable<V> c) {
        return meta.putMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMetaIfAbsent(String name, V val) {
        return meta.addMetaIfAbsent(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> V addMetaIfAbsent(String name, @Nullable Callable<V> c) {
        return meta.addMetaIfAbsent(name, c);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable @Override public <V> V meta(String name) {
        return (V)meta.meta(name);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable @Override public <V> V removeMeta(String name) {
        return (V)meta.removeMeta(name);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean removeMeta(String name, V val) {
        return meta.removeMeta(name, val);
    }

    @Override public <V> Map<String, V> allMeta() {
        return meta.allMeta();
    }

    /** {@inheritDoc} */
    @Override public boolean hasMeta(String name) {
        return meta.hasMeta(name);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean hasMeta(String name, V val) {
        return meta.hasMeta(name, val);
    }

    /** {@inheritDoc} */
    @Override public <V> boolean replaceMeta(String name, V curVal, V newVal) {
        return meta.replaceMeta(name, curVal, newVal);
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return qid;
    }

    /** {@inheritDoc} */
    @Override public GridCacheQueueType type() {
        return type;
    }

    /** {@inheritDoc} */
    @Override public boolean add(T item) {
        A.notNull(item, "item");

        checkRemoved();

        boolean retVal;

        try {
            retVal = CU.outTx(addCallable(Arrays.asList(item)), cctx);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }

        return retVal;
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> addAsync(T item) {
        A.notNull(item, "item");

        return cctx.closures().callLocalSafe(addCallable(Arrays.asList(item)), true);
    }

    /** {@inheritDoc} */
    @Override public boolean addx(T item) throws GridException {
        A.notNull(item, "item");

        checkRemovedx();

        return CU.outTx(addCallable(Arrays.asList(item)), cctx);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public boolean addAll(Collection<? extends T> items) {
        A.notNull(items, "items");

        checkRemoved();

        try {
            return CU.outTx(addCallable((Collection<T>)items), cctx);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public boolean addAllx(Collection<? extends T> items) throws GridException {
        A.notNull(items, "items");

        checkRemovedx();

        return CU.outTx(addCallable((Collection<T>)items), cctx);
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"unchecked"})
    @Override public GridFuture<Boolean> addAllAsync(Collection<? extends T> items) {
        A.notNull(items, "items");

        return cctx.closures().callLocalSafe(addCallable((Collection<T>)items), true);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public boolean contains(Object o) {
        A.notNull(o, "o");

        T item = (T)o;

        T[] items = (T[])new Object[] {item};

        Object[] hashes = new Object[] {item.hashCode()};

        try {
            return internalContains(items, hashes);
        }
        catch (GridException e) {
            throw new GridRuntimeException("Failed to find queue item [queue=" + qid + ", item=" + item + ']', e);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public boolean containsx(Object o) throws GridException {
        A.notNull(o, "o");

        T item = (T)o;

        T[] items = (T[])new Object[] {item};

        Object[] hashes = new Object[] {item.hashCode()};

        return internalContains(items, hashes);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public boolean containsAll(Collection<?> items) {
        A.ensure(!F.isEmpty(items), "items cannot be empty");

        // Try to cast collection.
        Collection<T> items0 = (Collection<T>)items;

        // Prepare id's for query.
        Collection<Integer> hashes = new LinkedList<Integer>();

        for (T item : items0)
            hashes.add(item.hashCode());

        try {
            return internalContains((T[])items0.toArray(), hashes.toArray());
        }
        catch (GridException e) {
            throw new GridRuntimeException("Failed to find queue items " +
                "[queue=" + qid + ", items=" + items0 + ']', e);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public boolean containsAllx(Collection<?> items) throws GridException {
        A.ensure(!F.isEmpty(items), "items cannot be empty");

        // Try to cast collection.
        Collection<T> items0 = (Collection<T>)items;

        // Prepare id's for query.
        Collection<Integer> hashes = new LinkedList<Integer>();

        for (T item : items0)
            hashes.add(item.hashCode());

        return internalContains((T[])items0.toArray(), hashes.toArray());
    }

    /**
     * @param hashes Array of items hash code
     * @param items Array of items.
     * @return {@code True} if queue contains all items, {@code false} otherwise.
     * @throws GridException If operation failed.
     */
    private boolean internalContains(T[] items, Object[] hashes) throws GridException {
        boolean retVal;

        GridCacheReduceQuery<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>, boolean[],
            Boolean> qry = qryFactory.containsQuery(type).queryArguments(qid, hashes).
            closureArguments(cctx.cache().name(), items);

        if (collocated) {
            // For case if primary node was changed during request.
            GridRichNode node = CU.primaryNode(cctx, key);

            retVal = qry.reduce(node).get();
        }
        else
            retVal = qry.reduce(cctx.grid()).get();

        return retVal;
    }

    /** {@inheritDoc} */
    @Override public T poll() throws GridException {
        checkRemovedx();

        return CU.outTx(queryCallable(qryFactory.firstItemQuery(type()), false), cctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<T> pollAsync() {
        return cctx.closures().callLocalSafe(queryCallable(qryFactory.firstItemQuery(type()), false), true);
    }

    /** {@inheritDoc} */
    @Override public T pollLast() throws GridException {
        checkRemovedx();

        return CU.outTx(queryCallable(qryFactory.lastItemQuery(type()), false), cctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<T> pollLastAsync() {
        return cctx.closures().callLocalSafe(queryCallable(qryFactory.lastItemQuery(type()), false), true);
    }

    /** {@inheritDoc} */
    @Override public T peek() throws GridException {
        checkRemovedx();

        return CU.outTx(queryCallable(qryFactory.firstItemQuery(type()), true), cctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<T> peekAsync() {
        return cctx.closures().callLocalSafe(queryCallable(qryFactory.firstItemQuery(type()), true), true);
    }

    /** {@inheritDoc} */
    @Override public T peekLast() throws GridException {
        checkRemovedx();

        return CU.outTx(queryCallable(qryFactory.lastItemQuery(type()), true), cctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<T> peekLastAsync() {
        return cctx.closures().callLocalSafe(queryCallable(qryFactory.lastItemQuery(type()), true), true);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public boolean remove(Object item) {
        A.notNull(item, "item");

        T locItem = (T)item;

        checkRemoved();

        try {
            return CU.outTx(removeItemsCallable(Arrays.asList(locItem), false), cctx);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> removeAsync(T item) {
        A.notNull(item, "item");

        return cctx.closures().callLocalSafe(removeItemsCallable(Arrays.asList(item), false), true);
    }

    /** {@inheritDoc} */
    @Override public boolean removex(T item) throws GridException {
        A.notNull(item, "item");

        checkRemovedx();

        return CU.outTx(removeItemsCallable(Arrays.asList(item), false), cctx);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public boolean removeAll(Collection<?> items) {
        A.ensure(!F.isEmpty(items), "items cannot be empty");

        // Try to cast collection.
        Collection<T> items0 = (Collection<T>)items;

        checkRemoved();

        try {
            return CU.outTx(removeItemsCallable(items0, false), cctx);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public GridFuture<Boolean> removeAllAsync(Collection<?> items) {
        A.ensure(!F.isEmpty(items), "items cannot be empty");

        // Try to cast collection.
        Collection<T> items0 = (Collection<T>)items;

        return cctx.closures().callLocalSafe(removeItemsCallable(items0, false), true);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public boolean removeAllx(Collection<?> items) throws GridException {
        // Try to cast collection.
        Collection<T> items0 = (Collection<T>)items;

        checkRemovedx();

        return CU.outTx(removeItemsCallable(items0, false), cctx);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public boolean retainAll(Collection<?> items) {
        A.ensure(!F.isEmpty(items), "items cannot be empty");

        // Try to cast collection.
        Collection<T> items0 = (Collection<T>)items;

        checkRemoved();

        try {
            return CU.outTx(removeItemsCallable(items0, true), cctx);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public GridFuture<Boolean> retainAllAsync(Collection<?> items) {
        A.ensure(!F.isEmpty(items), "items cannot be empty");

        // Try to cast collection.
        Collection<T> items0 = (Collection<T>)items;

        return cctx.closures().callLocalSafe(removeItemsCallable(items0, true), true);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked cast")
    @Override public boolean retainAllx(Collection<?> items) throws GridException {
        A.ensure(!F.isEmpty(items), "items cannot be empty");

        // Try to cast collection.
        Collection<T> items0 = (Collection<T>)items;

        checkRemovedx();

        return CU.outTx(removeItemsCallable(items0, true), cctx);
    }

    /** {@inheritDoc} */
    @Override public int position(T item) throws GridException {
        A.notNull(item, "item");

        checkRemovedx();

        if (!collocated)
            throw new GridException("Operation position(..) is supported only in collocated mode.");

        GridCacheReduceQuery<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>, Integer, Integer> qry =
            qryFactory.itemPositionQuery(type).queryArguments(qid).closureArguments(item);

        Collection<Integer> res = qry.reduceRemote(CU.primaryNode(cctx, key)).get();

        checkRemovedx();

        Integer pos = F.first(res);

        assert pos != null;

        return pos;
    }

    /** {@inheritDoc} */
    @Nullable @Override public Collection<T> items(Integer... positions) throws GridException {
        checkRemovedx();

        if (!collocated)
            throw new GridException("Operation items(..) is supported only in collocated mode.");

        if (F.isEmpty(positions))
            return Collections.emptyList();

        GridCacheQuery<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>> qry = qryFactory.
            itemsAtPositionsQuery(type);

        Collection<GridCacheQueueItemImpl<T>> queueItems = F.viewReadOnly(
            qry.queryArguments(qid, positions).execute(CU.primaryNode(cctx, key)).get(),
            F.<GridCacheQueueItemImpl<T>>mapEntry2Value());

        Collection<T> userItems = new LinkedList<T>();

        for (GridCacheQueueItemImpl<T> queueItem : queueItems)
            userItems.add(queueItem.userObject());

        return userItems;
    }

    /** {@inheritDoc} */
    @Override public void put(T item) throws GridException {
        A.notNull(item, "item");

        checkRemovedx();

        if (bounded)
            blockWriteOp(Arrays.asList(item), PUT);
        else
            CU.outTx(addCallable(Arrays.asList(item)), cctx);
    }

    /** {@inheritDoc} */
    @Override public boolean put(T item, long timeout, TimeUnit unit) throws GridException {
        A.notNull(item, "item");
        A.ensure(timeout >= 0, "Timeout cannot be negative: " + timeout);

        checkRemovedx();

        return bounded ? blockWriteOp(Arrays.asList(item), PUT_TIMEOUT, timeout, unit) :
            CU.outTx(addCallable(Arrays.asList(item)), cctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> putAsync(T item) {
        A.notNull(item, "item");

        return cctx.closures().callLocalSafe(addCallable(Arrays.asList(item)), true);
    }

    /** {@inheritDoc} */
    @Override public T take() throws GridException {
        checkRemovedx();

        boolean peek = false;

        return blockReadOp(queryCallable(qryFactory.firstItemQuery(type), peek), TAKE, peek);
    }

    /** {@inheritDoc} */
    @Nullable @Override public T takeLast() throws GridException {
        checkRemovedx();

        boolean peek = false;

        return blockReadOp(queryCallable(qryFactory.lastItemQuery(type), peek), TAKE_LAST, peek);
    }

    /** {@inheritDoc} */
    @Override public T take(long timeout, TimeUnit unit) throws GridException {
        A.ensure(timeout >= 0, "Timeout cannot be negative: " + timeout);

        checkRemovedx();

        boolean peek = false;

        return blockReadOp(queryCallable(qryFactory.firstItemQuery(type), peek), TAKE_TIMEOUT, timeout,
            unit, peek);
    }

    /** {@inheritDoc} */
    @Override public T takeLast(long timeout, TimeUnit unit) throws GridException {
        A.ensure(timeout >= 0, "Timeout cannot be negative: " + timeout);

        checkRemovedx();

        boolean peek = false;

        return blockReadOp(queryCallable(qryFactory.lastItemQuery(type), peek), TAKE_LAST_TIMEOUT, timeout,
            unit, peek);
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridFuture<T> takeAsync() {
        return cctx.closures().callLocalSafe(queryCallable(qryFactory.firstItemQuery(type), false), true);
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridFuture<T> takeLastAsync() {
        return cctx.closures().callLocalSafe(queryCallable(qryFactory.lastItemQuery(type), false), true);
    }

    /** {@inheritDoc} */
    @Override public T get() throws GridException {
        checkRemovedx();

        boolean peek = true;

        return blockReadOp(queryCallable(qryFactory.firstItemQuery(type), peek), GET, peek);
    }

    /** {@inheritDoc} */
    @Override public T getLast() throws GridException {
        checkRemovedx();

        boolean peek = true;

        return blockReadOp(queryCallable(qryFactory.lastItemQuery(type), peek), GET_LAST, peek);
    }

    /** {@inheritDoc} */
    @Override public T get(long timeout, TimeUnit unit) throws GridException {
        A.ensure(timeout >= 0, "Timeout cannot be negative: " + timeout);

        checkRemovedx();

        boolean peek = true;

        return blockReadOp(queryCallable(qryFactory.firstItemQuery(type), peek), GET_TIMEOUT, timeout,
            unit, peek);
    }

    /** {@inheritDoc} */
    @Override public T getLast(long timeout, TimeUnit unit) throws GridException {
        A.ensure(timeout >= 0, "Timeout cannot be negative: " + timeout);

        checkRemovedx();

        boolean peek = true;

        return blockReadOp(queryCallable(qryFactory.lastItemQuery(type), peek), GET_LAST_TIMEOUT, timeout,
            unit, peek);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<T> getAsync() {
        return cctx.closures().callLocalSafe(queryCallable(qryFactory.firstItemQuery(type), true), true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<T> getLastAsync() {
        return cctx.closures().callLocalSafe(queryCallable(qryFactory.lastItemQuery(type), true), true);
    }

    /** {@inheritDoc} */
    @Override public void clearx() throws GridException {
        checkRemovedx();

        CU.outTx(clearCallable(0), cctx);
    }

    /** {@inheritDoc} */
    @Override public void clear(int batchSize) throws GridRuntimeException {
        try {
            A.ensure(batchSize >= 0, "Batch size cannot be negative: " + batchSize);

            checkRemovedx();

            CU.outTx(clearCallable(batchSize), cctx);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public void clearx(int batchSize) throws GridException {
        A.ensure(batchSize >= 0, "Batch size cannot be negative: " + batchSize);

        checkRemovedx();

        CU.outTx(clearCallable(batchSize), cctx);
    }

    /** {@inheritDoc} */
    @Override public void clear() {
        checkRemoved();

        try {
            CU.outTx(clearCallable(0), cctx);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> clearAsync() {
        return cctx.closures().callLocalSafe(clearCallable(0), true);
    }

    /** {@inheritDoc} */
    @Override public boolean isEmptyx() throws GridException {
        checkRemovedx();

        GridCacheQueueHeader globalHdr = queueHdrView.get(key);

        assert globalHdr != null : "Failed to find queue header in cache: " + this;

        return globalHdr.empty();
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        checkRemoved();

        GridCacheQueueHeader globalHdr;

        try {
            globalHdr = queueHdrView.get(key);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }

        assert globalHdr != null : "Failed to find queue header in cache: " + this;

        return globalHdr.empty();
    }

    /** {@inheritDoc} */
    @Override public int sizex() throws GridException {
        checkRemovedx();

        GridCacheQueueHeader globalHdr = queueHdrView.get(key);

        assert globalHdr != null : "Failed to find queue header in cache: " + this;

        return globalHdr.size();
    }

    /** {@inheritDoc} */
    @Override public int size() {
        checkRemoved();

        GridCacheQueueHeader globalHdr;

        try {
            globalHdr = queueHdrView.get(key);
        }
        catch (GridException e) {
            throw new GridRuntimeException(e);
        }

        assert globalHdr != null : "Failed to find queue header in cache: " + this;

        return globalHdr.size();
    }

    /** {@inheritDoc} */
    @Override public int capacity() throws GridException {
        checkRemovedx();

        return cap;
    }

    /** {@inheritDoc} */
    @Override public boolean bounded() throws GridException {
        checkRemovedx();

        return bounded;
    }

    /** {@inheritDoc} */
    @Override public boolean collocated() throws GridException {
        checkRemovedx();

        return collocated;
    }

    /** {@inheritDoc} */
    @Override public boolean onRemoved() {
        synchronized (mux) {
            if (!rmvd)
                rmvd = true;

            // Free all blocked resources.
            writeSem.drainPermits();
            writeSem.release(Integer.MAX_VALUE);

            readSem.drainPermits();
            readSem.release(Integer.MAX_VALUE);
        }

        if (log.isDebugEnabled())
            log.debug("Queue has removed: " + this);

        return rmvd;
    }

    /** {@inheritDoc} */
    @Override public void onInvalid(@Nullable Exception err) {
        synchronized (mux) {
            if (rmvd)
                return;

            this.err = err;

            // Free all blocked resources.
            writeSem.drainPermits();
            writeSem.release(Integer.MAX_VALUE);

            readSem.drainPermits();
            readSem.release(Integer.MAX_VALUE);
        }
    }

    /** {@inheritDoc} */
    @Override public GridCacheInternalKey key() {
        return key;
    }

    /** {@inheritDoc} */
    @Override public void onHeaderChanged(GridCacheQueueHeader globalHdr) {
        // If queue is removed don't do anything.
        synchronized (mux) {
            if (rmvd)
                return;

            // Release read semaphore.
            if (!globalHdr.empty()) {
                readSem.drainPermits();
                readSem.release(globalHdr.size());
            }

            // Release write semaphore.
            if (bounded && !globalHdr.full()) {
                writeSem.drainPermits();
                writeSem.release(globalHdr.capacity() - globalHdr.size());
            }
        }

        if (log.isDebugEnabled())
            log.debug("Queue header has changed [hdr=" + globalHdr + ", queue=" + this + ']');
    }

    /** {@inheritDoc} */
    @Override public boolean removed() {
        return rmvd;
    }

    /**
     * Check removed status.
     *
     * @throws GridException If removed.
     */
    private void checkRemovedx() throws GridException {
        if (rmvd)
            throw new GridCacheDataStructureRemovedException("Queue has been removed from cache: " + this);

        if (err != null)
            throw new GridCacheDataStructureInvalidException("Queue is in invalid state " +
                "(discard this queue instance and get another from cache): " + this, err);
    }

    /**
     * Check removed status and throws GridRuntimeException if queue removed. Method is used for Ex methods.
     */
    private void checkRemoved() {
        if (rmvd)
            throw new GridCacheDataStructureRemovedRuntimeException("Queue has been removed from cache: " + this);


        if (err != null)
            throw new GridCacheDataStructureInvalidRuntimeException("Queue is in invalid state " +
                "(discard this queue instance and get another from cache): " + this, err);
    }

    /**
     * Method implements universal method for add object to queue.
     *
     * @param items Items.
     * @return Callable.
     */
    private Callable<Boolean> addCallable(final Collection<T> items) {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                if (items.contains(null))
                    throw new GridException("Queue item can't be null [queue=" + GridCacheQueueImpl.this + ", items=" +
                        items);

                checkRemovedx();

                GridCacheTx tx = CU.txStartInternal(cctx, cctx.cache(), PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheQueueHeader globalHdr = queueHdrView.get(key);

                    checkRemovedx();

                    assert globalHdr != null : "Failed to find queue header in cache: " + GridCacheQueueImpl.this;

                    if (globalHdr.full() || (bounded && globalHdr.size() + items.size() > cap)) {
                        tx.setRollbackOnly();

                        // Block all write attempts.
                        synchronized (mux) {
                            writeSem.drainPermits();
                        }

                        if (log.isDebugEnabled())
                            log.debug("Queue is full [globalHdr=" + globalHdr + ", queue=" + GridCacheQueueImpl.this +
                                ']');

                        return false;
                    }

                    Map<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>> data =
                        new HashMap<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>>();

                    // Prepare data.
                    for (T item : items) {
                        int pri = 0;

                        Object annotatedVal = cctx.dataStructures().priorityAnnotations().annotatedValue(item);

                        if (annotatedVal != null)
                            if (!(annotatedVal instanceof Integer))
                                throw new GridException("Invalid queue priority type [expected=int, actual=" +
                                    annotatedVal.getClass() + ']');
                            else
                                pri = (Integer)annotatedVal;

                        // Increment queue size and sequence.
                        globalHdr.incrementSize();

                        long seq = globalHdr.incrementSequence();

                        //Make queue item key.
                        GridCacheQueueItemKey itemKey = new GridCacheQueueItemKeyImpl(seq, qid, collocated);

                        // Make new queue item.
                        GridCacheQueueItemImpl<T> val = new GridCacheQueueItemImpl<T>(qid, seq, pri, item);

                        val.enqueueTime(System.currentTimeMillis());

                        data.put(itemKey, val);
                    }

                    // Put data to cache.
                    if (!data.isEmpty()) {
                        itemView.putAll(data);

                        // Update queue header.
                        queueHdrView.putx(key, globalHdr);
                    }

                    if (log.isDebugEnabled())
                        log.debug("Items will be added to queue [items=" + items + ", hdr=" + globalHdr + ", queue=" +
                            GridCacheQueueImpl.this + ']');

                    tx.commit();

                    return true;
                }
                finally {
                    tx.end();
                }
            }
        };
    }

    /**
     * Method implements universal method for getting object from queue.
     *
     * @param qry Query.
     * @param peek {@code true} don't release received queue item, {@code false} release received queue item.
     * @return Callable.
     */
    private Callable<T> queryCallable(final GridCacheReduceQuery<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>,
        Map.Entry<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>>, Map.Entry<GridCacheQueueItemKey,
        GridCacheQueueItemImpl<T>>> qry, final boolean peek) {
        return new Callable<T>() {
            @Override @Nullable public T call() throws Exception {
                checkRemovedx();

                GridCacheTx tx = CU.txStartInternal(cctx, cctx.cache(), PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheQueueHeader globalHdr = queueHdrView.get(key);

                    checkRemovedx();

                    assert globalHdr != null : "Failed to find queue header in cache: " + GridCacheQueueImpl.this;

                    if (globalHdr.empty()) {
                        tx.setRollbackOnly();

                        // Block all readers.
                        synchronized (mux) {
                            readSem.drainPermits();
                        }

                        return null;
                    }

                    Map.Entry<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>> entry;

                    if (collocated) {
                        // For case if primary node was changed during request.
                        while (true) {
                            GridRichNode node = CU.primaryNode(cctx, key);

                            entry = qry.queryArguments(qid).closureArguments(type).reduce(node).get();

                            if (log.isDebugEnabled())
                                log.debug("Entry has been found [node=" + node + ", entry=" + entry + ", queue=" +
                                    GridCacheQueueImpl.this + ']');

                            // Entry wasn't found perhaps grid topology was changed.
                            GridRichNode node2;

                            if (entry == null)
                                node2 = CU.primaryNode(cctx, key);
                            else
                                break;

                            // Topology wasn't changed.
                            if (node.equals(node2))
                                break;

                            if (log.isDebugEnabled())
                                log.debug("Entry wasn't found for queue: " + GridCacheQueueImpl.this);
                        }
                    }
                    else {
                        // In non-collocated mode query can return already removed entry. We should check this issue.
                        while (true) {
                            entry = qry.queryArguments(qid).closureArguments(type).reduce(cctx.grid()).get();

                            // We don't have eny items in queue.
                            if (entry == null)
                                break;

                            GridCacheQueueItem<T> val = itemView.get(entry.getKey());

                            if (val == null) {
                                if (log.isDebugEnabled())
                                    log.debug("Already removed entry have been found [entry=" + entry + ", queue=" +
                                        GridCacheQueueImpl.this + ']');

                                // Re-remove the key as it was removed before.
                                itemView.remove(entry.getKey());
                            }
                            else {
                                if (log.isDebugEnabled())
                                    log.debug("Queue entry have been found [entry=" + entry + ", queue=" +
                                        GridCacheQueueImpl.this + ']');

                                break;
                            }
                        }
                    }

                    if (entry == null) {
                        tx.setRollbackOnly();

                        if (log.isDebugEnabled())
                            log.debug("Failed to find queue item in: " + GridCacheQueueImpl.this);

                        return null;
                    }

                    GridCacheQueueItem<T> val = entry.getValue();

                    assert val != null : "Failed to get entry value: " + entry;
                    assert val.userObject() != null : "Failed to get user object from value: " + entry;

                    if (!peek) {
                        // Check queue size.
                        assert globalHdr.size() > 0 : "Queue is empty but item has been found [item=" + val +
                            ", header=" + globalHdr + ", queue=" + GridCacheQueueImpl.this + ']';

                        globalHdr.decrementSize();

                        // Refresh queue header in cache.
                        queueHdrView.putx(key, globalHdr);

                        // Remove item from cache.
                        boolean wasRmvd = itemView.removex(entry.getKey());

                        assert wasRmvd : "Already deleted entry: " + entry;
                    }

                    tx.commit();

                    if (log.isDebugEnabled())
                        log.debug("Retrieved queue item [item=" + val + ", queue=" + GridCacheQueueImpl.this + ']');

                    return val.userObject();
                }
                finally {
                    tx.end();
                }
            }
        };
    }

    /**
     * Method implements universal method for clear queue.
     *
     * @param batchSize Batch size.
     * @return Callable for queue clearing .
     */
    private Callable<Boolean> clearCallable(final int batchSize) {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                checkRemovedx();

                // Result of operations.
                GridTuple2<Integer, GridException> qryRes = new T2<Integer, GridException>(0, null);

                GridCacheTx tx = CU.txStartInternal(cctx, cctx.cache(), PESSIMISTIC, REPEATABLE_READ);

                int queueOldSize;

                try {
                    GridCacheQueueHeader globalHdr = queueHdrView.get(key);

                    checkRemovedx();

                    assert globalHdr != null : "Failed to find queue header in cache: " + GridCacheQueueImpl.this;

                    GridCacheReduceQuery<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>,
                        GridTuple2<Integer, GridException>, GridTuple2<Integer, GridException>> qry =
                        qryFactory.removeAllKeysQuery(type).closureArguments(batchSize).queryArguments(qid);

                    if (collocated) {
                        // For case if primary node was changed during request.
                        while (true) {
                            GridRichNode node = CU.primaryNode(cctx, key);

                            GridTuple2<Integer, GridException> tup = qry.closureArguments(batchSize).reduce(node).get();

                            // Check topology changes.
                            GridRichNode node2 = CU.primaryNode(cctx, key);

                            // If topology wasn't changed then break loop.
                            if (node.equals(node2)) {
                                qryRes = tup;

                                break;
                            }
                            else
                                qryRes.set(qryRes.get1() + tup.get1(), tup.get2() != null ? tup.get2() : qryRes.get2());

                            if (log.isDebugEnabled())
                                log.debug("Node topology was changed, request will be repeated for queue: " +
                                    GridCacheQueueImpl.this);
                        }
                    }
                    else
                        qryRes = qry.reduce(cctx.grid()).get();

                    assert qryRes != null;

                    // Queue old size.
                    queueOldSize = globalHdr.size();

                    // Update queue header in any case because some queue items can be already removed.
                    globalHdr.size(globalHdr.size() - qryRes.get1());

                    queueHdrView.putx(key, globalHdr);

                    tx.commit();

                    if (log.isDebugEnabled())
                        log.debug("Items were removed [itemsNumber=" + qryRes.get1() + ", queueHeader=" + globalHdr +
                            ", queue=" + GridCacheQueueImpl.this + ']');

                    if (queueOldSize != qryRes.get1() && log.isDebugEnabled())
                        log.debug("Queue size mismatch [itemsNumber=" + qryRes.get1() +
                            ", headerOldSize=" + queueOldSize + ", newHeader=" + globalHdr + ", queue=" +
                            GridCacheQueueImpl.this + ']');
                }
                finally {
                    tx.end();
                }

                // Throw remote exception if it's happened.
                if (qryRes.get2() != null)
                    throw qryRes.get2();

                return queueOldSize == qryRes.get1();
            }
        };
    }

    /**
     * Method implements universal method for remove queue items.
     *
     * @param items Items.
     * @param retain If {@code true} will be removed all queue items instead of {@code items}, {@code false} only all
     *      {@code items} will be removed from queue.
     * @return Callable for removing queue item.
     */
    private Callable<Boolean> removeItemsCallable(final Collection<T> items, final boolean retain) {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                checkRemovedx();

                // Result of operations.
                GridTuple2<Integer, GridException> qryRes = new T2<Integer, GridException>(0, null);

                // Prepare id's for query.
                Collection<Integer> hashes = new ArrayList<Integer>(items.size());

                for (T item : items)
                    hashes.add(item.hashCode());

                GridCacheTx tx = CU.txStartInternal(cctx, cctx.cache(), PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheQueueHeader globalHdr = queueHdrView.get(key);

                    checkRemovedx();

                    assert globalHdr != null : "Failed to find queue header in cache: " + GridCacheQueueImpl.this;

                    if (globalHdr.empty()) {
                        tx.setRollbackOnly();

                        return false;
                    }

                    GridCacheReduceQuery<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>,
                        GridTuple2<Integer, GridException>, GridTuple2<Integer, GridException>> qry =
                        qryFactory.itemsKeysQuery(type).queryArguments(qid, hashes.toArray()).
                            closureArguments(items, retain, items.size() == 1);

                    if (collocated) {
                        // For case if primary node was changed during request.
                        while (true) {
                            GridRichNode node = CU.primaryNode(cctx, key);

                            GridTuple2<Integer, GridException> tup = qry.reduce(node).get();

                            // Check topology changes.
                            GridRichNode node2 = CU.primaryNode(cctx, key);

                            // If topology wasn't changed then break loop.
                            if (node.equals(node2)) {
                                qryRes = tup;

                                break;
                            }
                            else
                                qryRes.set(qryRes.get1() + tup.get1(), tup.get2() != null ? tup.get2() : qryRes.get2());

                            if (log.isDebugEnabled())
                                log.debug("Node topology was changed, request will be repeated for queue: " +
                                    GridCacheQueueImpl.this);
                        }
                    }
                    else
                        qryRes = qry.reduce(cctx.grid()).get();

                    assert qryRes != null;

                    if (qryRes.get1() > 0) {
                        // Update queue header in any case because some queue items can be already removed.
                        globalHdr.size(globalHdr.size() - qryRes.get1());

                        queueHdrView.putx(key, globalHdr);

                        tx.commit();

                        if (log.isDebugEnabled())
                            log.debug("Items were removed [itemsNumber=" + qryRes.get1() + ", queueHeader=" + globalHdr +
                                ", queue=" + GridCacheQueueImpl.this + ']');
                    }
                }
                finally {
                    tx.end();
                }

                // Throw remote exception if it's happened.
                if (qryRes.get2() != null)
                    throw qryRes.get2();

                // At least one object was removed or retained.
                return qryRes.get1() > 0;
            }
        };
    }

    /**
     * Method implements universal blocking read operation without exactly timeout.
     *
     * @param cmd Callable for execution.
     * @param op Operation name.
     * @param peek {@code true} don't release received queue item, {@code false} release received queue item.
     * @return Queue item.
     * @throws GridException If failed.
     */
    private T blockReadOp(Callable<T> cmd, GridCacheQueueOperation op, boolean peek) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Read operation will be blocked [op=" + op + ", queue=" + this + ']');

        int attempts = 0;

        T retVal;

        // Operation will be repeated until success.
        while (true) {
            checkRemovedx();

            attempts++;

            try {
                readSem.acquire();

                // We should make redSem the same if we don't remove item from queue after reading.
                if (peek)
                    synchronized (mux) {
                        checkRemovedx();

                        readSem.release();
                    }
            }
            catch (InterruptedException e) {
                throw new GridInterruptedException("Operation has been interrupted [op=" + op +
                    ", queue=" + this + ']', e);
            }

            retVal = CU.outTx(cmd, cctx);

            // Break loop in case operation executed successfully.
            if (retVal != null)
                break;

            if (attempts % blockAttemptWarnThreshold == 0) {
                if (log.isDebugEnabled())
                    log.debug("Exceeded warning threshold for execution attempts [op=" + op +
                        ", attempts=" + attempts + ", queue=" + this + ']');
            }
        }

        if (log.isDebugEnabled())
            log.debug("Operation unblocked [op=" + op + ", retVal=" + retVal + ", queue=" + this + ']');

        return retVal;
    }

    /**
     * Method implements universal blocking read operation with exactly timeout.
     *
     * @param cmd Callable for execution.
     * @param op Operation name.
     * @param timeout Timeout.
     * @param unit a TimeUnit determining how to interpret the timeout parameter.
     * @param peek {@code true} don't release received queue item, {@code false} release received queue item.
     * @return Queue item.
     * @throws GridException If failed.
     */
    @Nullable private T blockReadOp(Callable<T> cmd, GridCacheQueueOperation op, long timeout, TimeUnit unit,
        boolean peek)
        throws GridException {
        long end = System.currentTimeMillis() + MILLISECONDS.convert(timeout, unit);

        if (log.isDebugEnabled())
            log.debug("Read operation will be blocked on timeout [op=" + op + ", timeout=" + end + ", queue=" + this +
                ']');

        T retVal = null;

        // Operation will be repeated until success or timeout will be expired.
        int attempts = 0;

        while (end - System.currentTimeMillis() > 0) {
            checkRemovedx();

            attempts++;

            try {
                if (readSem.tryAcquire(end - System.currentTimeMillis(), MILLISECONDS)) {
                    // We should make redSem the same if we don't remove item from queue after reading.
                    if (peek)
                        synchronized (mux) {
                            checkRemovedx();

                            readSem.release();
                        }

                    retVal = CU.outTx(cmd, cctx);
                }
            }
            catch (InterruptedException e) {
                throw new GridInterruptedException("Operation has been interrupted [op=" + op +
                    ", queue=" + this + ']', e);
            }

            // Break loop in case operation executed successfully.
            if (retVal != null)
                break;

            if (attempts % blockAttemptWarnThreshold == 0) {
                if (log.isDebugEnabled())
                    log.debug("Exceeded warning threshold for execution attempts [op=" + op +
                        ", attempts=" + attempts + ", queue=" + this + ']');
            }
        }

        if (log.isDebugEnabled())
            log.debug("Operation unblocked on timeout [op=" + op + ", retVal=" + retVal + ", queue=" + this + ']');

        return retVal;
    }

    /**
     * Method implements universal blocking write operation.
     *
     * @param items Item for putting to the queue.
     * @param op Operation name.
     * @throws GridException If failed.
     */
    private void blockWriteOp(Collection<T> items, GridCacheQueueOperation op) throws GridException {
        if (log.isDebugEnabled())
            log.debug("Operation will be blocked [op=" + op + ", queue=" + this + ']');

        // Operation will be repeated until success.
        int attempts = 0;

        while (true) {
            attempts++;

            try {
                writeSem.acquire(items.size());
            }
            catch (InterruptedException e) {
                throw new GridInterruptedException("Operation has been interrupted [op=" + op +
                    ", queue=" + this + ']', e);
            }

            checkRemovedx();

            boolean retVal = CU.outTx(addCallable(items), cctx);

            // Break loop in case operation executed successfully.
            if (retVal) {
                if (log.isDebugEnabled())
                    log.debug("Operation unblocked [op=" + op + ", retVal=" + retVal + ", queue=" + this + ']');

                return;
            }

            if (attempts % blockAttemptWarnThreshold == 0) {
                if (log.isDebugEnabled())
                    log.debug("Exceeded warning threshold for execution attempts [op=" + op +
                        ", attempts=" + attempts + ", queue=" + this + ']');
            }
        }
    }

    /**
     * Method implements universal blocking write operation with exactly timeout.
     *
     * @param items Items for putting to the queue.
     * @param op Operation name.
     * @param timeout Timeout.
     * @param unit a TimeUnit determining how to interpret the timeout parameter.
     * @return {@code "true"} if item was added successfully, {@code "false"} if timeout was expired.
     * @throws GridException If failed.
     */
    private boolean blockWriteOp(Collection<T> items, GridCacheQueueOperation op, long timeout, TimeUnit unit)
        throws GridException {
        long end = System.currentTimeMillis() + MILLISECONDS.convert(timeout, unit);

        if (log.isDebugEnabled())
            log.debug("Write operation will be blocked on timeout [op=" + op + ", timeout=" + end +
                ", queue=" + this + ']');

        int attempts = 0;

        boolean retVal = false;

        // Operation will be repeated until success.
        while (end - System.currentTimeMillis() > 0) {
            checkRemovedx();

            attempts++;

            try {
                if (writeSem.tryAcquire(items.size(), end - System.currentTimeMillis(), MILLISECONDS)) {
                    checkRemovedx();

                    retVal = CU.outTx(addCallable(items), cctx);
                }
            }
            catch (InterruptedException e) {
                throw new GridInterruptedException("Operation has been interrupted [op=" + op +
                    ", queue=" + this + ']', e);
            }

            // Break loop in case operation executed successfully.
            if (retVal)
                break;

            if (attempts % blockAttemptWarnThreshold == 0) {
                if (log.isDebugEnabled())
                    log.debug("Exceeded warning threshold for execution attempts [op=" + op +
                        ", attempts=" + attempts + ", queue=" + this + ']');
            }
        }

        if (log.isDebugEnabled())
            log.debug("Operation unblocked on timeout [op=" + op + ", retVal=" + retVal + ", queue=" + this + ']');

        return retVal;
    }

    /**
     * Remove all queue items and queue header.
     *
     * @return Callable for queue clearing .
     * @throws GridException If queue already removed or operation failed.
     */
    @Override public boolean removeQueue(int batchSize) throws GridException {
        checkRemovedx();

        return CU.outTx(removeQueueCallable(batchSize), cctx);
    }

    /**
     * Remove all queue items and queue header.
     *
     * @param batchSize Batch size.
     * @return Callable for queue clearing .
     */
    private Callable<Boolean> removeQueueCallable(final int batchSize) {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                if (log.isDebugEnabled())
                    log.debug("Try to remove queue: " + GridCacheQueueImpl.this);

                checkRemovedx();

                // Result of operations.
                boolean res = false;

                // Query execution result.
                GridTuple2<Integer, GridException> qryRes = new T2<Integer, GridException>(0, null);

                GridCacheTx tx = CU.txStartInternal(cctx, cctx.cache(), PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheQueueHeader globalHdr = queueHdrView.get(key);

                    checkRemovedx();

                    assert globalHdr != null : "Failed to find queue header in cache: " + GridCacheQueueImpl.this;

                    GridCacheReduceQuery<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>,
                        GridTuple2<Integer, GridException>, GridTuple2<Integer, GridException>> qry =
                        qryFactory.removeAllKeysQuery(type).closureArguments(batchSize).queryArguments(qid);

                    if (collocated) {
                        // For case if primary node was changed during request.
                        while (true) {
                            GridRichNode node = CU.primaryNode(cctx, key);

                            GridTuple2<Integer, GridException> tup = qry.closureArguments(batchSize).reduce(node).get();

                            // Check topology changes.
                            GridRichNode node2 = CU.primaryNode(cctx, key);

                            // If topology wasn't changed then break loop.
                            if (node.equals(node2)) {
                                qryRes = tup;

                                break;
                            }
                            else
                                qryRes.set(qryRes.get1() + tup.get1(), tup.get2() != null ? tup.get2() : qryRes.get2());

                            if (log.isDebugEnabled())
                                log.debug("Node topology was changed, request will be repeated for queue: " +
                                    GridCacheQueueImpl.this);
                        }
                    }
                    else
                        qryRes = qry.reduce(cctx.grid()).get();

                    assert qryRes != null;

                    // Queue old size.
                    int queueOldSize = globalHdr.size();

                    if (queueOldSize != qryRes.get1()) {
                        assert queueOldSize > qryRes.get1() : "Queue size mismatch [old=" + queueOldSize + ", rcvd=" +
                            qryRes.get1() + ", queue=" + this + ']';

                        // Update queue header in any case because some queue items can be already removed.
                        globalHdr.size(globalHdr.size() - qryRes.get1());

                        queueHdrView.putx(key, globalHdr);
                    }
                    else {
                        res = true;

                        queueHdrView.removex(key);

                        if (log.isDebugEnabled())
                            log.debug("Queue will be removed: " + GridCacheQueueImpl.this);
                    }

                    tx.commit();

                    if (queueOldSize != qryRes.get1() && log.isDebugEnabled())
                        log.debug("Queue size mismatch [itemsNumber=" + qryRes.get1() +
                            ", headerOldSize=" + queueOldSize + ", newHeader=" + globalHdr + ", queue=" +
                            GridCacheQueueImpl.this + ']');
                }
                finally {
                    tx.end();
                }

                // Throw remote exception if it's happened.
                if (qryRes.get2() != null)
                    throw qryRes.get2();

                return res;
            }
        };
    }

    /** {@inheritDoc} */
    @Override public Iterator<T> iterator() {
        checkRemoved();

        try {
            GridCacheQueueHeader globalHdr = queueHdrView.get(key);

            assert globalHdr != null;

            return new GridCacheQueueIterator();
        }
        catch (GridException e) {
            throw new GridRuntimeException("Failed to create iterator in queue: " + this, e);
        }
    }

    /** {@inheritDoc} */
    @Override public Object[] toArray() {
        checkRemoved();

        return super.toArray();
    }

    /** {@inheritDoc} */
    @SuppressWarnings( {"SuspiciousToArrayCall"})
    @Override public <T> T[] toArray(T[] a) {
        A.notNull(a, "a");

        checkRemoved();

        return super.toArray(a);
    }

    /**
     * Queue iterator.
     */
    private class GridCacheQueueIterator implements Iterator<T> {
        /** Default page size. */
        private static final int DFLT_PAGE_SIZE = 10;

        /** Query iterator. */
        private final GridIterator<Map.Entry<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>>> iter;

        /** Current entry. */
        private Map.Entry<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>> entry;

        /**
         * Default constructor.
         *
         */
        private GridCacheQueueIterator() {
            GridCacheQuery<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>> qry =
                qryFactory.itemsQuery(type).queryArguments(qid);

            qry.pageSize(DFLT_PAGE_SIZE);

            iter = collocated ? qry.execute(CU.primaryNode(cctx, key)) : qry.execute(cctx.grid());

            assert iter != null;
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            checkRemoved();

            return iter.hasNext();
        }

        /** {@inheritDoc} */
        @Nullable @Override public T next() {
            checkRemoved();

            entry = iter.next();

            assert entry != null;
            assert entry.getValue() != null;

            return entry.getValue().userObject();
        }

        /** {@inheritDoc} */
        @Override public void remove() {
            checkRemoved();

            // If entry hasn't been set.
            if (entry == null)
                throw new IllegalStateException("Remove cannot be called twice without advancing iterator.");

            // Save locally current iterator element.
            final Map.Entry<GridCacheQueueItemKey, GridCacheQueueItemImpl<T>> locEntry = entry;

            try {
                // Try to get item from cache.
                if (itemView.get(locEntry.getKey()) == null)
                    return;

                boolean res = CU.outTx(new Callable<Boolean>() {
                        @Override public Boolean call() throws Exception {
                            GridCacheTx tx = CU.txStartInternal(cctx, cctx.cache(), PESSIMISTIC, REPEATABLE_READ);

                            try {
                                GridCacheQueueHeader globalHdr = queueHdrView.get(key);

                                checkRemovedx();

                                assert globalHdr != null : "Failed to find queue header in cache: " +
                                    GridCacheQueueImpl.this;

                                if (globalHdr.empty()) {
                                    tx.setRollbackOnly();

                                    return false;
                                }

                                // Remove item from cache.
                                if (itemView.removex(locEntry.getKey())) {
                                    if (log.isDebugEnabled())
                                        log.debug("Removing queue item [item=" + locEntry.getValue() + ", queue=" +
                                            this + ']');

                                    globalHdr.decrementSize();

                                    // Update queue header.
                                    queueHdrView.putx(key, globalHdr);
                                }
                                else {
                                    tx.setRollbackOnly();

                                    if (log.isDebugEnabled())
                                        log.debug("Queue item has been removed [item=" + locEntry.getValue() +
                                            ", queue=" + this + ']');

                                    return false;
                                }

                                tx.commit();

                                return true;
                            }
                            finally {
                                tx.end();
                            }
                        }
                    }, cctx);

                if (res)
                    entry = null;
            }
            catch (GridException e) {
                log.error("Failed to remove item: " + entry, e);

                throw new GridRuntimeException("Failed to remove item: " + entry, e);
            }
        }

    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(cctx);
        out.writeUTF(qid);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        stash.get().set1((GridCacheContext)in.readObject());
        stash.get().set2(in.readUTF());
    }

    /**
     * Reconstructs object on demarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of demarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        GridTuple2<GridCacheContext, String> t = stash.get();

        try {
            return t.get1().dataStructures().queue(t.get2(), GridCacheQueueType.FIFO, Integer.MAX_VALUE, true, false);
        }
        catch (GridException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheQueueImpl.class, this);
    }
}
