// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.future;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.concurrent.*;

/**
 * Future that is completed at creation time.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
public class GridFinishedFuture<T> extends GridMetadataAwareAdapter implements GridFuture<T>, Externalizable {
    /** Synchronous notification flag. */
    private static final boolean SYNC_NOTIFY = U.isFutureNotificationSynchronous("true");

    /** Complete value. */
    private T t;

    /** Error. */
    private Throwable err;

    /** Context. */
    protected GridKernalContext ctx;

    /** Start time. */
    private final long startTime = System.currentTimeMillis();

    /** Synchronous notification flag. */
    private volatile boolean syncNotify = SYNC_NOTIFY;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridFinishedFuture() {
        // No-op.
    }

    /**
     * Creates finished future with complete value.
     *
     * @param ctx Context.
     */
    public GridFinishedFuture(GridKernalContext ctx) {
        assert ctx != null;

        this.ctx = ctx;

        t = null;
        err = null;
    }

    /**
     * Creates finished future with complete value.
     *
     * @param ctx Context.
     * @param t Finished value.
     */
    public GridFinishedFuture(GridKernalContext ctx, T t) {
        assert ctx != null;

        this.ctx = ctx;
        this.t = t;

        err = null;
    }

    /**
     * @param ctx Context.
     * @param err Future error.
     */
    public GridFinishedFuture(GridKernalContext ctx, Throwable err) {
        assert ctx != null;

        this.ctx = ctx;
        this.err = err;

        t = null;
    }

    /** {@inheritDoc} */
    @Override public long startTime() {
        return startTime;
    }

    /** {@inheritDoc} */
    @Override public long duration() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public boolean concurrentNotify() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public void concurrentNotify(boolean concurNotify) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void syncNotify(boolean syncNotify) {
        this.syncNotify = syncNotify;
    }

    /** {@inheritDoc} */
    @Override public boolean syncNotify() {
        return syncNotify;
    }

    /** {@inheritDoc} */
    @Override public T call() throws Exception {
        return get();
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean isCancelled() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean isDone() {
        return true;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"NullableProblems"})
    @Override public GridAbsPredicate predicate() {
        return F.alwaysTrue().curry(null);
    }

    /** {@inheritDoc} */
    @Override public T get() throws GridException {
        if (err != null)
            throw U.cast(err);

        return t;
    }

    /** {@inheritDoc} */
    @Override public T get(long timeout) throws GridException {
        return get();
    }

    /** {@inheritDoc} */
    @Override public T get(long timeout, TimeUnit unit) throws GridException {
        return get();
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void listenAsync(final GridInClosure<? super GridFuture<T>> lsnr) {
        if (ctx == null)
            throw new IllegalStateException("Cannot attach listener to deserialized future (context is null): " + this);

        if (lsnr != null) {
            if (syncNotify)
                lsnr.apply(this);
            else
                ctx.closure().runLocalSafe(new GPR() {
                    @Override public void run() {
                        lsnr.apply(GridFinishedFuture.this);
                    }
                }, true);
        }
    }

    /** {@inheritDoc} */
    @Override public void stopListenAsync(@Nullable GridInClosure<? super GridFuture<T>>... lsnr) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(t);
        out.writeObject(err);
        out.writeBoolean(syncNotify);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked"})
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        t = (T)in.readObject();
        err = (Throwable)in.readObject();
        syncNotify = in.readBoolean();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridFinishedFuture.class, this);
    }
}
