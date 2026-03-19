package com.theinterview.global.util;

import java.io.File;
import org.springframework.stereotype.Component;

@Component
public class FileDeleteUtils {

    /**
     * 디렉토리 및 하위 파일 전체 삭제 하위 파일 및 디렉토리를 재귀적으로 삭제 후 디렉토리 자체를 삭제
     *
     * @param dir 삭제할 디렉토리
     */
    public void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 하위 디렉토리가 또 있으면 또 삭제(혹시 몰라서 일단 추가)
                    deleteDirectory(file);
                } else {
                    // 파일 삭제
                    file.delete();
                }
            }
        }
        // 빈 디렉토리 삭제
        dir.delete();
    }

    /**
     * 경로 문자열로 디렉토리 삭제
     *
     * @param path 삭제할 디렉토리 경로
     */
    public void deleteDirectory(String path) {
        deleteDirectory(new File(path));
    }
}
