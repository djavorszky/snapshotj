# Patterns

Recurring problems when snapshotting real-world code, and the idioms that solve them.

The common thread: **snapshot comparison is exact** (modulo trailing-whitespace and trailing-newline normalization). Anything non-deterministic in the rendered output — wall-clock time, generated IDs, iteration order — will produce a flaky snapshot. The fix is always to make the output deterministic *before* it reaches `matches(...)`, either by controlling the input or by normalizing in the renderer.

- [Time and `Clock`](#time-and-clock)
- [Spring Boot and `Clock`](#spring-boot-and-clock)
- [Database-generated identifiers](#database-generated-identifiers)
- [General rule: normalize in the renderer](#general-rule-normalize-in-the-renderer)

## Time and `Clock`

`Instant.now()`, `LocalDateTime.now()`, `System.currentTimeMillis()` are global, ambient, and unpinnable. Any object that captures one at construction will serialize differently every run.

**Don't** call the static `now()` in code under test:

```java
class OrderService {
    Order place(Cart cart) {
        return new Order(cart, Instant.now()); // ambient — untestable
    }
}
```

**Do** inject a `java.time.Clock` and pin it from the test:

```java
class OrderService {
    private final Clock clock;

    OrderService(Clock clock) { this.clock = clock; }

    Order place(Cart cart) {
        return new Order(cart, Instant.now(clock));
    }
}
```

```java
@Test
void serializesOrder() {
    var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    var service = new OrderService(clock);

    var order = service.place(new Cart(...));

    snap(order).matchesJson("""
            {
              "cart": { ... },
              "placedAt": "2026-01-01T00:00:00Z"
            }
            """);
}
```

`Clock.fixed(...)` freezes time. `Clock.tickMillis(...)` / `Clock.offset(...)` give controlled motion if the test needs to advance time deterministically.

### Why this is non-negotiable for snapshots

Other test styles can tolerate "approximately now" with a tolerance window. Snapshots cannot — `2026-01-01T00:00:00.001Z` ≠ `2026-01-01T00:00:00.002Z` at the string level, so the comparison fails. Pin the clock, or don't include the timestamp in the snapshot at all (normalize it out — see [below](#general-rule-normalize-in-the-renderer)).

## Spring Boot and `Clock`

Spring Boot **does not auto-configure a `Clock` bean.** If you write `@Autowired Clock clock`, the context fails to start unless you've declared one yourself. There is no `spring.clock=fixed` property.

This bites people the first time they try to inject a `Clock` into a `@Service`. The fix:

```java
@Configuration
class ClockConfig {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
```

Then in a `@SpringBootTest` override it with a fixed clock:

```java
@TestConfiguration
class FixedClockConfig {
    @Bean
    @Primary
    Clock testClock() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
}

@SpringBootTest
@Import(FixedClockConfig.class)
class OrderServiceIT {

    @Autowired OrderService service;

    @Test
    void placesOrder() {
        snap(service.place(cart)).matchesJson("""
                ...
                """);
    }
}
```

Notes:

- `@Primary` on the test bean is what causes Spring to prefer it over the production `Clock`. Without `@Primary` you get an ambiguous-bean error.
- `@MockBean Clock clock` works too, but you then have to stub `clock.instant()` / `clock.getZone()` on every test. A fixed bean is less ceremony.
- If the production code uses `LocalDateTime.now()` instead of `LocalDateTime.now(clock)`, no amount of bean wiring helps — the static call ignores the bean entirely. Audit the code path before assuming Spring is at fault.
- `@DataJpaTest`, `@WebMvcTest`, and other sliced contexts don't include `@Configuration` classes outside the slice unless you `@Import` them. Add the fixed clock to the slice explicitly.

## Database-generated identifiers

Auto-generated IDs are the second-most-common source of snapshot flakiness after time. Postgres sequences, MySQL `AUTO_INCREMENT`, Hibernate `@GeneratedValue` — all advance based on JVM/DB state that the test doesn't control.

Specifically:

- Test ordering changes the ID a row gets.
- Sequence values are **not** rolled back when a transaction rolls back — the sequence already advanced. Even `@Transactional` rollback leaves a gap.
- Parallel tests against a shared DB see interleaved IDs.
- A `RANDOM_UUID()`-backed primary key is fresh on every insert by design.

Three options, in rough order of preference:

### 1. Use explicit IDs in the test fixture

If the test owns the inserts, supply the IDs:

```java
jdbc.update("INSERT INTO users (id, name) VALUES (?, ?)", 1L, "Ada");
jdbc.update("INSERT INTO users (id, name) VALUES (?, ?)", 2L, "Grace");

snap(repo.findAll()).matchesCsv("""
        id,name
        1,Ada
        2,Grace
        """);
```

Bypasses the sequence entirely. Works for repository tests, breaks down when you're testing the code that *issues* the insert.

### 2. Reset the sequence per test

If the code under test does the insert, reset the sequence before exercising it. Postgres:

```java
@BeforeEach
void resetSequences() {
    jdbc.execute("ALTER SEQUENCE users_id_seq RESTART WITH 1");
}
```

H2:

```sql
ALTER SEQUENCE users_id_seq RESTART WITH 1
-- or, for AUTO_INCREMENT:
ALTER TABLE users ALTER COLUMN id RESTART WITH 1
```

This makes the first inserted row deterministically get `id = 1`. Combine with `TRUNCATE` / `@Sql(scripts="cleanup.sql")` so the table is empty when the sequence resets.

Caveat: `@Transactional` test isolation doesn't help here — the sequence reset must happen *outside* the transaction the test rolls back, or it'll be undone. `@BeforeEach` with a separate `JdbcTemplate` call works; `@Sql(executionPhase = BEFORE_TEST_METHOD)` works.

### 3. Normalize the ID out in the renderer

When neither of the above fits (e.g. testing a service that returns an object graph with deep auto-IDs, or a `UUID`-keyed entity), use a custom renderer with `matches(...)` and replace IDs with a placeholder:

```java
snap(service.list()).matches("""
        [
          {"id": "<ID>", "name": "Ada"},
          {"id": "<ID>", "name": "Grace"}
        ]
        """, list -> normalizeIds(toJson(list)));

private static final Pattern UUID_RE =
        Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

private static String normalizeIds(String json) {
    return UUID_RE.matcher(json).replaceAll("<ID>");
}
```

This is the right tool when the IDs are genuinely irrelevant to what the test asserts. The snapshot still proves *structure and non-ID fields* — it just stops caring which specific UUID came out.

There is no built-in `<snap:ignore>` placeholder; redact in the renderer instead.

## General rule: normalize in the renderer

When you can't pin the input, **render to a canonical form first**. The renderer is `Function<T, String>` — anything you can compute in Java is fair game.

```java
snap(response).matches("""
        request-id: <ID>
        timestamp: <TS>
        status: 200
        body: {"ok": true}
        """, r -> """
                request-id: %s
                timestamp: %s
                status: %d
                body: %s
                """.formatted(
                        redactUuid(r.requestId()),
                        redactInstant(r.timestamp()),
                        r.status(),
                        r.body()));
```

Three keys to using this pattern well:

1. **Redact deterministically.** Replace with a fixed marker (`<ID>`, `<TS>`), never with a hash or random string — those drift too.
2. **Redact narrowly.** Don't blanket-redact every UUID-shaped substring if some of them are domain-meaningful. Use field-specific replacement, not regex shotgun.
3. **Keep the renderer pure.** It will run on every test invocation; non-deterministic logic inside the renderer is the same problem as non-deterministic input.

The built-in `matchesJson` / `matchesCsv` cover the deterministic case (alphabetical properties, ISO-8601 dates, `\n` line endings). When you need redaction, drop down to `matches(expected, renderer)` and compose your own.
