// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache;

import org.gridgain.grid.*;

/**
 * Exception thrown whenever grid transactions time out.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.5.1c.22112011
 */
public class GridCacheTxTimeoutException extends GridException {
    /**
     * Creates new timeout exception with given error message.
     *
     * @param msg Error message.
     */
    public GridCacheTxTimeoutException(String msg) {
        super(msg);
    }

    /**
     * Creates new timeout exception with given error message and optional nested exception.
     *
     * @param msg Error message.
     * @param cause Optional nested exception (can be <tt>null</tt>).
     */
    public GridCacheTxTimeoutException(String msg, Throwable cause) {
        super(msg, cause);
    }
}