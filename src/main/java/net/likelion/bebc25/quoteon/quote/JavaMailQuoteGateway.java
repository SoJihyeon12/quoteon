package net.likelion.bebc25.quoteon.quote;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Component
@EnableConfigurationProperties(QuoteMailProperties.class)
public class JavaMailQuoteGateway implements QuoteMailGateway {

    @Override
    public void send(
            QuoteMailProperties properties,
            String recipient,
            String subject,
            String content,
            QuoteFile pdf
    ) {
        JavaMailSenderImpl sender = sender(properties);
        MimeMessage message = sender.createMimeMessage();
        prepare(message, properties.username(), recipient, subject, content, pdf);
        sender.send(message);
    }

    static void prepare(
            MimeMessage message,
            String from,
            String recipient,
            String subject,
            String content,
            QuoteFile pdf
    ) {
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(content, false);
            helper.addAttachment(pdf.filename(), new ByteArrayResource(pdf.bytes()), pdf.mediaType().toString());
        } catch (MessagingException exception) {
            throw new IllegalStateException("quote email was not prepared", exception);
        }
    }

    private static JavaMailSenderImpl sender(QuoteMailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.portNumber());
        sender.setUsername(properties.username());
        sender.setPassword(properties.password());
        Properties mail = new Properties();
        mail.put("mail.smtp.auth", properties.smtpAuth());
        mail.put("mail.smtp.starttls.enable", properties.starttlsEnable());
        sender.setJavaMailProperties(mail);
        return sender;
    }
}
