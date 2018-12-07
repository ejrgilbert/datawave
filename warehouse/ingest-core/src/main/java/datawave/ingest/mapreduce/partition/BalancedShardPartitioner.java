package datawave.ingest.mapreduce.partition;

import com.google.common.collect.Maps;
import datawave.ingest.mapreduce.handler.shard.ShardIdFactory;
import datawave.ingest.mapreduce.job.BulkIngestKey;
import datawave.ingest.mapreduce.job.ShardedTableMapFile;
import datawave.util.time.DateHelper;
import org.apache.accumulo.core.data.Value;
import org.apache.commons.lang.time.DateUtils;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * The BalancedShardPartitioner takes advantage of the way that shards are balanced. See ShardedTableTabletBalancer. * The partitioner is designed to have no
 * collisions for today and for previous days, the number of days depends on the cluster size and its balance (see below). Moving beyond that threshold,
 * collisions will start to occur. * For the shard tables, the previously used partitioner was TabletLocationHashPartitioner. It guaranteed that shards hosted
 * on the same tablet server would go to the same reducer. This partitioner also guarantees that. The hash approach didn't take into account the balanced nature
 * of each day's shards and the hashCode function caused collisions for a given day's shards. * The number of days without collisions = floor(min(r, ts) / x)
 * 'ts' = number of tablet servers, 'r' = number of partitioners 'x' = number of shards in a given day * Depends on the ShardedTableMapFile for getting splits
 * and for identifying the tables it might see (ShardedTableMapFile.CONFIGURED_SHARDED_TABLE_NAMES), also assumes that the num of shard ids property is set.
 */
public class BalancedShardPartitioner extends Partitioner<BulkIngestKey,Value> implements Configurable, DelegatePartitioner {
    private static final Logger log = Logger.getLogger(BalancedShardPartitioner.class);
    private static final long now = System.currentTimeMillis();
    private static final String today = formatDay(0);
    private Configuration conf;
    private Map<String,Map<Text,Integer>> shardPartitionsByTable;
    private Map<String,TreeMap<Text,String>> shardIdToLocations = Maps.newHashMap();
    private Map<Text,Integer> offsetsFactorByTable;
    int missingShardIdCount = 0;
    
    public static final String MISSING_SHARD_STRATEGY_PROP = "datawave.ingest.mapreduce.partition.BalancedShardPartitioner.missing.shard.strategy";
    
    private ShardIdFactory shardIdFactory = null;
    
