// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.test.junit4;

import org.junit.runner.*;
import java.io.*;
import java.util.*;

/**
 * JUnit 4 test abstract runner.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
abstract class GridJunit4Runner extends Runner implements Serializable {
    /**
     * @return Class being tested.
     */
    abstract Class<?> getTestClass();

    /**
     * @param runner JUnit4 runner.
     */
    abstract void copyResults(GridJunit4Runner runner);

    /**
     * @param res JUnit4 result.
     * @return {@code true} if result has been set.
     */
    abstract boolean setResult(GridJunit4Result res);

    /**
    * @param res All JUnit4 results.
    * @return {@code true} if results has been set.
    */
   abstract boolean setResult(List<GridJunit4Result> res);
}
