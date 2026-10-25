package com.vm325.inventory_back.repositories;

import com.vm325.inventory_back.entities.Notification;
import com.vm325.inventory_back.enums.RoleList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByReadOrderByCreatedAtDesc(boolean read);
    List<Notification> findAllByOrderByCreatedAtDesc();

    /** Notificaciones dirigidas a todos (recipientRole nulo) o específicamente a ese rol. */
    @Query("select n from Notification n where n.recipientRole is null or n.recipientRole = :role "
            + "order by n.createdAt desc")
    List<Notification> findVisibleTo(RoleList role);

    @Query("select n from Notification n where (n.recipientRole is null or n.recipientRole = :role) "
            + "and n.read = :read order by n.createdAt desc")
    List<Notification> findVisibleToByRead(RoleList role, boolean read);
}
