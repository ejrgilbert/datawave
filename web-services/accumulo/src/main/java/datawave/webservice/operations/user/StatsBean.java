package datawave.webservice.operations.user;

import datawave.webservice.common.connection.AccumuloConnectionFactory;
import datawave.webservice.exception.AccumuloWebApplicationException;
import datawave.webservice.response.StatsProperties;
import datawave.webservice.response.StatsResponse;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.ZooKeeperInstance;
import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.UnmarshallerHandler;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.Map;

@Path("/Accumulo")
@RolesAllowed({"InternalUser", "Administrator"})
@DeclareRoles({"InternalUser", "Administrator"})
@LocalBean
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
@TransactionManagement(TransactionManagementType.BEAN)
public class StatsBean {
    
    private Logger log = Logger.getLogger(this.getClass());
    
    @EJB
    private AccumuloConnectionFactory connectionFactory;
    
    private String accumuloStatsURL = null;
    
    @PostConstruct
    public void retrieveAccumuloStatsURL() {
        Connector connection = null;
        AccumuloConnectionFactory.Priority priority = AccumuloConnectionFactory.Priority.ADMIN;
        try {
            Map<String,String> trackingMap = connectionFactory.getTrackingMap(Thread.currentThread().getStackTrace());
            connection = connectionFactory.getConnection(priority, trackingMap);
            
            ZooKeeperInstance instance = (ZooKeeperInstance) connection.getInstance();
            accumuloStatsURL = new AccumuloMonitorLocator().getUrl(instance);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            if (connection != null) {
                try {
                    connectionFactory.returnConnection(connection);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }
    
    @PermitAll
    public StatsResponse stats() {
        
        // Keep re-trying for the stats URL if we couldn't locate it at startup
        if (this.accumuloStatsURL == null) {
            retrieveAccumuloStatsURL();
        }
        
        StatsResponse response = new StatsResponse();
        
        try {
            Client client = ClientBuilder.newClient();
            WebTarget target = client.target("http://" + this.accumuloStatsURL + "/xml");
            
            Response clientResponse = target.request().get();
            try {
                int httpStatusCode = clientResponse.getStatus();
                if (httpStatusCode == 200) {
                    NamespaceFilter nsFilter = new NamespaceFilter();
                    SAXParserFactory spf = SAXParserFactory.newInstance();
                    spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    nsFilter.setParent(spf.newSAXParser().getXMLReader());
                    
                    JAXBContext ctx = JAXBContext.newInstance(StatsResponse.class);
                    UnmarshallerHandler umHandler = ctx.createUnmarshaller().getUnmarshallerHandler();
                    nsFilter.setContentHandler(umHandler);
                    nsFilter.parse(new InputSource(new StringReader(clientResponse.readEntity(String.class))));
                    response = (StatsResponse) umHandler.getResult();
                } else {
                    log.error("Error returned requesting stats from the cloud: " + httpStatusCode);
                    response.addException(new RuntimeException("Error returned requesting stats from the cloud: " + httpStatusCode));
                    
                    // maybe the monitor has moved, re-resolve the stats location
                    this.accumuloStatsURL = null;
                }
                return response;
            } finally {
                clientResponse.close();
            }
            
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            response.addException(e);
            throw new AccumuloWebApplicationException(e, response);
        }
    }
    
    /**
     * Retrieve statistics from the Accumulo monitor (Requires Administrator role)
     *
     * @HTTP 200 Success
     * @HTTP 500 Error while retrieving statistics
     * @return datawave.webservice.response.StatsResponse
     */
    @Path("/Stats")
    @Produces({"application/xml", "text/xml", "application/json", "text/yaml", "text/x-yaml", "application/x-yaml"})
    @GET
    public StatsResponse accumuloStats() {
        return stats();
    }
    
    private static class NamespaceFilter extends XMLFilterImpl {
        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            super.startElement(StatsProperties.NAMESPACE, localName, qName, atts);
        }
        
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(StatsProperties.NAMESPACE, localName, qName);
        }
    }
}
