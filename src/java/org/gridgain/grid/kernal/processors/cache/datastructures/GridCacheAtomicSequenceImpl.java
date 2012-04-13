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
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static java.util.concurrent.TimeUnit.*;
import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * Cache sequence implementation.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
public final class GridCacheAtomicSequenceImpl extends GridMetadataAwareAdapter implements GridCacheAtomicSequenceEx,
    Externalizable {
    /** Deserialization stash. */
    private static final ThreadLocal<GridTuple2<GridCacheContext, String>> stash =
        new ThreadLocal<GridTuple2<GridCacheContext, String>>() {
            @Override protected GridTuple2<GridCacheContext, String> initialValue() {
                return F.t2();
            }
        };

    /** Logger. */
    private GridLogger log;

    /** Sequence name. */
    private String name;

    /** Removed flag. */
    private volatile boolean rmvd;

    /** Sequence key. */
    private GridCacheInternalStorableKey key;

    /** Sequence projection. */
    private GridCacheProjection<GridCacheInternalStorableKey, GridCacheAtomicSequenceValue> seqView;

    /** Cache context. */
    private volatile GridCacheContext ctx;

    /** Local value of sequence. */
    private long locVal;

    /**  Upper bound of local counter. */
    private long upBound;

    /**  Sequence batch size */
    private volatile int batchSize;

    /** Synchronization lock. */
    private final Lock lock = new ReentrantLock();

    /** Await condition. */
    private Condition cond = lock.newCondition();

    /** Callable for execution {@link #incrementAndGet} operation in async and sync mode.  */
    private final Callable<Long> incAndGetCall = internalUpdate(1, true);

    /** Callable for execution {@link #getAndIncrement} operation in async and sync mode.  */
    private final Callable<Long> getAndIncCall = internalUpdate(1, false);

    /** Add and get cache call guard. */
    private final AtomicBoolean updateGuard = new AtomicBoolean();

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridCacheAtomicSequenceImpl() {
        // No-op.
    }

    /**
     * Default constructor.
     *
     * @param name Sequence name.
     * @param key Sequence key.
     * @param seqView Sequence projection.
     * @param ctx CacheContext.
     * @param locVal Local counter.
     * @param upBound Upper bound.
     */
    public GridCacheAtomicSequenceImpl(String name, GridCacheInternalStorableKey key,
        GridCacheProjection<GridCacheInternalStorableKey, GridCacheAtomicSequenceValue> seqView,
        GridCacheContext ctx, long locVal, long upBound) {
        assert key != null;
        assert seqView != null;
        assert ctx != null;
        assert locVal <= upBound;

        batchSize = ctx.config().getAtomicSequenceReserveSize();
        this.ctx = ctx;
        this.key = key;
        this.seqView = seqView;
        this.upBound = upBound;
        this.locVal = locVal;
        this.name = name;

        log = ctx.gridConfig().getGridLogger().getLogger(getClass());
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return name;
    }

    /** {@inheritDoc} */
    @Override public long get() throws GridException {
        checkRemoved();

        lock.lock();

        try {
            return locVal;
        }
        finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public long incrementAndGet() throws GridException {
        return internalUpdate(1, incAndGetCall, true);
    }

    /** {@inheritDoc} */
    @Override public long getAndIncrement() throws GridException {
        return internalUpdate(1, getAndIncCall, false);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> incrementAndGetAsync() throws GridException {
        return internalUpdateAsync(1, incAndGetCall, true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> getAndIncrementAsync() throws GridException {
        return internalUpdateAsync(1, getAndIncCall, false);
    }

    /** {@inheritDoc} */
    @Override public long addAndGet(long l) throws GridException {
        A.ensure(l > 0, " Parameter mustn't be less then 1: " + l);

        return internalUpdate(l, null, true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> addAndGetAsync(long l) throws GridException {
        A.ensure(l > 0, " Parameter mustn't be less then 1: " + l);

        return internalUpdateAsync(l, null, true);
    }

    /** {@inheritDoc} */
    @Override public long getAndAdd(long l) throws GridException {
        A.ensure(l > 0, " Parameter mustn't be less then 1: " + l);

        return internalUpdate(l, null, false);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> getAndAddAsync(long l) throws GridException {
        A.ensure(l > 0, " Parameter mustn't be less then 1: " + l);

        return internalUpdateAsync(l, null, false);
    }

    /**
     * Synchronous sequence update operation. Will add given amount to the sequence value.
     *
     * @param l Increment amount.
     * @param updateCall Cache call that will update sequence reservation count in accordance with l.
     * @param updated If {@code true}, will return sequence value after update, otherwise will return sequence value
     *      prior to update.
     * @return Sequence value.
     * @throws GridException If update failed.
     */
    private long internalUpdate(long l, @Nullable Callable<Long> updateCall, boolean updated) throws GridException {
        checkRemoved();

        assert l > 0;

        lock.lock();

        try {
            // If reserved range isn't exhausted.
            if (locVal + l <= upBound) {
                long curVal = locVal;

                locVal += l;

                return updated ? locVal : curVal;
            }
        }
        finally {
            lock.unlock();
        }

        if (updateCall == null)
            updateCall = internalUpdate(l, updated);

        while (true) {
            if (updateGuard.compareAndSet(false, true)) {
                try {
                    // This call must be outside lock.
                    return CU.outTx(updateCall, ctx);
                }
                finally {
                    lock.lock();

                    try {
                        updateGuard.set(false);

                        cond.signalAll();
                    }
                    finally {
                        lock.unlock();
                    }
                }
            }
            else {
                lock.lock();

                try {
                    while (locVal >= upBound && updateGuard.get()) {
                        try {
                            cond.await(500, MILLISECONDS);
                        }
                        catch (InterruptedException e) {
                            throw new GridInterruptedException(e);
                        }
                    }

                    checkRemoved();

                    // If reserved range isn't exhausted.
                    if (locVal + l <= upBound) {
                        long curVal = locVal;

                        locVal += l;

                        return updated ? locVal : curVal;
                    }
                }
                finally {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Asynchronous sequence update operation. Will add given amount to the sequence value.
     *
     * @param l Increment amount.
     * @param updateCall Cache call that will update sequence reservation count in accordance with l.
     * @param updated If {@code true}, will return sequence value after update, otherwise will return sequence value
     *      prior to update.
     * @return Future indicating sequence value.
     * @throws GridException If update failed.
     */
    private GridFuture<Long> internalUpdateAsync(long l, @Nullable Callable<Long> updateCall, boolean updated)
        throws GridException {
        checkRemoved();

        A.ensure(l > 0, " Parameter mustn't be less then 1: " + l);

        lock.lock();

        try {
            // If reserved range isn't exhausted.
            if (locVal + l <= upBound) {
                long curVal = locVal;

                locVal += l;

                return new GridFinishedFuture<Long>(ctx.kernalContext(), updated ? locVal : curVal);
            }
        }
        finally {
            lock.unlock();
        }

        if (updateCall == null)
            updateCall = internalUpdate(l, updated);

        while (true) {
            if (updateGuard.compareAndSet(false, true)) {
                try {
                    // This call must be outside lock.
                    return ctx.closures().callLocalSafe(updateCall, true);
                }
                finally {
                    lock.lock();

                    try {
                        updateGuard.set(false);

                        cond.signalAll();
                    }
                    finally {
                        lock.unlock();
                    }
                }
            }
            else {
                lock.lock();

                try {
                    while (locVal >= upBound && updateGuard.get()) {
                        try {
                            cond.await(500, MILLISECONDS);
                        }
                        catch (InterruptedException e) {
                            throw new GridInterruptedException(e);
                        }
                    }

                    checkRemoved();

                    // If reserved range isn't exhausted.
                    if (locVal + l <= upBound) {
                        long curVal = locVal;

                        locVal += l;

                        return new GridFinishedFuture<Long>(ctx.kernalContext(), updated ? locVal : curVal);
                    }
                }
                finally {
                    lock.unlock();
                }
            }
        }
    }

    /** Get local batch size for this sequences.
     *
     * @return Sequence batch size.
     */
    @Override public int batchSize() {
        return batchSize;
    }

    /**
     * Set local batch size for this sequences.
     *
     * @param size Sequence batch size. Must be more then 0.
     */
    @Override public void batchSize(int size) {
        A.ensure(size > 0, " Batch size can't be less then 0: " + size);

        lock.lock();

        try {
            batchSize = size;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Check removed status.
     *
     * @throws GridException If removed.
     */
    private void checkRemoved() throws GridException {
        if (rmvd)
            throw new GridCacheDataStructureRemovedException("Sequence was removed from cache: " + name);
    }

    /** {@inheritDoc} */
    @Override public boolean onRemoved() {
        return rmvd = true;
    }

    /** {@inheritDoc} */
    @Override public void onInvalid(@Nullable Exception err) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public GridCacheInternalStorableKey key() {
        return key;
    }

    /** {@inheritDoc} */
    @Override public boolean removed() {
        return rmvd;
    }

    /**
     * Method returns callable for execution all update operations in async and sync mode.
     *
     * @param l Value will be added to sequence.
     * @param updated If {@code true}, will return updated value, if {@code false}, will return previous value.
     * @return Callable for execution in async and sync mode.
     */
    @SuppressWarnings("TooBroadScope")
    private Callable<Long> internalUpdate(final long l, final boolean updated) {
        return new Callable<Long>() {
            @Override public Long call() throws Exception {
                GridCacheTx tx = CU.txStartInternal(ctx, seqView, PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheAtomicSequenceValue seq = seqView.get(key);

                    checkRemoved();

                    assert seq != null;

                    long curLocVal;

                    long newUpBound;

                    lock.lock();

                    try {
                        curLocVal = locVal;

                        // If local range was already reserved in another thread.
                        if (locVal + l <= upBound) {
                            long retVal = locVal;

                            locVal += l;

                            return updated ? locVal : retVal;
                        }

                        long curGlobalVal = seq.get();

                        long newLocVal;

                        /* We should use offset because we already reserved left side of range.*/
                        long off = batchSize > 1 ? batchSize - 1 : 1;

                        // Calculate new values for local counter, global counter and upper bound.
                        if (curLocVal + l >= curGlobalVal) {
                            newLocVal = curLocVal + l;

                            newUpBound = newLocVal + off;
                        }
                        else {
                            newLocVal = curGlobalVal;

                            newUpBound = newLocVal + off;
                        }

                        locVal = newLocVal;
                        upBound = newUpBound;

                        if (updated)
                            curLocVal = newLocVal;
                    }
                    finally {
                        lock.unlock();
                    }

                    // Global counter must be more than reserved upper bound.
                    seq.set(newUpBound + 1);

                    seqView.put(key, seq);

                    tx.commit();

                    return curLocVal;
                }
                catch (Error e) {
                    log.error("Failed to get and add: " + this, e);

                    throw e;
                }
                catch (Exception e) {
                    log.error("Failed to get and add: " + this, e);

                    throw e;
                }
                finally {
                    tx.end();
                }
            }
        };
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(ctx);
        out.writeUTF(name);
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
    private Object readResolve() throws ObjectStreamException {
        GridTuple2<GridCacheContext, String> t = stash.get();

        try {
            return t.get1().dataStructures().sequence(t.get2(), 0L, false, false);
        }
        catch (GridException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheAtomicSequenceImpl.class, this);
    }
}
