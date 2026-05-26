package mamokey.mom_med.backend.infra.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase Admin SDK를 이용한 FCM 푸시 발송기 (Slice 07).
 *
 * <p>{@code app.push.firebase.service-account-path}가 설정되어 있으면 실제 FCM 발송,
 * 미설정이면 콘솔 로그만 출력하는 mock 모드로 동작합니다.</p>
 */
@Component
public class FcmPushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private final String serviceAccountPath;
    private final ResourceLoader resourceLoader;
    private boolean initialized = false;

    public FcmPushSender(
            @Value("${app.push.firebase.service-account-path:}") String serviceAccountPath,
            ResourceLoader resourceLoader
    ) {
        this.serviceAccountPath = serviceAccountPath;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
            log.info("Firebase 서비스 계정 경로 미설정 — FCM 로그 모드로 동작합니다.");
            return;
        }
        try (InputStream stream = resourceLoader.getResource(serviceAccountPath).getInputStream()) {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(stream))
                        .build();
                FirebaseApp.initializeApp(options);
            }
            initialized = true;
            log.info("Firebase Admin SDK 초기화 완료. path={}", serviceAccountPath);
        } catch (IOException e) {
            log.warn("Firebase 서비스 계정 파일 읽기 실패 — 로그 모드로 동작합니다. path={}", serviceAccountPath, e);
        }
    }

    /**
     * FCM 토큰으로 푸시 알림을 발송합니다.
     *
     * @return "sent" (성공), "failed" (FCM 오류), "simulated" (mock 모드)
     */
    public String send(String fcmToken, PushMessage message) {
        if (!initialized) {
            log.info("[FCM MOCK] token={} | title={} | body={}", fcmToken, message.title(), message.body());
            return "simulated";
        }
        try {
            String messageId = FirebaseMessaging.getInstance().send(
                    Message.builder()
                            .setToken(fcmToken)
                            .setNotification(Notification.builder()
                                    .setTitle(message.title())
                                    .setBody(message.body())
                                    .build())
                            .build()
            );
            log.debug("FCM 발송 성공. messageId={}", messageId);
            return "sent";
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 발송 실패. token={}: {}", fcmToken, e.getMessage());
            return "failed";
        }
    }
}
