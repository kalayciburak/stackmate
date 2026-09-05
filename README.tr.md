# StackMate

> Spring MVC hataları için deterministik, yerel tanılama.

StackMate `0.1.0-rc.3`, Java 21 Spring Boot uygulamaları için bir public alpha sürümüdür. Spring MVC servlet
zincirinden kaçan exception'ları gözlemler; sınırlı ve sanitize edilmiş snapshot üretir; kanıta dayalı tanı çıkarır;
tekrarlanan incident'ları dedup eder ve uygulama loguna kısa bir İngilizce tanı yazar. İsteğe bağlı export, aynı
sanitize edilmiş deterministik kanıtı yerel dosya sistemine yazar.

Bu sürüm LLM, inference runtime, model, downloader, cloud çağrısı, telemetri veya upload yolu içermez. Tarihsel yerel
model deneyleri [docs/research/local-llm-findings.md](docs/research/local-llm-findings.md) altında izole edilmiştir ve
ürün yeteneği değildir.

İngilizce dokümantasyon: [README.md](README.md)

## Alpha durumu

Public API ve davranışlar kararlı sürümden önce değişebilir. RC2 yerel doğrulamayı ve Ubuntu x64, Windows x64 ve
macOS arm64 üzerindeki exact-commit hosted qualification'ı geçti. Her hosted platform external consumer dahil 549
Surefire + 6 Failsafe testi sıfır failure, error ve skip ile tamamladı. RC1 sabit, yalnız yerel bir checkpoint olarak
kalır ve RC2'nin CI/Central metadata düzeltmeleriyle superseded durumdadır. Maven Central'a artifact yayımlanmadı,
remote release oluşturulmadı, RC tag'i push edilmedi ve GPG imzalama yapılmadı.

## Neler yapar?

- Spring MVC servlet istek zincirinden kaçan işlenmemiş exception'ları yutmadan veya değiştirmeden yakalar.
- Sınırlı throwable grafiklerini dolaşır ve uygulamaya ait frame'leri seçer.
- Deterministik kategori, kanıt gücü, neden ve sonraki kontrol önerileri üretir.
- StackMate'in yönettiği konsol ve export içeriğini zorunlu secret ve PII kurallarıyla sanitize eder.
- Kararlı SHA-256 fingerprint üretir ve tekrarları sınırlı, bellek içi TTL deposunda izler.
- Sınırlı `box` veya `compact` konsol çıktısı üretir.
- İsteğe bağlı olarak dört dosyalı sanitize edilmiş incident paketini yerel diske yazar.

## Neler yapmaz?

StackMate HTTP status veya response body değiştirmez, uygulama loglamasının yerini almaz, kaynak kodu incelemez,
request/response body, query değeri veya header değeri toplamaz, incident upload etmez, telemetri göndermez, cloud
servisi çağırmaz, model indirmez, LLM çalıştırmaz, kod düzenlemez veya coding agent başlatmaz. Birçok
`@ExceptionHandler` akışı dahil Spring MVC içinde çözülmüş exception'lar mevcut yakalama sınırından geçmez.

Spring Boot 3, WebFlux, GraalVM native image, production deployment, container'lar ve belgelenen hosted runner'ların
dışındaki işletim sistemi sürümleri bu alpha için doğrulanmamıştır. Ayrıntılar:
[compatibility.md](docs/compatibility.md).

## Gereksinimler

- Java 21
- Spring Boot 4.1.0
- Spring Framework 7.0.8
- Servlet tabanlı Spring MVC
- Bu checkout build edilirken Maven 3.9.x

Yerel ortam Windows 11 ve Amazon Corretto 21.0.12.1'dir. Hosted qualification Temurin 21.0.12.1 ile Ubuntu 24.04
x64, Windows Server 2025 x64 ve macOS 26 arm64 üzerinde çalıştırılmıştır. Container ortamları doğrulanmamıştır.

## Starter'ı ekleme

Public Maven coordinates şöyledir:

```xml
<dependency>
  <groupId>io.github.kalayciburak</groupId>
  <artifactId>stackmate-spring-boot-starter</artifactId>
  <version>0.1.0-rc.3</version>
</dependency>
```

RC Maven Central'da değildir. Yayıma kadar bu dependency yalnız artifact'lar yerel olarak kurulduğunda veya
sağlandığında çözülür.

Bu checkout'tan yerel değerlendirme yapmak için önce yalnız public artifact zincirini kurun:

```powershell
$env:JAVA_HOME = "<jdk-21-yolu>"
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -Ppublic-release `
  -pl stackmate-core,stackmate-spring-boot-autoconfigure,stackmate-spring-boot-starter `
  -am clean install
```

Ardından yukarıdaki dependency'yi ayrı bir Spring MVC uygulamasına ekleyin. Repodaki bağımsız `release/consumer`
projesi kurulan coordinates'i doğrulamak için kullanılır; reactor modülü değildir.

Starter varsayılan olarak açıktır. Servlet Spring MVC uygulamasında yapılandırma zorunlu değildir. Starter'ı etkisiz
hâle getirmek için:

