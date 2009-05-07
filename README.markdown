## Hyperic HQ CouchDB plugin

This project is a Hyperic HQ plugin for monitoring [CouchDB](http://couchdb.apache.org/)

Source code is available at [github.com/hyperic/hq-couchdb](http://github.com/hyperic/hq-couchdb)

Plugin binary is available at [hudson.hyperic.com/job/hq-couchdb-plugin](http://hudson.hyperic.com/job/hq-couchdb-plugin/)

More info at [HyperForge/CouchDB](http://support.hyperic.com/display/hypcomm/CouchDB)

### Auto-Discovery

The CouchDB server process is auto-discovered and monitoring properties are configured
using the couch .ini files.  The Database service discovery creates an instance for each
database list in *_all_dbs*

### Metrics

Server process metrics are collectd via SIGAR.

The runtime metrics available in 0.9+ are collected via the *_stats* module,
see: <http://wiki.apache.org/couchdb/Runtime_Statistics>

The Database service collects status metrics via */dbname*,
see: <http://wiki.apache.org/couchdb/HTTP_database_API>

### Log File Tracking

Messages are optionally reported from couchdb.log and can be filtered by severity level
and/or regex include/exclude.

### Config File Tracking

Each .ini file can be watched for changes.

### Control Actions

None yet.

### Dependencies

[jcouchdb](http://code.google.com/p/jcouchdb/) - see LICENSES file for details.

### CouchDB version support

Tested on Linux using the RPMs installed via the
[chef ELFF guide](http://wiki.opscode.com/display/chef/Enterprise+Linux+variants+with+rpms),
which is **couchdb.x86_64 0.8.1-4.el5**

Tested on MacOSX using [CouchDBX 0.9.0-R13B](http://janl.github.com/couchdbx/)

### Hyperic HQ version support

Tested with [Hyperic HQ](http://www.hyperic.com/) version 4.1.1

