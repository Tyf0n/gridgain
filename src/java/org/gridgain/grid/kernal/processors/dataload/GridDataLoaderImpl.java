// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.dataload;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import static org.gridgain.grid.GridClosureCallMode.*;
import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.cache.GridCacheMode.*;

import static org.gridgain.grid.cache.GridCacheTxConcurrency.*;
import static org.gridgain.grid.cache.GridCacheTxIsolation.*;

/**
 * Data loader implementation.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
public class GridDataLoaderImpl<K, V> implements GridDataLoader<K, V>, Externalizable {
    /** Log reference. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<GridLogger>();

    /** Cache name ({@code null} for default cache). */
    private String cacheName;

    /** Per-node buffer size. */
    private int bufSize = DFLT_PER_NODE_BUFFER_SIZE;

    /** Max concurrent put tasks count. */
    private int parallelOps = DFLT_MAX_PARALLEL_OPS;

    /** Lock. */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Mapping. */
    private ConcurrentMap<UUID, Buffer> bufMappings = new ConcurrentHashMap<UUID, Buffer>();

    /** Entries to remap. */
    private GridConcurrentLinkedDeque<GridTuple2<K, Object>> remapEntries =
        new GridConcurrentLinkedDeque<GridTuple2<K, Object>>();

    /** Buffers to remap. */
    private GridConcurrentLinkedDeque<Buffer> remapBufs = new GridConcurrentLinkedDeque<Buffer>();

    /** Guard to process remap in single thread only. */
    private final AtomicBoolean remapGuard = new AtomicBoolean();

    /** Logger. */
    private GridLogger log;

    /** Cache mode. */
    private GridCacheMode cacheMode;

    /** Discovery listener. */
    private GridLocalEventListener discoLsnr;

    /** Busy lock. */
    private final GridBusyLock busyLock = new GridBusyLock();

    /** Guard to process close in single thread only. */
    private final AtomicBoolean closeGuard = new AtomicBoolean();

    /** Future to track loading finish. */
    private GridFutureAdapter<?> fut;

    /** Context. */
    private GridKernalContext ctx;

    /** Ignore events flag. */
    private boolean ignoreEvts;

    /** {@code True} if configuration has been saved (intentionally non-volatile). */
    private boolean cSaved;

    /** Loader configuration. */
    private final AtomicReference<Configuration> cRef = new AtomicReference<Configuration>();

    /** IDs of cache nodes. */
    private Collection<UUID> cacheNodes = new HashSet<UUID>();

    /** Class to deploy on remote node. */
    private Class<?> depCls;

    /** Job peer deploy aware. */
    private GridPeerDeployAware jobPda;

    /** Set to {@code false} on deserialization whenever loader is serialized. */
    private boolean valid = true;

    /**
     * Empty constructor for {@link Externalizable} support.
     */
    public GridDataLoaderImpl() {
        // No-op.
    }

    /**
     * @param ctx Grid kernal context.
     * @param cacheName Cache name.
     */
    public GridDataLoaderImpl(final GridKernalContext ctx, @Nullable final String cacheName) {
        assert ctx != null;

        this.ctx = ctx;
        this.cacheName = cacheName;

        fut = new GridDataLoaderFuture(ctx, this);

        log = U.logger(ctx, logRef, GridDataLoaderImpl.class);

        cacheMode = U.cacheMode(ctx.discovery().localNode(), cacheName);

        discoLsnr = new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                assert evt.type() == EVT_NODE_FAILED || evt.type() == EVT_NODE_LEFT || evt.type() == EVT_NODE_JOINED;

                GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                lock.writeLock().lock();

                try {
                    if (ignoreEvts)
                        // Ignore event - loader cancelled.
                        return;

                    UUID id = discoEvt.eventNodeId();

                    if (evt.type() == EVT_NODE_JOINED) {
                        GridNode n = ctx.discovery().node(id);

                        if (n != null && U.hasCache(n, cacheName))
                            cacheNodes.add(id);
                    }
                    else {
                        cacheNodes.remove(id);

                        Buffer buf = bufMappings.remove(id);

                        if (buf != null)
                            remapBufs.add(buf);
                    }
                }
                finally {
                    lock.writeLock().unlock();
                }
            }
        };

        ctx.event().addLocalEventListener(discoLsnr, EVT_NODE_FAILED, EVT_NODE_LEFT, EVT_NODE_JOINED);

        // Init cache nodes.
        Collection<GridNode> nodes = ctx.discovery().allNodes();

        lock.writeLock().lock();

        try {
            for (GridNode n : nodes) {
                if (ctx.discovery().node(n.id()) != null) {
                    GridCacheMode mode = U.cacheMode(n, cacheName);

                    if (mode != null && mode != LOCAL) {
                        cacheNodes.add(n.id());

                        if (cacheMode == null)
                            cacheMode = mode;
                    }
                }
            }

            if (log.isDebugEnabled())
                log.debug("Initialized cache nodes: " + cacheNodes);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override public GridFuture<?> future() {
        return fut;
    }

    /** {@inheritDoc} */
    @Override public int perNodeBufferSize() {
        return bufSize;
    }

    /** {@inheritDoc} */
    @Override public void perNodeBufferSize(int bufSize) {
        checkValid();

        A.ensure(bufSize > 0, "bufSize > 0");

        if (cSaved || cRef.get() != null)
            throw new IllegalStateException("Cannot change active data loader configuration.");

        this.bufSize = bufSize;
    }

    /** {@inheritDoc} */
    @Override public int perNodeParallelLoadOperations() {
        return parallelOps;
    }

    /** {@inheritDoc} */
    @Override public void perNodeParallelLoadOperations(int parallelOps) {
        checkValid();

        A.ensure(parallelOps > 0, "parallelOps > 0");

        if (cSaved || cRef.get() != null)
            throw new IllegalStateException("Cannot change active data loader configuration.");

        this.parallelOps = parallelOps;
    }

    /** {@inheritDoc} */
    @Override public void deployClass(Class<?> depCls) {
        checkValid();

        A.notNull(depCls, "depCls");

        if (cSaved || cRef.get() != null)
            throw new IllegalStateException("Cannot change active data loader configuration.");

        this.depCls = depCls;
    }

    /** {@inheritDoc} */
    @Override @Nullable public String cacheName() {
        return cacheName;
    }

    /** {@inheritDoc} */
    @Override public void addData(final K key, final GridClosure<V, V> clo) throws GridException,
        GridInterruptedException, IllegalStateException {
        addDataObject(key, clo);
    }

    /** {@inheritDoc} */
    @Override public void addData(final K key, final Callable<V> c) throws GridException, GridInterruptedException,
        IllegalStateException {
        addDataObject(key, c);
    }

    /** {@inheritDoc} */
    @Override public void addData(final K key, final V val) throws GridException, GridInterruptedException,
        IllegalStateException {
        addDataObject(key, val);
    }

    /**
     * Internal method to handle all kind of data that may be passed via public methods -
     * {@link #addData(Object, GridClosure)}, {@link #addData(Object, Callable), {@link #addData(Object, Object)}}.
     *
     * @param key Key.
     * @param val Callable, closure or plain value.
     * @throws GridException If failed to map key to node.
     * @throws GridInterruptedException If thread has been interrupted.
     * @throws IllegalStateException if grid has been concurrently stopped or
     *      {@link #close(boolean)} has already been called on loader.
     */
    private void addDataObject(final K key, final Object val) throws GridException, GridInterruptedException,
        IllegalStateException {
        checkValid();

        A.notNull(key, "key");
        A.notNull(val, "val");

        ctx.gateway().readLock();

        try {
            if (!busyLock.enterBusy())
                throw new IllegalStateException("Failed to add data (data loader has been closed) " +
                    "[key" + key + ", val=" + val + ", ldr=" + this + ']');

            try {
                // Check if close() has been called from the same thread.
                if (busyLock.blockedByCurrentThread())
                    throw new IllegalStateException("Failed to add data (data loader has been closed) " +
                        "[key" + key + ", val=" + val + ", ldr=" + this + ']');

                // Save configuration snapshot.
                if (!cSaved) {
                    cRef.compareAndSet(null, new Configuration(bufSize, parallelOps,
                        new GridPeerDeployAware() {
                            private Class<?> cls;

                            private ClassLoader ldr;

                            @Override public Class<?> deployClass() {
                                if (cls == null) {
                                    Class<?> cls0 = null;

                                    if (depCls != null)
                                        cls0 = depCls;
                                    else {
                                        if (cls0 == null)
                                            cls0 = detectClass(key);

                                        if (cls0 == null)
                                            cls0 = detectClass(val);

                                        if (cls0 == null)
                                            cls0 = GridDataLoaderImpl.class;
                                    }

                                    assert cls0 != null : "Failed to detect deploy class [ldr=" +
                                        GridDataLoaderImpl.this + ", key=" + key + ", val=" + val + ']';

                                    cls = cls0;
                                }

                                return cls;
                            }

                            @Override public ClassLoader classLoader() {
                                if (ldr == null)
                                    ldr = deployClass().getClassLoader();

                                // Safety.
                                if (ldr == null)
                                    ldr = GridDataLoaderImpl.class.getClassLoader();

                                return ldr;
                            }
                        }));

                    cSaved = true;
                }

                // Remap.
                remap();

                addData0(key, val);
            }
            finally {
                busyLock.leaveBusy();
            }
        }
        finally {
            ctx.gateway().readUnlock();
        }
    }

    /**
     * @param obj Object.
     * @return First non-JDK or deployment aware class.
     */
    @Nullable private Class<?> detectClass(Object obj) {
        assert obj != null;

        if (obj instanceof GridPeerDeployAware)
            return ((GridPeerDeployAware) obj).deployClass();

        if (!U.isJdk(obj.getClass()))
            return obj.getClass();

        if (obj instanceof Iterable<?>) {
            for (Object o : (Iterable) obj) {
                if (o instanceof GridPeerDeployAware)
                    return ((GridPeerDeployAware) o).deployClass();

                if (!U.isJdk(o.getClass()))
                    return o.getClass();
            }

            // No point to continue.
            return null;
        }

        if (obj.getClass().isArray()) {
            int len = Array.getLength(obj);

            for (int i = 0; i < len; i++) {
                Object o = Array.get(obj, i);

                if (o instanceof GridPeerDeployAware)
                    return ((GridPeerDeployAware) o).deployClass();

                if (!U.isJdk(o.getClass()))
                    return o.getClass();
            }
        }

        return null;
    }

    /**
     * @param key Key.
     * @param val Value.
     * @throws GridInterruptedException If thread gets interrupted.
     */
    private void addData0(K key, Object val) throws GridException, GridInterruptedException {
        assert key != null;
        assert val != null;

        Buffer buf = null;

        while (true) {
            UUID nodeId;

            if (cacheMode == LOCAL)
                nodeId = ctx.localNodeId();
            else if (cacheMode == REPLICATED) {
                // Use random of available cache nodes.
                lock.readLock().lock();

                try {
                    nodeId = F.rand(cacheNodes);
                }
                finally {
                    lock.readLock().unlock();
                }

                if (nodeId == null)
                    throw new GridException("Failed to map key to node (no nodes with cache found in topology) " +
                        "[key=" + key + ", cacheName=" + cacheName + ']');
            }
            else {
                GridRichNode node = ctx.affinity().mapKeyToNode(cacheName, F.viewReadOnly(ctx.discovery().allNodes(),
                    ctx.rich().richNode()), key, true);

                if (node == null)
                    throw new GridException("Failed to map key to node (no nodes with cache found in topology) " +
                        "[key=" + key + ", cacheName=" + cacheName + ']');

                nodeId = node.id();

                if (cacheMode == null) {
                    GridCacheMode mode = U.cacheMode(node, cacheName);

                    if (mode == LOCAL)
                        throw new GridException("Failed to load entry to LOCAL cache which is configured " +
                            "on remote node (use data loader locally instead) " +
                            "[cacheName=" + cacheName + ", nodeId=" + nodeId + ']');

                    cacheMode = mode;
                }
            }

            lock.readLock().lock();

            try {
                if (ctx.discovery().node(nodeId) == null)
                    // Node has left - remap.
                    continue;

                buf = bufMappings.get(nodeId);

                if (buf == null) {
                    Buffer old = bufMappings.putIfAbsent(nodeId, buf = new Buffer(nodeId));

                    if (old != null)
                        buf = old;
                }
            }
            finally {
                lock.readLock().unlock();
            }

            // Add outside of synchronization.
            if (!buf.add(key, val))
                // Buffer has been remapped - repeat add.
                continue;

            break;
        }

        if (buf != null)
            // Try to submit buffer.
            buf.submit(false);
    }

    /**
     * Remaps currently unmapped entries (entry may become unmapped if topology changes).
     *
     * @throws GridException If failed to remap entries.
     */
    private void remap() throws GridException {
        Collection<GridTuple2<K, Object>> entries = null;
        Collection<Buffer> bufs = null;

        if (remapGuard.compareAndSet(false, true)) {
            lock.writeLock().lock();

            try {
                if (!remapEntries.isEmptyx()) {
                    entries = remapEntries;

                    remapEntries = new GridConcurrentLinkedDeque<GridTuple2<K, Object>>();
                }

                if (!remapBufs.isEmptyx()) {
                    bufs = remapBufs;

                    remapBufs = new GridConcurrentLinkedDeque<Buffer>();
                }
            }
            finally {
                lock.writeLock().unlock();

                remapGuard.set(false);
            }
        }
        else
            // Remap is being handled concurrently.
            return;

        if (entries != null)
            for (GridTuple2<K, Object> t : entries)
                addData0(t.getKey(), t.getValue());

        if (bufs != null)
            for (Buffer buf : bufs)
                for (GridTuple2<K, Object> t : buf.entriesToRemap())
                    addData0(t.getKey(), t.getValue());
    }

    /** {@inheritDoc} */
    @Override public void close(boolean cancel) throws IllegalStateException, GridException {
        checkValid();

        if (closeGuard.compareAndSet(false, true)) {
            // No more adds are possible.
            busyLock.block();

            if (!cSaved) {
                cRef.compareAndSet(null, new Configuration(bufSize, parallelOps,
                    new GridPeerDeployAware() {
                        private Class<?> cls;

                        private ClassLoader ldr;

                        @Override public Class<?> deployClass() {
                            if (cls == null)
                                cls = depCls != null ? depCls : GridDataLoaderImpl.class;

                            assert cls != null : "Failed to detect deploy class on close [ldr=" +
                                GridDataLoaderImpl.this + ']';

                            return cls;
                        }

                        @Override public ClassLoader classLoader() {
                            if (ldr == null)
                                ldr = deployClass().getClassLoader();

                            return ldr;
                        }
                    }));

                cSaved = true;
            }

            Throwable err = null;

            try {
                if (cancel)
                    cancel();
                else
                    close0();
            }
            catch (RuntimeException e) {
                err = e;

                throw e;
            }
            catch (Error e) {
                err = e;

                throw e;
            }
            catch (GridException e) {
                err = e;

                throw e;
            }
            finally {
                if (err != null)
                    // Cancel all active tasks.
                    cancel();

                fut.onDone(null, err);

                ctx.event().removeLocalEventListener(discoLsnr);

                // Ensure we will not get disco notifications any more.
                lock.writeLock().lock();

                try {
                    ignoreEvts = true;
                }
                finally {
                    lock.writeLock().unlock();
                }

                // Clean up.
                remapEntries = null;
                remapBufs = null;
                bufMappings = null;
                cacheNodes = null;
            }
        }
        else
            fut.get();
    }

    /**
     * @throws GridException If failed.
     */
    @SuppressWarnings("TooBroadScope")
    private void close0() throws GridException {
        Queue<Buffer> bufs = new LinkedList<Buffer>();

        if (log.isDebugEnabled())
            log.debug("Mappings on close: " + bufMappings);

        Configuration c = cRef.get();

        assert c != null : "Configuration has not been saved.";

        int parallelOps0 = c != null ? c.parallelOps() : parallelOps;

        boolean cancel = true;

        try {
            // Process the rest of mappings.
            while (true) {
                // Remap first.
                remap();

                Buffer buf = null;

                // Remove buffers one by one inside lock.
                lock.readLock().lock();

                try {
                    UUID nodeId = F.firstKey(bufMappings);

                    if (nodeId != null) {
                        buf = bufMappings.remove(nodeId);

                        assert buf != null;
                    }
                }
                finally {
                    lock.readLock().unlock();
                }

                if (buf != null) {
                    // Force job submission.
                    buf.submit(true);

                    bufs.add(buf);
                }
                else if (bufs.size() > ctx.discovery().allNodes().size() * parallelOps0) {
                    // Safety, need to wait to avoid heap starvation.
                    for (Buffer b = bufs.poll(); b != null; b = bufs.poll())
                        b.waitAllTaskFinished();
                }
                else {
                    for (Buffer b = bufs.poll(); b != null; b = bufs.poll())
                        b.waitAllTaskFinished();

                    // Acquire exclusive lock to make sure that there is nothing
                    // else left to put to cache.
                    lock.writeLock().lock();

                    try {
                        if (remapBufs.isEmptyx() && remapEntries.isEmptyx() && bufMappings.isEmpty()) {
                            cancel = false;

                            break; // Main loop.
                        }
                    }
                    finally {
                        lock.writeLock().unlock();
                    }
                }
            }
        }
        finally {
            if (cancel)
                for (Buffer b = bufs.poll(); b != null; b = bufs.poll())
                    b.cancelAll();
        }
    }

    /**
     *
     */
    private void cancel() {
        if (log.isDebugEnabled())
            log.debug("Cancelling data loader: " + this);

        // Do not process events any more.
        ctx.event().removeLocalEventListener(discoLsnr);

        // Ensure we will not get disco notifications any more.
        lock.writeLock().lock();

        try {
            ignoreEvts = true;
        }
        finally {
            lock.writeLock().unlock();
        }

        while (true) {
            UUID nodeId = F.firstKey(bufMappings);

            if (nodeId == null)
                break;

            Buffer buf = bufMappings.remove(nodeId);

            assert buf != null;

            buf.cancelAll();
        }
    }

    /**
     * Checks that loader is in usable state.
     */
    protected void checkValid() {
        if (!valid)
            throw new IllegalStateException("Data loader cannot be used after deserialization.");
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(fut);
        out.writeInt(bufSize);
        out.writeInt(parallelOps);
        U.writeString(out, cacheName);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        valid = false;

        fut = (GridFutureAdapter<?>)in.readObject();
        bufSize = in.readInt();
        parallelOps = in.readInt();
        cacheName = U.readString(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDataLoaderImpl.class, this);
    }

    /**
     * Job to put entries to cache on affinity node.
     */
    private static class PutJob<K, V> implements Callable<Object>, GridPeerDeployAware, Externalizable {
        /** Grid. */
        @GridInstanceResource
        private Grid g;

        /** Logger. */
        @GridLoggerResource
        private GridLogger log;

        /** Cache name. */
        private String cacheName;

        /** Entries to put. */
        private Collection<GridTuple2<K, Object>> col;

        /** Peer deploy aware. */
        private GridPeerDeployAware pda;

        /**
         * {@link Externalizable} support.
         */
        public PutJob() {
            // No-op.
        }

        /**
         * @param cacheName Cache name.
         * @param col Entries to put.
         * @param pda Peer deploy aware.
         */
        private PutJob(@Nullable String cacheName, Collection<GridTuple2<K, Object>> col, GridPeerDeployAware pda) {
            assert col != null && !col.isEmpty();
            assert pda != null;

            this.cacheName = cacheName;
            this.col = col;
            this.pda = pda;
        }

        /** {@inheritDoc} */
        @Override public Object call() throws Exception {
            if (log.isDebugEnabled())
                log.debug("Running put job [nodeId: " + g.localNode().id() + ", size=" + col.size() + ']');

            try {
                GridCache<K, V> cache = g.cache(cacheName);

                assert cache != null;

                Map<K, V> objMap = null;
                Map<K, List<GridClosure<V, V>>> cloMap = null;

                for (GridTuple2<K, Object> t : col) {
                    K key = t.getKey();

                    Object obj = t.get2();

                    boolean unwrapped = false;

                    if (obj instanceof Callable) {
                        unwrapped = true;

                        obj = ((Callable)obj).call();
                    }

                    if (key instanceof Comparable) {
                        if (!unwrapped && obj instanceof GridClosure) {
                            if (cloMap == null)
                                cloMap = new TreeMap<K, List<GridClosure<V, V>>>();

                            List<GridClosure<V, V>> clos = cloMap.isEmpty() ? null : cloMap.get(key);

                            if (clos == null)
                                cloMap.put(key, clos = new LinkedList<GridClosure<V, V>>());

                            clos.add((GridClosure<V, V>)obj);
                        }
                        else {
                            if (objMap == null)
                                objMap = new TreeMap<K, V>();

                            objMap.put(key, (V)obj);
                        }
                    }
                    else {
                        // Key is not comparable, need to put in a separate tx.
                        if (!unwrapped && obj instanceof GridClosure) {
                            GridCacheTx tx = cache.txStart(PESSIMISTIC, REPEATABLE_READ);

                            try {
                                V val = cache.get(key);

                                cache.putx(key, ((GridClosure<V, V>)obj).apply(val));

                                tx.commit();
                            }
                            finally {
                                tx.end();
                            }
                        }
                        else
                            cache.putx(key, (V)obj);
                    }
                }

                if (objMap != null)
                    cache.putAll(objMap);

                if (cloMap != null) {
                    GridCacheTx tx = cache.txStart(PESSIMISTIC, REPEATABLE_READ);

                    try {
                        final Map<K, V> oldVals = cache.getAll(cloMap.keySet());

                        cache.putAll(F.viewReadOnly(cloMap, new C2<K, List<GridClosure<V, V>>, V>() {
                            @Override public V apply(K key, List<GridClosure<V, V>> l) {
                                V val = oldVals.get(key);

                                for (GridClosure<V, V> c: l)
                                    val = c.apply(val);

                                return val;
                            }
                        }));

                        tx.commit();
                    }
                    finally {
                        tx.end();
                    }
                }

                return null;
            }
            finally {
                if (log.isDebugEnabled())
                    log.debug("Put job finished on node: " + g.localNode().id());
            }
        }

        /** {@inheritDoc} */
        @Override public Class<?> deployClass() {
            return pda.deployClass();
        }

        /** {@inheritDoc} */
        @Override public ClassLoader classLoader() {
            return pda.classLoader();
        }

        /** {@inheritDoc} */
        @Override public void writeExternal(ObjectOutput out) throws IOException {
            U.writeString(out, cacheName);
            U.writeCollection(out, col);
        }

        /** {@inheritDoc} */
        @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            cacheName = U.readString(in);
            col = U.readCollection(in);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "PutJob [cacheName=" + cacheName + ", mapSize=" + col.size() + ']';
        }
    }

    /**
     *
     */
    private class Buffer {
        /** Node ID. */
        private final UUID nodeId;

        /** Active futures. */
        private final Collection<GridFuture<?>> activeFuts;

        /** Buffered entries. */
        private final GridConcurrentLinkedDeque<GridTuple2<K, Object>> entries =
            new GridConcurrentLinkedDeque<GridTuple2<K, Object>>();

        /** Remapped flag. */
        private boolean remapped;

        /** All futures cancelled flag. */
        private volatile boolean allCancelled;

        /** Submit guard. */
        @GridToStringExclude
        private final AtomicBoolean submitGuard = new AtomicBoolean();

        /** Semaphore. */
        @GridToStringExclude
        private final Semaphore sem;

        /** Buffer internal lock. */
        private final ReadWriteLock lock0 = new ReentrantReadWriteLock();

        /** Buffer size (from snapshot). */
        private final int bufSize0;

        /** Local node flag. */
        private final boolean isLocNode;

        /**
         * @param nodeId Node ID.
         */
        private Buffer(UUID nodeId) {
            assert nodeId != null;

            this.nodeId = nodeId;

            Configuration c = cRef.get();

            assert c != null : "Configuration has not been saved.";

            bufSize0 = c.get1();

            int parallelOps0 = c.get2();

            sem = new Semaphore(parallelOps0);

            // 1 segment will be enough.
            activeFuts = new GridConcurrentHashSet<GridFuture<?>>(parallelOps0, 0.75f, 1);

            // Cache local node flag.
            isLocNode = nodeId.equals(ctx.localNodeId());
        }

        /**
         * @param key Key.
         * @param val Value.
         * @return {@code True} if data was added.
         */
        boolean add(K key, Object val) {
            lock0.readLock().lock();

            try {
                if (remapped)
                    return false;

                entries.add(F.t(key, val));

                if (log.isDebugEnabled())
                    log.debug("Added data [buf=" + this + ", key=" + key + ", val=" + val + ']');

                return true;
            }
            finally {
                lock0.readLock().unlock();
            }
        }

        /**
         * @param force Force jobs submission.
         * @throws GridInterruptedException If interrupted.
         */
        void submit(boolean force) throws GridInterruptedException {
            if (force) {
                assert !submitGuard.get();

                if (entries.isEmptyx())
                    // No concurrent adds are possible when submission is forced.
                    return;
            }

            Collection<GridTuple2<K, Object>> col = null;

            GridNode node = null;

            if (force || (entries.sizex() >= bufSize0 && submitGuard.compareAndSet(false, true))) {
                lock0.writeLock().lock();

                try {
                    if (remapped)
                        return;

                    col = new ArrayList<GridTuple2<K, Object>>(entries.sizex());

                    for (GridTuple2<K, Object> t = entries.poll(); t != null; t = entries.poll())
                        col.add(t);

                    // If forced or remapped concurrently.
                    if (col.isEmpty())
                        return;

                    incrementActiveTasks();

                    if (!isLocNode) {
                        node = ctx.discovery().node(nodeId);

                        if (node == null) {
                            // Node has left, will remap.
                            signalTaskFinished(null);

                            // This buffer cannot be used any more.
                            remapped = true;
                        }
                    }
                }
                finally {
                    lock0.writeLock().unlock();

                    submitGuard.set(false);
                }
            }
            else
                // Submit is being handled concurrently.
                return;

            if (jobPda == null) {
                Configuration c = cRef.get();

                assert c != null;

                jobPda = c.pda();
            }

            GridFuture<Object> fut = null;

            if (isLocNode)
                fut = ctx.closure().callLocalSafe(new PutJob<K, Object>(cacheName, col, jobPda), true);

            else if (node != null)
                fut = ctx.closure().callAsyncNoFailover(UNICAST, new PutJob<K, Object>(cacheName, col, jobPda),
                    Arrays.asList(node), true);

            if (fut != null) {
                final Collection<GridTuple2<K, Object>> col0 = col;

                C2<Object, Exception, Object> lsnr = new C2<Object, Exception, Object>() {
                    @Override public Object apply(Object o, Exception e) {
                        boolean err = true;

                        if (e != null) {
                            if (e instanceof GridEmptyProjectionException) {
                                if (log.isDebugEnabled())
                                    log.debug("Failed to send put job to node (node has left): " + nodeId);
                            }
                            else if (e instanceof GridFutureCancelledException) {
                                if (log.isDebugEnabled())
                                    log.debug("Future has been cancelled.");

                                // Do not remap.
                                err = false;
                            }
                            else {
                                if (X.hasCause(e, ClassNotFoundException.class))
                                    U.error(log, "Put job has finished due to class-loading error (will retry, " +
                                        "most probably you should manually configure 'deployClass' for data loader).", e);
                                else if (X.hasCause(e, GridInterruptedException.class, InterruptedException.class))
                                    U.warn(log, "Put job was cancelled due to node stop (will retry).");
                                else
                                    U.error(log, "Put job has finished with error (will retry).", e);
                            }
                        }
                        else
                            err = false;

                        if (err)
                            scheduleRemap(col0);

                        return o;
                    }
                };

                GridFuture<Object> f = new GridEmbeddedFuture<Object, Object>(ctx, fut, lsnr);

                activeFuts.add(f);

                f.listenAsync(new CIX1<GridFuture<Object>>() {
                    @Override public void applyx(GridFuture<Object> fin) {
                        signalTaskFinished(fin);
                    }
                });

                // Safety.
                if (allCancelled) {
                    try {
                        f.cancel();
                    }
                    catch (GridException e) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to cancel task future: " + e);
                    }
                }
                else if (log.isDebugEnabled())
                    log.debug("Submitted buffer [buf=" + this + ", force=" + force +
                        ", size=" + col.size() + ']');
            }
            else {
                // Task has not been submitted since node has left.
                if (col != null && !col.isEmpty()) {
                    if (log.isDebugEnabled())
                        log.debug("Node has left (will remap): " + nodeId);

                    // Remap polled entries outside of synchronization.
                    scheduleRemap(col);
                }
            }
        }

        /**
         * @param col Entries to remap.
         */
        private void scheduleRemap(Collection<GridTuple2<K, Object>> col) {
            // Add inside main read lock.
            lock.readLock().lock();

            try {
                remapEntries.addAll(col);
            }
            finally {
                lock.readLock().unlock();
            }
        }

        /**
         * @return All entries currently contained in buffer.
         */
        Collection<GridTuple2<K, Object>> entriesToRemap() {
            lock0.writeLock().lock();

            try {
                if (remapped)
                    return Collections.emptyList();

                remapped = true;

                if (entries.isEmptyx())
                    return Collections.emptyList();

                Collection<GridTuple2<K, Object>> col = new ArrayList<GridTuple2<K, Object>>(entries.sizex());

                for (GridTuple2<K, Object> t = entries.poll(); t != null; t = entries.poll())
                    col.add(t);

                return col;
            }
            finally {
                lock0.writeLock().unlock();
            }
        }

        /**
         * Increments active tasks count.
         *
         * @throws GridInterruptedException If thread has been interrupted.
         */
        private void incrementActiveTasks() throws GridInterruptedException {
            try {
                sem.acquire();
            }
            catch (InterruptedException e) {
                throw new GridInterruptedException("Thread has been interrupted.", e);
            }
        }

        /**
         * @param f Future that finished.
         */
        private void signalTaskFinished(@Nullable GridFuture<?> f) {
            // Release
            sem.release();

            if (f != null) {
                boolean b = activeFuts.remove(f);

                assert b : "Future has not been added: " + f;
            }
        }

        /**
         * Waits until there are no ongoing put tasks.
         *
         * @throws GridInterruptedException If thread has been interrupted.
         */
        void waitAllTaskFinished() throws GridInterruptedException {
            for (GridFuture<?> f : activeFuts) {
                try {
                    f.get();
                }
                catch (GridInterruptedException e) {
                    throw e;
                }
                catch (GridException e) {
                    if (log.isDebugEnabled())
                        log.debug("Failed to get future result: " + e);
                }
            }
        }

        /**
         *
         */
        void cancelAll() {
            if (!allCancelled) {
                allCancelled = true;

                for (GridFuture<?> f : activeFuts) {
                    try {
                        f.cancel();
                    }
                    catch (GridException e) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to cancel task future: " + e);
                    }
                }
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(Buffer.class, this, "entriesCnt", entries.sizex());
        }
    }

    /**
     *
     */
    private static class Configuration extends GridTuple3<Integer, Integer, GridPeerDeployAware> {
        /**
         * @param bufSize Buffer size.
         * @param parallelOps Parallel operations.
         * @param pda Peer deploy aware.
         */
        private Configuration(int bufSize, int parallelOps, GridPeerDeployAware pda) {
            super(bufSize, parallelOps, pda);
        }

        /**
         * {@link Externalizable} support.
         */
        public Configuration() {
            // No-op.
        }

        /**
         * @return Buffer size.
         */
        int bufferSize() {
            return get1();
        }

        /**
         * @return Parallel operations.
         */
        int parallelOps() {
            return get2();
        }

        /**
         * @return Deploy class.
         */
        GridPeerDeployAware pda() {
            return get3();
        }
    }
}
