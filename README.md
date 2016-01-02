# micro-cassandra-spark
Testing spark with cassandra in a micro cluster consisting of a limited number of Raspberry Pi 2 nodes

# Configure the clusters
https://docs.google.com/document/d/1jbVum0ok8ANQ8KM5TdPPBNEMPzuC8iXfF-dVhc0fr6s/edit?usp=sharing

# Test the clusters

## Quick check of nodes' availability

    for i in $(seq 1 6);
    do
        ip=192.168.1.20${i};
        if ping -c 1 -t 2 $ip >/dev/null 2>&1;
        then
            echo "$ip OK";
        else
            echo "$ip doesn't respond";
        fi;
    done

## Start cassandra on nodes

To start cassandra on the first four node:

    cassandra_home=apache-cassandra-3.0.2;
    for i in $(seq 1 4);
    do
        ip=192.168.1.20${i};
        echo $ip;
        ssh linaro@$ip ". /home/linaro/.profile; $cassandra_home/bin/cassandra";
    done

## Stop cassandra on all 6 nodes

    for i in $(seq 1 6);
    do
        ip=192.168.1.20${i};
        ssh linaro@${ip} \
            "pid=\$(ps -ef | grep '[j]ava.*org.apache.cassandra.service.CassandraDaemon' | awk '{print \$2;}'); "\
            "echo \$pid; "\
            "if test -n \"\$pid\"; then kill \$pid; fi";
    done

## Import the UPC DVD database

1. Download the database from http://www.hometheaterinfo.com/dvdlist.htm.

2. Build the SSTables

        gradle run

3. Load the SSTables

        sstableloader -d 192.168.1.123 $PWD/data/mykeyspace/dvds

## Test with Cassandra


### select count(*) from dvds

    $ for n in $(seq 1 3); do time ./bin/cqlsh -e 'use mykeyspace; select count(*) from dvds' 192.168.1.123; done

#### Single node

    real    2m20.671s
    real    2m18.641s
    real    2m19.670s

#### Two nodes

    real    1m41.223s
    real    1m39.203s
    real    1m37.644s

#### Three nodes

    real    2m42.679s
    real    2m42.278s
    real    2m43.151s
