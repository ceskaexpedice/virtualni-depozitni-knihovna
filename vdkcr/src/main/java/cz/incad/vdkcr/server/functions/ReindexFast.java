package cz.incad.vdkcr.server.functions;

import com.fastsearch.esp.content.DocumentFactory;
import com.fastsearch.esp.content.IDocument;
import com.typesafe.config.Config;
import cz.incad.vdkcr.server.fast.FastIndexer;
import cz.incad.vdkcr.server.index.IndexTypes;
import org.aplikator.server.Context;
import org.aplikator.server.function.Executable;
import org.aplikator.server.function.FunctionParameters;
import org.aplikator.server.function.FunctionResult;
import org.aplikator.server.persistence.Persister;
import org.aplikator.server.persistence.PersisterFactory;
import org.aplikator.server.util.Configurator;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aplikator.client.shared.data.Record;
import org.aplikator.server.descriptor.WizardPage;

//import org.json.JSONArray;
//import org.json.JSONObject;

public class ReindexFast extends Executable {

    Logger logger = Logger.getLogger(ReindexFast.class.getName());
    private FastIndexer fastIndexer;
    Connection conn;
    DocumentBuilderFactory domFactory;

    @Override
    public FunctionResult execute(FunctionParameters parameters, Context context) {
        Config config = Configurator.get().getConfig();
        fastIndexer = new FastIndexer();
        fastIndexer.config(config);

        domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(false);
        try {
            builder = domFactory.newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        if (getRecords()) {

            return new FunctionResult("Reindexovano", true);
        } else {
            return new FunctionResult("Reindexace se nepovedla", false);
        }
    }

    public static void main(String[] args) {
        ReindexFast ri = new ReindexFast();
        ri.execute(null, null);
    }

    private void connect() throws ClassNotFoundException, SQLException {
        logger.fine("Connecting...");
        Persister persister = PersisterFactory.getPersister();
        conn = persister.getJDBCConnection();
    }

    private void disconnect() {
        try {
            conn.close();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Cant disconnect", ex);
        }

    }

    private void addFastElement(IDocument doc, String name, String value) {
        try {
            if (value != null) {
                doc.addElement(DocumentFactory.newString(name, value));
            }
        } catch (Exception ex) {
            logger.log(Level.FINE, "Cant add element  " + name, ex);
        }
    }
    String sqlIdentifikator = "select * from identifikator where zaznam=?";
    PreparedStatement psId;

    private void getIdentifikator(int zaznam_id, IDocument doc) {
        try {
            psId.setInt(1, zaznam_id);
            ResultSet rs = psId.executeQuery();
            while (rs.next()) {
                String typ = rs.getString("typ");
                if (typ.equals("cCNB")) {
                    addFastElement(doc, "ccnb", rs.getString("hodnota"));
                    addFastElement(doc, "igeneric1", rs.getString("hodnota"));
                } else if (typ.equals("ISBN") || typ.equals("ISSN")) {
                    addFastElement(doc, "isxn", rs.getString("hodnota"));
                    addFastElement(doc, "igeneric2", rs.getString("hodnota"));
                }
                //addFastElement(doc, rs.getString("typ").toLowerCase(), rs.getString("hodnota"));
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Cant get identifikator for zaznam_id " + zaznam_id, ex);
        }

    }
    String sqlAutori = "select * from autor where zaznam=?";
    PreparedStatement psAutori;

    private void getAutori(int zaznam_id, IDocument doc) {
        try {
            psAutori.setInt(1, zaznam_id);
            ResultSet rs = psAutori.executeQuery();
            String autori = "";
            while (rs.next()) {
                autori += rs.getString("nazev") + ";";
            }
            addFastElement(doc, "autor", autori);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Cant get autori for zaznam_id " + zaznam_id, ex);
        }

    }
    String sqlZdroj = "select nazev, typZdroje, formatxml from sklizen, zdroj where sklizen.ZDROJ=zdroj.ZDROJ_ID and sklizen.SKLIZEN_ID=?";
    PreparedStatement psZdroj;

    private void getZdroj(int sklizen_id, IDocument doc) {
        try {
            psZdroj.setInt(1, sklizen_id);
            ResultSet rs = psZdroj.executeQuery();
            if (rs.next()) {
                addFastElement(doc, "zdroj", rs.getString("nazev"));
                addFastElement(doc, "base", rs.getString("nazev"));
                addFastElement(doc, "harvester", rs.getString("typZdroje"));
                addFastElement(doc, "originformat", rs.getString("formatxml"));
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Cant get zdroj for sklizen_id " + sklizen_id, ex);
        }

    }

    private void getZdroj(int sklizen_id, FastZaznam zaznam) {
        try {
            psZdroj.setInt(1, sklizen_id);
            ResultSet rs = psZdroj.executeQuery();
            if (rs.next()) {
                zaznam.zdroj = rs.getString("nazev");
                zaznam.typZdroje = rs.getString("typZdroje");
                zaznam.formatxml = rs.getString("formatxml");
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Cant get zdroj for sklizen_id " + sklizen_id, ex);
        }

    }
    DocumentBuilder builder;

    private String getClob(Clob data) {
        if (data != null) {
            Reader reader = null;
            try {
                StringBuilder sb = new StringBuilder();
                reader = data.getCharacterStream();
                BufferedReader br = new BufferedReader(reader);
                String line;
                while (null != (line = br.readLine())) {
                    sb.append(line);
                }
                br.close();
                if (!sb.toString().equals("")) {
                    try {

                        InputSource source = new InputSource(new StringReader(sb.toString()));
                        @SuppressWarnings("unused")
                        Document doc = builder.parse(source);

                        return sb.toString();
                    } catch (Exception ex) {
                        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><record></record>";
                    }
                } else {

                    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><record></record>";
                }
            } catch (Exception ex) {
                return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><record></record>";
            } finally {
                try {
                    reader.close();
                } catch (IOException ex) {
                    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><record></record>";
                }
            }
        } else {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><record></record>";
        }

    }
    String sqlExemplar = "select * from Exemplar where zaznam=?";
    PreparedStatement psExemplar;

    private void getExemplare(int zaznam_id, FastZaznam zaznam) {
        try {
            psExemplar.setInt(1, zaznam_id);
            ResultSet rs = psExemplar.executeQuery();
            while (rs.next()) {
                IDocument doc = DocumentFactory.newDocument(rs.getString("EXEMPLAR_ID"));

                //Zaznam
                addFastElement(doc, "title", zaznam.hlavninazev);
                doc.addElement(DocumentFactory.newInteger("dbid", zaznam_id));
                doc.addElement(DocumentFactory.newString("url", zaznam.url));
                addFastElement(doc, "druhdokumentu", zaznam.typdokumentu);

                //Zdroj
                addFastElement(doc, "zdroj", zaznam.zdroj);
                addFastElement(doc, "base", zaznam.zdroj);
                addFastElement(doc, "harvester", zaznam.typZdroje);
                addFastElement(doc, "originformat", zaznam.formatxml);
                /*                
                 JSONObject json = new JSONObject();
                 json.put("signatura", rs.getString("signatura"));
                
                 json.put("signatura", rs.getString("signatura"));
                 json.put("carovyKod", rs.getString("carovyKod"));
                 json.put("popis", rs.getString("popis"));
                 json.put("svazek", rs.getString("svazek"));
                 json.put("rocnik", rs.getString("rocnik"));
                 json.put("cislo", rs.getString("cislo"));
                 json.put("rok", rs.getString("rok"));
                 json.put("dilciKnih", rs.getString("dilciKnih"));
                 json.put("sbirka", rs.getString("sbirka"));
                 json.put("status", rs.getString("statusJednotky"));
                 json.put("pocetVypujcek", rs.getString("pocetVypujcek"));
                 json.put("poznXerokopii", rs.getString("poznXerokopii"));
                 */

                addFastElement(doc, "generic1", rs.getString("statusJednotky"));
                addFastElement(doc, "generic2", rs.getString("pocetVypujcek"));



                getIdentifikator(zaznam_id, doc);
                getAutori(zaznam_id, doc);


                //String xmlStr = getClob(rs.getClob("sourcexml"));
                String xmlStr = "<record />";
                addFastElement(doc, "data", xmlStr);
                fastIndexer.add(doc, IndexTypes.INSERTED);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Cant get exemplar for zaznam_id " + zaznam_id, ex);
        }
    }

    private int getExemplare(int zaznam_id, IDocument doc) {
        try {
            psExemplar.setInt(1, zaznam_id);
            ResultSet rs = psExemplar.executeQuery();
            String status = "";
            int res = 0;
//            JSONObject jsonEx = new JSONObject();
//            JSONArray exArray = new JSONArray();
//            jsonEx.put("exemplare", exArray);
            while (rs.next()) {
                /*
                 JSONObject json = new JSONObject();
                 json.put("signatura", rs.getString("signatura"));
                
                 json.put("signatura", rs.getString("signatura"));
                 json.put("carovyKod", rs.getString("carovyKod"));
                 json.put("popis", rs.getString("popis"));
                 json.put("svazek", rs.getString("svazek"));
                 json.put("rocnik", rs.getString("rocnik"));
                 json.put("cislo", rs.getString("cislo"));
                 json.put("rok", rs.getString("rok"));
                 json.put("dilciKnih", rs.getString("dilciKnih"));
                 json.put("sbirka", rs.getString("sbirka"));
                 json.put("status", rs.getString("statusJednotky"));
                 json.put("pocetVypujcek", rs.getString("pocetVypujcek"));
                 json.put("poznXerokopii", rs.getString("poznXerokopii"));
                 exArray.put(json);
                 */
                status += rs.getString("statusJednotky") + ";";
                res++;
            }
//            addFastElement(doc, "poznamka", jsonEx.toString());
            addFastElement(doc, "generic2", status);
            return res;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Cant get exemplar for zaznam_id " + zaznam_id, ex);
            return 0;
        }
    }

    private void getCNBDocuments() throws SQLException {
        String sql = "select * from Identifikator where typ='cCNB'";
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {

            int zaznam_id = rs.getInt("ZAZNAM");
            FastZaznam zaznam = new FastZaznam(zaznam_id,
                    rs.getString("hlavninazev"),
                    rs.getString("url"),
                    rs.getString("typdokumentu"),
                    rs.getInt("SKLIZEN"));
            getExemplare(zaznam_id, zaznam);
        }
    }

    private boolean getRecords() {
        try {
            connect();
            getZaznamy();
            fastIndexer.finished();
            logger.log(Level.INFO, "Finished");
            return true;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
            return false;
        } finally {
            disconnect();
        }
    }

    private void getZaznamy() throws Exception {

        String sqlZaznam = "select ZAZNAM_ID, hlavninazev, url, typdokumentu, SKLIZEN from zaznam where SKLIZEN=5";
        logger.log(Level.INFO, "Getting zaznamy: " + sqlZaznam);
        PreparedStatement ps = conn.prepareStatement(sqlZaznam);
        psId = conn.prepareStatement(sqlIdentifikator);
        psAutori = conn.prepareStatement(sqlAutori);
        psZdroj = conn.prepareStatement(sqlZdroj);
        psExemplar = conn.prepareStatement(sqlExemplar);
        ResultSet rs = ps.executeQuery();
        int zaznam_id;
        int start = 0;
        while (rs.next()) {
            //if (start++>500000) break;
            zaznam_id = rs.getInt("ZAZNAM_ID");
//                FastZaznam zaznam = new FastZaznam(zaznam_id, 
//                        rs.getString("hlavninazev"), 
//                        rs.getString("url"), 
//                        rs.getString("typdokumentu"),
//                        rs.getInt("SKLIZEN"));
//                getExemplare(zaznam_id, zaznam);
            IDocument doc = DocumentFactory.newDocument(rs.getString("url"));
            addFastElement(doc, "title", rs.getString("hlavninazev"));
            doc.addElement(DocumentFactory.newInteger("dbid", zaznam_id));
            doc.addElement(DocumentFactory.newString("url", rs.getString("url")));
            addFastElement(doc, "druhdokumentu", rs.getString("typdokumentu"));
            int exemplare = getExemplare(zaznam_id, doc);
            doc.addElement(DocumentFactory.newString("generic1", Integer.toString(exemplare)));

            getIdentifikator(zaznam_id, doc);
            getAutori(zaznam_id, doc);
            getZdroj(rs.getInt("SKLIZEN"), doc);

            //String xmlStr = getClob(rs.getClob("sourcexml"));
            String xmlStr = "<record />";
            addFastElement(doc, "data", xmlStr);
            fastIndexer.add(doc, IndexTypes.INSERTED);
        }
    }
    
    

    @Override
    public WizardPage getWizardPage(String currentPage, boolean forwardFlag, Record currentProcessingRecord, Record clientParameters, Context context) {
        return null;
    }

    private class FastZaznam {

        int zaznam_id;
        String hlavninazev;
        String url;
        String typdokumentu;
        int sklizen;
        String zdroj;
        String typZdroje;
        String formatxml;

        public FastZaznam(int zaznam_id,
                String hlavninazev,
                String url,
                String typdokumentu,
                int sklizen) {
            this.zaznam_id = zaznam_id;
            this.hlavninazev = hlavninazev;
            this.url = url;
            this.typdokumentu = typdokumentu;
            this.sklizen = sklizen;

            getZdroj(sklizen, this);
        }
    }
}
