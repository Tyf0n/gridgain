// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.deployment;

import org.gridgain.grid.*;

/**
 * TODO: add file description.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
public interface GridDeploymentResource {
    /**
     * Gets resource name, either class name or alias name, such as alias
     * specified by {@link GridTaskName} annotation.
     *
     * @return Resource name.
     */
    public String getName();

    /**
     * Gets resource class.
     *
     * @return Resource class.
     */
    public Class<?> getResourceClass();

    /**
     * Gets resource class loader.
     *
     * @return Resource class loader.
     */
    public ClassLoader getClassLoader();
}
