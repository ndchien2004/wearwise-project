package org.group7.wearwise.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Gửi email đặt lại mật khẩu.
 *
 * <p>Nếu chưa cấu hình SMTP ({@code spring.mail.host} trống) hoặc gửi thất bại,
 * link đặt lại được ghi ra log để môi trường phát triển vẫn dùng được ngay,
 * không cần dựng máy chủ mail.</p>
 */
@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;
    private final String resetUrlBase;

    public AuthMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${wearwise.mail.from:no-reply@wearwise.local}") String fromAddress,
            @Value("${wearwise.mail.reset-url-base:http://localhost:5173/reset-password}") String resetUrlBase
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
        this.resetUrlBase = resetUrlBase;
    }

    public void sendPasswordResetEmail(String email, String username, String rawToken, long expiresInMinutes) {
        String resetUrl = buildResetUrl(rawToken);
        String subject = "WearWise — Đặt lại mật khẩu";
        String body = """
                Xin chào %s,

                Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản WearWise của bạn.
                Mở link dưới đây để chọn mật khẩu mới (link có hiệu lực trong %d phút):

                %s

                Nếu bạn không yêu cầu việc này, hãy bỏ qua email — mật khẩu hiện tại vẫn giữ nguyên.

                — WearWise
                """.formatted(username, expiresInMinutes, resetUrl);

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!isConfigured(mailSender)) {
            logResetLink(email, resetUrl, "chưa cấu hình SMTP (spring.mail.host)");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Đã gửi email đặt lại mật khẩu tới {}", maskEmail(email));
        } catch (MailException exception) {
            log.warn("Gửi email đặt lại mật khẩu thất bại: {}", exception.getMessage());
            logResetLink(email, resetUrl, "gửi email thất bại");
        }
    }

    public void sendRegistrationOtpEmail(String email, String username, String otp, long expiresInMinutes) {
        String subject = "WearWise — Mã xác nhận đăng ký";
        String body = """
                Xin chào %s,

                Mã xác nhận (OTP) để hoàn tất đăng ký tài khoản WearWise của bạn là:

                    %s

                Mã có hiệu lực trong %d phút. Hãy nhập mã này vào màn hình đăng ký để tạo tài khoản.

                Nếu bạn không thực hiện đăng ký này, hãy bỏ qua email.

                — WearWise
                """.formatted(username, otp, expiresInMinutes);

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!isConfigured(mailSender)) {
            log.warn("[WearWise] Chưa cấu hình SMTP — mã OTP đăng ký cho {}: {}", maskEmail(email), otp);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Đã gửi mã OTP đăng ký tới {}", maskEmail(email));
        } catch (MailException exception) {
            log.warn("Gửi mã OTP đăng ký thất bại: {}", exception.getMessage());
            log.warn("[WearWise] Mã OTP đăng ký cho {}: {}", maskEmail(email), otp);
        }
    }

    /** Có bean JavaMailSender vẫn chưa đủ — host rỗng thì coi như chưa cấu hình. */
    private boolean isConfigured(JavaMailSender mailSender) {
        if (mailSender == null) {
            return false;
        }

        if (mailSender instanceof JavaMailSenderImpl mailSenderImpl) {
            String host = mailSenderImpl.getHost();
            return host != null && !host.isBlank();
        }

        return true;
    }

    private String buildResetUrl(String rawToken) {
        String separator = resetUrlBase.contains("?") ? "&" : "?";
        return resetUrlBase + separator + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private void logResetLink(String email, String resetUrl, String reason) {
        log.warn("""
                        [WearWise] Không gửi được email đặt lại mật khẩu ({}). Dùng link sau cho {}:
                        {}""",
                reason, maskEmail(email), resetUrl);
    }

    /** Che bớt email trong log để không ghi trọn địa chỉ người dùng. */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex < 0 ? "" : email.substring(atIndex));
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
