package datawave.query.testframework;

import org.apache.hadoop.conf.Configuration;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Collection;

/**
 * Defines the methods for retrieval of Hadoop configuration information for a data type.
 *
 * @see org.apache.hadoop.conf.Configuration
 */
public interface DataTypeHadoopConfig {
    
    String DATE_FIELD_FORMAT = "yyyyMMdd";
    SimpleDateFormat YMD_DateFormat = new SimpleDateFormat(DATE_FIELD_FORMAT);
    
    /**
     * Data type string representation.
     * 
     * @return string representation of datatype
     */
    String dataType();
    
    /**
     * List of Hadoop data header fields for the data type.
     *
     * @return list of fields
     */
    URI getIngestFile();
    
    /**
     * Retrieves the current Hadoop configuration for the datatype.
     * 
     * @return populated configuration
     */
    Configuration getHadoopConfiguration();
    
    /**
     * Retrieves a list of shard ids for the test data.
     *
     * @return list of shard ids included in the raw data
     */
    Collection<String> getShardIds();
}
