package com.hy.estate.batch.store;

import com.hy.estate.batch.config.CollectProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRawResponseStoreTest {

    // 테스트마다 새로 만들어지고 끝나면 지워지는 임시 폴더. 실제 batch/data/raw는 건드리지 않는다.
    @TempDir
    Path baseDir;

    private LocalRawResponseStore store() {
        return new LocalRawResponseStore(new CollectProperties(3, baseDir.toString()));
    }

    @Test
    void 중간_폴더가_없어도_만들고_저장한다() throws IOException {
        String key = "apt-trade/sgg=11680/deal_ym=202609/run_date=2026-11-21/page-001.xml";

        store().save(key, "<response/>");

        Path file = baseDir.resolve(key);
        assertThat(file).exists();
        assertThat(Files.readString(file)).isEqualTo("<response/>");
    }

    @Test
    void 한글은_UTF8로_저장된다() throws IOException {
        String key = "apt-trade/sgg=11680/deal_ym=202609/run_date=2026-11-21/page-001.xml";

        store().save(key, "<aptNm>대치삼성</aptNm>");

        byte[] bytes = Files.readAllBytes(baseDir.resolve(key));
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("<aptNm>대치삼성</aptNm>");
    }

    // 같은 runDate로 재시작하면 같은 파일을 덮어쓴다 (결정 37)
    @Test
    void 같은_키로_다시_저장하면_덮어쓴다() throws IOException {
        String key = "apt-trade/sgg=11680/deal_ym=202609/run_date=2026-11-21/page-001.xml";

        store().save(key, "첫 번째 응답");
        store().save(key, "두 번째");

        assertThat(Files.readString(baseDir.resolve(key))).isEqualTo("두 번째");
    }

    // 다른 runDate는 다른 폴더라 이전 실행의 원본이 남는다 (결정 37)
    @Test
    void 실행일이_다르면_이전_원본이_남는다() {
        store().save("apt-trade/sgg=11680/deal_ym=202609/run_date=2026-11-20/page-001.xml", "어제");
        store().save("apt-trade/sgg=11680/deal_ym=202609/run_date=2026-11-21/page-001.xml", "오늘");

        assertThat(baseDir.resolve("apt-trade/sgg=11680/deal_ym=202609/run_date=2026-11-20/page-001.xml")).exists();
        assertThat(baseDir.resolve("apt-trade/sgg=11680/deal_ym=202609/run_date=2026-11-21/page-001.xml")).exists();
    }
}
