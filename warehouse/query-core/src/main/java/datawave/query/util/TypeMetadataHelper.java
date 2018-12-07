package datawave.query.util;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import datawave.data.ColumnFamilyConstants;
import datawave.security.util.AuthorizationsUtil;
import datawave.security.util.ScannerHelper;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.Instance;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.hadoop.io.Text;
import org.apache.log4j.Logger;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 */
@Configuration
@EnableCaching
@Component("typeMetadataHelper")
public class TypeMetadataHelper {
    private static final Logger log = Logger.getLogger(TypeMetadataHelper.class);
    
    public static final String NULL_BYTE = "\0";
    
    protected final List<Text> metadataTypeColfs = Arrays.asList(ColumnFamilyConstants.COLF_T);
    
    protected Connector connector;
    protected Instance instance;
    protected String metadataTableName;
    protected Set<Authorizations> auths;
    protected String typeMetadataFileName;
    protected boolean useTypeSubstitution = false;
    protected Map<String,String> typeSubstitutions = Maps.newHashMap();
    protected Set<Authorizations> allMetadataAuths = Collections.emptySet();
    
    public TypeMetadataHelper initialize(Connector connector, String metadataTableName, Set<Authorizations> auths) {
        return this.initialize(connector, connector.getInstance(), metadataTableName, auths, false);
    }
    
    public TypeMetadataHelper initialize(Connector connector, String metadataTableName, Set<Authorizations> auths, boolean useTypeSubstitution) {
        return this.initialize(connector, connector.getInstance(), metadataTableName, auths, useTypeSubstitution);
    }
    
    /**
     * Initializes the instance with a provided update interval.
     * 
     * @param connector
     *            A Connector to Accumulo
     * @param metadataTableName
     *            The name of the DatawaveMetadata table
     * @param auths
     *            Any {@link Authorizations} to use
     */
    public TypeMetadataHelper initialize(Connector connector, Instance instance, String metadataTableName, Set<Authorizations> auths,
                    boolean useTypeSubstitution) {
        this.connector = connector;
        this.instance = instance;
        this.metadataTableName = metadataTableName;
        this.auths = auths;
        this.useTypeSubstitution = useTypeSubstitution;
        
        if (log.isTraceEnabled()) {
            log.trace("Constructor  connector: " + connector.getClass().getCanonicalName() + " with auths: " + auths + " and metadata table name: "
                            + metadataTableName);
        }
        return this;
    }
    
    public void setTypeSubstitutions(Map<String,String> typeSubstitutions) {
        this.typeSubstitutions = typeSubstitutions;
    }
    
    public void setAllMetadataAuths(Set<Authorizations> allMetadataAuths) {
        this.allMetadataAuths = allMetadataAuths;
    }
    
    public Set<Authorizations> getAuths() {
        return auths;
    }
    
    public String getMetadataTableName() {
        return metadataTableName;
    }
    
    public String getTypeMetadataFileName() {
        return typeMetadataFileName;
    }
    
    public void setTypeMetadataFileName(String typeMetadataFileName) {
        this.typeMetadataFileName = typeMetadataFileName;
    }
    
    @Cacheable(value = "getTypeMetadata", key = "{#root.target.auths,#root.target.metadataTableName}", cacheManager = "metadataHelperCacheManager")
    public TypeMetadata getTypeMetadata() throws TableNotFoundException {
        if (log.isDebugEnabled())
            log.debug("cache fault for getTypeMetadata(" + this.auths + "," + this.metadataTableName + ")");
        return this.getTypeMetadata(null);
    }
    
    @Cacheable(value = "getTypeMetadata", key = "{#root.target.auths,#root.target.metadataTableName,#datatypeFilter}",
                    cacheManager = "metadataHelperCacheManager")
    public TypeMetadata getTypeMetadata(Set<String> datatypeFilter) throws TableNotFoundException {
        if (log.isDebugEnabled())
            log.debug("cache fault for getTypeMetadata(" + this.auths + "," + this.metadataTableName + "," + datatypeFilter + ")");
        return this.getTypeMetadata(this.auths, this.metadataTableName, datatypeFilter);
        
    }
    
