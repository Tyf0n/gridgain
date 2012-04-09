// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.datastructures;

import org.gridgain.grid.cache.affinity.GridCacheAffinityMapped;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;

import java.io.*;

/**
 * Key is used for caching cache data structures.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.1c.09042012
 */
public class GridCacheInternalKeyImpl implements GridCacheInternalKey, Externalizable, Cloneable {
    /** Name of cache data structure. */
    private String name;

    /**
     * Default constructor.
     *
     * @param name - Name of cache data structure.
     */
    public GridCacheInternalKeyImpl(String name) {
        assert !F.isEmpty(name);

        this.name = name;
    }

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridCacheInternalKeyImpl() {
        // No-op.
    }

    /** {@inheritDoc} */
    @GridCacheAffinityMapped
    @Override public String name() {
        return name;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return name.hashCode();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        return this == obj || (obj instanceof GridCacheInternalKey && name.equals(((GridCacheInternalKey)obj).name()));
    }

    /** {@inheritDoc} */
    @Override public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, name);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException {
        name = U.readString(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCacheInternalKeyImpl.class, this);
    }
}