```yaml
stackmate:
  enabled: false
```

Uygulama frame'lerinin bulunmasına yardımcı olmak için:

```yaml
stackmate:
  application-packages:
    - com.example.uygulamam
```

## Production profil koruması

Aktif Spring profili `prod` veya `production` ile eşleşirse StackMate varsayılan olarak analizi engeller ve sınırlı
bir uyarı loglar. Açık opt-in için:

```yaml
stackmate:
  safety:
    allow-in-production: true
```

Bu koruma yardımcı bir kontroldür; security boundary veya production-readiness iddiası değildir. Opt-in öncesi
gizlilik, dosya sistemi, loglama ve kapasite politikasını inceleyin.

## Temsili çıktı

Aşağıdaki değerler deterministik renderer'ın alanlarını gösterir; bir RC test koşusunun gerçek transkripti olduğu
iddia edilmez:

```text
+--------------------------------------------------------------------------------------------------+
| StackMate Quick Diagnosis                                                                        |
| Incident: STM-7A3C1D2E                                                                           |
| Category: DATABASE_CONSTRAINT                                                                    |
| Evidence: HIGH                                                                                   |
| Summary: A database unique constraint was violated                                               |
| Root: java.sql.SQLException: duplicate key value violates unique constraint                      |
| First app location: OrderService#create(OrderService.java:42)                                    |
| Details: The database rejected a value that must be unique.                                      |
| Request: POST /orders                                                                            |
| First checks:                                                                                    |
|   - Check whether the value already exists before retrying.                                      |
| Application trace:                                                                              |
|   OrderService#create(OrderService.java:42)                                                      |
| Source: Deterministic rules                                                                      |
| Runtime: Java 21.0.12.1 | Spring Boot 4.1.0 | Network disabled                                  |
+--------------------------------------------------------------------------------------------------+
```

Incident ID ve tanı metni yakalanan kanıta göre değişir. Host uygulamanın normal exception işleme davranışı ve ham
logları değişmez. Diğer temsili düzenler [console-gallery.md](docs/console-gallery.md) belgesindedir.

## Yapılandırma

StackMate bu RC'de tam olarak 25 adet `stackmate.*` property sunar. Bilinmeyen alanlar reddedilir. Kullanıcıya açık
tek ek redaction alanı `stackmate.privacy.redact-values`'dır; sınırlı literal değer kabul eder, regular expression
kabul etmez. Public API'de body, query-value veya header-capture switch'i yoktur.

Tüm property, varsayılan ve sınırlar için [configuration.md](docs/configuration.md) belgesine bakın.

## Yerel export

Export varsayılan olarak kapalıdır. Açıldığında ilk görülen her fingerprint için sanitize edilmiş tek paket yazar:

```yaml
stackmate:
  export:
    enabled: true
    directory: .stackmate/incidents
```

```text
.stackmate/incidents/STM-XXXXXXXX/
|-- incident.json
|-- diagnosis.md
|-- stacktrace.txt
`-- agent-prompt.md
```

Export yerel ve isteğe bağlıdır. `agent-prompt.md` yalnız pasif bağlamdır; StackMate agent çalıştırmaz. Paylaşmadan
önce tüm dosyaları inceleyin.

## Mimari ve sınırlar

Public artifact zinciri:

```text
stackmate-core
  <- stackmate-spring-boot-autoconfigure
       <- stackmate-spring-boot-starter
```

`stackmate-core` yalnız JDK kullanır. Auto-configuration; Servlet/Spring MVC adapter'ı, typed configuration,
konsol entegrasyonu ve isteğe bağlı yerel exporter'ı içerir. Repodaki research, sample, benchmark, local-inference
ve E2E modülleri public release'e dahil değildir.

Bilinen sınırlar:

- yalnız servlet filter zincirinden kaçan exception'lar görünür;
- startup failure ve yalnız loglanan hatalar yakalanmaz;
- zorunlu sanitization riski azaltır ancak gelişigüzel metnin güvenli olacağını garanti etmez;
- uygulamanın değişmeyen ham logları veri sızdırabilir;
- duplicate durumu yerel ve bellek içidir;
- export retention yalnız tanınan StackMate paketlerine uygulanır.

Daha geniş değerlendirme öncesi [architecture.md](docs/architecture.md),
[privacy-and-threat-model.md](docs/privacy-and-threat-model.md) ve
[troubleshooting.md](docs/troubleshooting.md) belgelerini okuyun.

## Proje durumu ve katılım

- [Değişiklik günlüğü](CHANGELOG.md)
- [Uyumluluk](docs/compatibility.md)
- [Destek](SUPPORT.md)
- [Güvenlik politikası](SECURITY.md)
- [Katkı rehberi](CONTRIBUTING.md)
- [Davranış kuralları](CODE_OF_CONDUCT.md)
- [Release süreci](docs/release-process.md)

## Lisans

StackMate [Apache License 2.0](LICENSE) ile lisanslanır. Public runtime dependency bildirimi için
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) belgesine bakın.