    public Map<Set<String>,TypeMetadata> getTypeMetadataMap(Collection<Authorizations> allAuths) throws TableNotFoundException {
        Collection<Set<String>> powerset = getAllMetadataAuthsPowerSet(allAuths);
        if (log.isTraceEnabled()) {
            log.trace("powerset:" + powerset);
            for (Set<String> s : powerset) {
                log.trace("powerset has :" + s);
            }
        }
        Map<Set<String>,TypeMetadata> map = Maps.newHashMap();
        
        for (Set<String> a : powerset) {
            if (log.isTraceEnabled())
                log.trace("get TypeMetadata with auths:" + a);
            
            Authorizations at = new Authorizations(a.toArray(new String[a.size()]));
            
            if (log.isTraceEnabled())
                log.trace("made an Authorizations:" + at);
            TypeMetadata tm = this.getTypeMetadataForAuths(Collections.singleton(at));
            map.put(a, tm);
        }
        return map;
    }
    
    private Set<Set<String>> getAllMetadataAuthsPowerSet(Collection<Authorizations> allMetadataAuthsCollection) {
        
        // first, minimize the usersAuths:
        Collection<Authorizations> minimizedCollection = AuthorizationsUtil.minimize(allMetadataAuthsCollection);
        // now, the first entry in the minimized auths should have everything common to every Authorizations in the set
        // make sure that the first entry contains all the Authorizations in the allMetadataAuths
        Authorizations minimized = minimizedCollection.iterator().next(); // get the first one, which has all auths common to all in the original collection
        Set<String> minimizedUserAuths = Sets.newHashSet(MetadataHelper.getAuthsAsStringCollection(minimized));
        if (log.isTraceEnabled())
            log.trace("minimizedUserAuths:" + minimizedUserAuths + " with size " + minimizedUserAuths.size());
        Set<Set<String>> powerset = Sets.powerSet(minimizedUserAuths);
        Set<Set<String>> set = Sets.newHashSet();
        for (Set<String> sub : powerset) {
            Set<String> serializableSet = Sets.newHashSet(sub);
            set.add(serializableSet);
        }
        return set;
    }
    
    public TypeMetadata getTypeMetadataForAuths(Set<Authorizations> authSet) throws TableNotFoundException {
        if (log.isTraceEnabled())
            log.trace("getTypeMetadataForAuths(" + authSet + ")");
        return this.getTypeMetadata(authSet, this.metadataTableName, null);
    }
    
    private TypeMetadata getTypeMetadata(Set<Authorizations> auths, String metadataTableName, Set<String> datatypeFilter) throws TableNotFoundException {
        TypeMetadata typeMetadata = new TypeMetadata();
        
        // Scanner to the provided metadata table
        if (log.isTraceEnabled()) {
            log.trace("connector:" + connector + ", metadataTableName:" + metadataTableName + ", auths:" + auths);
            Collection<Authorizations> got = AuthorizationsUtil.minimize(auths);
            log.trace("got:" + got + " and it is a " + (got == null ? null : got.getClass()));
        }
        
        Scanner bs = ScannerHelper.createScanner(connector, metadataTableName, auths);
        
        Range range = new Range();
        bs.setRange(range);
        
        // Fetch all the column
        for (Text colf : metadataTypeColfs) {
            bs.fetchColumnFamily(colf);
        }
        
        for (Entry<Key,Value> entry : bs) {
            // Get the column qualifier from the key. It contains the datatype
            // and normalizer class
            if (null != entry.getKey().getColumnQualifier()) {
                String colq = entry.getKey().getColumnQualifier().toString();
                int idx = colq.indexOf(NULL_BYTE);
                
                if (idx != -1) {
                    String field = entry.getKey().getRow().toString();
                    String type = colq.substring(0, idx);
                    String className = null;
                    if (datatypeFilter == null || datatypeFilter.isEmpty()) { // no filtering, getem all
                        className = colq.substring(idx + 1);
                    } else if (datatypeFilter.contains(type)) {
                        className = colq.substring(idx + 1);
                    }
                    if (className != null) {
                        if (this.useTypeSubstitution && this.typeSubstitutions.containsKey(className)) {
                            className = this.typeSubstitutions.get(className);
                        }
                        typeMetadata.put(field, type, className);
                    }
                    
                } else {
                    log.warn("EventMetadata entry did not contain a null byte in the column qualifier: " + entry.getKey());
                }
            } else {
                log.warn("ColumnQualifier null in EventMetadata for key: " + entry.getKey());
            }
        }
        
        bs.close();
        
        return typeMetadata;
    }
    
    /**
     * Invalidates all elements in all internal caches this should also write the new typemetadata to hdfs so that the tservers will get the latest.
     */
    @CacheEvict(value = {"getTypeMetadata"}, allEntries = true, cacheManager = "metadataHelperCacheManager")
    public void evictCaches() {
        log.debug("evictCaches");
    }
}
