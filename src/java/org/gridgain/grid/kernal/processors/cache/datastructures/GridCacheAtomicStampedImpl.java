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
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.concurrent.*;

import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * Cache atomic stamped implementation.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.0c.25032012
 */
public final class GridCacheAtomicStampedImpl<T, S> extends GridMetadataAwareAdapter implements
    GridCacheAtomicStampedEx<T, S>, Externalizable {
    /** Deserialization stash. */
    private static final ThreadLocal<GridTuple2<GridCacheContext, String>> stash =
        new ThreadLocal<GridTuple2<GridCacheContext, String>>() {
            @Override protected GridTuple2<GridCacheContext, String> initialValue() {
                return F.t2();
            }
        };

    /** Logger. */
    private GridLogger log;

    /** Atomic stamped name. */
    private String name;

    /** Removed flag.*/
    private volatile boolean rmvd;

    /** Atomic stamped key. */
    private GridCacheInternalKey key;

    /** Atomic stamped projection. */
    private GridCacheProjection<GridCacheInternalKey, GridCacheAtomicStampedValue<T, S>> atomicView;

    /** Cache context. */
    private GridCacheContext ctx;

    /** Callable for {@link #get()} operation */
    private final Callable<GridTuple2<T, S>> getCall = new Callable<GridTuple2<T, S>>() {
        @Override public GridTuple2<T, S> call() throws Exception {
            GridCacheAtomicStampedValue<T, S> stmp = atomicView.get(key);

            if (stmp == null)
                throw new GridException("Failed to find atomic stamped with given name: " + name);

            return stmp.get();
        }
    };

    /** Callable for {@link #value()} operation */
    private final Callable<T> valueCall = new Callable<T>() {
        @Override public T call() throws Exception {
            GridCacheAtomicStampedValue<T, S> stmp = atomicView.get(key);

            if (stmp == null)
                throw new GridException("Failed to find atomic stamped with given name: " + name);

            return stmp.value();
        }
    };

    /** Callable for {@link #stamp()} operation */
    private final Callable<S> stampCall = new Callable<S>() {
        @Override public S call() throws Exception {
            GridCacheAtomicStampedValue<T, S> stmp = atomicView.get(key);

            if (stmp == null)
                throw new GridException("Failed to find atomic stamped with given name: " + name);

            return stmp.stamp();
        }
    };

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridCacheAtomicStampedImpl() {
        // No-op.
    }

    /**
     * Default constructor.
     *
     * @param name Atomic stamped name.
     * @param key Atomic stamped key.
     * @param atomicView Atomic projection.
     * @param ctx Cache context.
     */
    public GridCacheAtomicStampedImpl(String name, GridCacheInternalKey key, GridCacheProjection<GridCacheInternalKey,
        GridCacheAtomicStampedValue<T, S>> atomicView, GridCacheContext ctx) {
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
    @Override public GridTuple2<T, S> get() throws GridException {
        checkRemoved();

        return CU.outTx(getCall, ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<GridTuple2<T, S>> getAsync() throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(getCall, true);
    }

    /** {@inheritDoc} */
    @Override public void set(T val, S stamp) throws GridException {
        checkRemoved();

        CU.outTx(internalSet(val, stamp), ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> setAsync(T val, S stamp) throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalSet(val, stamp), true);
    }

    /** {@inheritDoc} */
    @Override public boolean compareAndSet(T expVal, T newVal, S expStamp, S newStamp) throws GridException {
        checkRemoved();

        return CU.outTx(internalCompareAndSet(F.equalTo(expVal), wrapperClosure(newVal),
            F.equalTo(expStamp), wrapperClosure(newStamp)), ctx);
    }

    /** {@inheritDoc} */
    @Override public boolean compareAndSet(T expVal, GridClosure<T, T> newValClos, S expStamp,
        GridClosure<S, S> newStampClos) throws GridException {
        checkRemoved();

        return CU.outTx(internalCompareAndSet(F.equalTo(expVal), newValClos,
            F.equalTo(expStamp), newStampClos), ctx);
    }

    /** {@inheritDoc} */
    @Override public boolean compareAndSet(GridPredicate<T> expValPred, GridClosure<T, T> newValClos,
        GridPredicate<S> expStampPred, GridClosure<S, S> newStampClos) throws GridException {
        checkRemoved();

        return CU.outTx(internalCompareAndSet(expValPred, newValClos, expStampPred,
            newStampClos), ctx);
    }

    /** {@inheritDoc} */
    @Override public boolean compareAndSet(GridPredicate<T> expValPred, T newVal,
        GridPredicate<S> expStampPred, S newStamp) throws GridException {
        checkRemoved();

        return CU.outTx(internalCompareAndSet(expValPred, wrapperClosure(newVal), expStampPred,
            wrapperClosure(newStamp)), ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> compareAndSetAsync(T expVal, T newVal, S expStamp, S newStamp)
        throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalCompareAndSet(F.equalTo(expVal), wrapperClosure(newVal),
            F.equalTo(expStamp), wrapperClosure(newStamp)), true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> compareAndSetAsync(T expVal, GridClosure<T, T> newValClos,
        S expStamp, GridClosure<S, S> newStampClos) throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalCompareAndSet(F.equalTo(expVal), newValClos,
            F.equalTo(expStamp), newStampClos), true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> compareAndSetAsync(GridPredicate<T> expValPred,
        GridClosure<T, T> newValClos, GridPredicate<S> expStampPred, GridClosure<S, S> newStampClos)
        throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalCompareAndSet(expValPred, newValClos,
            expStampPred, newStampClos), true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> compareAndSetAsync(GridPredicate<T> expValPred, T newVal,
        GridPredicate<S> expStampPred, S newStamp) throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalCompareAndSet(expValPred, wrapperClosure(newVal),
            expStampPred, wrapperClosure(newStamp)), true);
    }

    /** {@inheritDoc} */
    @Override public S stamp() throws GridException {
        checkRemoved();

        return CU.outTx(stampCall, ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<S> stampAsync() throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(stampCall, true);
    }

    /** {@inheritDoc} */
    @Override public T value() throws GridException {
        checkRemoved();

        return CU.outTx(valueCall, ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<T> valueAsync() throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(valueCall, true);
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
    @Override public GridCacheInternalKey key() {
        return key;
    }

    /** {@inheritDoc} */
    @Override public boolean removed() {
        return rmvd;
    }

    /**
     * Method make wrapper closure for existing value.
     *
     * @param val Value.
     * @return Closure.
     */
    private <N> GridClosure<N, N> wrapperClosure(final N val) {
        return new GridClosure<N, N>() {
            @Override public N apply(N e) {
                return val;
            }
        };
    }

    /**
     * Method returns callable for execution {@link #set(Object,Object)}} operation in async and sync mode.
     *
     * @param val Value will be set in the atomic stamped.
     * @param stamp Stamp will be set in the atomic stamped.
     * @return Callable for execution in async and sync mode.
     */
    private Callable<Boolean> internalSet(final T val, final S stamp) {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {

                GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheAtomicStampedValue<T, S> stmp = atomicView.get(key);

                    if (stmp == null)
                        throw new GridException("Failed to find atomic stamped with given name: " + name);

                    stmp.set(val, stamp);

                    atomicView.put(key, stmp);

                    tx.commit();

                    return true;
                }
                catch (Error e) {
                    log.error("Failed to set [val=" + val + ", stamp=" + stamp + ", atomicStamped=" + this + ']', e);

                    throw e;
                }
                catch (Exception e) {
                    log.error("Failed to set [val=" + val + ", stamp=" + stamp + ", atomicStamped=" + this + ']', e);

                    throw e;
                }
                finally {
                    tx.end();
                }
            }
        };
    }

    /**
     * Conditionally asynchronously sets the new value and new stamp. They will be set if
     * {@code expValPred} and {@code expStampPred} both evaluate to {@code true}.
     *
     * @param expValPred Predicate which should evaluate to {@code true} for value to be set
     * @param newValClos Closure generates new value.
     * @param expStampPred Predicate which should evaluate to {@code true} for value to be set
     * @param newStampClos Closure generates new stamp value.
     * @return Callable for execution in async and sync mode.
     */
    private Callable<Boolean> internalCompareAndSet(final GridPredicate<T> expValPred,
        final GridClosure<T, T> newValClos, final GridPredicate<S> expStampPred,
        final GridClosure<S, S> newStampClos) {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheAtomicStampedValue<T, S> stmp = atomicView.get(key);

                    if (stmp == null)
                        throw new GridException("Failed to find atomic stamped with given name: " + name);

                    if (!(expValPred.apply(stmp.value()) && expStampPred.apply(stmp.stamp()))) {
                        tx.setRollbackOnly();

                        return false;
                    }
                    else {
                        stmp.set(newValClos.apply(stmp.value()), newStampClos.apply(stmp.stamp()));

                        atomicView.put(key, stmp);

                        tx.commit();

                        return true;
                    }
                }
                catch (Error e) {
                    log.error("Failed to compare and set [expValPred=" + expValPred + ", newValClos=" + newValClos
                        + ", expStampPred=" + expStampPred + ", newStampClos=" + newStampClos + ", atomicStamped=" +
                        this + ']', e);

                    throw e;
                }
                catch (Exception e) {
                    log.error("Failed to compare and set [expValPred=" + expValPred + ", newValClos=" + newValClos
                        + ", expStampPred=" + expStampPred + ", newStampClos=" + newStampClos + ", atomicStamped=" +
                        this + ']', e);

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
    @SuppressWarnings("unchecked")
    private Object readResolve() throws ObjectStreamException {
        GridTuple2<GridCacheContext, String> t = stash.get();

        try {
            return t.get1().dataStructures().atomicStamped(t.get2(), null, null, false);
        }
        catch (GridException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /**
     * Check removed status.
     *
     * @throws org.gridgain.grid.GridException If removed.
     */
    private void checkRemoved() throws GridException {
        if (rmvd)
            throw new GridCacheDataStructureRemovedException("Atomic stamped was removed from cache: " + name);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return GridToStringBuilder.toString(GridCacheAtomicStampedImpl.class, this);
    }
}
