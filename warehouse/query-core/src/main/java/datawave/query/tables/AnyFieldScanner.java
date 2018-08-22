package datawave.query.tables;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executor;

import datawave.webservice.query.Query;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.hadoop.io.Text;
import org.apache.log4j.Logger;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Purpose: Extends Scanner session so that we can modify how we build our subsequent ranges. Breaking this out cleans up the code. May require implementation
 * specific details if you are using custom iterators, as we are reinitializing a seek
 * 
 * Design: Extends Scanner session and only overrides the buildNextRange.
 * 
 * 
 */
public class AnyFieldScanner extends ScannerSession {
    
    private static final Logger log = Logger.getLogger(AnyFieldScanner.class);
    
    /**
     * @param tableName
     * @param auths
     * @param delegator
     * @param maxResults
     */
    public AnyFieldScanner(String tableName, Set<Authorizations> auths, ResourceQueue delegator, int maxResults, Query settings, SessionOptions options,
                    Collection<Range> ranges) {
        super(tableName, auths, delegator, maxResults, settings, options, ranges);
    }
    
    public AnyFieldScanner(ScannerSession other) {
        this(other.tableName, other.auths, other.sessionDelegator, other.maxResults, other.settings, other.options, other.ranges);
    }
    
    /**
     * Override this for your specific implementation.
     * 
     * In this specific implementation our row key will be the term, the column family will be the field name, and the column family will be the shard,so we
     * should have the following as our last key
     * 
     * bar FOO:20130101_0
     * 
     * so we should append a null so that we we don't skip shards. similarly, an assumption is made of the key structure within this class.
     * 
     * @param lastKey
     * @param previousRange
     */
    public Range buildNextRange(final Key lastKey, final Range previousRange) {
        
        /**
         * This will re-seek the next column family when performing any field expansion.
         */
        Range r = new Range(new Key(lastKey.getRow(), new Text(lastKey.getColumnFamily() + "\u0000\uffff")), true, previousRange.getEndKey(),
                        previousRange.isEndKeyInclusive());
        if (log.isTraceEnabled())
            log.trace(r);
        return r;
        
    }
    
    /**
     * Do not set uncaught exception handler
     * 
     */
    @Override
    protected Executor executor() {
        return command -> {
            String name = serviceName();
            Preconditions.checkNotNull(name);
            Preconditions.checkNotNull(command);
            Thread result = MoreExecutors.platformThreadFactory().newThread(command);
            try {
                result.setName(name);
            } catch (SecurityException e) {
                // OK if we can't set the name in this environment.
            }
            result.start();
        };
    }
    
}
