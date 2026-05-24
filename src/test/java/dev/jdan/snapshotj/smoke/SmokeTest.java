package dev.jdan.snapshotj.smoke;

import static dev.jdan.snapshotj.Snap.snap;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SmokeTest {

  record ThisIsARealRecord(String word, int index, LocalDate myDate) {}

  record SubRecord(long id, Instant time, String word) {}
  record TestRecord(long id, Instant time, SubRecord subRecord) {}

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

  @Test
  void testReplacements() {

    var x = new TestRecord(123L, Instant.now(), new SubRecord(110L, Instant.now().minusSeconds(10), "Haha"));

    snap(x)
        .replacingType(Instant.class, "<Instant.class>")
        .replacingField("..id", "<id>")
        .replacingField("$.subRecord.time", "<time>")
        .matchesJson("""
            {
              "id" : "<id>",
              "subRecord" : {
                "id" : "<id>",
                "time" : "<time>",
                "word" : "Haha"
              },
              "time" : "<Instant.class>"
            }
            """);

    snap(List.of(x))
        .update()
        .replacingType(Instant.class, "<Instant.class>")
        .replacingField("..id", "<id>")
        .matchesCsv("""
            id,subRecord,time
            <id>,"{""id"":110,""time"":""<Instant.class>"",""word"":""Haha""}",<Instant.class>
            """);

  }

  @Test
  void testReplacementsInLists() {
    var xs = List.of(

        new TestRecord(110L, Instant.now(), new SubRecord(120L, Instant.now().minusSeconds(10), "Haha")),
        new TestRecord(111L, Instant.now(), new SubRecord(121L, Instant.now().minusSeconds(10), "Haha2"))
    );

    snap(xs)
        .replacingType(Instant.class, "<Instant.class>")
        .replacingField("..id", "<id>")
        .matchesJson("""
        [
          {
            "id" : "<id>",
            "subRecord" : {
              "id" : "<id>",
              "time" : "<Instant.class>",
              "word" : "Haha"
            },
            "time" : "<Instant.class>"
          },
          {
            "id" : "<id>",
            "subRecord" : {
              "id" : "<id>",
              "time" : "<Instant.class>",
              "word" : "Haha2"
            },
            "time" : "<Instant.class>"
          }
        ]
        """);

  }
}
