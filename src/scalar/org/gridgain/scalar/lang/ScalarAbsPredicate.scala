// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________               ______                    ______   _______
 * __  ___/_____________ ____  /______ _________    __/__ \  __  __ \
 * _____ \ _  ___/_  __ `/__  / _  __ `/__  ___/    ____/ /  _  / / /
 * ____/ / / /__  / /_/ / _  /  / /_/ / _  /        _  __/___/ /_/ /
 * /____/  \___/  \__,_/  /_/   \__,_/  /_/         /____/_(_)____/
 *
 */
 
package org.gridgain.scalar.lang

import org.gridgain.grid.util.{GridUtils => U}
import org.gridgain.grid.lang.GridAbsPredicate

/**
 * Peer deploy aware adapter for Java's `GridAbsPredicate`.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.5.1c.22112011
 */
class ScalarAbsPredicate(private val f: () => Boolean) extends GridAbsPredicate {
    assert(f != null)

    peerDeployLike(U.peerDeployAware(f))

    /**
     * Delegates to passed in function.
     */
    def apply(): Boolean = {
        f()
    }
}