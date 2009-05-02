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

import java.util.Properties;

import org.hyperic.hq.product.Collector;
import org.hyperic.hq.product.PluginException;
import org.jcouchdb.db.Database;
import org.jcouchdb.db.DatabaseStatus;

public class CouchDatabaseCollector extends Collector {

    static final String PROP_DBNAME = "name";

    private String hostname;
    private int port;
    private String dbname;

    protected void init() throws PluginException {
        Properties props = getProperties();
        this.hostname = props.getProperty(PROP_HOSTNAME);
        this.port = Integer.valueOf(props.getProperty(PROP_PORT));
        this.dbname = props.getProperty(PROP_DBNAME);
        setSource(this.dbname);
        super.init();
    }


    public void collect() {
        Database db =
            new Database(this.hostname, this.port, this.dbname);
        DatabaseStatus status = db.getStatus();
        setAvailability(true);
        setValue("DocumentCount", status.getDocumentCount());
        setValue("DeletedDocumentCount", status.getDeletedDocumentCount());
        setValue("DiskSize", status.getDiskSize());
        setValue("UpdateSequence", status.getUpdateSequence());
    }
}
