// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.datastructures;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.editions.*;

/**
 * Queue managed by cache ({@code 'Ex'} stands for external).
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.1c.09042012
 */
public interface GridCacheQueueEx<T> extends GridCacheQueue<T>, GridCacheRemovable {
    /**
     * Get current queue key.
     *
     * @return Queue key.
     */
    public GridCacheInternalKey key();

    /**
     * Callback for queue notification about header changing.
     *
     * @param hdr Queue header, received from {@link GridCacheDataStructuresManager}.
     */
    public void onHeaderChanged(GridCacheQueueHeader hdr);

    /**
     * Remove all queue items and queue header from cache.
     *
     * @param batchSize Batch size.
     * @return Callable for queue clearing .
     * @throws GridException If queue already removed or operation failed.
     */
    public boolean removeQueue(int batchSize) throws GridException;
}
