package datawave.query.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import org.apache.commons.vfs2.FileChangeEvent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;
import org.apache.log4j.Logger;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.ObjectInputStream;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This singleton is created on the tservers. It provides a map of metadataTableName to maps of auths to the appropriate TypeMetadata loaded by the
 * TypeMetadataHelper and written to vfs. This Monitors the file in vfs so that when the file is updated, the {@code Map<Set<String>>,TypeMetadata>} is
 * refreshed.
 *
 */
public class TypeMetadataProvider implements FileListener {
    
    private static final Logger log = Logger.getLogger(TypeMetadataProvider.class);
    
    private TypeMetadataBridge bridge;
    
    private String[] metadataTableNames;
    
    private final Pattern metadataTableNamePattern = Pattern.compile(".*/(\\w+)/typeMetadata");
    
    private LoadingCache<String,Map<Set<String>,TypeMetadata>> typeMetadataMap = CacheBuilder.newBuilder().build(
                    new CacheLoader<String,Map<Set<String>,TypeMetadata>>() {
                        @Override
                        public Map<Set<String>,TypeMetadata> load(String metadataTableName) {
                            log.debug("loading the cache for " + metadataTableName);
                            return reloadTypeMetadata(metadataTableName);
                        }
                    });
    
    private long delay;
    
    private Map<String,DefaultFileMonitor> monitors = Maps.newHashMap();
    
    private TypeMetadataProvider() {}
    
    public synchronized TypeMetadata getTypeMetadata(String metadataTableName, Set<String> authKey) {
        try {
            return typeMetadataMap.get(metadataTableName).get(authKey);
        } catch (Exception ex) {
            log.warn("could not get TypeMetadata for " + metadataTableName + " and " + authKey, ex);
            return new TypeMetadata();
        }
    }
    
    private Map<Set<String>,TypeMetadata> reloadTypeMetadata(String metadataTableName) {
        Map<Set<String>,TypeMetadata> typeMetadataMap = Maps.newHashMap();
        try {
            log.debug("reloading TypeMetadata");
            ObjectInputStream ois = new ObjectInputStream(this.bridge.getFileObject(metadataTableName).getContent().getInputStream());
            typeMetadataMap = (Map<Set<String>,TypeMetadata>) ois.readObject();
            
            if (log.isTraceEnabled()) {
                log.trace("reloaded TypeMetadataProvider.typeMetadataMap =" + typeMetadataMap);
            }
            ois.close();
        } catch (Exception ex) {
            log.warn("Unable to reload typeMetadata. Current value is " + typeMetadataMap);
        }
        return typeMetadataMap;
    }
    
    public long getDelay() {
        return delay;
    }
    
    public void setDelay(long delay) {
        this.delay = delay;
    }
    
    public TypeMetadataBridge getBridge() {
        return bridge;
    }
    
    public void setBridge(TypeMetadataBridge bridge) {
        this.bridge = bridge;
    }
    
    public String[] getMetadataTableNames() {
        return metadataTableNames;
    }
    
    public void setMetadataTableNames(String[] metadataTableNames) {
        this.metadataTableNames = metadataTableNames;
    }
    
    /**
     * set up the monitor so that when the file system data is changed, our singleton will be refreshed
     */
    public void init() {
        for (String metadataTableName : this.metadataTableNames) {
            DefaultFileMonitor monitor = new DefaultFileMonitor(this);
            try {
                monitor.setDelay(delay);
                monitor.setRecursive(false);
                monitor.addFile(this.bridge.getFileObject(metadataTableName));
                log.debug("monitoring " + this.bridge.getFileObject(metadataTableName));
                monitor.start();
                this.monitors.put(metadataTableName, monitor);
            } catch (Exception ex) {
                monitor.stop();
                throw new RuntimeException("Failed to create TypeMetadataProvider with " + this.bridge.getUri() + this.bridge.getDir() + "/"
                                + this.bridge.getFileName(), ex);
            }
        }
    }
    
