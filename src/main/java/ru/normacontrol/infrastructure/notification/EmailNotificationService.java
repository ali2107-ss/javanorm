package ru.normacontrol.infrastructure.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Document;
import ru.normacontrol.domain.entity.User;
import ru.normacontrol.domain.entity.Violation;
import ru.normacontrol.domain.enums.ViolationSeverity;

/**
 * Сервис email-уведомлений НормаКонтроль.
 *
 * <p>Отправляет HTML-письма при:
 * <ul>
 *   <li>завершении проверки документа ({@link #sendCheckCompleted})</li>
 *   <li>регистрации нового пользователя ({@link #sendWelcome})</li>
 * </ul>
 * </p>
 *
 * <p>Все письма отправляются асинхронно через Spring {@code @Async},
 * чтобы не блокировать основной поток обработки документа.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@normacontrol.ru}")
    private String fromAddress;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Отправить HTML-уведомление о завершении проверки документа.
     *
     * @param user   пользователь-владелец документа
     * @param result результат проверки
     * @param doc    проверенный документ
     */
    @Async
    public void sendCheckCompleted(User user, CheckResult result, Document doc) {
        String subject = String.format("НормаКонтроль: проверка завершена — %d/100",
                result.getComplianceScore());
        String html = buildCheckCompletedHtml(user, result, doc);
        sendHtml(user.getEmail(), subject, html);
    }

    /**
     * Отправить приветственное письмо после регистрации.
     *
     * @param user новый пользователь
     */
    @Async
    public void sendWelcome(User user) {
        String subject = "Добро пожаловать в НормаКонтроль!";
        String html    = buildWelcomeHtml(user);
        sendHtml(user.getEmail(), subject, html);
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    /** Низкоуровневая отправка HTML-письма. */
    private void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "НормаКонтроль");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email отправлен: to={}, subject={}", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Ошибка отправки email на {}: {}", to, e.getMessage(), e);
        }
    }

    // ── HTML builders ──────────────────────────────────────────────────────────

    /** Построить HTML-тело письма «Проверка завершена». */
    private String buildCheckCompletedHtml(User user, CheckResult result, Document doc) {
        int    score     = result.getComplianceScore() != null ? result.getComplianceScore() : 0;
        String color     = scoreColor(score);
        String verdict   = result.isPassed() ? "✅ ПРОШЁЛ" : "❌ НЕ ПРОШЁЛ";
        String name      = user.getFullName() != null ? user.getFullName() : user.getEmail();
        String docName   = doc.getOriginalFilename();
        String reportUrl = baseUrl + "/check.html?docId=" + doc.getId();

        long critical = countBySeverity(result, ViolationSeverity.CRITICAL);
        long warning  = countBySeverity(result, ViolationSeverity.WARNING);
        long info     = countBySeverity(result, ViolationSeverity.INFO);

        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <title>Проверка завершена</title>
                </head>
                <body style="margin:0;padding:0;font-family:'Segoe UI',Arial,sans-serif;
                             background:#f0f4f8;color:#1a2332;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f0f4f8;padding:32px 0">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;
                                    box-shadow:0 4px 20px rgba(0,0,0,.12);overflow:hidden;">

                        <!-- Header -->
                        <tr>
                          <td style="background:#1F3864;padding:28px 36px;text-align:center;">
                            <div style="font-size:32px;margin-bottom:6px;">🔍</div>
                            <div style="font-size:22px;font-weight:700;color:#fff;
                                        letter-spacing:.5px;">НормаКонтроль</div>
                            <div style="font-size:12px;color:rgba(255,255,255,.6);margin-top:4px;">
                              ГОСТ 19.201-78
                            </div>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:36px;">
                            <p style="font-size:16px;margin:0 0 24px;">
                              Здравствуйте, <strong>%s</strong>!
                            </p>
                            <p style="font-size:15px;color:#6b7a90;margin:0 0 28px;">
                              Проверка документа <strong>«%s»</strong> завершена.
                            </p>

                            <!-- Score circle -->
                            <div style="text-align:center;margin-bottom:28px;">
                              <div style="display:inline-block;width:110px;height:110px;
                                          border-radius:50%%;border:6px solid %s;
                                          line-height:1;text-align:center;padding-top:22px;">
                                <div style="font-size:36px;font-weight:700;color:%s;">%d</div>
                                <div style="font-size:12px;color:%s;font-weight:600;">баллов</div>
                              </div>
                              <div style="margin-top:12px;font-size:20px;font-weight:700;color:%s;">
                                %s
                              </div>
                            </div>

                            <!-- Counters table -->
                            <table width="100%%" cellpadding="0" cellspacing="0"
                                   style="margin-bottom:28px;">
                              <tr>
                                <td width="33%%" style="text-align:center;
                                    background:#fdecea;border-radius:8px;padding:14px 8px;
                                    margin-right:8px;">
                                  <div style="font-size:22px;font-weight:700;color:#c0392b;">%d</div>
                                  <div style="font-size:12px;color:#c0392b;font-weight:600;">
                                    🔴 Критично
                                  </div>
                                </td>
                                <td width="4%%"></td>
                                <td width="33%%" style="text-align:center;
                                    background:#fef9e7;border-radius:8px;padding:14px 8px;">
                                  <div style="font-size:22px;font-weight:700;color:#b7770d;">%d</div>
                                  <div style="font-size:12px;color:#b7770d;font-weight:600;">
                                    🟡 Предупреждений
                                  </div>
                                </td>
                                <td width="4%%"></td>
                                <td width="33%%" style="text-align:center;
                                    background:#e8f1fb;border-radius:8px;padding:14px 8px;">
                                  <div style="font-size:22px;font-weight:700;color:#2E74B5;">%d</div>
                                  <div style="font-size:12px;color:#2E74B5;font-weight:600;">
                                    ℹ️ Информация
                                  </div>
                                </td>
                              </tr>
                            </table>

                            <!-- CTA button -->
                            <div style="text-align:center;margin-bottom:28px;">
                              <a href="%s"
                                 style="display:inline-block;padding:14px 36px;
                                        background:#2E74B5;color:#fff;text-decoration:none;
                                        border-radius:8px;font-size:15px;font-weight:600;
                                        letter-spacing:.3px;">
                                📄 Открыть полный отчёт
                              </a>
                            </div>

                            <p style="font-size:13px;color:#6b7a90;text-align:center;
                                      margin:0;border-top:1px solid #dde3ec;padding-top:20px;">
                              НормаКонтроль v1.0 &nbsp;•&nbsp;
                              Автоматическая проверка документов по ГОСТ 19.201-78
                            </p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(name, docName,
                color, color, score, color, color, verdict,
                critical, warning, info,
                reportUrl);
    }

    /** Построить HTML-тело приветственного письма. */
    private String buildWelcomeHtml(User user) {
        String name    = user.getFullName() != null ? user.getFullName() : user.getEmail();
        String loginUrl = baseUrl + "/index.html";

        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head><meta charset="UTF-8"/><title>Добро пожаловать</title></head>
                <body style="margin:0;padding:0;font-family:'Segoe UI',Arial,sans-serif;
                             background:#f0f4f8;">
                  <table width="100%%" cellpadding="0" cellspacing="0"
                         style="background:#f0f4f8;padding:32px 0;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0"
                             style="background:#fff;border-radius:12px;
                                    box-shadow:0 4px 20px rgba(0,0,0,.12);overflow:hidden;">
                        <tr>
                          <td style="background:#1F3864;padding:28px 36px;text-align:center;">
                            <div style="font-size:36px;">🔍</div>
                            <div style="font-size:22px;font-weight:700;color:#fff;margin-top:6px;">
                              НормаКонтроль
                            </div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:36px;text-align:center;">
                            <div style="font-size:48px;margin-bottom:16px;">🎉</div>
                            <h1 style="font-size:22px;font-weight:700;margin:0 0 12px;">
                              Добро пожаловать, %s!
                            </h1>
                            <p style="font-size:15px;color:#6b7a90;margin:0 0 28px;
                                      line-height:1.6;">
                              Вы успешно зарегистрированы в системе автоматической проверки
                              документов по ГОСТ 19.201-78. Теперь вы можете загружать
                              документы и получать детальные отчёты о соответствии требованиям.
                            </p>
                            <a href="%s"
                               style="display:inline-block;padding:14px 36px;
                                      background:#2E74B5;color:#fff;text-decoration:none;
                                      border-radius:8px;font-size:15px;font-weight:600;">
                              🚀 Перейти к системе
                            </a>
                            <p style="font-size:13px;color:#6b7a90;margin-top:28px;">
                              НормаКонтроль v1.0
                            </p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(name, loginUrl);
    }

    // ── Util ───────────────────────────────────────────────────────────────────

    private long countBySeverity(CheckResult result, ViolationSeverity severity) {
        if (result.getViolations() == null) return 0;
        return result.getViolations().stream()
                .filter(v -> severity.equals(v.getSeverity()))
                .count();
    }

    private String scoreColor(int score) {
        if (score >= 80) return "#27AE60";
        if (score >= 60) return "#F39C12";
        return "#E74C3C";
    }
}
