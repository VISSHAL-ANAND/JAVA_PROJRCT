package util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvExportTest {
    @Test
    void quotesCellsAndEscapesQuotesAndLineBreaks() {
        assertEquals("\"Ticket, one\",\"A \"\"quoted\"\" title\",\"line one\nline two\"\r\n",
                CsvExport.row("Ticket, one", "A \"quoted\" title", "line one\nline two"));
    }

    @Test
    void neutralizesSpreadsheetFormulaCells() {
        assertEquals("\"'=HYPERLINK(\"\"https://example.test\"\",\"\"open\"\")\",\"'  +1+1\",\"safe\"\r\n",
                CsvExport.row("=HYPERLINK(\"https://example.test\",\"open\")", "  +1+1", "safe"));
    }

    @Test
    void rendersNullAsAnEmptyCell() {
        assertEquals("\"\",\"ok\"\r\n", CsvExport.row(null, "ok"));
    }
}
