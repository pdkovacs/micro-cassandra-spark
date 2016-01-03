/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bulkload;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.List;

import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import org.apache.cassandra.config.Config;
import org.apache.cassandra.dht.Murmur3Partitioner;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.io.sstable.CQLSSTableWriter;

/**
 * Usage: java bulkload.DVDsLoad
 */
public class DVDsLoad
{
    public static final String CSV_URL = "file:///Users/pkovacs/Downloads/dvd_csv/dvd_csv.txt";

    /** Default output directory */
    public static final String DEFAULT_OUTPUT_DIR = "./data";

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    /** Keyspace name */
    public static final String KEYSPACE = "mykeyspace";
    /** Table name */
    public static final String TABLE = "dvds";

    /**
     * Schema for bulk loading table.
     * It is important not to forget adding keyspace name before table name,
     * otherwise CQLSSTableWriter throws exception.
     */
    public static final String SCHEMA = String.format("CREATE TABLE %s.%s (" +
														  "title text, " +
														  "studio text, " +
														  "released text, " +
														  "status text, " +
														  "sound text, " +
														  "versions text, " +
														  "price text, " +
														  "rating text, " +
														  "year text, " +
														  "genre text, " +
														  "aspect text, " +
														  "upc_id text, " +
														  "release_date text, " +
														  "id int, " +
														  "timestamp text, " +
														  "PRIMARY KEY (id)" +
    													");", KEYSPACE, TABLE);

    /**
     * INSERT statement to bulk load.
     * It is like prepared statement. You fill in place holder for each data.
     */
    public static final String INSERT_STMT = String.format(
    		"INSERT INTO %s.%s (" +
    				"title, studio, released, status, sound, versions, price, rating, year, genre, "
    				+ "aspect, upc_id, release_date, id, timestamp" +
           ") VALUES (" +
                   "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?" +
           ")", KEYSPACE, TABLE);

    public static void main(String[] args)
    {
        if (args.length == 0)
        {
            System.out.println("usage: java bulkload.DVDsLoad <list of ticker symbols>");
            return;
        }

        // magic!
        Config.setClientMode(true);

        // Create output directory that has keyspace and table name in the path
        File outputDir = new File(DEFAULT_OUTPUT_DIR + File.separator + KEYSPACE + File.separator + TABLE);
        if (!outputDir.exists() && !outputDir.mkdirs())
        {
            throw new RuntimeException("Cannot create output directory: " + outputDir);
        }

        // Prepare SSTable writer
        CQLSSTableWriter.Builder builder = CQLSSTableWriter.builder();
        // set output directory
        builder.inDirectory(outputDir)
               // set target schema
               .forTable(SCHEMA)
               // set CQL statement to put data
               .using(INSERT_STMT)
               // set partitioner if needed
               // default is Murmur3Partitioner so set if you use different one.
               .withPartitioner(new Murmur3Partitioner());
        CQLSSTableWriter writer = builder.build();

        for (String ticker : args)
        {
            URLConnection conn;
            try
            {
                URL url = new URL(String.format(CSV_URL, ticker));
                conn = url.openConnection();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                CsvListReader csvReader = new CsvListReader(reader, CsvPreference.STANDARD_PREFERENCE)
            )
            {
                csvReader.getHeader(true);

                // Write to SSTable while reading data
                List<String> line;
                while ((line = csvReader.read()) != null)
                {
                    // We use Java types here based on
                    // http://www.datastax.com/drivers/java/2.0/com/datastax/driver/core/DataType.Name.html#asJavaClass%28%29
                    writer.addRow(
                    		line.get(0) == null ? null : new String(line.get(0)),
            				line.get(1) == null ? null : new String(line.get(1)),
                            line.get(2) == null ? null : new String(line.get(2)),
                            line.get(3) == null ? null : new String(line.get(3)),
                            line.get(4) == null ? null : new String(line.get(4)),
                            line.get(5) == null ? null : new String(line.get(5)),
                            line.get(6) == null ? null : new String(line.get(6)),
                            line.get(7) == null ? null : new String(line.get(7)),
                            line.get(8) == null ? null : new String(line.get(8)),
                            line.get(9) == null ? null : new String(line.get(9)),
                            line.get(10) == null ? null : new String(line.get(10)),
                            line.get(11) == null ? null : new String(line.get(11)),
                            line.get(12) == null ? "0001-01-01" : new String(line.get(12)),
                            line.get(13) == null ? null : new Integer(line.get(13)),
                            line.get(14) == null ? null : new String(line.get(14))
                        );
                }
            }
            catch (InvalidRequestException | IOException e)
            {
                e.printStackTrace();
            }
        }

        try
        {
            writer.close();
        }
        catch (IOException ignore) {}
    }
}
