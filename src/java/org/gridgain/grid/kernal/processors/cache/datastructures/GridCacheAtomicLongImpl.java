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
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.GridLogger;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.concurrent.*;

import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * Cache atomic long implementation.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.1c.07042012
 */
public final class GridCacheAtomicLongImpl extends GridMetadataAwareAdapter implements GridCacheAtomicLongEx,
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

    /** Atomic long name. */
    private String name;

    /** Removed flag.*/
    private volatile boolean rmvd;

    /** Atomic long key. */
    private GridCacheInternalStorableKey key;

    /** Atomic long projection. */
    private GridCacheProjection<GridCacheInternalStorableKey, GridCacheAtomicLongValue> atomicView;

    /** Cache context. */
    private GridCacheContext ctx;

    /** Callable for {@link #get()}. */
    private final Callable<Long> getCall = new Callable<Long>() {
        @Override public Long call() throws Exception {
            GridCacheAtomicLongValue val = atomicView.get(key);

            if (val == null)
                throw new GridException("Failed to find atomic long with given name: " + name);

            return val.get();
        }
    };

    /** Callable for {@link #incrementAndGet()}. */
    private final Callable<Long> incAndGetCall = new Callable<Long>() {
        @Override public Long call() throws Exception {
            GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

            try {
                GridCacheAtomicLongValue val = atomicView.get(key);

                if (val == null)
                    throw new GridException("Failed to find atomic long with given name: " + name);

                long retVal = val.get() + 1;

                val.set(retVal);

                atomicView.put(key, val);

                tx.commit();

                return retVal;
            }
            catch (Error e) {
                log.error("Failed to increment and get: " + this, e);

                throw e;
            }
            catch (Exception e) {
                log.error("Failed to increment and get: " + this, e);

                throw e;
            }
            finally {
                tx.end();
            }
        }
    };

    /** Callable for {@link #getAndIncrement()}. */
    private final Callable<Long> getAndIncCall = new Callable<Long>() {
        @Override public Long call() throws Exception {
            GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

            try {
                GridCacheAtomicLongValue val = atomicView.get(key);

                if (val == null)
                    throw new GridException("Failed to find atomic long with given name: " + name);

                long retVal = val.get();

                val.set(retVal + 1);

                atomicView.put(key, val);

                tx.commit();

                return retVal;
            }
            catch (Error e) {
                log.error("Failed to get and increment: " + this, e);

                throw e;
            }
            catch (Exception e) {
                log.error("Failed to get and increment: " + this, e);

                throw e;
            }
            finally {
                tx.end();
            }
        }
    };

    /** Callable for {@link #decrementAndGet()}. */
    private final Callable<Long> decAndGetCall = new Callable<Long>() {
        @Override public Long call() throws Exception {
            GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

            try {
                GridCacheAtomicLongValue val = atomicView.get(key);

                if (val == null)
                    throw new GridException("Failed to find atomic long with given name: " + name);

                long retVal = val.get() - 1;

                val.set(retVal);

                atomicView.put(key, val);

                tx.commit();

                return retVal;
            }
            catch (Error e) {
                log.error("Failed to decrement and get: " + this, e);

                throw e;
            }
            catch (Exception e) {
                log.error("Failed to decrement and get: " + this, e);

                throw e;
            }
            finally {
                tx.end();
            }
        }
    };

    /** Callable for {@link #getAndDecrement()}. */
    private final Callable<Long> getAndDecCall = new Callable<Long>() {
        @Override public Long call() throws Exception {
            GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

            try {
                GridCacheAtomicLongValue val = atomicView.get(key);

                if (val == null)
                    throw new GridException("Failed to find atomic long with given name: " + name);

                long retVal = val.get();

                val.set(retVal - 1);

                atomicView.put(key, val);

                tx.commit();

                return retVal;
            }
            catch (Error e) {
                log.error("Failed to get and decrement and get: " + this, e);

                throw e;
            }
            catch (Exception e) {
                log.error("Failed to get and decrement and get: " + this, e);

                throw e;
            }
            finally {
                tx.end();
            }
        }
    };

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridCacheAtomicLongImpl() {
        // No-op.
    }

    /**
     * Default constructor.
     *
     * @param name Atomic long name.
     * @param key Atomic long key.
     * @param atomicView Atomic projection.
     * @param ctx CacheContext.
     */
    public GridCacheAtomicLongImpl(String name, GridCacheInternalStorableKey key,
        GridCacheProjection<GridCacheInternalStorableKey, GridCacheAtomicLongValue> atomicView, GridCacheContext ctx) {
        assert key != null;
        assert atomicView != null;
        assert ctx != null;
        assert name != null;

        this.ctx = ctx;
        this.key = key;
        this.atomicView = atomicView;
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

        return CU.outTx(getCall, ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> getAsync() throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(getCall, true);
    }

    /** {@inheritDoc} */
    @Override public long incrementAndGet() throws GridException {
        checkRemoved();

        return CU.outTx(incAndGetCall, ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> incrementAndGetAsync() throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(incAndGetCall, true);
    }

    /** {@inheritDoc} */
    @Override public long getAndIncrement() throws GridException {
        checkRemoved();

        return CU.outTx(getAndIncCall, ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> getAndIncrementAsync() throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(getAndIncCall, true);
    }

    /** {@inheritDoc} */
    @Override public long addAndGet(long l) throws GridException {
        checkRemoved();

        return CU.outTx(internalAddAndGet(l), ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> addAndGetAsync(long l) throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalAddAndGet(l), true);
    }

    /** {@inheritDoc} */
    @Override public long getAndAdd(long l) throws GridException {
        checkRemoved();

        return CU.outTx(internalGetAndAdd(l), ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> getAndAddAsync(long l) throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalGetAndAdd(l), true);
    }

    /** {@inheritDoc} */
    @Override public long decrementAndGet() throws GridException {
        checkRemoved();

        return CU.outTx(decAndGetCall, ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> decrementAndGetAsync() throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(decAndGetCall, true);
    }

    /** {@inheritDoc} */
    @Override public long getAndDecrement() throws GridException {
        checkRemoved();

        return CU.outTx(getAndDecCall, ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> getAndDecrementAsync() throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(getAndDecCall, true);
    }

    /** {@inheritDoc} */
    @Override public long getAndSet(long l) throws GridException {
        checkRemoved();

        return CU.outTx(internalGetAndSet(l), ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Long> getAndSetAsync(long l) throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalGetAndSet(l), true);
    }

    /** {@inheritDoc} */
    @Override public boolean compareAndSet(long l, GridPredicate<Long> p, GridPredicate<Long>... ps)
        throws GridException {
        checkRemoved();
        A.notNull(p, "p");

        return CU.outTx(internalCompareAndSet(l, p, ps), ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> compareAndSetAsync(long l, GridPredicate<Long> p, GridPredicate<Long>... ps)
        throws GridException {
        checkRemoved();
        A.notNull(p, "p");

        return ctx.closures().callLocalSafe(internalCompareAndSet(l, p, ps), true);
    }

    /**
     * Check removed flag.
     *
     * @throws GridException If removed.
     */
    private void checkRemoved() throws GridException {
        if (rmvd)
            throw new GridCacheDataStructureRemovedException("Atomic long was removed from cache: " + name);
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
     * Method returns callable for execution {@link #addAndGet(long)} operation in async and sync mode.
     *
     * @param l Value will be added to atomic long.
     * @return Callable for execution in async and sync mode.
     */
    private Callable<Long> internalAddAndGet(final long l) {
        return new Callable<Long>() {
            @Override public Long call() throws Exception {
                GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheAtomicLongValue val = atomicView.get(key);

                    if (val == null)
                        throw new GridException("Failed to find atomic long with given name: " + name);

                    long retVal = val.get() + l;

                    val.set(retVal);

                    atomicView.put(key, val);

                    tx.commit();

                    return retVal;
                }
                catch (Error e) {
                    log.error("Failed to add and get: " + this, e);

                    throw e;
                }
                catch (Exception e) {
                    log.error("Failed to add and get: " + this, e);

                    throw e;
                }
                finally {
                    tx.end();
                }
            }
        };
    }

    /**
     * Method returns callable for execution {@link #getAndAdd(long)} operation in async and sync mode.
     *
     * @param l Value will be added to atomic long.
     * @return Callable for execution in async and sync mode.
     */
    private Callable<Long> internalGetAndAdd(final long l) {
        return new Callable<Long>() {
            @Override public Long call() throws Exception {
                GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheAtomicLongValue val = atomicView.get(key);

                    if (val == null)
                        throw new GridException("Failed to find atomic long with given name: " + name);

                    long retVal = val.get();

                    val.set(retVal + l);

                    atomicView.put(key, val);

                    tx.commit();

                    return retVal;
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

    /**
     * Method returns callable for execution {@link #getAndSet(long)} operation in async and sync mode.
     *
     * @param l Value will be added to atomic long.
     * @return Callable for execution in async and sync mode.
     */
    private Callable<Long> internalGetAndSet(final long l) {
        return new Callable<Long>() {
            @Override public Long call() throws Exception {
                GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheAtomicLongValue val = atomicView.get(key);

                    if (val == null)
                        throw new GridException("Failed to find atomic long with given name: " + name);

                    long retVal = val.get();

                    val.set(l);

                    atomicView.put(key, val);

                    tx.commit();

                    return retVal;
                }
                catch (Error e) {
                    log.error("Failed to get and set: " + this, e);

                    throw e;
                }
                catch (Exception e) {
                    log.error("Failed to get and set: " + this, e);

                    throw e;
                }
                finally {
                    tx.end();
                }
            }
        };
    }

    /**
     * Method returns callable for execution {@link #compareAndSet(long,GridPredicate,GridPredicate[])}
     * operation in async and sync mode.
     *
     * @param l Value will be added to atomic long.
     * @param p  Predicate contains conditions which will be checked.
     *      Argument of predicate is current atomic long value.
     * @param ps Additional predicates can be used optional .
     * @return Callable for execution in async and sync mode.
     */
    private Callable<Boolean> internalCompareAndSet(final long l, final GridPredicate<Long> p,
        final GridPredicate<Long>... ps) {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheAtomicLongValue val = atomicView.get(key);

                    if (val == null)
                        throw new GridException("Failed to find atomic long with given name: " + name);

                    boolean retVal = p.apply(val.get());

                    for (GridPredicate<Long> p : ps)
                        retVal &= p.apply(val.get());

                    if (retVal) {
                        val.set(l);

                        atomicView.put(key, val);

                        tx.commit();
                    }

                    return retVal;
                }
                catch (Error e) {
                    log.error("Failed to compare and set: " + this, e);

                    throw e;
                }
                catch (Exception e) {
                    log.error("Failed to compare and set: " + this, e);

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
            return t.get1().dataStructures().atomicLong(t.get2(), 0L, false, false);
        }
        catch (GridException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheAtomicLongImpl.class, this);
    }
}
