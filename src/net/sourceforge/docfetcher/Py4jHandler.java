/*******************************************************************************
 * Copyright (c) 2018 Zhengmian Hu.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Zhengmian Hu - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher;

import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.util.Util;
import py4j.GatewayServer;

/**
 * Created by huzhengmian on 2018/5/5.
 */
public class Py4jHandler {

    private static GatewayServer server;
    private static boolean serverStarted = false;

    private static synchronized GatewayServer getServer(){
        if (server == null) {
            server = new GatewayServer(
                    new Py4jHandler(), ProgramConf.Int.PythonApiPort.get()
            );
        }
        return server;
    }

    public static void openGatewayServer(){
        try {
            getServer().start();
            serverStarted = true;
        } catch (Exception e) {
            // Don't crash the application if the Python API server fails to start
            // This is an optional feature and port conflicts are common
            int port = ProgramConf.Int.PythonApiPort.get();
            Util.printErr("Failed to start Python API server on port " + port + ": " + e.getMessage());
            Util.printErr("The Python API will be unavailable. Possible causes:");
            Util.printErr("  - Another instance of DocFetcher is already running");
            Util.printErr("  - Port " + port + " is already in use by another application");
            Util.printErr("  - A previous DocFetcher instance did not shut down cleanly");
            Util.printErr("To resolve: Close other DocFetcher instances or change PythonApiPort in program-conf.txt");
        }
    }

    public static void shutdownGatewayServer(){
        if (serverStarted) {
            try {
                getServer().shutdown();
            } catch (Exception e) {
                // Ignore errors during shutdown
            }
        }
    }
}