    public void forceUpdate() {
        this.typeMetadataMap.invalidateAll();
    }
    
    @Override
    public void fileCreated(FileChangeEvent event) throws FileSystemException {
        String metadataFileName = event.getFile().getName().toString();
        Matcher matcher = this.metadataTableNamePattern.matcher(metadataFileName);
        if (matcher.matches()) {
            String metadataTableName = matcher.group(1);
            typeMetadataMap.refresh(metadataTableName);
            if (log.isDebugEnabled()) {
                long modTime = event.getFile().getContent().getLastModifiedTime();
                log.debug("TypeMetadata file created, modified at: " + modTime);
            }
        }
    }
    
    @Override
    public void fileDeleted(FileChangeEvent event) {
        String metadataFileName = event.getFile().getName().toString();
        Matcher matcher = this.metadataTableNamePattern.matcher(metadataFileName);
        if (matcher.matches()) {
            String metadataTableName = matcher.group(1);
            typeMetadataMap.refresh(metadataTableName);
            log.debug("TypeMetadata file deleted");
        }
    }
    
    @Override
    public void fileChanged(FileChangeEvent event) throws FileSystemException {
        String metadataFileName = event.getFile().getName().toString();
        Matcher matcher = this.metadataTableNamePattern.matcher(metadataFileName);
        if (matcher.matches()) {
            String metadataTableName = matcher.group(1);
            typeMetadataMap.refresh(metadataTableName);
            if (log.isDebugEnabled()) {
                long modTime = event.getFile().getContent().getLastModifiedTime();
                log.debug("TypeMetadata file changed, modified at: " + modTime);
            }
        }
    }
    
    public void close() {
        for (DefaultFileMonitor monitor : this.monitors.values()) {
            monitor.stop();
        }
    }
    
    protected synchronized void update() {
        for (String metadataTableName : this.metadataTableNames) {
            this.reloadTypeMetadata(metadataTableName);
        }
    }
    
    @Override
    public String toString() {
        return "TypeMetadataProvider{" + "bridge=" + bridge + ", delay=" + delay + ", monitors=" + monitors + '}';
    }
    
    /**
     * this Factory for TypeMetadataProvider is designed to be used on the tservers, where there is a vfs-classloader
     */
    public static class Factory {
        public static final Logger log = Logger.getLogger(TypeMetadataProvider.Factory.class);
        
        private static TypeMetadataProvider typeMetadataProvider;
        
        public static synchronized TypeMetadataProvider createTypeMetadataProvider() {
            if (typeMetadataProvider != null)
                return typeMetadataProvider;
            ClassLoader thisClassLoader = TypeMetadataProvider.Factory.class.getClassLoader();
            
            // ignore calls to close as this blows away the cache manager
            ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext();
            try {
                // To prevent failure when this is run on the tservers:
                // The VFS ClassLoader has been created and has been made the current thread's context classloader, but its resource paths are empty at this
                // time.
                // The spring ApplicationContext will prefer the current thread's context classloader, so the spring context would fail to find
                // any classes or context files to load.
                // Instead, set the classloader on the ApplicationContext to be the one that is loading this class.
                // It is a VFSClassLoader that has the accumulo lib/ext jars set as its resources.
                // After setting the classloader, then set the config locations and refresh the context.
                context.setClassLoader(thisClassLoader);
                context.setConfigLocations("classpath:/TypeMetadataBridgeContext.xml", "classpath:/TypeMetadataProviderContext.xml");
                context.refresh();
                typeMetadataProvider = context.getBean("typeMetadataProvider", TypeMetadataProvider.class);
            } catch (Throwable t) {
                // got here because the VFSClassLoader on the tservers does not implement findResources
                // none of the spring wiring will work.
                log.warn("Could not load spring context files. got " + t);
            }
            
            return typeMetadataProvider;
        }
    }
    
}
