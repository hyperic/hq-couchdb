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
import org.hyperic.hq.product.ConfigFileTrackPlugin;
import org.hyperic.hq.product.DaemonDetector;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ServerResource;
import org.hyperic.hq.product.ServiceResource;
import org.hyperic.hq.product.SigarMeasurementPlugin;
import org.hyperic.sigar.NetFlags;
import org.hyperic.util.config.ConfigResponse;

import org.jcouchdb.db.Server;
import org.jcouchdb.db.ServerImpl;

public class CouchDBDetector extends DaemonDetector {

    private static final String BIND_ADDRESS = "httpd.bind_address";
    private static final String HTTPD_PORT = "httpd.port";
    private static final String PROP_VERSION = "version";
    private static final String OPT_COUCHINI = "-couchini";
    private static final String SERVER_START =
        "couch_server:start(";
    //0.8 -> 0.9
    private static final String[][] ALIASES = {
        { "couch.logfile", "log.file" },
        { "couch.bindaddress", BIND_ADDRESS },
        { "couch.port", HTTPD_PORT }
    };
    private Map<String,String> opts;

    private void setOpt(Map<String,String> opts, String opt, String val) {
        if (opts.containsKey(opt) || //set by process                                                         
            (getTypeProperty(opt) != null)) //set in agent.properties                                         
        {
            return; //dont override user config                                                               
        }
        opts.put(opt, val);
    }

    //set log/config track options
    //XXX super class should take care of this via getProcOpts()
    protected ServerResource newServerResource(long pid, String exe) {
        ServerResource server = super.newServerResource(pid, exe);
        ConfigResponse config = server.getMeasurementConfig();
        String[] options = {
            ConfigFileTrackPlugin.PROP_FILES_SERVER,
            CouchDBLogFileTrackPlugin.PROP_FILES_SERVER
        };

        for (String name : options) {
            String opt = getTypeProperty(name + ".opt");
            if (opt == null) {
                continue;
            }
            String val = this.opts.get(opt);
            if (val != null) {
                config.setValue(name, val);
            }
        }

        server.setMeasurementConfig(config);
        return server;
    }

    private boolean parseIni(String file) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            String line, section = null;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if ((line.length() == 0) || (line.charAt(0) == ';')) {
                    continue;
                }
                if (line.charAt(0) == '[') {
                    section = xtrim(line, ']').toLowerCase();
                }
                String[] elts = line.split("=");
                if (elts.length == 2) {
                    String key = elts[0].toLowerCase().trim();
                    if (!key.startsWith("_")) {
                        opts.put(section + "." + key, elts[1].trim());
                    }
                }
            }
            getLog().debug("Parsed ini: " + file);
            return true;
        } catch (IOException e) {
            getLog().error("Failed to read " + file + e.getMessage(), e);
            return false;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException e) {}
            }
        }
    }

    private String xtrim(String str, char c) {
        str = str.trim();
        int ix = str.lastIndexOf(c);
        if (ix == -1) {
            return str;
        }
        return str.substring(1, ix).trim();
    }

    //0.9 style
    //couch_server:start([ "couchdb/etc/couchdb/default.ini", \
    //                     "couchdb/etc/couchdb/local.ini"]), \
    //                   receive done -> done end.<=
    private void parseOpts(long pid) {
        String cwd = getProcCwd(pid);
        String[] args = getProcArgs(pid);
        String[] files = null;
        for (String arg : args) {
            if (!arg.startsWith(SERVER_START)) {
                continue;
            }
            arg = arg.substring(SERVER_START.length());
            arg = xtrim(arg, ']');
            files = arg.split("[,\\s]+");
            break;
        }
        if (files == null) {
            getLog().warn("Unable to find .ini files for pid=" + pid);
            return;
        }
        if (cwd == null) {
            File root = new File(args[0]).getParentFile(); //beam
            while (root != null) {
                if (new File(root, files[0]).exists()) {
                    cwd = root.getPath();
                    break;
                }
                root = root.getParentFile();
            }
        }
        if (cwd == null) {
            getLog().warn("Unable to find determine cwd for pid=" + pid);
            return;
        }

        setOpt(opts, INSTALLPATH, cwd);
        StringBuilder configs = new StringBuilder();
        for (String file : files) {
            String arg = xtrim(file, '"');
            String ini = cwd + File.separator + arg;
            if (parseIni(ini)) {
                if (configs.length() != 0) {
                    configs.append(',');
                }
                configs.append(arg);
            }
        }
        opts.put(OPT_COUCHINI, configs.toString()); //config_track
    }

    //0.8 style
    private void parseOpts(String ini) {
        parseIni(ini);
        for (int i=0; i<ALIASES.length; i++) {
            String value = opts.remove(ALIASES[i][0]);
            if (value != null) {
                opts.put(ALIASES[i][1], value);
            }
        }
        String home = opts.get("-home");
        if (home != null) {
            setOpt(opts, INSTALLPATH, home);
        }
    }

    protected Map<String,String> getProcOpts(long pid) {
        this.opts = super.getProcOpts(pid);
        String defaultPort = getTypeProperty(Collector.PROP_PORT);

        setOpt(opts, INVENTORY_ID, "couchdb@%port%");

        String pidfile = opts.get("-pidfile");
        if ((pidfile != null) && new File(pidfile).canRead()) {
            opts.put(SigarMeasurementPlugin.PTQL_CONFIG,
                     "Pid.PidFile.eq=" + pidfile);
        }

        String ini = opts.get(OPT_COUCHINI);
        if (ini != null) {
            parseOpts(ini);
        }
        else {
            parseOpts(pid);
        }

        if (NetFlags.ANY_ADDR.equals(opts.get(BIND_ADDRESS))) {
            opts.put(BIND_ADDRESS, NetFlags.LOOPBACK_ADDRESS);
        }
        if (opts.get(HTTPD_PORT) == null) {
            opts.put(HTTPD_PORT, defaultPort);
        }
        //append http port for unique name
        if (!opts.get(HTTPD_PORT).equals(defaultPort)) {
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

        ConfigResponse cprops = new ConfigResponse();
        Map response = server.get("/").getContentAsMap();
        String version = (String)response.get(PROP_VERSION);
        if (version != null) {
            cprops.setValue(PROP_VERSION, version);
        }
        setCustomProperties(cprops);

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
