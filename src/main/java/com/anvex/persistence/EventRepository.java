package com.anvex.persistence;

import com.anvex.event.EventListener;
import com.anvex.event.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.slf4j.*;

public final class EventRepository implements EventListener {
    private static final Logger log = LoggerFactory.getLogger(EventRepository.class);
    private static final String COLUMNS = "event_id,sequence_no,run_id,timestamp,event_type,username,client_type,source,outcome,"
            + "threat_level,threat_score,metadata,evidence_failed_attempts,evidence_window_ms,evidence_frequency,evidence_threshold_exceeded,message";
    private final DatabaseManager db;
    public EventRepository(DatabaseManager db) { this.db = db; }
    @Override public void onEvent(SecurityEvent e) {
        try { insertEvent(e); if (e.getEvidence()!=null && e.getThreatLevel()!=null) insertAlert(e); }
        catch(SQLException ex) { log.error("Failed to persist event {}", e.getEventId(), ex); throw new IllegalStateException(ex); }
    }
    public List<PersistedEvent> findByRun(long id) throws SQLException {
        return query("SELECT "+COLUMNS+" FROM security_events WHERE run_id=? ORDER BY event_id", s->s.setLong(1,id));
    }
    public List<PersistedEvent> findBySource(String source) throws SQLException {
        return query("SELECT "+COLUMNS+" FROM security_events WHERE source=? ORDER BY event_id", s->s.setString(1,source));
    }
    public List<PersistedEvent> findAfterId(long cursor,int limit) throws SQLException {
        return query("SELECT "+COLUMNS+" FROM security_events WHERE event_id>? ORDER BY event_id LIMIT ?",
                s->{s.setLong(1,cursor);s.setInt(2,limit);});
    }
    public List<PersistedAlert> findAlertsForRun(long id) throws SQLException {
        List<PersistedAlert> result=new ArrayList<>();
        try(Connection c=db.getConnection(); PreparedStatement s=c.prepareStatement(
                "SELECT alert_id,run_id,rule_id,severity,timestamp,username,explanation FROM alerts WHERE run_id=? ORDER BY alert_id")) {
            s.setLong(1,id); try(ResultSet r=s.executeQuery()) { while(r.next()) result.add(new PersistedAlert(
                    r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getTimestamp(5).toInstant(),r.getString(6),r.getString(7))); }
        } return result;
    }
    public int countEvents() throws SQLException { return count("security_events"); }
    public int countAlerts() throws SQLException { return count("alerts"); }
    private int count(String table) throws SQLException {
        try(Connection c=db.getConnection(); PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM "+table);
            ResultSet r=s.executeQuery()) { r.next(); return r.getInt(1); }
    }
    private List<PersistedEvent> query(String sql,Binder binder) throws SQLException {
        List<PersistedEvent> result=new ArrayList<>();
        try(Connection c=db.getConnection(); PreparedStatement s=c.prepareStatement(sql)) {
            binder.bind(s); try(ResultSet r=s.executeQuery()) { while(r.next()) result.add(map(r)); }
        } return result;
    }
    private PersistedEvent map(ResultSet r) throws SQLException {
        long window=r.getLong("evidence_window_ms");
        Evidence evidence=r.wasNull()?null:Evidence.builder().failedAttemptCount(r.getLong("evidence_failed_attempts"))
                .timeWindowMs(window).requestFrequency(r.getDouble("evidence_frequency"))
                .thresholdExceeded(r.getBoolean("evidence_threshold_exceeded")).build();
        int rawScore=r.getInt("threat_score"); Integer score=r.wasNull()?null:rawScore;
        String level=r.getString("threat_level");
        return new PersistedEvent(r.getLong("event_id"),r.getLong("sequence_no"),r.getLong("run_id"),
                r.getTimestamp("timestamp").toInstant(),r.getString("event_type"),r.getString("username"),
                r.getString("client_type"),r.getString("source"),r.getString("outcome"),
                level==null?null:ThreatLevel.valueOf(level),score,decode(r.getString("metadata")),evidence,r.getString("message"));
    }
    private void insertEvent(SecurityEvent e) throws SQLException {
        String sql="INSERT INTO security_events (sequence_no,run_id,timestamp,event_type,username,client_type,source,outcome,"
                +"threat_level,threat_score,metadata,evidence_failed_attempts,evidence_window_ms,evidence_frequency,"
                +"evidence_threshold_exceeded,message) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(Connection c=db.getConnection(); PreparedStatement s=c.prepareStatement(sql)) {
            s.setLong(1,e.getSequenceNumber());s.setLong(2,e.getRunId());s.setTimestamp(3,Timestamp.from(e.getTimestamp()));
            s.setString(4,e.getEventType().name());s.setString(5,e.getUsername());s.setString(6,e.getClientType());
            s.setString(7,e.getSource());s.setString(8,e.getOutcome());
            s.setString(9,e.getThreatLevel()==null?null:e.getThreatLevel().name());
            if(e.getThreatScore()==null)s.setNull(10,Types.INTEGER);else s.setInt(10,e.getThreatScore());
            s.setString(11,encode(e.getMetadata()));
            if(e.getEvidence()==null) { s.setNull(12,Types.BIGINT);s.setNull(13,Types.BIGINT);s.setNull(14,Types.DOUBLE);s.setNull(15,Types.BOOLEAN); }
            else { Evidence v=e.getEvidence();s.setLong(12,v.getFailedAttemptCount());s.setLong(13,v.getTimeWindowMs());
                s.setDouble(14,v.getRequestFrequency());s.setBoolean(15,v.isThresholdExceeded()); }
            s.setString(16,e.getMessage());s.executeUpdate();
        }
    }
    private void insertAlert(SecurityEvent e) throws SQLException {
        String explanation=e.getMessage()+" | "+String.join("; ",e.getEvidence().describe());
        try(Connection c=db.getConnection(); PreparedStatement s=c.prepareStatement(
                "INSERT INTO alerts (run_id,rule_id,severity,timestamp,username,explanation) VALUES (?,?,?,?,?,?)")) {
            s.setLong(1,e.getRunId());s.setString(2,e.getEventType().name());s.setString(3,e.getThreatLevel().name());
            s.setTimestamp(4,Timestamp.from(e.getTimestamp()));s.setString(5,e.getUsername());s.setString(6,explanation);s.executeUpdate();
        }
    }
    private static String encode(Map<String,String> metadata) {
        if(metadata==null||metadata.isEmpty())return null;
        StringJoiner j=new StringJoiner("&");metadata.forEach((k,v)->j.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
        return j.toString();
    }
    private static Map<String,String> decode(String value) {
        if(value==null||value.isEmpty())return Map.of();Map<String,String> result=new LinkedHashMap<>();
        for(String pair:value.split("&")){String[] p=pair.split("=",2);if(p.length==2)result.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),URLDecoder.decode(p[1],StandardCharsets.UTF_8));}
        return Map.copyOf(result);
    }
    private interface Binder {void bind(PreparedStatement s)throws SQLException;}
    public record PersistedEvent(long eventId,long sequenceNumber,long runId,Instant timestamp,String eventType,
            String username,String clientType,String source,String outcome,ThreatLevel threatLevel,Integer threatScore,
            Map<String,String> metadata,Evidence evidence,String message){}
    public record PersistedAlert(long alertId,long runId,String ruleId,String severity,Instant timestamp,String username,String explanation){}
}
