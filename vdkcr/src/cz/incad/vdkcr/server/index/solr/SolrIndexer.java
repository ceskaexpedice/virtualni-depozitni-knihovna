package cz.incad.vdkcr.server.index.solr;

import com.typesafe.config.Config;
import cz.incad.vdkcr.server.index.DataSourceIndexer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.LukeRequest;
import org.apache.solr.client.solrj.response.LukeResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.aplikator.client.shared.data.ContainerNode;
import org.aplikator.client.shared.data.PrimaryKey;
import org.aplikator.client.shared.data.Record;
import org.aplikator.client.shared.data.RecordContainer;
import org.aplikator.client.shared.data.SearchResult;
import org.aplikator.client.shared.descriptor.EntityDTO;
import org.aplikator.client.shared.descriptor.ViewDTO;
import org.aplikator.server.Context;
import org.aplikator.server.DescriptorRegistry;
import org.aplikator.server.descriptor.Entity;
import org.aplikator.server.descriptor.View;
import org.aplikator.server.persistence.search.Search;

/**
 *
 * @author alberto
 */
public class SolrIndexer implements DataSourceIndexer, Search {

    private static final Logger logger = Logger.getLogger(SolrIndexer.class.getName());
    private String host;
    private String collection;
    private int batchSize;
    HttpSolrServer server;
    Collection<SolrInputDocument> insertDocs = new ArrayList<SolrInputDocument>();
    List<String> delDocs = new ArrayList<String>();

    private void commit() throws Exception {
        server.commit();
    }

    private void check() throws Exception {
        if (insertDocs.size() >= batchSize) {
            server.add(insertDocs);
            server.commit();
            insertDocs.clear();
        }
        if (delDocs.size() >= batchSize) {
            server.deleteById(delDocs);
            server.commit();
            delDocs.clear();
        }
    }
    
    private Map<String, LukeResponse.FieldInfo> schemaFields;
    String idField;

    private void getSchemaFields() throws Exception {
//        SolrQuery query = new SolrQuery(); 
//        query.setRequestHandler("/schema/version"); 
//        QueryResponse response = server.query(query); 
//        Double version = (Double) response.getResponse().get("version"); 
//        System.out.println(version); 

        LukeRequest sr = new LukeRequest();
        LukeResponse lr = sr.process(server);
        
        

        schemaFields = lr.getFieldInfo();
//        for (LukeResponse.FieldInfo fi : schemaFields.values()) {
//            //fi.getFlags()
//            System.out.println("fi: " + fi.getName());
//        }
    }

    @Override
    public void config(Config config) throws Exception {
        this.host = config.getString("aplikator.solrHost");
        this.batchSize = config.getInt("aplikator.solrBatchSize");
        this.collection = config.getString("aplikator.solrCollection");
        
        this.idField = config.getString("aplikator.solrIdField");


        server = new HttpSolrServer(host + "/" + collection);
        //server.setMaxRetries(1); // defaults to 0.  > 1 not recommended.
        //server.setConnectionTimeout(1000); // 5 seconds to establish TCP
        // Setting the XML response parser is only required for cross
        // version compatibility and only when one side is 1.4.1 or
        // earlier and the other side is 3.1 or later.
        //server.setParser(new XMLResponseParser()); // binary parser is used by default
        // The following settings are provided here for completeness.
        // They will not normally be required, and should only be used 
        // after consulting javadocs to know whether they are truly required.
        //server.setSoTimeout(1000);  // socket read timeout
        //server.setDefaultMaxConnectionsPerHost(100);
        //server.setMaxTotalConnections(100);
        //server.setFollowRedirects(false);  // defaults to false
        // allowCompression defaults to false.
        // Server side must support gzip or deflate for this to have any effect.
        //server.setAllowCompression(true);

        getSchemaFields();
    }

    @Override
    public void finish() throws Exception {
        if (!insertDocs.isEmpty()) {
            server.add(insertDocs);
            server.commit();
            insertDocs.clear();
        }
        if (!delDocs.isEmpty()) {
            server.deleteById(delDocs);
            server.commit();
            delDocs.clear();
        }
    }

    @Override
    public void insertRecord(RecordContainer rc) throws Exception {

        //System.out.println("rc: " + Marshalling.toJSON(rc));
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField(idField, rc.getRecords().get(0).getEdited().getPrimaryKey().getId());
        for (ContainerNode cn : rc.getRecords()) {
            Record record = cn.getEdited();
            String entityId = record.getPrimaryKey().getEntityId();
            for (String name : record.getProperties()) {
                
                String shortName = name.substring("Property:".length());
                //if(schemaFields.containsKey(shortName)){
                    doc.addField(shortName, record.getValue(name));
                //}
                //System.out.println("name: " + shortName + " val: " + record.getValue(name));
            }
        }
        insertDocs.add(doc);
        check();
    }

    @Override
    public void insertDoc(String id, Map<String, String> fields) throws Exception {
        SolrInputDocument doc = new SolrInputDocument();
        for (String name : fields.keySet()) {
            doc.addField(name, fields.get(name));
        }
        insertDocs.add(doc);
        check();
    }

    @Override
    public void updateDoc(String id, Map<String, String> fields) throws Exception {
        insertDoc(id, fields);
    }

    @Override
    public void removeDoc(String id) throws Exception {
        delDocs.add(id);
        check();
    }
    
    /* Search implements */

    @Override
    public SearchResult getPagefromSearch(ViewDTO vd, String searchArgument, int pageOffset, int pageSize, Context ctx) {
        
        ArrayList<Record> records = new ArrayList<Record>();
        
		return search(searchArgument, pageOffset,
				pageSize);
			// TODO - missing info from entity to create record preview
    }

    @Override
    public SearchResult getPagefromSearch(String searchArgument, int pageOffset, int pageSize, Context ctx) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void index(Record record) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void index(EntityDTO entityDTO) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SearchResult search(String searchArgument, String type, int offset, int size) {
        
        try {
            SolrQuery query = new SolrQuery(); 
            query.setQuery(searchArgument);
            query.setStart(offset);
            query.setRows(size);
            QueryResponse response = server.query(query); 
            
            
            List<Record> records = new ArrayList<Record>();
            for(SolrDocument sd : response.getResults()){
                PrimaryKey pk = new PrimaryKey("Zaznam", (Integer)sd.get("id"));
                Record record = new Record(pk);
                records.add(record);
            }
            return new SearchResult(records, response.getResults().getNumFound());
        } catch (SolrServerException ex) {
            Logger.getLogger(SolrIndexer.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    @Override
    public SearchResult search(String searchArgument, int offset, int size) {
        return search(searchArgument, null, offset, size);
    }

    @Override
    public void update(PrimaryKey primaryKey) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void insert(PrimaryKey primaryKey) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void delete(PrimaryKey primaryKey) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}