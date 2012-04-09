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
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.concurrent.*;

import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * Cache atomic reference implementation.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.1c.09042012
 */
public final class GridCacheAtomicReferenceImpl<T> extends GridMetadataAwareAdapter implements
    GridCacheAtomicReferenceEx<T>, Externalizable {
    /** Deserialization stash. */
    private static final ThreadLocal<GridTuple2<GridCacheContext, String>> stash =
        new ThreadLocal<GridTuple2<GridCacheContext, String>>() {
            @Override protected GridTuple2<GridCacheContext, String> initialValue() {
                return F.t2();
            }
        };

    /** Logger. */
    private GridLogger log;

    /** Atomic reference name. */
    private String name;

    /** Status.*/
    private volatile boolean rmvd;

    /** Atomic reference key. */
    private GridCacheInternalStorableKey<GridCacheAtomicReferenceValue<T>, T> key;

    /** Atomic reference projection. */
    private GridCacheProjection<GridCacheInternalStorableKey<GridCacheAtomicReferenceValue<T>, T>,
        GridCacheAtomicReferenceValue<T>> atomicView;

    /** Cache context. */
    private GridCacheContext ctx;

    /** Callable for {@link #get} operation */
    private final Callable<T> getCall = new Callable<T>() {
        @Override public T call() throws Exception {
            GridCacheAtomicReferenceValue<T> ref = atomicView.get(key);

            if (ref == null)
                throw new GridException("Failed to find atomic reference with given name: " + name);

            return ref.get();
        }
    };

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridCacheAtomicReferenceImpl() {
        // No-op.
    }

    /**
     * Default constructor.
     *
     * @param name Atomic reference name.
     * @param key Atomic reference key.
     * @param atomicView Atomic projection.
     * @param ctx Cache context.
     */
    public GridCacheAtomicReferenceImpl(String name,
        GridCacheInternalStorableKey<GridCacheAtomicReferenceValue<T>, T> key,
        GridCacheProjection<GridCacheInternalStorableKey<GridCacheAtomicReferenceValue<T>, T>,
            GridCacheAtomicReferenceValue<T>> atomicView,
        GridCacheContext ctx) {
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
    @Override public T get() throws GridException {
        checkRemoved();

        return CU.outTx(getCall, ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<T> getAsync() throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(getCall, true);
    }

    /** {@inheritDoc} */
    @Override public void set(T val) throws GridException {
        checkRemoved();

        CU.outTx(internalSet(val), ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> setAsync(T val) throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalSet(val), true);
    }

    /** {@inheritDoc} */
    @Override public boolean compareAndSet(T expVal, T newVal) throws GridException {
        checkRemoved();

        return CU.outTx(internalCompareAndSet(wrapperPredicate(expVal), wrapperClosure(newVal)), ctx);
    }

    /** {@inheritDoc} */
    @Override public boolean compareAndSet(T expVal, GridClosure<T, T> newValClos) throws GridException {
        checkRemoved();

        return CU.outTx(internalCompareAndSet(wrapperPredicate(expVal), newValClos), ctx);
    }

    /** {@inheritDoc} */
    @Override public boolean compareAndSet(GridPredicate<T> expValPred, GridClosure<T, T> newValClos)
        throws GridException {
        checkRemoved();

        return CU.outTx(internalCompareAndSet(expValPred, newValClos), ctx);
    }

    /** {@inheritDoc} */
    @Override public boolean compareAndSet(GridPredicate<T> expValPred, T newVal) throws GridException {
        checkRemoved();

        return CU.outTx(internalCompareAndSet(expValPred, wrapperClosure(newVal)), ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> compareAndSetAsync(T expVal, T newVal) throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalCompareAndSet(wrapperPredicate(expVal), wrapperClosure(newVal)),
            true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> compareAndSetAsync(T expVal, GridClosure<T, T> newValClos)
        throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalCompareAndSet(wrapperPredicate(expVal), newValClos), true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> compareAndSetAsync(GridPredicate<T> expValPred,
        GridClosure<T, T> newValClos) throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalCompareAndSet(expValPred, newValClos), true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> compareAndSetAsync(GridPredicate<T> expValPred, T newVal)
        throws GridException {
        checkRemoved();

        return ctx.closures().callLocalSafe(internalCompareAndSet(expValPred, wrapperClosure(newVal)), true);
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
     * Method make wrapper predicate for existing value.
     *
     * @param val Value.
     * @return Predicate.
     */
    private GridPredicate<T> wrapperPredicate(final T val) {
        return new GridPredicate<T>() {
            @Override public boolean apply(T e) {
                return val != null && val.equals(e);
            }
        };
    }

    /**
     * Method make wrapper closure for existing value.
     *
     * @param val Value.
     * @return Closure.
     */
    private GridClosure<T, T> wrapperClosure(final T val) {
        return new GridClosure<T, T>() {
            @Override public T apply(T e) {
                return val;
            }
        };
    }

    /**
     * Method returns callable for execution {@link #set(Object)} operation in async and sync mode.
     *
     * @param val Value will be set in reference .
     * @return Callable for execution in async and sync mode.
     */
    private Callable<Boolean> internalSet(final T val) {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {

                GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheAtomicReferenceValue<T> ref = atomicView.get(key);

                    if (ref == null)
                        throw new GridException("Failed to find atomic reference with given name: " + name);

                    ref.set(val);

                    atomicView.put(key, ref);

                    tx.commit();

                    return true;
                }
                catch (Error e) {
                    log.error("Failed to set value [val=" + val + ", atomicReference=" + this + ']', e);

                    throw e;
                }
                catch (Exception e) {
                    log.error("Failed to set value [val=" + val + ", atomicReference=" + this + ']', e);

                    throw e;
                }
                finally {
                    tx.end();
                }
            }
        };
    }

    /**
     * Conditionally sets the new value. It will be set if {@code expValPred} is
     * evaluate to {@code true}.
     *
     * @param expValPred Predicate which should evaluate to {@code true} for value to be set.
     * @param newValClos Closure which generates new value.
     * @return Callable for execution in async and sync mode.
     */
    private Callable<Boolean> internalCompareAndSet(final GridPredicate<T> expValPred,
        final GridClosure<T, T> newValClos) {
        return new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                GridCacheTx tx = CU.txStartInternal(ctx, atomicView, PESSIMISTIC, REPEATABLE_READ);

                try {
                    GridCacheAtomicReferenceValue<T> ref = atomicView.get(key);

                    if (ref == null)
                        throw new GridException("Failed to find atomic reference with given name: " + name);

                    if (!expValPred.apply(ref.get())) {
                        tx.setRollbackOnly();

                        return false;
                    }
                    else {
                        ref.set(newValClos.apply(ref.get()));

                        atomicView.put(key, ref);

                        tx.commit();

                        return true;
                    }
                }
                catch (Error e) {
                    log.error("Failed to compare and value [expValPred=" + expValPred + ", newValClos" + newValClos
                        + ", atomicReference" + this + ']', e);

                    throw e;
                }
                catch (Exception e) {
                    log.error("Failed to compare and value [expValPred=" + expValPred + ", newValClos" + newValClos
                        + ", atomicReference" + this + ']', e);

                    throw e;
                }
                finally {
                    tx.end();
                }
            }
        };
    }

    /**
     * Check removed status.
     *
     * @throws GridException If removed.
     */
    private void checkRemoved() throws GridException {
        if (rmvd)
            throw new GridCacheDataStructureRemovedException("Atomic reference was removed from cache: " + name);
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
            return t.get1().dataStructures().atomicReference(t.get2(), null, false, false);
        }
        catch (GridException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheAtomicReferenceImpl.class, this);
    }
}
