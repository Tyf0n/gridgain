// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.distributed;

import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Transaction node mapping.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
public class GridDistributedTxMapping<K, V> implements Externalizable {
    /** Mapped node. */
    @GridToStringExclude
    private GridRichNode node;

    /** Entries. */
    @GridToStringInclude
    private Collection<GridCacheTxEntry<K, V>> entries;

    /** Explicit lock flag. */
    private boolean explicitLock;

    /** DHT version. */
    private GridCacheVersion dhtVer;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDistributedTxMapping() {
        // No-op.
    }

    /**
     * @param node Mapped node.
     */
    public GridDistributedTxMapping(GridRichNode node) {
        this.node = node;

        entries = new ConcurrentLinkedQueue<GridCacheTxEntry<K, V>>();
    }

    /**
     * @return Node.
     */
    public GridRichNode node() {
        return node;
    }

    /**
     * @return Entries.
     */
    public Collection<GridCacheTxEntry<K, V>> entries() {
        return entries;
    }

    /**
     * @return {@code True} if lock is explicit.
     */
    public boolean explicitLock() {
        return explicitLock;
    }

    /**
     * Sets explicit flag to {@code true}.
     */
    public void markExplicitLock() {
        explicitLock = true;
    }

    /**
     * @return DHT version.
     */
    public GridCacheVersion dhtVersion() {
        return dhtVer;
    }

    /**
     * @param dhtVer DHT version.
     */
    public void dhtVersion(GridCacheVersion dhtVer) {
        this.dhtVer = dhtVer;

        for (GridCacheTxEntry<K, V> e : entries)
            e.dhtVersion(dhtVer);
    }

    /**
     * @return Reads.
     */
    public Collection<GridCacheTxEntry<K, V>> reads() {
        return F.view(entries, CU.<K, V>reads());
    }

    /**
     * @return Writes.
     */
    public Collection<GridCacheTxEntry<K, V>> writes() {
        return F.view(entries, CU.<K, V>writes());
    }

    /**
     * @param entry Adds entry.
     */
    public void add(GridCacheTxEntry<K, V> entry) {
        entries.add(entry);
    }

    /**
     * @param entry Entry to remove.
     * @return {@code True} if entry was removed.
     */
    public boolean removeEntry(GridCacheTxEntry<K, V> entry) {
        return entries.remove(entry);
    }

    /**
     * @param parts Evicts partitions from mapping.
     */
    public void evictPartitions(@Nullable int[] parts) {
        if (!F.isEmpty(parts))
            evictPartitions(parts, entries);
    }

    /**
     * @param parts Partitions.
     * @param c Collection.
     */
    private void evictPartitions(int[] parts, Collection<GridCacheTxEntry<K, V>> c) {
        assert parts != null;

        for (Iterator<GridCacheTxEntry<K, V>> it = c.iterator(); it.hasNext();) {
            GridCacheTxEntry<K, V> e = it.next();

            GridCacheEntryEx<K,V> cached = e.cached();

            if (U.containsIntArray(parts, cached.partition()))
                it.remove();
        }
    }

    /**
     * @param keys Keys to evict readers for.
     */
    public void evictReaders(@Nullable Collection<K> keys) {
        if (keys == null || keys.isEmpty())
            return;

        evictReaders(keys, entries);
    }

    /**
     * @param keys Keys to evict readers for.
     * @param entries Entries to check.
     */
    private void evictReaders(Collection<K> keys, @Nullable Collection<GridCacheTxEntry<K, V>> entries) {
        if (entries == null || entries.isEmpty())
            return;

        for (Iterator<GridCacheTxEntry<K, V>> it = entries.iterator(); it.hasNext();) {
            GridCacheTxEntry<K, V> entry = it.next();

            if (keys.contains(entry.key()))
                it.remove();
        }
    }

    /** {@inheritDoc} */
    public boolean empty() {
        return entries.isEmpty();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(node);

        U.writeCollection(out, entries);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        node = (GridRichNode)in.readObject();

        entries = U.readCollection(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDistributedTxMapping.class, this, "node", node.id());
    }
}
