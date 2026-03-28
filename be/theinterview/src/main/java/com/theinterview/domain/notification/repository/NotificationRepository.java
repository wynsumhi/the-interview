package com.theinterview.domain.notification.repository;

import com.theinterview.domain.notification.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    @Query("SELECT n FROM Notification n " +
            "JOIN FETCH n.sender " +
            "JOIN FETCH n.interview " +
            "WHERE n.receiver.id = :receiverId " +
            "ORDER BY n.createdAt DESC")
    List<Notification> findAllByReceiverId(@Param("receiverId") Integer receiverId);

    void deleteAllByReceiverId(Integer receiverId);
}
