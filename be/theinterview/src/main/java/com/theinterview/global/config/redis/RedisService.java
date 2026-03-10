package com.theinterview.global.config.redis;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
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
     * @return 저장된 값(없으면 null)
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
}

