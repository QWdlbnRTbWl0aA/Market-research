package local.myhome.marketresearch.tools;

import java.nio.file.*;
import java.util.Map;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Explicit one-shot report delivery. Credentials are loaded locally, never copied into the report. */
final class ReportMail {
    private ReportMail() {}
    static Map<String,Object> send(Path credentials,Path report,String recipient,String subject) throws Exception {
        return send(credentials,report,recipient,subject,new JavaMailSenderImpl());
    }
    static Map<String,Object> send(Path credentials,Path report,String recipient,String subject,JavaMailSenderImpl sender) throws Exception {
        if (!Files.isRegularFile(report) || Files.size(report)>1_000_000 || recipient==null || !recipient.matches("[^\\s@]+@[^\\s@]+"))
            throw new ToolFailure("Invalid report or recipient");
        sender.setHost(SecretFiles.get(credentials,"SMTP_HOST"));
        sender.setPort(Integer.parseInt(SecretFiles.get(credentials,"SMTP_PORT")));
        sender.setUsername(SecretFiles.get(credentials,"SMTP_USERNAME"));
        sender.setPassword(SecretFiles.get(credentials,"SMTP_PASSWORD"));
        String from=SecretFiles.get(credentials,"MAIL_FROM");
        var properties=sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth","true");
        properties.setProperty("mail.smtp.starttls.enable","true");
        properties.setProperty("mail.smtp.starttls.required","true");
        properties.setProperty("mail.smtp.localhost","localhost");
        properties.setProperty("mail.from",from);
        for(String timeout:java.util.List.of("connectiontimeout","timeout","writetimeout"))
            properties.setProperty("mail.smtp."+timeout,"15000");
        var message=new SimpleMailMessage();
        message.setFrom(from);message.setTo(recipient);message.setSubject(subject);message.setText(Files.readString(report));
        try {sender.send(message);}
        catch(Exception failure){throw new ToolFailure("SMTP report delivery failed or is uncertain; no automatic resend");}
        return Map.of("status","SMTP_ACCEPTED","recipient",recipient,"report",report.getFileName().toString());
    }
}
