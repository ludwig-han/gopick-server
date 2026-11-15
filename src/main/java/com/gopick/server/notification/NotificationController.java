package com.gopick.server.notification;

import com.gopick.server.global.ErrorResponse;
import com.gopick.server.global.ValidationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/me/notifications")
    public List<NotificationResponse> list(HttpServletRequest request) {
        return service.list(requireAccountId(request));
    }

    @PatchMapping("/me/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable("notificationId") String notificationId, HttpServletRequest request) {
        service.markRead(requireAccountId(request), notificationId);
    }

    @ExceptionHandler(InvalidNotificationIdException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ValidationErrorResponse invalidId(InvalidNotificationIdException exception) {
        return new ValidationErrorResponse("VALIDATION_ERROR", "입력값을 확인해주세요.",
                Map.of("notificationId", exception.getMessage()));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse notFound(NotificationNotFoundException exception) {
        return new ErrorResponse("NOTIFICATION_NOT_FOUND", exception.getMessage());
    }
}
