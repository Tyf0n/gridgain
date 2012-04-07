﻿// @csharp.file.header

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

namespace GridGain.Client.Hasher {
    using System;

    /** <summary>Interface for pluggable hasher functions.</summary> */
    public interface IGridClientHasher {
        /**
         * <summary>
         * Produces hash code for a given binary data.</summary>
         *
         * <param name="data">Binary data to hash.</param>
         * <returns>Hash code for the binary data.</returns>
         */
        int Hash(byte[] data);
    }
}
