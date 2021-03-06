// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover.examples

import org.gridgain.grid.*
import static org.gridgain.grid.GridClosureCallMode.*
import org.gridgain.grid.cache.*
import org.gridgain.grid.cache.affinity.*
import org.gridgain.grid.lang.*
import org.gridgain.grid.typedef.*
import static org.gridgain.grover.Grover.*
import org.gridgain.grover.categories.*
import org.jetbrains.annotations.*

/**
 * Example of how to collocate computations and data in GridGain using
 * {@link GridCacheAffinityMapped} annotation as opposed to direct API calls. This
 * example will first populate cache on some node where cache is available, and then
 * will send jobs to the nodes where keys reside and print out values for those
 * keys.
 * <p>
 * Remote nodes should always be started with configuration file which includes
 * cache: {@code 'ggstart.sh examples/config/spring-cache.xml'}. Local node can
 * be started with or without cache.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
@Typed
@Use(GroverProjectionCategory)
class GroverCacheAffinityExample1 {
    /** Configuration file name. */
    //private static final String CONFIG = "examples/config/spring-cache-none.xml" // No cache - remote node with cache is required.
    private static final String CONFIG = "examples/config/spring-cache.xml" // Cache.

    /** Name of cache specified in spring configuration. */
    private static final String NAME = "partitioned"

    /** Ensure singleton. */
    private GridCacheAffinityExample1() {
        // No-op.
    }

    /**
     * Executes cache affinity example.
     * <p>
     * Note that in case of {@code LOCAL} configuration,
     * since there is no distribution, values may come back as {@code nulls}.
     *
     * @param args Command line arguments
     * @throws Exception If failed.
     */
    static void main(String[] args) throws Exception {
        grover(CONFIG) { Grid g ->
            def keys = 'A' .. 'Z'

            // Populate cache.
            populateCache(g, keys)

            // Result map (ordered by key for readability).
            def results = new TreeMap<String, String>()

            // Bring computations to the nodes where the data resides (i.e. collocation).
            for (final String key : keys) {
                String result = g.call(
                    BALANCE,
                    new GridCallable<String>() {
                        // This annotation allows to route job to the node
                        // where the key is cached.
                        @GridCacheAffinityMapped
                        public String affinityKey() {
                            return key
                        }

                        // Specify name of cache to use for affinity.
                        @GridCacheName
                        public String cacheName() {
                            return NAME
                        }

                        @Nullable @Override public String call() {
                            println(">>> Executing affinity job for key: " + key)

                            // Get cache with name 'partitioned'.
                            GridCache<String, String> cache = g.cache(NAME)

                            // If cache is not defined at this point then it means that
                            // job was not routed by affinity.
                            if (cache == null) {
                                println(">>> Cache not found [nodeId=" + g.localNode().id() +
                                    ", cacheName=" + NAME + ']')

                                return "Error"
                            }

                            // Check cache without loading the value.
                            return cache.peek(key)
                        }
                    }
                )

                results.put(key, result)
            }

            // Print out results.
            for (Map.Entry<String, String> e : results.entrySet())
                println(">>> Affinity job result for key '" + e.key + "': " + e.value)
        }
    }

    /**
     * Populates cache with given keys. This method accounts for the case when
     * cache is not started on local node. In that case a job which populates
     * the cache will be sent to the node where cache is started.
     *
     * @param g Grid.
     * @param keys Keys to populate.
     * @throws GridException If failed.
     */
    private static void populateCache(final Grid g, Collection<String> keys) throws GridException {
        GridProjection prj = g.projectionForPredicate(F.cacheNodesForNames(NAME))

        // Give preference to local node.
        if (prj.nodes().contains(g.localNode()))
            prj = g.localNode()

        // Populate cache on some node (possibly this node) which has cache with given name started.
        // Note that CIX1 is a short type alias for GridInClosureX class. If you
        // find it too cryptic, you can use GridInClosureX class directly.
        prj.run$(
            UNICAST,
            { Collection<String> ks ->
                println(">>> Storing keys in cache: " + ks)

                GridCache<String, String> c = g.cache(NAME)

                for (String k : ks)
                    c.put(k, k.toLowerCase())
            },
            keys
        )
    }
}
