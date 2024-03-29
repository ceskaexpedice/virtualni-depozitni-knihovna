/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.incad.vdkcr.server.functions;

import cz.incad.vdkcommon.Slouceni;
import cz.incad.vdkcr.server.datasources.AbstractPocessDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aplikator.client.shared.data.Record;
import org.aplikator.server.Context;
import org.aplikator.server.persistence.Persister;
import org.aplikator.server.persistence.PersisterFactory;

/**
 *
 * @author alberto
 */
public class RegenerateMD5Process extends AbstractPocessDataSource {

    static final Logger logger = Logger.getLogger(RegenerateMD5Process.class.getName());

    Connection conn;
    int total = 0;

    String usql = "update zaznam set uniqueCode=?, codeType=? where zaznam_id=?";
    String sql = "select zaznam_id, sourceXML from zaznam";
    PreparedStatement ps;
    PreparedStatement ups;

    @Override
    public int harvest(String params, Record sklizen, Context ctx) throws Exception {
        run();
        return total;

    }
    
    public void run(){
        try {
            connect();
            ps = conn.prepareStatement(sql);
            ups = conn.prepareStatement(usql);
            logger.log(Level.INFO, "Prepared statments ok");
            getRecords();
            logger.log(Level.INFO, "REGENERATE MD5 CODE FINISHED. Total records: {0}", total);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error generating MD5 codes", ex);
        } finally {
            //disconnect();
        }
    }

    private void updateZaznam(String code, String codeType, int id) throws SQLException {
        ups.setString(1, code);
        ups.setString(2, codeType);
        ups.setInt(3, id);
        ups.executeUpdate();
        conn.commit();
        logger.log(Level.INFO, "Record id {0} updated. Total: {1}", new Object[]{id, total});
    }

    private void getRecords() throws Exception {
        logger.log(Level.INFO, "Getting records...");
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            //logger.log(Level.INFO, rs.getString("sourceXML"));
            // check interrupted thread
            if (Thread.currentThread().isInterrupted()) {
                logger.log(Level.INFO, "REGENERATE MD5 CODE INTERRUPTED. Total records: {0}", total);
                throw new InterruptedException();
            }
                int id = 0;
            try {
                id = rs.getInt("zaznam_id");
                String codeType = "md5";
                logger.log(Level.INFO, "processing record " + id);
                String uniqueCode = Slouceni.generateMD5(rs.getString("sourceXML"));
                logger.log(Level.INFO, "uniqueCode: " + uniqueCode);
                updateZaznam(uniqueCode, codeType, id);
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Error in record " + id, ex);
            }
            total++;
        }
        conn.commit();
        rs.close();
    }

    private void connect() throws ClassNotFoundException, SQLException {
        logger.info("Getting connection to db...");
        Persister persister = PersisterFactory.getPersister();
        conn = persister.getJDBCConnection();
        logger.log(Level.INFO, "Connection {0}", conn);
    }

    private void disconnect() {
        logger.info("Disconnecting from db...");
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Cant disconnect", ex);
        }

    }
}
