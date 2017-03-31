import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.text.Normalizer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class TextExtractor extends PDFTextStripper {

    public static void main(String[] args) throws IOException {
        for (String path: args) {
            Path p = Paths.get(path);
            if (Files.isDirectory(p)) {
                FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.toString().endsWith(".pdf")) {
                            processFile(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                };
                Files.walkFileTree(p, visitor);
            } else {
                processFile(p);
            }
        }
    }

    static void processFile(Path path) throws IOException {
        PDDocument doc = PDDocument.load(path.toFile());
        String outPath = path.toString().replace(".pdf", ".txt");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), "UTF-8"))) {
            TextExtractor te = new TextExtractor();
            te.writeText(doc, w);
        }
    }

    public TextExtractor() throws IOException {
    }

    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        output.write(String.valueOf(getCurrentPageNo())); output.write("\t");
        for (TextPosition p : textPositions) {
            String str = Normalizer.normalize(p.getUnicode(), Normalizer.Form.NFKD);
            output.write(str);
        }
        //output.write("\t");
        //TextPosition p0 = textPositions.get(0);
        //output.write(String.valueOf(p0.getXDirAdj())); output.write("\t");
        //output.write(String.valueOf(p0.getYDirAdj())); output.write("\t");
        //output.write(String.valueOf(p.getWidthDirAdj())); output.write("\t");
        //output.write(String.valueOf(p.getHeightDir())); output.write("\t");
        //output.write(p0.getFont().getName()); output.write("\t");
        //output.write(String.valueOf(p0.getFontSize())); output.write("\t");
        //output.write(String.valueOf(p.getWidthOfSpace())); output.write("\n");

        output.write("\n");
        //super.output.append(sb.toString());
        //super.output.append("\n");
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        output.write("\n");
    }

    @Override
    protected void writeWordSeparator() throws IOException {
    }
}