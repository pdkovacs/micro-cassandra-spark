# micro-cassandra-spark
Testing spark with cassandra in a micro cluster consisting of a limited number of Raspberry Pi 2 nodes

# Configure the clusters
https://docs.google.com/document/d/1jbVum0ok8ANQ8KM5TdPPBNEMPzuC8iXfF-dVhc0fr6s/edit?usp=sharing

# Test the clusters

## Import the UPC DVD database

1. Download the database from http://www.hometheaterinfo.com/dvdlist.htm.

2. Build the SSTables

        gradle run

3. Load the SSTables

        sstableloader -d 192.168.1.123 $PWD/data/mykeyspace/dvds


