package dev.jdan.snapshotj.smoke;

import static dev.jdan.snapshotj.Snap.snap;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SmokeTest {

  record ThisIsARealRecord(String word, int index, LocalDate myDate) {}

  @Test
  void smokeTestAgainstRecord() {
    var aRecord = new ThisIsARealRecord("my word!", 42, LocalDate.of(2026, 5, 17));

    snap(aRecord)
        .matchesJson("""
            {
              "index" : 42,
              "myDate" : "2026-05-17",
              "word" : "my word!"
            }
            """);
  }

  @Test
  void smokeTestAgainstArray() {
    var aRecord = new ThisIsARealRecord(
        "my word!", 42, LocalDate.of(2026, 5, 17)
    );

    var bRecord = new ThisIsARealRecord(
        "fav word is bob", 1, LocalDate.of(2020, 5, 17)
    );

    var list = List.of(aRecord, bRecord);

    snap(list)
        .matchesJson("""
            [
              {
                "index" : 42,
                "myDate" : "2026-05-17",
                "word" : "my word!"
              },
              {
                "index" : 1,
                "myDate" : "2020-05-17",
                "word" : "fav word is bob"
              }
            ]
            """);

    snap(list)
        .matchesCsv("""
            index,myDate,word
            42,2026-05-17,my word!
            1,2020-05-17,fav word is bob
            """);
  }

}
