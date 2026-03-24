package com.theinterview.domain.shareboard.repository;

import com.theinterview.domain.shareboard.entity.ShareBoard;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 공유 보드 엔티티에 대한 JPA Repository
public interface ShareBoardRepository extends JpaRepository<ShareBoard, Integer> {

    // 보드 소유자 회원 ID 로 공유 보드를 조회한다
    // 현재는 한 사용자당 하나의 보드만 존재한다는 전제를 사용한다
    Optional<ShareBoard> findByOwnerId(Integer ownerId);
}
