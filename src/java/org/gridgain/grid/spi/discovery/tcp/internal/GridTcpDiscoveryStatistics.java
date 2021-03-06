// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.discovery.tcp.internal;

import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.spi.discovery.tcp.*;
import org.gridgain.grid.spi.discovery.tcp.messages.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Statistics for {@link GridTcpDiscoverySpi}.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
public class GridTcpDiscoveryStatistics {
    /** Join started timestamp. */
    private long joinStartedTs;

    /** Join finished timestamp. */
    private long joinFinishedTs;

    /** Coordinator since timestamp. */
    private final AtomicLong crdSinceTs = new AtomicLong();

    /** Joined nodes count. */
    private int joinedNodesCnt;

    /** Failed nodes count. */
    private int failedNodesCnt;

    /** Left nodes count. */
    private int leftNodesCnt;

    /** Ack timeouts count. */
    private int ackTimeoutsCnt;

    /** Socket timeouts count. */
    private int sockTimeoutsCnt;

    /** Received messages. */
    @GridToStringInclude
    private final Map<String, Integer> rcvdMsgs = new HashMap<String, Integer>();

    /** Processed messages. */
    @GridToStringInclude
    private final Map<String, Integer> procMsgs = new HashMap<String, Integer>();

    /** Average time taken to serialize messages. */
    @GridToStringInclude
    private final Map<String, Long> avgMsgsSndTimes = new HashMap<String, Long>();

    /** Average time taken to serialize messages. */
    @GridToStringInclude
    private final Map<String, Long> maxMsgsSndTimes = new HashMap<String, Long>();

    /** Sent messages. */
    @GridToStringInclude
    private final Map<String, Integer> sentMsgs = new HashMap<String, Integer>();

    /** Messages receive timestamps. */
    private final Map<GridUuid, Long> msgsRcvTs = new GridBoundedLinkedHashMap<GridUuid, Long>(1024);

    /** Messages processing start timestamps. */
    private final Map<GridUuid, Long> msgsProcStartTs = new GridBoundedLinkedHashMap<GridUuid, Long>(1024);

    /** Ring messages sent timestamps. */
    private final Map<GridUuid, Long> ringMsgsSndTs = new GridBoundedLinkedHashMap<GridUuid, Long>(1024);

    /** Average time messages is in queue. */
    private long avgMsgQueueTime;

    /** Max time messages is in queue. */
    private long maxMsgQueueTime;

    /** Total number of ring messages sent. */
    private int ringMsgsSent;

    /** Average time it takes for messages to pass the full ring. */
    private long avgRingMsgTime;

    /** Max time it takes for messages to pass the full ring. */
    private long maxRingMsgTime;

    /** Class name of ring message that required the biggest time for full ring traverse. */
    private String maxRingTimeMsgCls;

    /** Average message processing time. */
    private long avgMsgProcTime;

    /** Max message processing time. */
    private long maxMsgProcTime;

    /** Class name of the message that required the biggest time to process. */
    private String maxProcTimeMsgCls;

    /** Socket readers created count. */
    private int sockReadersCreated;

    /** Socket readers removed count. */
    private int sockReadersRmv;

    /** Average time it takes to initialize connection from another node. */
    private long avgServerSockInitTime;

    /** Max time it takes to initialize connection from another node. */
    private long maxServerSockInitTime;

    /** Number of outgoing connections established. */
    private int clientSockCreatedCnt;

    /** Average time it takes to connect to another node. */
    private long avgClientSockInitTime;

    /** Max time it takes to connect to another node. */
    private long maxClientSockInitTime;

    /** Pending messages registered count. */
    private int pendingMsgsRegistered;

    /** Pending messages discarded count. */
    private int pendingMsgsDiscarded;

    /**
     * Increments joined nodes count.
     */
    public synchronized void onNodeJoined() {
        joinedNodesCnt++;
    }

    /**
     * Increments left nodes count.
     */
    public synchronized void onNodeLeft() {
        leftNodesCnt++;
    }

    /**
     * Increments failed nodes count.
     */
    public synchronized void onNodeFailed() {
        failedNodesCnt++;
    }

    /**
     * Increments ack timeouts count.
     */
    public synchronized void onAckTimeout() {
        ackTimeoutsCnt++;
    }

