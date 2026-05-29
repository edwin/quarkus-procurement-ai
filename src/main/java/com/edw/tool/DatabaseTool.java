package com.edw.tool;

import dev.langchain4j.agent.tool.Tool;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * <pre>
 *  com.edw.tool.DatabaseTool
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 01 May 2026 15:24
 */
@ApplicationScoped
public class DatabaseTool {

    @Inject
    AgroalDataSource dataSource;

    private Logger logger = LoggerFactory.getLogger(DatabaseTool.class);

    @Tool("""
        
        use this method to connect to a postgresql database and run a dynamic sql
        leverage sql functionality like max, min, count, group by, having, avg, or sum for analysis
        method allowed is only SELECT, no INSERT, UPDATE, or DELETE allowed
        
        can only use public.procurement_record table with this column,
        id_rup (character varying(20)), title (text), budget (numeric 20,2), year (integer), institution (character varying(250)), category (character varying(128)), embedded (boolean)
        
        field category contains 'ATK & Perlengkapan Kantor', 'Alat Kesehatan & Farmasi', 'Teknologi Informasi', 'Konstruksi & Infrastruktur', 'Jasa Konsultansi', 'Konsumsi & Catering', 'Pelatihan & Pendidikan', 'Kendaraan'
        always use embedded=true parameter
        always use LIMIT to at most 10 data to save memory
        """)
    public String executeQuery(String sqlQuery) {
        String trimmed = sqlQuery.strip().toLowerCase();
        if (!trimmed.startsWith("select") || trimmed.contains(";") || trimmed.contains("--")) {
            return "Error: only SELECT queries are allowed.";
        }

        if (!trimmed.contains("procurement_record")) {
            return "Error: only queries to table procurement_record are allowed.";
        }

        try (Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement()) {

            logger.debug("running query : {} ",sqlQuery);

            ResultSet rs = stmt.executeQuery(sqlQuery);
            return convertResultSetToString(rs);
        } catch (SQLException e) {
            return "Error executing query: " + e.getMessage();
        }
    }

    
    private String convertResultSetToString(ResultSet rs) {
        StringBuilder result = new StringBuilder();
        try {
            int columnCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rs.getMetaData().getColumnName(i);
                    int columnType = rs.getMetaData().getColumnType(i);

                    result.append(columnName).append(": ");
                    if (columnType == java.sql.Types.INTEGER || columnType == java.sql.Types.BIGINT ||
                            columnType == java.sql.Types.NUMERIC || columnType == java.sql.Types.DECIMAL ||
                            columnType == java.sql.Types.FLOAT || columnType == java.sql.Types.DOUBLE) {
                        result.append(String.format("%,d", Long.parseLong(rs.getObject(i).toString().replace(",", "").split("\\.")[0])).replace(',', '.'));
                    } else {
                        result.append(rs.getObject(i));
                    }
                    result.append(", ");
                }
                result.delete(result.length() - 2, result.length());
                result.append("\n");
            }
        } catch (SQLException e) {
            return "Error processing ResultSet: " + e.getMessage();
        }

        return result.toString();
    }
}
