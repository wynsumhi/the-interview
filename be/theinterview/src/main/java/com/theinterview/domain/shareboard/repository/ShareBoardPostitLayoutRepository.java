package com.theinterview.domain.shareboard.repository;

import com.theinterview.domain.shareboard.entity.ShareBoardPostitLayout;
import com.theinterview.domain.shareboard.enums.ShareBoardPostitType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareBoardPostitLayoutRepository extends
        JpaRepository<ShareBoardPostitLayout, Long> {

    Slice<ShareBoardPostitLayout> findAllByShareBoardIdAndPostitTypeOrderByUpdatedAtDesc(
            Integer shareBoardId, ShareBoardPostitType postitType, Pageable pageable);

    Optional<ShareBoardPostitLayout> findByShareBoardIdAndPostitTypeAndSourceId(
            Integer shareBoardId, ShareBoardPostitType postitType, Integer sourceId);

    List<ShareBoardPostitLayout> findAllByShareBoardIdAndPostitTypeAndSourceIdIn(
            Integer shareBoardId, ShareBoardPostitType postitType, List<Integer> sourceIds);

    void deleteByShareBoardIdAndPostitTypeAndSourceId(Integer shareBoardId,
            ShareBoardPostitType postitType, Integer sourceId);

    void deleteAllByShareBoardId(Integer shareBoardId);
}
