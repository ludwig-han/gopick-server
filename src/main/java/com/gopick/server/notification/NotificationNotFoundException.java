package com.gopick.server.notification;

class NotificationNotFoundException extends RuntimeException {
    NotificationNotFoundException() {
        super("알림을 찾을 수 없습니다.");
    }
}
