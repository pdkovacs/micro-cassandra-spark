select toDate(now()) from system.local;

DROP KEYSPACE mykeyspace;

CREATE KEYSPACE mykeyspace
  WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };

use mykeyspace;

DROP TABLE dvds;

CREATE TABLE dvds (
  title text,
  studio text,
  released text,
  status text,
  sound text,
  versions text,
  price text,
  rating text,
  year text,
  genre text,
  aspect text,
  upc_id text,
  release_date text,
  id int,
  timestamp text,
  PRIMARY KEY (id)
);

-- COPY dvds (title, studio, released, status, sound, versions, price, rating, year, genre, aspect, upc_id, release_date, id, timestamp) FROM '/Users/pkovacs/Downloads/dvd_csv/dvd_csv.txt';

select toDate(now()) from system.local;