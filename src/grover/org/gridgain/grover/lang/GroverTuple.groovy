// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover.lang

/**
 * Convenient tuple.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.5.1c.28112011
 */
@Typed
class GroverTuple<V1, V2> {
    /** First value. */
    private final V1 val1

    /** Second value. */
    private final V2 val2

    /** */
    GroverTuple(V1 val1, V2 val2) {
        this.val1 = val1
        this.val2 = val2
    }

    /**
     * Gets first value.
     *
     * @return Value.
     */
    V1 get_1() {
        val1
    }

    /**
     * Gets second value.
     *
     * @return Value.
     */
    V2 get_2() {
        val2
    }
}
