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
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * Cache count down latch implementation.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.0c.24032012
 */
public final class GridCacheCountDownLatchImpl extends GridMetadataAwareAdapter implements GridCacheCountDownLatchEx,
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

    /** Latch name. */
    private String name;

    /** Removed flag.*/
    private volatile boolean rmvd;

    /** Latch key. */
    private GridCacheInternalKey key;

    /** Latch projection. */
    private GridCacheProjection<GridCacheInternalKey, GridCacheCountDownLatchValue> latchView;

    /** Cache context. */
    private GridCacheContext ctx;

    /** Current count. */
    private int cnt;

    /** Initial count. */
    private int initCnt;

    /** Auto delete flag. */
    private boolean autoDel;

    /** Internal latch (transient). */
    private volatile CountDownLatch internalLatch;

    /** Initialization guard. */
    private final AtomicBoolean initGuard = new AtomicBoolean();

    /** Initialization latch. */
    private final CountDownLatch initLatch = new CountDownLatch(1);

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridCacheCountDownLatchImpl() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param name Latch name.
     * @param cnt Current count.
     * @param initCnt Initial count.
     * @param autoDel Auto delete flag.
     * @param key Latch key.
     * @param latchView Latch projection.
     * @param ctx Cache context.
     */
    public GridCacheCountDownLatchImpl(String name, int cnt, int initCnt, boolean autoDel, GridCacheInternalKey key,
        GridCacheProjection<GridCacheInternalKey, GridCacheCountDownLatchValue> latchView, GridCacheContext ctx) {
        assert name != null;
        assert cnt >= 0;
        assert initCnt >= 0;
        assert key != null;
        assert latchView != null;
        assert ctx != null;

        this.name = name;
        this.cnt = cnt;
        this.initCnt = initCnt;
        this.autoDel = autoDel;
        this.key = key;
        this.latchView = latchView;
        this.ctx = ctx;

        log = ctx.gridConfig().getGridLogger().getLogger(getClass());
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return name;
    }

    /** {@inheritDoc} */
    @Override public int count() {
        return cnt;
    }

    /** {@inheritDoc} */
    @Override public int initialCount() {
        return initCnt;
    }

    /** {@inheritDoc} */
    @Override public boolean autoDelete() {
        return autoDel;
    }

    /** {@inheritDoc} */
    @Override public void await() throws GridException {
        initializeLatch();

        try {
            internalLatch.await();
        }
        catch (InterruptedException e) {
            throw new GridInterruptedException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean await(long timeout, TimeUnit unit) throws GridException {
        initializeLatch();

        try {
            return internalLatch.await(timeout, unit);
        }
        catch (InterruptedException e) {
            throw new GridInterruptedException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean await(long timeout) throws GridException {
        return await(timeout, TimeUnit.MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> awaitAsync() {
        return ctx.closures().callLocalSafe(
            new Callable<Object>() {
                @Nullable @Override public Object call() throws Exception {
                    await();

                    return null;
                }
            },
            true
        );
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> awaitAsync(final long timeout) {
        return ctx.closures().callLocalSafe(
            new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return await(timeout);
                }
            },
            true
        );
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Boolean> awaitAsync(final long timeout, final TimeUnit unit) {
        return ctx.closures().callLocalSafe(
            new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return await(timeout, unit);
                }
            },
            true
        );
    }

    /** {@inheritDoc} */
    @Override public int countDown() throws GridException {
        return CU.outTx(new CountDownCallable(1), ctx);
    }

    /** {@inheritDoc} */
    @Override public int countDown(int val) throws GridException {
        A.ensure(val > 0, "val should be positive");

        return CU.outTx(new CountDownCallable(val), ctx);
    }

    /** {@inheritDoc}*/
    @Override public void countDownAll() throws GridException {
        CU.outTx(new CountDownCallable(0), ctx);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Integer> countDownAsync() {
        return ctx.closures().callLocalSafe(new CountDownCallable(1), true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<Integer> countDownAsync(int val) {
        return ctx.closures().callLocalSafe(new CountDownCallable(val), true);
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> countDownAllAsync() {
        return ctx.closures().callLocalSafe(new CountDownCallable(0), true);
    }

    /** {@inheritDoc} */
    @Override public boolean onRemoved() {
        assert cnt == 0;

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

    /** {@inheritDoc} */
    @Override public void onUpdate(int cnt) {
        assert cnt >= 0;

        this.cnt = cnt;

        while (internalLatch != null && internalLatch.getCount() > cnt)
            internalLatch.countDown();
    }

    /**
     * @throws GridException If operation failed.
     */
    private void initializeLatch() throws GridException {
        if (initGuard.compareAndSet(false, true)) {
            try {
                internalLatch = CU.outTx(
                    new Callable<CountDownLatch>() {
                        @Override public CountDownLatch call() throws Exception {
                            GridCacheTx tx = CU.txStartInternal(ctx, latchView, PESSIMISTIC, REPEATABLE_READ);

                            try {
                                GridCacheCountDownLatchValue val = latchView.get(key);

                                if (val == null) {
                                    if (log.isDebugEnabled())
                                        log.debug("Failed to find count down latch with given name: " + name);

                                    assert cnt == 0;

                                    return new CountDownLatch(cnt);
                                }

                                tx.commit();

                                return new CountDownLatch(val.get());
                            }
                            finally {
                                tx.end();
                            }
                        }
                    },
                    ctx
                );

                if (log.isDebugEnabled())
                    log.debug("Initialized internal latch: " + internalLatch);
            }
            finally {
                initLatch.countDown();
            }
        }
        else {
            try {
                initLatch.await();
            }
            catch (InterruptedException ignored) {
                throw new GridException("Thread has been interrupted.");
            }

            if (internalLatch == null)
                throw new GridException("Internal latch has not been properly initialized.");
        }
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
    @SuppressWarnings({"ConstantConditions"})
    private Object readResolve() throws ObjectStreamException {
        GridTuple2<GridCacheContext, String> t = stash.get();

        try {
            return t.get1().dataStructures().countDownLatch(t.get2(), 0, false, false);
        }
        catch (GridException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheCountDownLatchImpl.class, this);
    }

    /**
     *
     */
    private class CountDownCallable implements Callable<Integer> {
        /** Value to count down on (if 0 then latch is counted down to 0). */
        private final int val;

        /**
         * @param val Value to count down on (if 0 is passed latch is counted down to 0).
         */
        private CountDownCallable(int val) {
            assert val >= 0;

            this.val = val;
        }

        /** {@inheritDoc} */
        @Override public Integer call() throws Exception {
            GridCacheTx tx = CU.txStartInternal(ctx, latchView, PESSIMISTIC, REPEATABLE_READ);

            try {
                GridCacheCountDownLatchValue latchVal = latchView.get(key);

                if (latchVal == null) {
                    if (log.isDebugEnabled())
                        log.debug("Failed to find count down latch with given name: " + name);

                    assert cnt == 0;

                    return cnt;
                }

                int retVal;

                if (val > 0) {
                    retVal = latchVal.get() - val;

                    if (retVal < 0)
                        retVal = 0;
                }
                else
                    retVal = 0;

                latchVal.set(retVal);

                latchView.put(key, latchVal);

                tx.commit();

                return retVal;
            }
            finally {
                tx.end();
            }
        }
    }
}
