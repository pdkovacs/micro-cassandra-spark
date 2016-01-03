# micro-cassandra-spark
Testing spark with cassandra in a micro cluster consisting of a limited number of Raspberry Pi 2 nodes

# Configure the clusters

## Confiugre the nodes

### Clone system disk
Dump the “master” disk image into a file:
    
    sudo dd if=/dev/disk1 of=${HOME}/tmp/linaro.img bs=1073741824

Insert the disk to copy the master image to and unmount it so that it can be accessed as /dev/rdisk*:

    sudo diskutil umountDisk /dev/disk1

Copy the master image to the new disk:

    sudo dd of=/dev/rdisk1 if=${HOME}/tmp/linaro.img bs=1048576

Note that

1. rdisk is used (instead of disk) to make the writes faster
2. with a buffer size (bs) too high [such as with 1024^3], dd will time out;

### Manage swapping

Either allow cassandra to lock the heap

    sudo su -
    awk '/# End of file/ {
        print "linaro   hard   memlock           unlimited";
        print "linaro   soft   memlock           unlimited";
        print "";
    }1' /etc/security/limits.conf > new.limits.conf && \
    mv new.limits.conf /etc/security/limits.conf

or (what we shouldn’t but may do with significant physical memory constraints) allow swapping:

    $ sudo dd if=/dev/zero of=/swapfile bs=1024k count=1024
    $ sudo chmod 600 /swapfile
    $ sudo mkswap /swapfile
    $ sudo swapon /swapfile


### Configure Cassandra

1. All nodes use MAX_HEAP_SIZE=200M (in conf/cassandra-env.sh)

2. All nodes except for the first node (with the IP address 192.168.1.201) use 192.168.1.201,192.168.1.202 as the seeds list.

3. Optionally disable vnodes and set initial tokens:

        cassandra_home=apache-cassandra-3.0.2;

        create_send_script() {
            cat > t.sh <<EOF
            awk '
                /^num_tokens:/ {
                    print "num_tokens: 1";
                    next;
                }
                /^# initial_token:/ {
                    print "initial_token: ${t}";
                    next;
                }1
            ' $cassandra_home/conf/cassandra.yaml > temp.cassandra.yaml
            mv temp.cassandra.yaml $cassandra_home/conf/cassandra.yaml
        EOF
            scp t.sh linaro@${ip}:
        }

        i=1
        for t in -9223372036854775808 -6148914691236517206 -3074457345618258604 \
                 -2 3074457345618258600 6148914691236517202;
        do
            ip=192.168.1.20${i};

            create_send_script
            ssh linaro@${ip} bash t.sh

            i=$((i+1));
        done

#### CQL-Shell

1. select count(*) may take a long time -> set [connection]/client_timeout in ~/.cassandra/.cqlshrc
1. put all settings which are strictly necessary in ~/.cassandra/.cqlshrc into comment

### Setup Spark

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

To start all six cassandra nodes:

    cassandra_home=apache-cassandra-3.0.2;
    for i in $(seq 1 6);
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
