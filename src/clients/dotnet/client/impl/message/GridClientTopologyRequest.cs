// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

namespace GridGain.Client.Impl.Message {
    using System;
    using System.Text;

    /** <summary><c>Topology</c> command request.</summary> */
    internal class GridClientTopologyRequest : GridClientRequest {
        /** <summary>Include metrics flag.</summary> */
        public bool IncludeMetrics {
            get;
            set;
        }

        /** <summary>Include node attributes flag.</summary> */
        public bool IncludeAttributes {
            get;
            set;
        }

        /** <summary>Id of requested node if specified, <c>null</c> otherwise.</summary> */
        public String NodeId {
            get;
            set;
        }

        /** <summary>IP address of requested node if specified, <c>null</c> otherwise.</summary> */
        public String NodeIP {
            get;
            set;
        }
    }
}
