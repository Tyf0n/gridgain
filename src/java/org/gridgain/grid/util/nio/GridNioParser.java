// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */
package org.gridgain.grid.util.nio;

import org.gridgain.grid.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.*;

/**
 * This interface declares a basic contract on message parsing and encoding to the underlying network layer.
 * <p>
 * Note that methods of this interface are called within NIO threads and should be as fast as possible. If
 * any of methods throw an exception, corresponding session will be closed and listener will be notified with
 * exception passed in as argument.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 4.0.2c.12042012
 */
public interface GridNioParser<T> {
    /**
     * This method is called when input bytes are available on the underlying network connection.
     * <p>
     * Method must read given buffer until either it reaches the end of buffer or a valid user message
     * is encountered. In this case it must return parsed message.
     *
     * @param ses Session on which bytes are read.
     * @param buf Buffer that contains input data.
     * @return Parsed user message or {@code null} if complete message has not been received yet. Note
     *         that in case of returning {@code null} given buffer must be completely read.
     * @throws IOException If exception occurred while reading data.
     * @throws org.gridgain.grid.GridException If any user-specific error occurred.
     */
    @Nullable public T decode(GridNioSession ses, ByteBuffer buf) throws IOException, GridException;

    /**
     * This method is called whenever a message should be sent to the network connection
     * and network buffer is ready to be filled with bytes.
     * <p>
     * Method must encode the complete message and return byte buffer from which data should be read.
     *
     * @param ses Session on which message is being sent.
     * @param msg Message to encode.
     * @return Buffer containing encoded message.
     * @throws java.io.IOException If exception occurred while encoding data.
     * @throws org.gridgain.grid.GridException If any user-specific error occurred while encoding data.
     */
    public ByteBuffer encode(GridNioSession ses, T msg) throws IOException, GridException;
}
