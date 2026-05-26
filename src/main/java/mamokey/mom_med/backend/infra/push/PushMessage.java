package mamokey.mom_med.backend.infra.push;

/**
 * FCM 푸시 알림 메시지 (Slice 07).
 */
public record PushMessage(String title, String body) {}
