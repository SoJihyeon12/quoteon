package net.likelion.bebc25.quoteon.quote;

import net.likelion.bebc25.quoteon.quote.dto.QuoteEmailHistoryListResponse;
import net.likelion.bebc25.quoteon.quote.dto.QuoteEmailHistoryResponse;
import net.likelion.bebc25.quoteon.quote.dto.QuoteEmailResponse;
import net.likelion.bebc25.quoteon.quote.dto.SendQuoteEmailRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class QuoteEmailService {

    private static final Logger log = LoggerFactory.getLogger(QuoteEmailService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int SUBJECT_COLUMN_LENGTH = 255;

    private final QuoteDocumentService quoteDocumentService;
    private final QuoteRepository quoteRepository;
    private final QuoteEmailRepository quoteEmailRepository;
    private final QuoteMailGateway quoteMailGateway;
    private final QuoteMailProperties quoteMailProperties;
    private final TransactionTemplate historyTransaction;
    private final TransactionTemplate sentTransaction;

    public QuoteEmailService(
            QuoteDocumentService quoteDocumentService,
            QuoteRepository quoteRepository,
            QuoteEmailRepository quoteEmailRepository,
            QuoteMailGateway quoteMailGateway,
            QuoteMailProperties quoteMailProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.quoteDocumentService = quoteDocumentService;
        this.quoteRepository = quoteRepository;
        this.quoteEmailRepository = quoteEmailRepository;
        this.quoteMailGateway = quoteMailGateway;
        this.quoteMailProperties = quoteMailProperties;
        this.historyTransaction = new TransactionTemplate(transactionManager);
        this.sentTransaction = new TransactionTemplate(transactionManager);
    }

    public QuoteEmailResponse send(long memberId, long quoteId, SendQuoteEmailRequest request) {
        String subject = trimmed(request.subject());
        if (subject != null && subject.length() > SUBJECT_COLUMN_LENGTH) {
            throw new InvalidQuoteQueryException("이메일 제목은 255자 이하로 입력해 주세요.");
        }
        QuoteFile pdf = quoteDocumentService.salesPdf(memberId, quoteId);
        String recipient = request.recipient().trim();
        String content = request.content() == null ? "" : request.content().trim();
        List<String> missing = quoteMailProperties.missing();
        if (!missing.isEmpty()) {
            recordFailure(quoteId, recipient, subject, "메일 발송 설정이 없습니다: " + String.join(", ", missing));
            throw new QuoteMailNotConfiguredException(missing);
        }
        try {
            quoteMailGateway.send(
                    quoteMailProperties,
                    recipient,
                    subject == null ? "" : subject,
                    content,
                    pdf
            );
        } catch (MailException exception) {
            log.warn("quote email send failed. quoteId={}", quoteId);
            recordFailure(quoteId, recipient, subject, failureReason(exception));
            throw new QuoteEmailSendException(exception);
        }
        try {
            sentTransaction.executeWithoutResult(status -> {
                int updated = quoteEmailRepository.markSent(quoteId, memberId);
                if (updated != 1) {
                    throw new IllegalStateException("quote status was not updated");
                }
                LocalDateTime sentAt = LocalDateTime.now(SEOUL);
                quoteEmailRepository.insert(
                        quoteId,
                        recipient,
                        subject,
                        QuoteEmailRepository.SUCCESS,
                        null,
                        sentAt,
                        sentAt
                );
            });
        } catch (RuntimeException exception) {
            log.error("quote email was sent but the status was not saved. quoteId={}", quoteId, exception);
            throw new QuoteEmailStatusException(exception);
        }
        return new QuoteEmailResponse(quoteId, "SENT");
    }

    public QuoteEmailHistoryListResponse histories(long memberId, long quoteId) {
        requireOwner(memberId, quoteId);
        List<QuoteEmailHistoryResponse> items = quoteEmailRepository.findByQuoteId(quoteId).stream()
                .map(QuoteEmailService::toResponse)
                .toList();
        return new QuoteEmailHistoryListResponse(items);
    }

    public QuoteEmailResponse retry(long memberId, long quoteId, long historyId, SendQuoteEmailRequest request) {
        requireOwner(memberId, quoteId);
        QuoteEmailHistory history = quoteEmailRepository.findByIdAndQuoteId(historyId, quoteId)
                .orElseThrow(QuoteEmailHistoryNotFoundException::new);
        if (!QuoteEmailRepository.FAILED.equals(history.status())) {
            throw new QuoteEmailRetryNotAllowedException();
        }
        return send(memberId, quoteId, request);
    }

    private void requireOwner(long memberId, long quoteId) {
        SalesQuoteAccess.requireAccessible(
                quoteRepository.findAccessById(quoteId).orElseThrow(QuoteNotFoundException::new),
                memberId
        );
    }

    private static QuoteEmailHistoryResponse toResponse(QuoteEmailHistory history) {
        return new QuoteEmailHistoryResponse(
                history.id(),
                history.recipientEmail(),
                history.subject(),
                history.status(),
                history.sentAt(),
                history.failureReason(),
                history.createdAt()
        );
    }

    private void recordFailure(long quoteId, String recipient, String subject, String reason) {
        try {
            LocalDateTime createdAt = LocalDateTime.now(SEOUL);
            historyTransaction.executeWithoutResult(status -> quoteEmailRepository.insert(
                    quoteId,
                    recipient,
                    subject,
                    QuoteEmailRepository.FAILED,
                    reason,
                    null,
                    createdAt
            ));
        } catch (RuntimeException exception) {
            log.error("quote email history was not saved. quoteId={}", quoteId);
        }
    }

    private static String failureReason(MailException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "견적서 이메일을 전송하지 못했습니다.";
        }
        return message;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }
}
