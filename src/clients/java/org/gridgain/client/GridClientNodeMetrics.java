// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.client;

/**
 * Node metrics.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
public interface GridClientNodeMetrics {

    /**
     * Gets last update time.
     *
     * @return Last update time.
     */
    long getLastUpdateTime();

    /**
     * Gets max active jobs.
     *
     * @return Max active jobs.
     */
    int getMaximumActiveJobs();

    /**
     * Gets current active jobs.
     *
     * @return Current active jobs.
     */
    int getCurrentActiveJobs();

    /**
     * Gets average active jobs.
     *
     * @return Average active jobs.
     */
    float getAverageActiveJobs();

    /**
     * Gets maximum waiting jobs.
     *
     * @return Maximum active jobs.
     */
    int getMaximumWaitingJobs();

    /**
     * Gets current waiting jobs.
     *
     * @return Current waiting jobs.
     */
    int getCurrentWaitingJobs();

    /**
     * Gets average waiting jobs.
     *
     * @return Average waiting jobs.
     */
    float getAverageWaitingJobs();

    /**
     * @return Maximum number of jobs rejected during a single collision resolution event.
     */
    int getMaximumRejectedJobs();

    /**
     * @return Number of jobs rejected during most recent collision resolution.
     */
    int getCurrentRejectedJobs();

    /**
     * @return Average number of jobs this node rejects.
     */
    float getAverageRejectedJobs();

    /**
     * @return Total number of jobs this node ever rejected.
     */
    int getTotalRejectedJobs();

    /**
     * Gets maximum cancelled jobs.
     *
     * @return Maximum cancelled jobs.
     */
    int getMaximumCancelledJobs();

    /**
     * Gets current cancelled jobs.
     *
     * @return Current cancelled jobs.
     */
    int getCurrentCancelledJobs();

    /**
     * Gets average cancelled jobs.
     *
     * @return Average cancelled jobs.
     */
    float getAverageCancelledJobs();

    /**
     * Gets total active jobs.
     *
     * @return Total active jobs.
     */
    int getTotalExecutedJobs();

    /**
     * Gets total cancelled jobs.
     *
     * @return Total cancelled jobs.
     */
    int getTotalCancelledJobs();

    /**
     * Gets max job wait time.
     *
     * @return Max job wait time.
     */
    long getMaximumJobWaitTime();

    /**
     * Gets current job wait time.
     *
     * @return Current job wait time.
     */
    long getCurrentJobWaitTime();

    /**
     * Gets average job wait time.
     *
     * @return Average job wait time.
     */
    double getAverageJobWaitTime();

    /**
     * Gets maximum job execution time.
     *
     * @return Maximum job execution time.
     */
    long getMaximumJobExecuteTime();

    /**
     * Gets current job execute time.
     *
     * @return Current job execute time.
     */
    long getCurrentJobExecuteTime();

    /**
     * Gets average job execution time.
     *
     * @return Average job execution time.
     */
    double getAverageJobExecuteTime();

    /**
     * @return Total busy time.
     */
    long getTotalBusyTime();

    /**
     * @return Total idle time.
     */
    long getTotalIdleTime();

    /**
     * @return Current idle time.
     */
    long getCurrentIdleTime();

    /**
     * Gets percentage of time this node is busy executing jobs vs. idling.
     *
     * @return Percentage of time this node is busy (value is less than
     *      or equal to {@code 1} and greater than or equal to {@code 0})
     */
    float getBusyTimePercentage();

    /**
     * Gets percentage of time this node is idling vs. executing jobs.
     *
     * @return Percentage of time this node is idle (value is less than
     *      or equal to {@code 1} and greater than or equal to {@code 0})
     */
    float getIdleTimePercentage();

    /**
     * Returns the number of CPUs available to the Java Virtual Machine.
     * This method is equivalent to the {@link Runtime#availableProcessors()}
     * method.
     * <p>
     * Note that this value may change during successive invocations of the
     * virtual machine.
     *
     * @return The number of processors available to the virtual
     *      machine, never smaller than one.
     */
    int getTotalCpus();

    /**
     * Returns the system load average for the last minute.
     * The system load average is the sum of the number of runnable entities
     * queued to the {@linkplain #getTotalCpus available processors}
     * and the number of runnable entities running on the available processors
     * averaged over a period of time.
     * The way in which the load average is calculated is operating system
     * specific but is typically a damped time-dependent average.
     * <p>
     * If the load average is not available, a negative value is returned.
     * <p>
     * This method is designed to provide a hint about the system load
     * and may be queried frequently. The load average may be unavailable on
     * some platform where it is expensive to implement this method.
     *
     * @return The system load average in <code>[0, 1]</code> range.
     *      Negative value if not available.
     */
    double getCurrentCpuLoad();

    /**
     * Gets average of CPU load values over all metrics kept in the history.
     *
     * @return Average of CPU load value in <code>[0, 1]</code> range over all metrics kept
     *      in the history.
     */
    double getAverageCpuLoad();

    /**
     * Returns the amount of heap memory in bytes that the Java virtual machine
     * initially requests from the operating system for memory management.
     * This method returns {@code -1} if the initial memory size is undefined.
     *
     * @return The initial size of memory in bytes; {@code -1} if undefined.
     */
    long getHeapMemoryInitialized();

    /**
     * Returns the current heap size that is used for object allocation.
     * The heap consists of one or more memory pools. This value is
     * the sum of {@code used} heap memory values of all heap memory pools.
     * <p>
     * The amount of used memory in the returned is the amount of memory
     * occupied by both live objects and garbage objects that have not
     * been collected, if any.
     * <p>
     * <b>Note:</b> this is <b>not</b> an aggregated metric and it's calculated
     * from the time of the node's startup.
     *
     * @return Amount of heap memory used.
     */
    long getHeapMemoryUsed();

    /**
     * Returns the amount of heap memory in bytes that is committed for
     * the Java virtual machine to use. This amount of memory is
     * guaranteed for the Java virtual machine to use.
     * The heap consists of one or more memory pools. This value is
     * the sum of {@code committed} heap memory values of all heap memory pools.
     * <p>
     * <b>Note:</b> this is <b>not</b> an aggregated metric and it's calculated
     * from the time of the node's startup.
     *
     * @return The amount of committed memory in bytes.
     */
    long getHeapMemoryCommitted();

    /**
     * Returns the maximum amount of heap memory in bytes that can be
     * used for memory management. This method returns {@code -1}
     * if the maximum memory size is undefined.
     * <p>
     * This amount of memory is not guaranteed to be available
     * for memory management if it is greater than the amount of
     * committed memory. The Java virtual machine may fail to allocate
     * memory even if the amount of used memory does not exceed this
     * maximum size.
     * <p>
     * This value represents a setting of the heap memory for Java VM and is
     * not a sum of all initial heap values for all memory pools.
     * <p>
     * <b>Note:</b> this is <b>not</b> an aggregated metric and it's calculated
     * from the time of the node's startup.
     *
     * @return The maximum amount of memory in bytes; {@code -1} if undefined.
     */
    long getHeapMemoryMaximum();

    /**
     * Returns the amount of non-heap memory in bytes that the Java virtual machine
     * initially requests from the operating system for memory management.
     * This method returns {@code -1} if the initial memory size is undefined.
     * <p>
     * This value represents a setting of non-heap memory for Java VM and is
     * not a sum of all initial heap values for all memory pools.
     * <p>
     * <b>Note:</b> this is <b>not</b> an aggregated metric and it's calculated
     * from the time of the node's startup.
     *
     * @return The initial size of memory in bytes; {@code -1} if undefined.
     */
    long getNonHeapMemoryInitialized();

    /**
     * Returns the current non-heap memory size that is used by Java VM.
     * The non-heap memory consists of one or more memory pools. This value is
     * the sum of {@code used} non-heap memory values of all non-heap memory pools.
     * <p>
     * <b>Note:</b> this is <b>not</b> an aggregated metric and it's calculated
     * from the time of the node's startup.
     * <p>
     * <b>Note:</b> this is <b>not</b> an aggregated metric and it's calculated
     * from the time of the node's startup.
     *
     * @return Amount of none-heap memory used.
     */
    long getNonHeapMemoryUsed();

    /**
     * Returns the amount of non-heap memory in bytes that is committed for
     * the Java virtual machine to use. This amount of memory is
     * guaranteed for the Java virtual machine to use.
     * The non-heap memory consists of one or more memory pools. This value is
     * the sum of {@code committed} non-heap memory values of all non-heap memory pools.
     * <p>
     * <b>Note:</b> this is <b>not</b> an aggregated metric and it's calculated
     * from the time of the node's startup.
     *
     * @return The amount of committed memory in bytes.
     */
    long getNonHeapMemoryCommitted();

    /**
     * Returns the maximum amount of non-heap memory in bytes that can be
     * used for memory management. This method returns {@code -1}
     * if the maximum memory size is undefined.
     * <p>
     * This amount of memory is not guaranteed to be available
     * for memory management if it is greater than the amount of
     * committed memory.  The Java virtual machine may fail to allocate
     * memory even if the amount of used memory does not exceed this
     * maximum size.
     * <p>
     * This value represents a setting of the non-heap memory for Java VM and is
     * not a sum of all initial non-heap values for all memory pools.
     * <p>
     * <b>Note:</b> this is <b>not</b> an aggregated metric and it's calculated
     * from the time of the node's startup.
     *
     * @return The maximum amount of memory in bytes; {@code -1} if undefined.
     */
    long getNonHeapMemoryMaximum();

    /**
     * Returns the uptime of the Java virtual machine in milliseconds.
     *
     * @return Uptime of the Java virtual machine in milliseconds.
     */
    long getUpTime();

    /**
     * Returns the start time of the Java virtual machine in milliseconds.
     * This method returns the approximate time when the Java virtual
     * machine started.
     *
     * @return Start time of the Java virtual machine in milliseconds.
     */
    long getStartTime();

    /**
     * Returns the start time of grid node in milliseconds.
     * There can be several grid nodes started in one JVM, so JVM start time will be
     * the same for all of them, but node start time will be different.
     *
     * @return Start time of the grid node in milliseconds.
     */
    long getNodeStartTime();

    /**
     * Returns the current number of live threads including both
     * daemon and non-daemon threads.
     *
     * @return Current number of live threads.
     */
    int getCurrentThreadCount();

    /**
     * Returns the maximum live thread count since the Java virtual machine
     * started or peak was reset.
     * <p>
     * <b>Note:</b> this is <b>not</b> an aggregated metric and it's calculated
     * from the time of the node's startup.
     *
     * @return The peak live thread count.
     */
    int getMaximumThreadCount();

    /**
     * Returns the total number of threads created and also started
     * since the Java virtual machine started.
     * <p>
     * <b>Note:</b> this is <b>not</b> an aggregated metric and it's calculated
     * from the time of the node's startup.
     *
     * @return The total number of threads started.
     */
    long getTotalStartedThreadCount();

    /**
     * Returns the current number of live daemon threads.
     *
     * @return Current number of live daemon threads.
     */
    int getCurrentDaemonThreadCount();

    /**
     * Returns the number of unallocated bytes in the partition.
     *
     * @return Number of unallocated bytes in the partition.
     */
    long getFileSystemFreeSpace();

    /**
     * Returns the size of the partition.
     *
     * @return Size of the partition.
     */
    long getFileSystemTotalSpace();

    /**
     * Returns the number of bytes available to this virtual machine on the partition.
     *
     * @return Number of bytes available to this virtual machine on the partition.
     */
    long getFileSystemUsableSpace();

    /**
     * Data grid assigns incremental versions to all cache operations. This method provides
     * the latest data version on the node.
     *
     * @return Last data version.
     */
    long getLastDataVersion();
}
