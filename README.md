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

or (what we shouldn’t but may do under significant physical memory constraints) allow swapping:

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

### Configure Spark

#### Set environment variables

    create_send_script() {
        cat > t.sh <<EOF
        awk '
            /^# - SPARK_WORKER_CORES/ {
                print "SPARK_WORKER_CORES=2";
            }
            /^# - SPARK_WORKER_MEMORY:/ {
                print "SPARK_WORKER_MEMORY=400m";
            }1
        ' spark/conf/spark-env.sh > temp.spark-env.sh
        mv temp.spark-env.sh spark/conf/spark-env.sh
    EOF
        scp t.sh linaro@${ip}:
    }

    i=1
    for i in $(seq 1 6);
    do
        ip=192.168.1.20${i};

        create_send_script
        ssh linaro@${ip} bash t.sh

        i=$((i+1));
    done


# Build the Cassandra connector

## Prepare the build

### Make sure sbt is installed

http://www.scala-sbt.org/download.html

### Clone and checkout the connector code from Github

    $ git clone https://github.com/datastax/spark-cassandra-connector
    $ cd spark-cassandra-connector
    $ git checkout b1.5

### Add missing entries to the SBT-assemblyMergeStrategy

    [pkovacs@gokyuzu spark-cassandra-connector]$ git diff
    diff --git a/project/Settings.scala b/project/Settings.scala
    index 18b094b..87fac88 100644
    --- a/project/Settings.scala
    +++ b/project/Settings.scala
    @@ -331,8 +331,12 @@ object Settings extends Build {
         assemblyMergeStrategy in assembly <<= (assemblyMergeStrategy in assembly) {
           (old) => {
             case PathList("META-INF", "io.netty.versions.properties", xs @ _*) => MergeStrategy.last
    -        case PathList("com", "google", xs @ _*) => MergeStrategy.last
    -        case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
    +        case PathList("com", xs @ _*) => MergeStrategy.last
    +        case PathList("io", xs @ _*) => MergeStrategy.last
    +        case PathList("javax", xs @ _*) => MergeStrategy.first
    +        case PathList("org", xs @ _*) => MergeStrategy.last
    +        case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
    +        case PathList("META-INF", xs @ _*) => MergeStrategy.last
             case x => old(x)
           }
         }
    [pkovacs@gokyuzu spark-cassandra-connector]$ 

## Execute the build

    $ sbt assembly

## Create convenience shorthand for assembled connector jar

    $ export SPARK_CASSANDRA_CONNECTOR_ASSEMBLY=/Users/pkovacs/kittenpark/git/spark-cassandra-connector/spark-cassandra-connector-java/target/scala-2.10/spark-cassandra-connector-java-assembly-1.5.0-M4-SNAPSHOT.jar

## Fix manifest files

    $ zip -d $SPARK_CASSANDRA_CONNECTOR_ASSEMBLY META-INF/*.RSA META-INF/*.DSA META-INF/*.SF

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

## Start Spark

### Start stand-alone resource manager

    ./sbin/start-master.sh

### Start the nodes

    for i in $(seq 1 6);
    do
        ip=192.168.1.20${i};
        echo $ip;
        ssh linaro@$ip ". /home/linaro/.profile; export SPARK_DAEMON_MEMORY=400m; spark/sbin/start-slave.sh spark://gokyuzu:7077";
    done

## Working with the Spark-shell

### Work around a Spark-shell issue

http://stackoverflow.com/a/34264232

    [pkovacs@gokyuzu spark-1.5.2-bin-hadoop2.6]$ grep cassandra conf/spark-defaults.conf
    spark.cassandra.connection.host    192.168.1.201
    [pkovacs@gokyuzu spark-1.5.2-bin-hadoop2.6]$ 

### Start Spark-shell

    ./bin/spark-shell \
        --executor-memory 400M \
        --jars $SPARK_CASSANDRA_CONNECTOR_ASSEMBLY \
        --master spark://gokyuzu:7077

### Execute in Spark-shell

    import com.datastax.spark.connector._, org.apache.spark.SparkContext, org.apache.spark.SparkContext._, org.apache.spark.SparkConf
    val rdd = sc.cassandraTable("mykeyspace", "dvds");
    val pirateMovies = rdd.filter(_.getString("title").contains("Pirate")).cache();
    println(pirateMovies.count);
    val knownYearPirateMovies = pirateMovies.filter(_.getString("year").forall(Character.isDigit(_)));
    println(knownYearPirateMovies.count);
    val mostRecentPirateMovie = knownYearPirateMovies.max()(new Ordering[com.datastax.spark.connector.CassandraRow]() {
        override def compare(x: com.datastax.spark.connector.CassandraRow, y: com.datastax.spark.connector.CassandraRow): Int = 
        Ordering[Int].compare(Integer.parseInt(x.getString("year")), Integer.parseInt(y.getString("year")))
    });
    println(mostRecentPirateMovie);
})
