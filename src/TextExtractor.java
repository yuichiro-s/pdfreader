import java.io.*;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.cos.COSBase;

public class TextExtractor extends PDFTextStripper {

    public TextExtractor() throws IOException {
    }

    public void process(PDDocument doc, String filename) throws IOException {
        //stripper.setSortByPosition(true);

        //PrintWriter w = new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"));
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(filename), "UTF-8");

        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
        //for (int i = 1; i <= 1; i++) {
            setStartPage(i);
            setEndPage(i);
            writeText(doc, w);
        }
        w.close();
    }

    void appendString(StringBuilder sb, TextPosition p, String str) throws IOException {
        sb.append(getCurrentPageNo()).append("\t")
                .append(str).append("\t")
                .append(p.getXDirAdj()).append("\t")
                .append(p.getYDirAdj()).append("\t")
                .append(p.getWidthDirAdj()).append("\t")
                .append(p.getHeightDir()).append("\t")
                .append(p.getFont().getName()).append("\t")
                .append(p.getFontSize()).append("\t")
                .append(p.getWidthOfSpace()).append("\n");
    }

    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (TextPosition p : textPositions) {
            String str = p.getUnicode();
            if (str.equals("ﬁ")) {
                appendString(sb, p, "f");
                appendString(sb, p, "i");
            }
            else appendString(sb, p, p.getUnicode());
        }
        super.output.append(sb.toString());
        super.output.append("\n");
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        super.output.append("*").append("\n");
    }

    @Override
    protected void writeWordSeparator() throws IOException {
    }
}