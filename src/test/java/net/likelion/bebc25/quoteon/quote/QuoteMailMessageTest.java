package net.likelion.bebc25.quoteon.quote;

import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteMailMessageTest {

    @Test
    void preparesPdfAttachmentWithoutOpeningAConnection() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        JavaMailQuoteGateway.prepare(
                message,
                "sender@example.com",
                "client@example.com",
                "신발장 견적서",
                "상담 내용을 첨부합니다.",
                new QuoteFile(pdf, "견적서_Q-1.pdf", MediaType.APPLICATION_PDF)
        );
        message.saveChanges();

        assertEquals("client@example.com", message.getAllRecipients()[0].toString());
        assertEquals("신발장 견적서", message.getSubject());
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        BodyPart attachment = null;
        for (int index = 0; index < multipart.getCount(); index++) {
            BodyPart part = multipart.getBodyPart(index);
            if (part.getFileName() != null) {
                attachment = part;
            }
        }
        assertTrue(attachment != null);
        assertTrue(attachment.getContentType().startsWith("application/pdf"));
        assertEquals("견적서_Q-1.pdf", attachment.getFileName());
        assertEquals("%PDF-1.4", new String(attachment.getInputStream().readAllBytes(), StandardCharsets.US_ASCII));
    }
}
