/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2009], Hyperic, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.plugin.couchdb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hyperic.hq.product.Collector;
import org.hyperic.hq.product.DaemonDetector;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ServiceResource;
import org.hyperic.hq.product.SigarMeasurementPlugin;
import org.hyperic.sigar.NetFlags;
import org.hyperic.util.config.ConfigResponse;

import org.jcouchdb.db.Server;
import org.jcouchdb.db.ServerImpl;

public class CouchDBDetector extends DaemonDetector {

    private static final String BIND_ADDRESS = "bindaddress";

    private void setOpt(Map opts, String opt, String val) {
        if (opts.containsKey(opt) || //set by process                                                         
            (getTypeProperty(opt) != null)) //set in agent.properties                                         
        {
            return; //dont override user config                                                               
        }
        opts.put(opt, val);
    }

    protected Map getProcOpts(long pid) {
        Map opts = super.getProcOpts(pid);
        String defaultPort = getTypeProperty(Collector.PROP_PORT);

        String home = (String)opts.get("-home");
        if (home != null) {
            setOpt(opts, INSTALLPATH, home);
        }
        setOpt(opts, INVENTORY_ID, "couchdb@%port%");

        String pidfile = (String)opts.get("-pidfile");
        if ((pidfile != null) && new File(pidfile).canRead()) {
            opts.put(SigarMeasurementPlugin.PTQL_CONFIG,
                     "Pid.PidFile.eq=" + pidfile);
        }

        //parse couchdb.ini
        String ini = (String)opts.get("-couchini");

        if (ini == null) {
            getLog().debug("Unable to find couchdb.conf");
            return opts;
        }

        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(ini));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if ((line.length() == 0) || (line.charAt(0) == ';')) {
                    continue;
                }
                String[] elts = line.split("=");
                if (elts.length == 2) {
                    opts.put(elts[0].toLowerCase(), elts[1]);
                }
            }
        } catch (IOException e) {
            getLog().error("Failed to read " + ini + e.getMessage(), e);
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException e) {}
            }
        }

        if (NetFlags.ANY_ADDR.equals(opts.get(BIND_ADDRESS))) {
            opts.put(BIND_ADDRESS, NetFlags.LOOPBACK_ADDRESS);
        }
        if (opts.get(Collector.PROP_PORT) == null) {
            opts.put(Collector.PROP_PORT, defaultPort);
        }
        //append http port for unique name
        if (!opts.get(Collector.PROP_PORT).equals(defaultPort)) {
            setOpt(opts, AUTOINVENTORY_NAME,
                   "%platform.name% " + getTypeInfo().getName() + " @%port%");
        }

        return opts;
    }

    protected List<ServiceResource> discoverServices(ConfigResponse config)
        throws PluginException {

        List<ServiceResource> services = new ArrayList<ServiceResource>();
        String hostname = config.getValue(Collector.PROP_HOSTNAME);
        int port = Integer.valueOf(config.getValue(Collector.PROP_PORT));
        Server server = new ServerImpl(hostname, port);

        for (String dbname : server.listDatabases()) {
            ServiceResource service = createServiceResource("Database");
            ConfigResponse serviceConfig = new ConfigResponse();
            serviceConfig.setValue(CouchDatabaseCollector.PROP_DBNAME, dbname);
            service.setProductConfig(serviceConfig);
            service.setMeasurementConfig();
            service.setServiceName(dbname);
            services.add(service);
        }
        return services;
    }
}