    /**
     * Increments socket timeouts count.
     */
    public synchronized void onSocketTimeout() {
        sockTimeoutsCnt++;
    }

    /**
     * Initializes coordinator since date (if needed).
     */
    public void onBecomingCoordinator() {
        crdSinceTs.compareAndSet(0, System.currentTimeMillis());
    }

    /**
     * Initializes join started timestamp.
     */
    public synchronized void onJoinStarted() {
        joinStartedTs = System.currentTimeMillis();
    }

    /**
     * Initializes join finished timestamp.
     */
    public synchronized void onJoinFinished() {
        joinFinishedTs = System.currentTimeMillis();
    }

    /**
     * @return Join started timestamp.
     */
    public synchronized long joinStarted() {
        return joinStartedTs;
    }

    /**
     * @return Join finished timestamp.
     */
    public synchronized long joinFinished() {
        return joinFinishedTs;
    }

    /**
     * Collects necessary stats for message received by SPI.
     *
     * @param msg Received message.
     */
    public synchronized void onMessageReceived(GridTcpDiscoveryAbstractMessage msg) {
        assert msg != null;

        Integer cnt = F.addIfAbsent(rcvdMsgs, msg.getClass().getSimpleName(), new Callable<Integer>() {
            @Override public Integer call() {
                return 0;
            }
        });

        assert cnt != null;

        rcvdMsgs.put(msg.getClass().getSimpleName(), ++cnt);

        msgsRcvTs.put(msg.id(), System.currentTimeMillis());
    }

    /**
     * Collects necessary stats for message processed by SPI.
     *
     * @param msg Processed message.
     */
    public synchronized void onMessageProcessingStarted(GridTcpDiscoveryAbstractMessage msg) {
        assert msg != null;

        Integer cnt = F.addIfAbsent(procMsgs, msg.getClass().getSimpleName(), new Callable<Integer>() {
            @Override public Integer call() {
                return 0;
            }
        });

        assert cnt != null;

        procMsgs.put(msg.getClass().getSimpleName(), ++cnt);

        Long rcvdTs = msgsRcvTs.remove(msg.id());

        if (rcvdTs != null) {
            long duration = System.currentTimeMillis() - rcvdTs;

            if (maxMsgQueueTime < duration)
                maxMsgQueueTime = duration;

            avgMsgQueueTime = (avgMsgQueueTime * (totalReceivedMessages() -1)) / totalProcessedMessages();
        }

        msgsProcStartTs.put(msg.id(), System.currentTimeMillis());
    }

    /**
     * Collects necessary stats for message processed by SPI.
     *
     * @param msg Processed message.
     */
    public synchronized void onMessageProcessingFinished(GridTcpDiscoveryAbstractMessage msg) {
        assert msg != null;

        Long startTs = msgsProcStartTs.get(msg.id());

        if (startTs != null) {
            long duration = System.currentTimeMillis() - startTs;

            avgMsgProcTime = (avgMsgProcTime * (totalProcessedMessages() - 1) + duration) / totalProcessedMessages();

            if (duration > maxMsgProcTime) {
                maxMsgProcTime = duration;

                maxProcTimeMsgCls = msg.getClass().getSimpleName();
            }

            msgsProcStartTs.remove(msg.id());
        }
    }

    /**
     * Called by coordinator when ring message is sent.
     *
     * @param msg Sent message.
     * @param time Time taken to serialize message.
     */
    public synchronized void onMessageSent(GridTcpDiscoveryAbstractMessage msg, long time) {
        assert msg != null;
        assert time >= 0;

        if (crdSinceTs.get() > 0 &&
            (msg instanceof GridTcpDiscoveryNodeAddedMessage) ||
            (msg instanceof GridTcpDiscoveryNodeLeftMessage) ||
            (msg instanceof GridTcpDiscoveryNodeFailedMessage)) {
            ringMsgsSndTs.put(msg.id(), System.currentTimeMillis());

            ringMsgsSent++;
        }

        Integer cnt = F.addIfAbsent(sentMsgs, msg.getClass().getSimpleName(), new Callable<Integer>() {
            @Override public Integer call() {
                return 0;
            }
        });

        assert cnt != null;

        sentMsgs.put(msg.getClass().getSimpleName(), ++cnt);

        Long avgTime = F.addIfAbsent(avgMsgsSndTimes, msg.getClass().getSimpleName(), new Callable<Long>() {
            @Override public Long call() {
                return 0L;
            }
        });

        assert avgTime != null;

        avgTime = (avgTime * (cnt - 1) + time) / cnt;

        avgMsgsSndTimes.put(msg.getClass().getSimpleName(), avgTime);

        Long maxTime = F.addIfAbsent(maxMsgsSndTimes, msg.getClass().getSimpleName(), new Callable<Long>() {
            @Override public Long call() {
                return 0L;
            }
        });

        assert maxTime != null;

        if (time > maxTime)
            maxMsgsSndTimes.put(msg.getClass().getSimpleName(), time);
    }

