// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.cache.datastructures;

import org.gridgain.grid.cache.datastructures.*;
import org.gridgain.grid.editions.*;
import org.gridgain.grid.kernal.processors.cache.*;

/**
 * Sequence managed by cache ({@code 'Ex'} stands for external.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.0c.25032012
 */
public interface GridCacheAtomicSequenceEx extends GridCacheRemovable, GridCacheAtomicSequence {
    /**
     * Get current sequence key.
     *
     * @return Sequence key.
     */
    public GridCacheInternalStorableKey key();
}
