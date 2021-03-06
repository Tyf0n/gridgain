// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

namespace GridGain.Client {
    using System;
    using System.Collections.Generic;

    /** <summary>Grid client API.</summary> */
    public interface IGridClient {
        /**
         * <summary>
         * Gets a unique client identifier. This identifier is generated by factory on client creation
         * and used in identification and authentication procedure on server node.</summary>
         */
        Guid Id {
            get;
        }

        /**
         * <summary>
         * Gets a data projection for a default grid cache with <c>null</c> name.</summary>
         *
         * <returns>Data projection for grid cache with <c>null</c> name.</returns>
         * <exception cref="GridClientException">If client was closed.</exception>
         */
        IGridClientData Data();

        /**
         * <summary>
         * Gets a data projection for grid cache with name <tt>cacheName</tt>. If
         * no data configuration with given name was provided at client startup, an
         * exception will be thrown.</summary>
         *
         * <param name="cacheName">Grid cache name for which data projection should be obtained.</param>
         * <returns>Data projection for grid cache with name <tt>cacheName</tt>.</returns>
         * <exception cref="GridClientException">If client was closed or no configuration with given name was provided.</exception>
         */
        IGridClientData Data(String cacheName);

        /**
         * <summary>
         * Gets a default compute projection. Default compute projection will include all nodes
         * in remote grid. Selection of node that will be connected to perform operations will be
         * done according to <see ctype="GridClientLoadBalancer"/> provided in client configuration or
         * according to affinity if projection call involves affinity key.
         * <para/>
         * More restricted projection configurations may be created with <see ctype="GridClientCompute"/> methods.</summary>
         *
         * <returns>Default compute projection.</returns>
         *
         * @see GridClientCompute
         */
        IGridClientCompute Compute();

        /**
         * <summary>
         * Adds topology listener. Remote grid topology is refreshed every
         * <see ctype="GridClientConfiguration#getTopologyRefreshFrequency()"/> milliseconds. If any node was added or removed,
         * a listener will be notified.</summary>
         *
         * <param name="lsnr">Listener to add.</param>
         */
        void AddTopologyListener(IGridClientTopologyListener lsnr);

        /**
         * <summary>
         * Removes previously added topology listener.</summary>
         *
         * <param name="lsnr">Listener to remove.</param>
         */
        void RemoveTopologyListener(IGridClientTopologyListener lsnr);

        /**
         * <summary>
         * Gets an unmodifiable snapshot of topology listeners list.</summary>
         *
         * <returns>List of topology listeners.</returns>
         */
        ICollection<IGridClientTopologyListener> TopologyListeners();
    }
}
