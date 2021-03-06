// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.managers.deployment;

import org.gridgain.grid.*;
import org.gridgain.grid.events.*;
import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.managers.communication.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.lang.utils.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.tostring.*;

import java.io.*;
import java.util.*;

import static org.gridgain.grid.GridEventType.*;
import static org.gridgain.grid.kernal.GridTopic.*;

/**
 * Communication helper class. Provides request and response sending methods.
 * It uses communication manager as a way of sending and receiving requests.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
@SuppressWarnings({"deprecation"})
@GridToStringExclude
class GridDeploymentCommunication {
    /** */
    private final GridLogger log;

    /** */
    private final GridKernalContext ctx;

    /** */
    private final GridMessageListener peerLsnr;

    /** */
    private final ThreadLocal<Collection<UUID>> activeReqNodeIds = new ThreadLocal<Collection<UUID>>();

    /** */
    private final GridBusyLock busyLock = new GridBusyLock();

    /**
     * Creates new instance of deployment communication.
     *
     * @param ctx Kernal context.
     * @param log Logger.
     */
    GridDeploymentCommunication(final GridKernalContext ctx, GridLogger log) {
        assert log != null;

        this.ctx = ctx;
        this.log = log.getLogger(getClass());

        peerLsnr = new GridMessageListener() {
            @Override public void onMessage(UUID nodeId, Object msg) {
                processDeploymentRequest(nodeId, msg);
            }
        };
    }

    /**
     * Starts deployment communication.
     */
    void start() {
        ctx.io().addMessageListener(TOPIC_CLASSLOAD, peerLsnr);

        if (log.isDebugEnabled())
            log.debug("Started deployment communication.");
    }

    /**
     * Stops deployment communication.
     */
    void stop() {
        if (log.isDebugEnabled())
            log.debug("Stopping deployment communication.");

        busyLock.block();

        ctx.io().removeMessageListener(TOPIC_CLASSLOAD, peerLsnr);
    }

    /**
     * @param nodeId Node ID.
     * @param msg Request.
     */
    private void processDeploymentRequest(UUID nodeId, Object msg) {
        assert nodeId != null;
        assert msg != null;

        if (!busyLock.enterBusy()) {
            if (log.isDebugEnabled())
                log.debug("Ignoring deployment request since grid is stopping " +
                    "[nodeId=" + nodeId + ", msg=" + msg + ']');

            return;
        }

        try {
            GridDeploymentRequest req = (GridDeploymentRequest)msg;

            if (req.isUndeploy())
                processUndeployRequest(nodeId, req);
            else {
                assert activeReqNodeIds.get() == null;

                Collection<UUID> nodeIds = req.nodeIds();

                nodeIds = nodeIds == null ? new HashSet<UUID>() : new HashSet<UUID>(nodeIds);

                boolean b = nodeIds.add(nodeId);

                assert b;

                activeReqNodeIds.set(nodeIds);

                try {
                    processResourceRequest(nodeId, req);
                }
                finally {
                    activeReqNodeIds.set(null);
                }
            }
        }
        finally {
            busyLock.leaveBusy();
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param req Undeploy request.
     */
    private void processUndeployRequest(UUID nodeId, GridDeploymentRequest req) {
        if (log.isDebugEnabled())
            log.debug("Received undeploy request [nodeId=" + nodeId + ", req=" + req + ']');

        ctx.deploy().undeployTask(nodeId, req.resourceName());
    }

    /**
     * Handles classes/resources requests.
     *
     * @param nodeId Originating node id.
     * @param req Request.
     */
    private void processResourceRequest(UUID nodeId, GridDeploymentRequest req) {
        if (log.isDebugEnabled())
            log.debug("Received peer class/resource loading request [node=" + nodeId + ", req=" + req + ']');

        GridDeploymentResponse res = new GridDeploymentResponse();

        GridDeployment dep = ctx.deploy().getDeployment(req.classLoaderId());

        // Null class loader means failure here.
        if (dep != null) {
            ClassLoader ldr = dep.classLoader();

            // In case the class loader is ours - skip the check
            // since it was already performed before (and was successful).
            if (!(ldr instanceof GridDeploymentClassLoader)) {
                // First check for @GridNotPeerDeployable annotation.
                try {
                    String clsName = req.resourceName().replace('/', '.');

                    int idx = clsName.indexOf(".class");

                    if (idx >= 0)
                        clsName = clsName.substring(0, idx);

                    Class<?> cls = Class.forName(clsName, true, ldr);

                    if (U.getAnnotation(cls, GridNotPeerDeployable.class) != null) {
                        String errMsg = "Attempt to peer deploy class that has @GridNotPeerDeployable " +
                            "annotation: " + clsName;

                        U.error(log, errMsg);

                        res.errorMessage(errMsg);
                        res.success(false);

                        sendResponse(nodeId, req.responseTopic(), res);

                        return;
                    }
                }
                catch (ClassNotFoundException ignore) {
                    // Safely ignore it here - resource wasn't a class name.
                }
            }

            InputStream in = ldr.getResourceAsStream(req.resourceName());

            if (in == null) {
                String errMsg = "Requested resource not found (ignoring locally): " + req.resourceName();

                // Java requests the same class with BeanInfo suffix during
                // introspection automatically. Usually nobody uses this kind
                // of classes. Thus we print it out with DEBUG level.
                // Also we print it with DEBUG level because of the
                // frameworks which ask some classes just in case - for
                // example to identify whether certain framework is available.
                // Remote node will throw an exception if needs.
                if (log.isDebugEnabled())
                    log.debug(errMsg);

                res.success(false);
                res.errorMessage(errMsg);
            }
            else {
                try {
                    GridByteArrayList bytes = new GridByteArrayList(1024);

                    bytes.readAll(in);

                    res.success(true);
                    res.byteSource(bytes);
                }
                catch (IOException e) {
                    String errMsg = "Failed to read resource due to IO failure: " + req.resourceName();

                    U.error(log, errMsg, e);

                    res.errorMessage(errMsg);
                    res.success(false);
                }
                finally {
                    U.close(in, log);
                }
            }
        }
        else {
            String errMsg = "Failed to find local deployment for peer request: " + req;

            U.warn(log, errMsg);

            res.success(false);
            res.errorMessage(errMsg);
        }

        sendResponse(nodeId, req.responseTopic(), res);
    }

    /**
     * @param nodeId Destination node ID.
     * @param topic Response topic.
     * @param res Response.
     */
    private void sendResponse(UUID nodeId, String topic, Serializable res) {
        GridNode node = ctx.discovery().node(nodeId);

        if (node != null) {
            try {
                ctx.io().send(node, topic, res, GridIoPolicy.P2P_POOL);

                if (log.isDebugEnabled())
                    log.debug("Sent peer class loading response [node=" + node.id() + ", res=" + res + ']');
            }
            catch (GridException e) {
                if (ctx.discovery().pingNode(nodeId))
                    U.error(log, "Failed to send peer class loading response to node: " + nodeId, e);
                else if (log.isDebugEnabled())
                    log.debug("Failed to send peer class loading response to node " +
                        "(node does not exist): " + nodeId);
            }
        }
        else if (log.isDebugEnabled())
                log.debug("Failed to send peer class loading response to node " +
                    "(node does not exist): " + nodeId);
    }


    /**
     * @param rsrcName Resource to undeploy.
     * @throws GridException If request could not be sent.
     */
    void sendUndeployRequest(String rsrcName) throws GridException {
        Serializable req = new GridDeploymentRequest(null, rsrcName, true);

        Collection<GridNode> rmtNodes = ctx.discovery().remoteNodes();

        if (!rmtNodes.isEmpty()) {
            ctx.io().send(
                rmtNodes,
                TOPIC_CLASSLOAD,
                req,
                GridIoPolicy.P2P_POOL
            );
        }
    }

    /**
     * Sends request to the remote node and wait for response. If there is
     * no response until threshold time, method returns null.
     *
     *
     * @param rsrcName Resource name.
     * @param clsLdrId Class loader ID.
     * @param dstNode Remote node request should be sent to.
     * @param threshold Time in milliseconds when request is decided to
     *      be obsolete.
     * @return Either response value or {@code null} if timeout occurred.
     * @throws GridException Thrown if there is no connection with remote node.
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    GridDeploymentResponse sendResourceRequest(final String rsrcName, GridUuid clsLdrId,
        final GridNode dstNode, long threshold) throws GridException {
        assert rsrcName != null;
        assert dstNode != null;
        assert clsLdrId != null;

        Collection<UUID> nodeIds = activeReqNodeIds.get();

        if (nodeIds != null && nodeIds.contains(dstNode.id())) {
            if (log.isDebugEnabled())
                log.debug("Node attempts to load resource from one of the requesters " +
                    "[rsrcName=" + rsrcName + ", dstNodeId=" + dstNode.id() +
                    ", requesters=" + nodeIds + ']');

            GridDeploymentResponse fake = new GridDeploymentResponse();

            fake.success(false);
            fake.errorMessage("Node attempts to load resource from one of the requesters " +
                "[rsrcName=" + rsrcName + ", dstNodeId=" + dstNode.id() +
                ", requesters=" + nodeIds + ']');

            return fake;
        }

        String resTopic = TOPIC_CLASSLOAD.name(GridUuid.randomUuid());

        GridDeploymentRequest req = new GridDeploymentRequest(clsLdrId, rsrcName, false);

        req.responseTopic(resTopic);

        // Send node IDs chain with request.
        req.nodeIds(nodeIds);

        final Object qryMux = new Object();

        final GridTuple<GridDeploymentResponse> res = F.t1();

        GridLocalEventListener discoLsnr = new GridLocalEventListener() {
            @Override public void onEvent(GridEvent evt) {
                assert evt instanceof GridDiscoveryEvent;

                assert evt.type() == EVT_NODE_LEFT || evt.type() == EVT_NODE_FAILED;

                GridDiscoveryEvent discoEvt = (GridDiscoveryEvent)evt;

                UUID nodeId = discoEvt.eventNodeId();

                if (!nodeId.equals(dstNode.id()))
                    // Not a destination node.
                    return;

                GridDeploymentResponse fake = new GridDeploymentResponse();

                String errMsg = "Originating node left grid (resource will not be peer loaded) " +
                    "[nodeId=" + dstNode.id() + ", rsrc=" + rsrcName + ']';

                U.warn(log, errMsg);

                fake.success(false);
                fake.errorMessage(errMsg);

                // We put fake result here to interrupt waiting peer-to-peer thread
                // because originating node has left grid.
                synchronized (qryMux) {
                    res.set(fake);

                    qryMux.notifyAll();
                }
            }
        };

        GridMessageListener resLsnr = new GridMessageListener() {
            @Override public void onMessage(UUID nodeId, Object msg) {
                assert nodeId != null;
                assert msg != null;

                synchronized (qryMux) {
                    if (!(msg instanceof GridDeploymentResponse)) {
                        U.error(log, "Received unknown peer class loading response [node=" + nodeId + ", msg=" +
                            msg + ']');
                    }
                    else
                        res.set((GridDeploymentResponse)msg);

                    qryMux.notifyAll();
                }
            }
        };

        try {
            ctx.io().addMessageListener(resTopic, resLsnr);

            // The destination node has potentially left grid here but in this case
            // Communication manager will throw the exception while sending message.
            ctx.event().addLocalEventListener(discoLsnr, EVT_NODE_FAILED, EVT_NODE_LEFT);

            long start = System.currentTimeMillis();

            ctx.io().send(dstNode, TOPIC_CLASSLOAD, req, GridIoPolicy.P2P_POOL);

            if (log.isDebugEnabled())
                log.debug("Sent peer class loading request [node=" + dstNode.id() + ", req=" + req + ']');

            synchronized (qryMux) {
                try {
                    long timeout = threshold - start;

                    if (log.isDebugEnabled()) {
                        log.debug("Waiting for peer response from node [node=" + dstNode.id() +
                            ", timeout=" + timeout + ']');
                    }

                    while (res.get() == null && timeout > 0) {
                        qryMux.wait(timeout);

                        timeout = threshold - System.currentTimeMillis();
                    }
                }
                catch (InterruptedException e) {
                    // Interrupt again to get it in the users code.
                    Thread.currentThread().interrupt();

                    throw new GridException("Got interrupted while waiting for response from node: " +
                        dstNode.id(), e);
                }
            }

            if (res.get() == null) {
                U.warn(log, "Failed to receive peer response from node within duration [node=" + dstNode.id() +
                    ", duration=" + (System.currentTimeMillis() - start) + ']');
            }
            else if (log.isDebugEnabled())
                log.debug("Received peer loading response [node=" + dstNode.id() + ", res=" + res.get() + ']');

            return res.get();
        }
        finally {
            ctx.event().removeLocalEventListener(discoLsnr, EVT_NODE_FAILED, EVT_NODE_LEFT);

            ctx.io().removeMessageListener(resTopic, resLsnr);
        }
    }
}
