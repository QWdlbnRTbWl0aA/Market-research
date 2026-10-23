package local.myhome.marketresearch.orchestrator.discovery;

import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Durable notification outbox; SMTP remains off until the operator supplies configuration. */
@Component
public class DiscoveryNotices {
    private final JdbcTemplate db;private final Environment env;
    public DiscoveryNotices(JdbcTemplate db,Environment env) {
        this.db=db;this.env=env;
        db.execute("CREATE TABLE IF NOT EXISTS discovery_notices(id VARCHAR(200) PRIMARY KEY, subject VARCHAR(200), body CLOB, status VARCHAR(40))");
        if (env.getProperty("research.discovery.workers-enabled",Boolean.class,true))
            db.update("UPDATE discovery_notices SET status='DELIVERY_UNCERTAIN' WHERE status='SENDING'");
    }
    public synchronized void queue(String key,String subject,String body) {
        if(db.queryForList("SELECT id FROM discovery_notices WHERE id=?",key).isEmpty())
            db.update("INSERT INTO discovery_notices VALUES(?,?,?,'PENDING')",key,subject,body);
    }
    public List<Map<String,Object>> list(){return db.queryForList("SELECT id,subject,status FROM discovery_notices");}
    public void retry(String key) {db.update("UPDATE discovery_notices SET status='PENDING' WHERE id=? AND status IN ('DELIVERY_UNCERTAIN','WAITING_CONFIGURATION')",key);}
    @Scheduled(fixedDelayString="${research.notifications.delay-ms:10000}")
    public void tick() {
        if (!env.getProperty("research.discovery.workers-enabled",Boolean.class,true)) return;
        if(!env.getProperty("research.notifications.smtp-enabled",Boolean.class,false)) {
            db.update("UPDATE discovery_notices SET status='WAITING_CONFIGURATION' WHERE status='PENDING'");return;
        }
        for(var row:db.queryForList("SELECT * FROM discovery_notices WHERE status IN ('PENDING','WAITING_CONFIGURATION') FETCH FIRST 1 ROW ONLY")) {
            String id=row.get("ID").toString();
            if(db.update("UPDATE discovery_notices SET status='SENDING' WHERE id=? AND status IN ('PENDING','WAITING_CONFIGURATION')",id)!=1)continue;
            try {
                var sender=new JavaMailSenderImpl();sender.setHost(env.getRequiredProperty("spring.mail.host"));
                sender.setPort(env.getProperty("spring.mail.port",Integer.class,587));
                sender.setUsername(env.getProperty("spring.mail.username"));sender.setPassword(env.getProperty("spring.mail.password"));
                var props=sender.getJavaMailProperties();
                props.setProperty("mail.smtp.auth",env.getProperty("spring.mail.properties.mail.smtp.auth","true"));
                props.setProperty("mail.smtp.starttls.enable","true");props.setProperty("mail.smtp.starttls.required","true");
                props.setProperty("mail.smtp.connectiontimeout","5000");props.setProperty("mail.smtp.timeout","5000");props.setProperty("mail.smtp.writetimeout","5000");
                var message=new SimpleMailMessage();message.setFrom(env.getRequiredProperty("research.notifications.from"));
                message.setTo(env.getRequiredProperty("research.notifications.to"));message.setSubject(row.get("SUBJECT").toString());
                message.setText(db.queryForObject("SELECT body FROM discovery_notices WHERE id=?",String.class,id));sender.send(message);
                db.update("UPDATE discovery_notices SET status='SENT' WHERE id=?",id);
            } catch(Exception e) {db.update("UPDATE discovery_notices SET status='DELIVERY_UNCERTAIN' WHERE id=?",id);}
        }
    }
}
