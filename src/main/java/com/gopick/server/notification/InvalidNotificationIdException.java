package com.gopick.server.notification;

class InvalidNotificationIdException extends RuntimeException {
    InvalidNotificationIdException() {
        super("notificationId는 1 이상의 정수여야 합니다.");
    }
}
