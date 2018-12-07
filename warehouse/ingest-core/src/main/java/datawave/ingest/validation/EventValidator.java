package datawave.ingest.validation;

import java.util.Collection;
import java.util.Map;

import datawave.ingest.data.RawRecordContainer;
import datawave.ingest.data.Type;

import org.apache.hadoop.conf.Configuration;

public interface EventValidator {
    /**
     * Setup this event validator for the type specified
     * 
     * @param type
     * @param conf
     */
    void setup(Type type, Configuration conf);
    
    /**
     * Is this a fieldname that is to be validated. This method can be used to gather the fields for the final validation
     * 
     * @param fieldname
     * @return true if required for validation, false otherwise
     */
    boolean validated(String fieldname);
    
    /**
     * Validate the event against a set of metadata
     * 
     * @param event
     * @param parameters
     */
    void validate(RawRecordContainer event, Map<String,Collection<Object>> parameters);
    
}
