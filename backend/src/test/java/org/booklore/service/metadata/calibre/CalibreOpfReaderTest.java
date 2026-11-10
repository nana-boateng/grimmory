package org.booklore.service.metadata.calibre;

import org.booklore.model.dto.BookMetadata;
import org.booklore.service.metadata.extractor.EpubMetadataExtractor;
import org.booklore.util.epub.CoverDetectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CalibreOpfReaderTest {

    private static final String OPF_HEAD = """
            <?xml version='1.0' encoding='utf-8'?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uuid_id" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                <dc:identifier opf:scheme="calibre" id="calibre_id">42</dc:identifier>
                <dc:identifier opf:scheme="uuid" id="uuid_id">00000000-0000-4000-8000-000000000042</dc:identifier>
                <dc:contributor opf:file-as="calibre" opf:role="bkp">calibre (7.16.0) [https://calibre-ebook.com]</dc:contributor>
            """;

    private static final String OPF_TAIL = """
              </metadata>
              <guide>
                <reference type="cover" title="Cover" href="cover.jpg"/>
              </guide>
            </package>
            """;

    @TempDir
    Path folder;

    private CalibreOpfReader reader;

    @BeforeEach
    void setUp() {
        reader = new CalibreOpfReader(new EpubMetadataExtractor(JsonMapper.shared(), mock(CoverDetectorService.class)));
    }

    private Path writeOpf(String metadataBody) throws IOException {
        Path opf = folder.resolve(CalibreOpfReader.OPF_FILE_NAME);
        Files.writeString(opf, OPF_HEAD + metadataBody + OPF_TAIL);
        return opf;
    }

    @Test
    void read_mapsWhatCalibreWritesForABook() throws IOException {
        Path opf = writeOpf("""
                    <dc:title>The Test Book: A Subtitle</dc:title>
                    <dc:creator opf:file-as="Author, Ann" opf:role="aut">Ann Author</dc:creator>
                    <dc:creator opf:file-as="Writer, Bob" opf:role="aut">Bob Writer</dc:creator>
                    <dc:date>2009-06-23T03:37:17.499361+00:00</dc:date>
                    <dc:description>&lt;p&gt;A &lt;strong&gt;short&lt;/strong&gt; blurb.&lt;/p&gt;</dc:description>
                    <dc:publisher>Example House</dc:publisher>
                    <dc:identifier opf:scheme="ISBN">9780306406157</dc:identifier>
                    <dc:identifier opf:scheme="GOOGLE">abc123XYZ</dc:identifier>
                    <dc:language>eng</dc:language>
                    <dc:subject>Fiction</dc:subject>
                    <dc:subject>Thrillers</dc:subject>
                    <meta name="calibre:series" content="Test Series"/>
                    <meta name="calibre:series_index" content="2"/>
                    <meta name="calibre:rating" content="10"/>
                    <meta name="calibre:timestamp" content="2024-10-23T03:22:32.015024+00:00"/>
                """);

        BookMetadata metadata = reader.read(opf).orElseThrow();

        assertThat(metadata.getTitle()).isEqualTo("The Test Book: A Subtitle");
        assertThat(metadata.getAuthors()).containsExactly("Ann Author", "Bob Writer");
        assertThat(metadata.getPublisher()).isEqualTo("Example House");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2009, 6, 23));
        assertThat(metadata.getDescription()).isEqualTo("<p>A <strong>short</strong> blurb.</p>");
        assertThat(metadata.getIsbn13()).isEqualTo("9780306406157");
        assertThat(metadata.getGoogleId()).isEqualTo("abc123XYZ");
        assertThat(metadata.getLanguage()).isEqualTo("eng");
        assertThat(metadata.getCategories()).containsExactlyInAnyOrder("Fiction", "Thrillers");
        assertThat(metadata.getSeriesName()).isEqualTo("Test Series");
        assertThat(metadata.getSeriesNumber()).isEqualTo(2f);
    }

    @Test
    void read_doesNotTreatCalibreItselfAsAnAuthor() throws IOException {
        Path opf = writeOpf("""
                    <dc:title>T</dc:title>
                    <dc:creator opf:file-as="Author, Ann" opf:role="aut">Ann Author</dc:creator>
                """);

        assertThat(reader.read(opf).orElseThrow().getAuthors()).containsExactly("Ann Author");
    }

    @Test
    void read_dropsTheAuthorCalibreWritesWhenThereIsNone() throws IOException {
        Path opf = writeOpf("""
                    <dc:title>Anonymous Tract</dc:title>
                    <dc:creator opf:file-as="Unknown" opf:role="aut">Unknown</dc:creator>
                """);

        BookMetadata metadata = reader.read(opf).orElseThrow();

        assertThat(metadata.getAuthors()).isNull();
        assertThat(metadata.getTitle()).isEqualTo("Anonymous Tract");
    }

    @Test
    void read_dropsCalibresNoPublicationDatePlaceholder() throws IOException {
        Path opf = writeOpf("""
                    <dc:title>Undated</dc:title>
                    <dc:creator opf:file-as="Author, Ann" opf:role="aut">Ann Author</dc:creator>
                    <dc:date>0101-01-01T00:00:00+00:00</dc:date>
                """);

        assertThat(reader.read(opf).orElseThrow().getPublishedDate()).isNull();
    }

    @Test
    void read_keepsAnOldButRealPublicationDate() throws IOException {
        Path opf = writeOpf("""
                    <dc:title>Old</dc:title>
                    <dc:creator opf:file-as="Author, Ann" opf:role="aut">Ann Author</dc:creator>
                    <dc:date>1867-09-14T00:00:00+00:00</dc:date>
                """);

        assertThat(reader.read(opf).orElseThrow().getPublishedDate()).isEqualTo(LocalDate.of(1867, 9, 14));
    }

    @Test
    void read_returnsEmptyForAFileThatIsNotXml() throws IOException {
        Path opf = folder.resolve(CalibreOpfReader.OPF_FILE_NAME);
        Files.writeString(opf, "this is not an opf");

        assertThat(reader.read(opf)).isEmpty();
    }

    @Test
    void findOpfAndFindCover_lookBesideTheBookFile() throws IOException {
        Path book = Files.createFile(folder.resolve("Title - Ann Author.epub"));

        assertThat(reader.findOpf(book)).isEmpty();
        assertThat(reader.findCover(book)).isEmpty();

        Files.createFile(folder.resolve("metadata.opf"));
        Files.createFile(folder.resolve("cover.jpg"));

        assertThat(reader.findOpf(book)).contains(folder.resolve("metadata.opf"));
        assertThat(reader.findCover(book)).contains(folder.resolve("cover.jpg"));
    }

    @Test
    void findOpfAndFindCover_returnEmptyWithoutABookFile() {
        assertThat(reader.findOpf(null)).isEqualTo(Optional.empty());
        assertThat(reader.findCover(null)).isEqualTo(Optional.empty());
    }
}