    /**
     * Called by coordinator when ring message makes full pass.
     *
     * @param msg Message.
     */
    public synchronized void onRingMessageReceived(GridTcpDiscoveryAbstractMessage msg) {
        assert msg != null;

        Long sentTs = ringMsgsSndTs.get(msg.id());

        if (sentTs != null) {
            long duration  = System.currentTimeMillis() - sentTs;

            if (maxRingMsgTime < duration) {
                maxRingMsgTime = duration;

                maxRingTimeMsgCls = msg.getClass().getSimpleName();
            }

            if (ringMsgsSent != 0)
                avgRingMsgTime = (avgRingMsgTime * (ringMsgsSent - 1) + duration) / ringMsgsSent;
        }
    }

    /**
     * Gets max time for ring message to make full pass.
     *
     * @return Max full pass time.
     */
    public synchronized long maxRingMessageTime() {
        return maxRingMsgTime;
    }

    /**
     * Gets class name of the message that took max time to make full pass.
     *
     * @return Message class name.
     */
    public synchronized String maxRingDurationMessageClass() {
        return maxRingTimeMsgCls;
    }

    /**
     * Gets class name of the message took max time to process.
     *
     * @return Message class name.
     */
    public synchronized String maxProcessingTimeMessageClass() {
        return maxProcTimeMsgCls;
    }

    /**
     * @param initTime Time socket was initialized in.
     */
    public synchronized void onServerSocketInitialized(long initTime) {
        assert initTime >= 0;

        if (maxServerSockInitTime < initTime)
            maxServerSockInitTime = initTime;

        avgServerSockInitTime = (avgServerSockInitTime * (sockReadersCreated - 1) + initTime) / sockReadersCreated;
    }

    /**
     * @param initTime Time socket was initialized in.
     */
    public synchronized void onClientSocketInitialized(long initTime) {
        assert initTime >= 0;

        clientSockCreatedCnt++;

        if (maxClientSockInitTime < initTime)
            maxClientSockInitTime = initTime;

        avgClientSockInitTime = (avgClientSockInitTime * (clientSockCreatedCnt - 1) + initTime) / clientSockCreatedCnt;
    }

    /**
     * Increments pending messages registered count.
     */
    public synchronized void onPendingMessageRegistered() {
        pendingMsgsRegistered++;
    }

    /**
     * Increments pending messages discarded count.
     */
    public synchronized void onPendingMessageDiscarded() {
        pendingMsgsDiscarded++;
    }

    /**
     * Increments socket readers created count.
     */
    public synchronized void onSocketReaderCreated() {
        sockReadersCreated++;
    }

    /**
     * Increments socket readers removed count.
     */
    public synchronized void onSocketReaderRemoved() {
        sockReadersRmv++;
    }

    /**
     * Gets processed messages counts (grouped by type).
     *
     * @return Map containing message types and respective counts.
     */
    public synchronized Map<String, Integer> processedMessages() {
        return new HashMap<String, Integer>(procMsgs);
    }

    /**
     * Gets received messages counts (grouped by type).
     *
     * @return Map containing message types and respective counts.
     */
    public synchronized Map<String, Integer> receivedMessages() {
        return new HashMap<String, Integer>(rcvdMsgs);
    }

    /**
     * Gets max messages send time (grouped by type).
     *
     * @return Map containing messages types and max send times.
     */
    public synchronized Map<String, Long> maxMessagesSendTimes() {
        return new HashMap<String, Long>(maxMsgsSndTimes);
    }

