import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.PDTextState;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.geom.Point2D;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class TextDrawExtractor {

    public static void main(String[] args) throws IOException {
        for (String path: args) {
            Path p = Paths.get(path);
            if (Files.isDirectory(p)) {
                FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.toString().endsWith(".pdf")) processFile(file);
                        return FileVisitResult.CONTINUE;
                    }
                };
                Files.walkFileTree(p, visitor);
            }
            else processFile(p);
        }
    }

    private static void processFile(Path path) throws IOException {
        PDDocument doc = PDDocument.load(path.toFile());
        String outPath = path.toString().replace(".pdf", ".feats");

        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), "UTF-8"))) {
            List<List<String>> objects = new ArrayList<>();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDFGraphicsStreamEngine gse = new MyPDFGraphicsStreamEngine(doc.getPage(i), objects);
                gse.processPage(doc.getPage(i));
                for (List<String> obj : objects) {
                    if (obj != null) obj.add(0, String.valueOf(i+1));
                }
            }

            PDFTextStripper ts = new MyPDFTextStripper(objects);
            ts.writeText(doc, new OutputStreamWriter(new ByteArrayOutputStream()));
            for (List<String> obj : objects) {
                if (obj.size() == 1 && obj.get(0).length() == 0) w.write("\n");
                else {
                    w.write(String.join("\t", obj));
                    w.write("\n");
                }
            }
        }
        doc.close();
    }

    private static class MyPDFTextStripper extends PDFTextStripper {
        int cursor = 0;
        List<List<String>> objects;

        MyPDFTextStripper(List<List<String>> objects) throws IOException {
            this.objects = objects;
        }

        void addNewLine() {
            List<String> obj = new ArrayList<>();
            obj.add("");
            objects.add(cursor, obj);
            cursor++;
        }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
            boolean isdraw = cursor< objects.size() && objects.get(cursor) != null;
            while (cursor< objects.size() && objects.get(cursor) != null) cursor++;
            if (isdraw) addNewLine();

            for (TextPosition p : textPositions) {
                String s = Normalizer.normalize(p.getUnicode(), Normalizer.Form.NFKD);
                for (char c : s.toCharArray()) {
                    if (objects.get(cursor) != null) throw new IllegalStateException("Something wrong with draw extraction.");

                    List<String> obj = new ArrayList<>();
                    obj.add(String.valueOf(getCurrentPageNo()));
                    obj.add(String.valueOf(c));
                    obj.add(String.valueOf(p.getXDirAdj()));
                    obj.add(String.valueOf(p.getYDirAdj()));
                    obj.add(String.valueOf(p.getWidthDirAdj()));
                    obj.add(String.valueOf(p.getHeightDir()));
                    obj.add(p.getFont().getName());
                    obj.add(String.valueOf(p.getFontSize()));
                    obj.add(String.valueOf(p.getWidthOfSpace()));
                    objects.set(cursor, obj);
                    cursor++;
                }
            }
            addNewLine();
        }

        @Override
        protected void writeWordSeparator() throws IOException {
        }

        @Override
        protected void writeLineSeparator() throws IOException {
        }
    }

    private static class MyPDFGraphicsStreamEngine extends PDFGraphicsStreamEngine {

        private final List<List<String>> objects;

        MyPDFGraphicsStreamEngine (PDPage page, List<List<String>> objects) {
            super(page);
            this.objects = objects;
        }

        @Override
        public void appendRectangle(Point2D point2D, Point2D point2D1, Point2D point2D2, Point2D point2D3) throws IOException {
            assert false;
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            assert false;
        }

        @Override
        public void clip(int i) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[CLIP]");
            obj.add(String.valueOf(i));
            objects.add(obj);
        }

        @Override
        public void moveTo(float x, float y) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[MOVE_TO]");
            obj.add(String.valueOf(x));
            obj.add(String.valueOf(y));
            objects.add(obj);
        }

        @Override
        public void lineTo(float x, float y) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[LINE_TO]");
            obj.add(String.valueOf(x));
            obj.add(String.valueOf(y));
            objects.add(obj);
        }

        @Override
        public void curveTo(float v, float v1, float v2, float v3, float v4, float v5) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[CURVE_TO]");
            obj.add(String.valueOf(v));
            obj.add(String.valueOf(v1));
            obj.add(String.valueOf(v2));
            obj.add(String.valueOf(v3));
            obj.add(String.valueOf(v4));
            obj.add(String.valueOf(v5));
            objects.add(obj);
        }

        @Override
        public Point2D getCurrentPoint() throws IOException {
            return new Point2D.Float(0, 0);
        }

        @Override
        public void closePath() throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[CLOSE_PATH]");
            objects.add(obj);
        }

        @Override
        public void endPath() throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[END_PATH]");
            objects.add(obj);
        }

        @Override
        public void strokePath() throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[STROKE_PATH]");
            objects.add(obj);
        }

        @Override
        public void fillPath(int i) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[FILL_PATH]");
            obj.add(String.valueOf(i));
            objects.add(obj);
        }

        @Override
        public void fillAndStrokePath(int i) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[FILL_AND_STROKE_PATH]");
            obj.add(String.valueOf(i));
            objects.add(obj);
        }

        @Override
        public void shadingFill(COSName cosName) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[SHADING_FILL]");
            obj.add(String.valueOf(cosName));
            objects.add(obj);
        }

        @Override
        public void showText(byte[] string) throws IOException {
            PDGraphicsState state = this.getGraphicsState();
            PDTextState textState = state.getTextState();
            PDFont font = textState.getFont();
            if (font == null) font = PDFontFactory.createDefaultFont();

            for (ByteArrayInputStream in = new ByteArrayInputStream(string); in.available() > 0;) {
                int code = font.readCode(in);
                String unicode = font.toUnicode(code);
                if (unicode == null) objects.add(null);
                else {
                    for (char c : Normalizer.normalize(unicode, Normalizer.Form.NFKD).toCharArray()) {
                        objects.add(null);
                    }
                }
            }
        }
    }
}
