// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.eventstorage;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.*;
import java.util.*;

/**
 * This SPI provides local node events storage. SPI allows for recording local
 * node events and querying recorded local events. Every node during its life-cycle
 * goes through a serious of events such as task deployment, task execution, job
 * execution, etc. For
 * performance reasons GridGain is designed to store all locally produced events
 * locally. These events can be later retrieved using either distributed query:
 * <ul>
 *      <li>{@link Grid#remoteEvents(GridPredicate, long, org.gridgain.grid.lang.GridPredicate[])}</li>
 *      <li>{@link Grid#remoteEventsAsync(org.gridgain.grid.lang.GridPredicate , long, GridPredicate[])}</li>
 * </ul>
 * or local only query:
 * <ul>
 *      <li>{@link Grid#localEvents(org.gridgain.grid.lang.GridPredicate[])}</li>
 * </ul>
 * <b>NOTE:</b> this SPI (i.e. methods in this interface) should never be used directly. SPIs provide
 * internal view on the subsystem and is used internally by GridGain kernal. In rare use cases when
 * access to a specific implementation of this SPI is required - an instance of this SPI can be obtained
 * via {@link Grid#configuration()} method to check its configuration properties or call other non-SPI
 * methods. Note again that calling methods from this interface on the obtained instance can lead
 * to undefined behavior and explicitly not supported.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 * @see GridEvent
 */
public interface GridEventStorageSpi extends GridSpi, GridSpiJsonConfigurable {
    /**
     * Queries locally-stored events only. Events could be filtered out
     * by given predicate filters.
     *
     * @param p Event predicate filters. If no filters are provided - all local events
     *      will be returned.      
     * @return Collection of events.
     */
    public Collection<GridEvent> localEvents(GridPredicate<? super GridEvent>... p);

    /**
     * Records single event.
     *
     * @param evt Event that should be recorded.
     * @throws GridSpiException If event recording failed for any reason.
     */
    public void record(GridEvent evt) throws GridSpiException;
}
