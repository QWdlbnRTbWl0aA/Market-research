package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class ReportMailTest {
    @TempDir Path directory;
    @Test void sendsCompleteReportWithTlsAndWithoutMachineHostname() throws Exception {
        Path credentials=directory.resolve("smtp.env"),report=directory.resolve("report.md");
        Files.writeString(credentials,"SMTP_HOST=smtp.example.test\nSMTP_PORT=587\nSMTP_USERNAME=test@example.test\nSMTP_PASSWORD=fixture-only\nMAIL_FROM=test@example.test\n");
        Files.writeString(report,"# Full report\n\nEvidence and limitations.\n");
        var sender=spy(new JavaMailSenderImpl());
        doNothing().when(sender).send(any(SimpleMailMessage.class));
        var result=ReportMail.send(credentials,report,"recipient@example.test","Acceptance report",sender);
        var message=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals(Files.readString(report),message.getValue().getText());
        assertEquals("localhost",sender.getJavaMailProperties().getProperty("mail.smtp.localhost"));
        assertEquals("true",sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
        assertEquals("test@example.test",sender.getJavaMailProperties().getProperty("mail.from"));
        assertEquals("SMTP_ACCEPTED",result.get("status"));
        assertFalse(result.toString().contains("fixture-only"));
    }
    @Test void rejectsMissingReportBeforeReadingCredentialsOrUsingTransport() {
        var sender=mock(JavaMailSenderImpl.class);
        assertThrows(ToolFailure.class,()->ReportMail.send(directory.resolve("missing.env"),directory.resolve("missing.md"),
            "recipient@example.test","Report",sender));
        verifyNoInteractions(sender);
    }
}
