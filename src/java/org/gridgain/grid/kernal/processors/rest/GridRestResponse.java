// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.rest;

import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

/**
 * JSON response. Getters and setters must conform to JavaBean standard.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.0c.24032012
 */
public class GridRestResponse {
    /** Command succeeded. */
    public static final int STATUS_SUCCESS = 0;

    /** Command failed. */
    public static final int STATUS_FAILED = 1;

    /** Authentication failure. */
    public static final int STATUS_AUTH_FAILED = 2;

    /** Success status. */
    private int successStatus = STATUS_SUCCESS;

    /** Session token. */
    private byte[] sesTokBytes;

    /** Session token string representation. */
    private String sesTokStr;

    /** Error. */
    private String err;

    /** Response object. */
    @GridToStringInclude
    private Object obj;

    /**
     *
     */
    public GridRestResponse() {
        // No-op.
    }

    /**
     * Constructs successful rest response.
     *
     * @param obj Response object.
     */
    public GridRestResponse(Object obj) {
        successStatus = STATUS_SUCCESS;
        this.obj = obj;
    }

    /**
     * Constructs failed rest response.
     *
     * @param status Response status.
     * @param err Error, {@code null} if success is {@code true}.
     */
    public GridRestResponse(int status, @Nullable String err) {
        assert status != STATUS_SUCCESS;

        successStatus = status;
        this.err = err;
    }

    /**
     * @return Success flag.
     */
    public int getSuccessStatus() {
        return successStatus;
    }

    /**
     * @return Response object.
     */
    public Object getResponse() {
        return obj;
    }

    /**
     * @param obj Response object.
     */
    public void setResponse(@Nullable Object obj) {
        this.obj = obj;
    }

    /**
     * @return Error.
     */
    public String getError() {
        return err;
    }

    /**
     * @param err Error.
     */
    public void setError(String err) {
        this.err = err;
    }

    /**
     * @return Session token for remote client.
     */
    public byte[] sessionTokenBytes() {
        return sesTokBytes;
    }

    /**
     * @param sesTokBytes Session token for remote client.
     */
    public void sessionTokenBytes(@Nullable byte[] sesTokBytes) {
        this.sesTokBytes = sesTokBytes;
    }

    /**
     * @return String representation of session token.
     */
    public String getSessionToken() {
        return sesTokStr;
    }

    /**
     * @param sesTokStr String representation of session token.
     */
    public void setSessionToken(@Nullable String sesTokStr) {
        this.sesTokStr = sesTokStr;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridRestResponse.class, this);
    }
}