    /**
     * Gets average messages send time (grouped by type).
     *
     * @return Map containing messages types and average send times.
     */
    public synchronized Map<String, Long> avgMessagesSendTimes() {
        return new HashMap<String, Long>(avgMsgsSndTimes);
    }

    /**
     * Gets total received messages count.
     *
     * @return Total received messages count.
     */
    public synchronized int totalReceivedMessages() {
        return F.sum(receivedMessages().values());
    }

    /**
     * Gets total processed messages count.
     *
     * @return Total processed messages count.
     */
    public synchronized int totalProcessedMessages() {
        return F.sum(processedMessages().values());
    }

    /**
     * Gets max message processing time.
     *
     * @return Max message processing time.
     */
    public synchronized long maxMessageProcessingTime(){
        return maxMsgProcTime;
    }

    /**
     * Gets average message processing time.
     *
     * @return Average message processing time.
     */
    public synchronized long avgMessageProcessingTime() {
        return avgMsgProcTime;
    }

    /**
     * Gets pending messages registered count.
     *
     * @return Pending messages registered count.
     */
    public synchronized long pendingMessagesRegistered() {
        return pendingMsgsRegistered;
    }

    /**
     * Gets pending messages discarded count.
     *
     * @return Pending messages registered count.
     */
    public synchronized long pendingMessagesDiscarded() {
        return pendingMsgsDiscarded;
    }

    /**
     * Gets nodes joined count.
     *
     * @return Nodes joined count.
     */
    public synchronized int joinedNodesCount() {
        return joinedNodesCnt;
    }

    /**
     * Gets nodes left count.
     *
     * @return Nodes left count.
     */
    public synchronized int leftNodesCount() {
        return leftNodesCnt;
    }

    /**
     * Gets failed nodes count.
     *
     * @return Failed nodes count.
     */
    public synchronized int failedNodesCount() {
        return failedNodesCnt;
    }

    /**
     * @return Ack timeouts count.
     */
    public synchronized int ackTimeoutsCount() {
        return ackTimeoutsCnt;
    }

    /**
     * @return Socket timeouts count.
     */
    public synchronized int socketTimeoutsCount() {
        return sockTimeoutsCnt;
    }

    /**
     * Gets socket readers created count.
     *
     * @return Socket readers created count.
     */
    public synchronized int socketReadersCreated() {
        return sockReadersCreated;
    }

    /**
     * Gets socket readers removed count.
     *
     * @return Socket readers removed count.
     */
    public synchronized int socketReadersRemoved() {
        return sockReadersRmv;
    }

    /**
     * Gets time local node has been coordinator since.
     *
     * @return Coordinator since timestamp.
     */
    public long coordinatorSinceTimestamp() {
        return crdSinceTs.get();
    }

    /**
     * Clears statistics.
     */
    public synchronized void clear() {
        ackTimeoutsCnt = 0;
        avgClientSockInitTime = 0;
        avgMsgProcTime = 0;
        avgMsgQueueTime = 0;
        avgMsgsSndTimes.clear();
        avgRingMsgTime = 0;
        avgServerSockInitTime = 0;
        clientSockCreatedCnt = 0;
        crdSinceTs.set(0);
        failedNodesCnt = 0;
        joinedNodesCnt = 0;
        joinFinishedTs = 0;
        joinStartedTs = 0;
        leftNodesCnt = 0;
        maxClientSockInitTime = 0;
        maxMsgProcTime = 0;
        maxMsgQueueTime = 0;
        maxMsgsSndTimes.clear();
        maxProcTimeMsgCls = null;
        maxRingMsgTime = 0;
        maxRingTimeMsgCls = null;
        maxServerSockInitTime = 0;
        pendingMsgsDiscarded = 0;
        pendingMsgsRegistered = 0;
        procMsgs.clear();
        rcvdMsgs.clear();
        ringMsgsSent = 0;
        sentMsgs.clear();
        sockReadersCreated = 0;
        sockReadersRmv = 0;
        sockTimeoutsCnt = 0;
    }

    /** {@inheritDoc} */
    @Override public synchronized String toString() {
        return S.toString(GridTcpDiscoveryStatistics.class, this);
    }
}
