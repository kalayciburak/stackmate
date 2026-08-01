# Phase 3 console gallery

This gallery separates actual product console output from laboratory model prose.
Gate A failed, so no llama.cpp runtime or provisioning console flow exists.

## Actual English deterministic diagnosis

```text
+--------------------------------------------------------------------------------------------------+
| StackMate Quick Diagnosis                                                                        |
| Category: DATABASE_CONSTRAINT                                                                    |
| Evidence: HIGH                                                                                   |
| Summary: A database unique constraint was violated                                               |
| Root: java.sql.SQLException: duplicate key value violates unique constraint                      |
| "uk_sample_reference"                                                                            |
| First app location: SampleDatabaseException#create(SampleDatabaseException.java:47)              |
| Request: GET /demo/database/duplicate                                                            |
| Source: Deterministic rules                                                                      |
| Runtime: Java 21.0.12 | Spring Boot 4.1.0 | Network disabled                                     |
+--------------------------------------------------------------------------------------------------+
```

## Actual Turkish deterministic diagnosis

```text
+--------------------------------------------------------------------------------------------------+
| StackMate Hızlı Tanı                                                                             |
| Kategori: VERİTABANI KISITI                                                                      |
| Kanıt gücü: YÜKSEK                                                                               |
| Özet: Benzersizlik kısıtı ihlal edildi                                                           |
| Kök hata: java.sql.SQLException: duplicate key value violates unique constraint                  |
| "uk_sample_reference"                                                                            |
| İlk uygulama konumu: SampleDatabaseException#create(SampleDatabaseException.java:47)             |
| İstek: GET /demo/database/duplicate                                                              |
| Kaynak: Deterministik kurallar                                                                   |
| Çalışma ortamı: Java 21.0.12 | Spring Boot 4.1.0 | Ağ devre dışı                                 |
+--------------------------------------------------------------------------------------------------+
```

These cards are sanitized excerpts captured during Phase 3 baseline reproduction;
the normal Spring Boot HTTP 500 response remained unchanged.

## Validated laboratory prose, not product output

English Qwen3.5-4B greedy example:

```text
Summary: Unique constraint violation detected.
Explanation: SQL state 23505 confirms a unique constraint was violated.
Selected cause/check: supplied IDs only
```

Turkish Qwen3.5-4B greedy example:

```text
Özet: Veritabanı benzersiz kısıt ihlali hatası.
Açıklama: SQL durumu 23505, benzersiz kısıt ihlali olduğunu gösterir.
Seçilen neden/kontrol: yalnızca sağlanan kimlikler
```

These strings passed the Phase 2 validator in the isolated screening harness. They
must not be confused with a supported `StackMate Local Explanation` feature: the
candidate failed Gate A on other cases.

## Provisioning and fallback states

The following requested gallery states are deliberately unavailable:

| Requested state | Actual Phase 3 result |
|---|---|
| First startup / background provisioning | `NOT_IMPLEMENTED` — Gate A failed |
| Provisioning cache hit | `NOT_IMPLEMENTED` |
| Download failure | `NOT_IMPLEMENTED`; no downloader exists |
| llama.cpp local explanation | Laboratory output only, not integrated |
| Invalid llama.cpp output fallback | Invalid lab output was quarantined; no production adapter exists |

The existing local starter retains its Phase 2 Jlama behavior: a missing, invalid,
timed-out, or failed optional model leaves the deterministic quick diagnosis active.