    @Override
    public synchronized int getPartition(BulkIngestKey key, Value value, int numReduceTasks) {
        try {
            // partition will be balanced for a given day, more so for recent days
            int partition = getAssignedPartition(key.getTableName().toString(), key.getKey().getRow());
            
            // the offsets should help send today's shard data to a different set of reducers than today's error shard data
            int offsetForTable = shardIdFactory.getNumShards(key.getKey().getTimestamp()) * offsetsFactorByTable.get(key.getTableName());
            
            return (partition + offsetForTable) % numReduceTasks;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     */
    private int getAssignedPartition(String tableName, Text shardId) throws IOException {
        Map<Text,Integer> assignments = lazilyCreateAssignments(tableName);
        
        Integer partitionId = assignments.get(shardId);
        if (partitionId != null) {
            return partitionId;
        }
        // if the partitionId is not there, either shards were not created for the day
        // or not all shards were created for the day
        
        String missingShardStrategy = conf.get(MISSING_SHARD_STRATEGY_PROP, "hash");
        switch (missingShardStrategy) {
            case "hash":
                // only warn a few times per partitioner to avoid flooding the logs
                if (missingShardIdCount < 10) {
                    log.warn("shardId didn't have a partition assigned to it: " + shardId);
                    missingShardIdCount++;
                }
                return (shardId.hashCode() & Integer.MAX_VALUE);
            case "collapse":
                ArrayList<Text> keys = new ArrayList<>(assignments.keySet());
                Collections.sort(keys);
                int closestAssignment = Collections.binarySearch(keys, shardId);
                if (closestAssignment >= 0) {
                    // Should have found it earlier, but just in case go ahead and return it
                    log.warn("Something is screwy, found " + shardId + " on the second try");
                    return assignments.get(shardId);
                }
                // <tt>(-(<i>insertion point</i>) - 1)</tt> // insertion point in the index of the key greater
                Text shardString = keys.get(Math.abs(closestAssignment + 1));
                return assignments.get(shardString);
            default:
                throw new RuntimeException("Unsupported missing shard strategy " + MISSING_SHARD_STRATEGY_PROP + "=" + missingShardStrategy);
        }
    }
    
    /**
     * For a given tablename, provides the mapping from {@code shard id -> partition}
     */
    private Map<Text,Integer> lazilyCreateAssignments(String tableName) throws IOException {
        if (this.shardPartitionsByTable == null) {
            this.shardPartitionsByTable = new HashMap<>();
        }
        if (null == this.shardPartitionsByTable.get(tableName)) {
            this.shardPartitionsByTable.put(tableName, getPartitionsByShardId(tableName));
        }
        return this.shardPartitionsByTable.get(tableName);
    }
    
    /**
     * Loads the splits file for the table name and uses it to assign partitions.
     */
    private HashMap<Text,Integer> getPartitionsByShardId(String tableName) throws IOException {
        if (log.isDebugEnabled())
            log.debug("Loading splits data for " + tableName);
        
        TreeMap<Text,String> shardIdToLocation = shardIdToLocations.get(tableName);
        if (null == shardIdToLocation) {
            shardIdToLocation = ShardedTableMapFile.getShardIdToLocations(conf, tableName);
            shardIdToLocations.put(tableName, shardIdToLocation);
        }
        if (log.isDebugEnabled())
            log.debug("Assigning partitioners for each shard in " + tableName);
        return assignPartitionsForEachShard(shardIdToLocation);
    }
    
    /**
     * 1. sorts the the tablet assignments by shard id, starting with the most recent going backwards<br>
     * 2. assigns partitions to each tservers, starting from the beginning, but skipping future dates<br>
     * 3. assigns partitions to each shardid by looking up its tserver's assignments<br>
     * 4. returns the {@code shard id -> partition} map
     * <p>
     * e.g.,<br>
     * 1. sorted assignments<br>
     * 2. tserver map<br>
     * 3. shard map: {@code future->tserver7 *no change* future->2 shard4->tserver2 tserver2->0 shard4->0
     * shard3->tserver3 tserver3->1 shard3->1 shard2->tserver2 *no change* shard2->0 shard1->tserver7 tserver7->2 shard1->2}
     *
     * @param shardIdToLocations
     * @return shardId to
     */
    private HashMap<Text,Integer> assignPartitionsForEachShard(TreeMap<Text,String> shardIdToLocations) {
        int totalNumUniqueTServers = calculateNumberOfUniqueTservers(shardIdToLocations);
        
        TreeMap<Text,String> sortedShardIdsToTservers = reverseSortByShardIds(shardIdToLocations);
        HashMap<String,Integer> partitionsByTServer = getTServerAssignments(totalNumUniqueTServers, sortedShardIdsToTservers);
        HashMap<Text,Integer> partitionsByShardId = getShardIdAssignments(sortedShardIdsToTservers, partitionsByTServer);
        
        if (log.isDebugEnabled())
            log.debug("Number of shardIds assigned: " + partitionsByShardId.size());
        
        return partitionsByShardId;
    }
    
    private int calculateNumberOfUniqueTservers(TreeMap<Text,String> shardIdToLocations) {
        int totalNumUniqueTServers = new HashSet(shardIdToLocations.values()).size();
        if (log.isDebugEnabled())
            log.debug("Total TServers involved: " + totalNumUniqueTServers);
        return totalNumUniqueTServers;
    }
    
    private TreeMap<Text,String> reverseSortByShardIds(TreeMap<Text,String> shardIdToLocations) {
        // drop the dates after today's date
        TreeMap<Text,String> shardIdsToTservers = Maps.newTreeMap((o1, o2) -> o2.compareTo(o1));
        shardIdsToTservers.putAll(shardIdToLocations);
        return shardIdsToTservers;
    }
    
    private HashMap<String,Integer> getTServerAssignments(int totalNumTServers, TreeMap<Text,String> shardIdsToTservers) {
        HashMap<String,Integer> partitionsByTServer = new HashMap<>(totalNumTServers);
        int nextAvailableSlot = 0;
        boolean alreadySkippedFutureShards = false;
        for (Map.Entry<Text,String> entry : shardIdsToTservers.entrySet()) {
            if (alreadySkippedFutureShards || !isFutureShard(entry.getKey())) { // short circuiting for performance
                alreadySkippedFutureShards = true;
                Integer assignedPartition = partitionsByTServer.get(entry.getValue());
                if (null == assignedPartition) {
                    assignedPartition = nextAvailableSlot;
                    partitionsByTServer.put(entry.getValue(), assignedPartition);
                    nextAvailableSlot++;
                }
                if (partitionsByTServer.size() == totalNumTServers) {
                    // all the tservers have been assigned partitions, so we can stop
                    return partitionsByTServer;
                }
            }
        }
        return partitionsByTServer;
    }
    
    private static boolean isFutureShard(Text shardId) {
        String shardIdStr = shardId.toString().intern();
        if (shardIdStr.length() < 8) {
            return true;
        }
        return shardIdStr.substring(0, 8).compareTo(today) > 0;
    }
    
    private static String formatDay(int numDaysBack) {
        return DateHelper.format(now - (DateUtils.MILLIS_PER_DAY * numDaysBack));
    }
    
    private HashMap<Text,Integer> getShardIdAssignments(TreeMap<Text,String> shardIdsToTservers, HashMap<String,Integer> partitionsByTServer) {
        HashMap<Text,Integer> partitionsByShardId = new HashMap<>();
        for (Map.Entry<Text,String> entry : shardIdsToTservers.entrySet()) {
            partitionsByShardId.put(entry.getKey(), partitionsByTServer.get(entry.getValue()));
        }
        return partitionsByShardId;
    }
    
    @Override
    public void configureWithPrefix(String prefix) {/* no op */}
    
    @Override
    public int getNumPartitions() {
        return Integer.MAX_VALUE;
    }
    
    @Override
    public void initializeJob(Job job) {}
    
    @Override
    public Configuration getConf() {
        return conf;
    }
    
    @Override
    public void setConf(Configuration conf) {
        this.conf = conf;
        shardIdFactory = new ShardIdFactory(conf);
        defineOffsetsForTables(conf);
    }
    
    private void defineOffsetsForTables(Configuration conf) {
        offsetsFactorByTable = new HashMap<>();
        int offsetFactor = 0;
        for (String tableName : conf.getStrings(ShardedTableMapFile.CONFIGURED_SHARDED_TABLE_NAMES)) {
            offsetsFactorByTable.put(new Text(tableName), offsetFactor++);
        }
    }
}
