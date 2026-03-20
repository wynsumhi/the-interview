package com.theinterview.global.config.redis;

import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    // RedisConfig를 통해 스프링 빈에 등록해둔 템플릿
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 데이터를 Redis에 저장함(TTL 설정 포함)
     *
     * @param key      저장할 키
     * @param data     저장할 값
     * @param duration 만료 시간(이 시간이 지나면 Redis에서 자동 삭제)
     */
    public void setValues(String key, String data, Duration duration) {
        ValueOperations<String, String> values = redisTemplate.opsForValue();
        values.set(key, data, duration);
        log.info("Redis에 데이터 저장 완료 Key: {}, TTL: {}초", key, duration.getSeconds());
    }

    /**
     * 키를 기반으로 Redis에서 데이터를 조회함
     *
     * @param key 찾을 키
     * @return 저장된 값(없으면 reull)
     */
    public String getValues(String key) {
        ValueOperations<String, String> values = redisTemplate.opsForValue();
        return values.get(key);
    }

    // 데이터 삭제
    public void deleteValues(String key) {
        redisTemplate.delete(key);
        log.info("Redis에서 데이터 삭제 완료 - Key: {}", key);
    }

    // 해당 키가 Redis에 존재하는지 확인
    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    // 왜 Sorted Set을 선택하였는가?
    // WebClient의 비동기 통신 특성 상 3번 청크가 2번 청크보다 먼저 addChunkToSortedSet을 호출하더라도
    // Redis 내부에서 알아서 index(score)를 기준으로 줄을 세워 줌 나중에 반드시 순서가 보장됨

    /**
     * STT 청크 데이터를 순서(Index)를 보장하여 Sorted Set에 저장함
     *
     * @param key        Redis Key (예: interview:1:question:5:chunks)
     * @param chunkText  STT로 변환된 텍스트
     * @param chunkIndex 프론트엔드에서 보낸 청크의 순서 (Score로 사용됨)
     */
    public void addChunkToSortedSet(String key, String chunkText, double chunkIndex) {

        // ZSet(Sorted Set) 오퍼레이션 호출
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // add(key, value, score) - score 기준으로 자동 오름차순 정렬
        zSetOps.add(key, chunkText, chunkIndex);

        log.info("Redis Sorted Set에 청크 저장 완료 - Key: {}, Index: {}", key, chunkIndex);
    }

    /**
     * Sorted Set에 저장된 모든 청크 텍스트를 순서대로 조합하여 반환함
     *
     * @param key Redis Key
     * @return 순서가 보장된 전체 텍스트 조합
     */
    public String getOrderedChucks(String key) {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // range(key, 0, -1)은 마지막까지 Score 기준 오름차순 정렬
        Set<String> orderedChunks = zSetOps.range(key, 0, -1);

        if (orderedChunks == null || orderedChunks.isEmpty()) {
            return "";
        }

        // 문자열을 하나로 이어붙여 반환함
        return String.join(" ", orderedChunks);
    }

    // redis에 저장되어 있는 청크 수를 확인하는 메서드
    public Long savedChunkCount(String key) {

        return redisTemplate.opsForZSet().size(key);
    }

    // 사용자가 다음 버튼을 눌러서 이벤트를 발생시키면 총 저장되어야 하는 청크 수를 저장하는 메서드
    public void saveAllChuckCount(String key, int totalChunks) {

        ValueOperations<String, String> values = redisTemplate.opsForValue();

        values.set(key, String.valueOf(totalChunks));
    }

}

