// @csharp.file.header

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

namespace GridGain.Client.Impl.Message {
    using System;
    using System.Collections;
    using System.Text;

    using U = GridGain.Client.Util.GridClientUtils;

    /** <summary><c>Task</c> command request.</summary> */
    internal class GridClientTaskRequest : GridClientRequest {
        /** <summary>Task name.</summary> */
        public String TaskName {
            get;
            set;
        }

        /** <summary>Task argument.</summary> */
        public Object Argument {
            get;
            set;
        }
    }
}
