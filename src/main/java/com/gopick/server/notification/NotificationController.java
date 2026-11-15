package com.gopick.server.notification;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.gopick.server.global.SessionUtil.requireAccountId;
@RestController
public class NotificationController {
 private final NotificationService service;
 public NotificationController(NotificationService service) { this.service=service; }
 @GetMapping("/me/notifications") public List<NotificationResponse> list(HttpServletRequest request) { return service.list(requireAccountId(request)); }
 @PatchMapping("/me/notifications/{notificationId}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@PathVariable("notificationId") Long id, HttpServletRequest request) { service.markRead(requireAccountId(request),id); }
}
