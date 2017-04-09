import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

import java.awt.geom.Point2D;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class TextDrawExtractor extends PDFGraphicsStreamEngine {

    static final Log LOG = LogFactory.getLog(TextDrawExtractor.class);

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
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                TextDrawExtractor tde = new TextDrawExtractor(doc.getPage(i), w, i+1);
                tde.processPage(doc.getPage(i));
                tde.writeAll();
            }
        }
    }

    Writer writer;
    int pageIndex;
    int pageRotation;
    PDRectangle pageSize;
    Matrix translateMatrix;
    final GlyphList glyphList;
    List<Object> buffer = new ArrayList<>();

    public TextDrawExtractor(PDPage page, Writer writer, int pageIndex) throws IOException {
        super(page);
        this.writer = writer;
        this.pageIndex = pageIndex;

        String path = "org/apache/pdfbox/resources/glyphlist/additional.txt";
        InputStream input = GlyphList.class.getClassLoader().getResourceAsStream(path);
        this.glyphList = new GlyphList(GlyphList.getAdobeGlyphList(), input);

        this.pageRotation = page.getRotation();
        this.pageSize = page.getCropBox();
        if (this.pageSize.getLowerLeftX() == 0.0F && this.pageSize.getLowerLeftY() == 0.0F) {
            this.translateMatrix = null;
        } else {
            this.translateMatrix = Matrix.getTranslateInstance(-this.pageSize.getLowerLeftX(), -this.pageSize.getLowerLeftY());
        }
    }

    float getPageHeight() { return getPage().getMediaBox().getHeight(); }

    void writeAll() throws IOException {
        List<TextPosition> texts = new ArrayList<>();
        List<String> draws = new ArrayList<>();
        for (Object b : buffer) {
            if (b instanceof TextPosition) {
                if (!draws.isEmpty()) writeDraw(draws);
                texts.add((TextPosition)b);
            }
            else {
                if (!texts.isEmpty()) writeText(texts);
                draws.add((String)b);
            }
        }
        if (!draws.isEmpty()) writeDraw(draws);
        if (!texts.isEmpty()) writeText(texts);
    }

    void writeText(List<TextPosition> textList) throws IOException {
        // copied from PDFTextStripper.writePage
        float maxYForLine = -3.4028235E38F;
        float minYTopForLine = 3.4028235E38F;
        float endOfLastTextX = -1.0F;
        float lastWordSpacing = -1.0F;
        float maxHeightForLine = -1.0F;
        PositionWrapper lastPosition = null;
        PositionWrapper lastLineStartPosition = null;

        float spacingTolerance = 0.5F;
        float averageCharTolerance = 0.3F;

        boolean startOfArticle = true;
        List<TextDrawExtractor.LineItem> line = new ArrayList<>();
        Iterator<TextPosition> textIter = textList.iterator();

        float averageCharWidth;
        for (float previousAveCharWidth = -1.0F; textIter.hasNext(); previousAveCharWidth = averageCharWidth) {
            TextPosition position = textIter.next();
            TextDrawExtractor.PositionWrapper current = new TextDrawExtractor.PositionWrapper(position);
            String characterValue = position.getUnicode();
            if (lastPosition != null && (position.getFont() != lastPosition.getTextPosition().getFont() || position.getFontSize() != lastPosition.getTextPosition().getFontSize())) {
                previousAveCharWidth = -1.0F;
            }

            float positionX;
            float positionY;
            float positionWidth;
            float positionHeight;
            positionX = position.getX();
            positionY = position.getY();
            positionWidth = position.getWidth();
            positionHeight = position.getHeight();

            int wordCharCount = position.getIndividualWidths().length;
            float wordSpacing = position.getWidthOfSpace();
            float deltaSpace;
            if (wordSpacing != 0.0F && !Float.isNaN(wordSpacing)) {
                if (lastWordSpacing < 0.0F) {
//                    deltaSpace = wordSpacing * this.getSpacingTolerance();
                    deltaSpace = wordSpacing * spacingTolerance;
                } else {
//                    deltaSpace = (wordSpacing + lastWordSpacing) / 2.0F * this.getSpacingTolerance();
                    deltaSpace = (wordSpacing + lastWordSpacing) / 2.0F * spacingTolerance;
                }
            } else {
                deltaSpace = 3.4028235E38F;
            }

            if (previousAveCharWidth < 0.0F) {
                averageCharWidth = positionWidth / (float)wordCharCount;
            } else {
                averageCharWidth = (previousAveCharWidth + positionWidth / (float)wordCharCount) / 2.0F;
            }

//            float deltaCharWidth = averageCharWidth * this.getAverageCharTolerance();
            float deltaCharWidth = averageCharWidth * averageCharTolerance;
            float expectedStartOfNextWordX = -3.4028235E38F;
            if (endOfLastTextX != -1.0F) {
                if (deltaCharWidth > deltaSpace) {
                    expectedStartOfNextWordX = endOfLastTextX + deltaSpace;
                } else {
                    expectedStartOfNextWordX = endOfLastTextX + deltaCharWidth;
                }
            }

            if (lastPosition != null) {
                if (startOfArticle) {
                    lastPosition.setArticleStart();
                    startOfArticle = false;
                }

                if (!overlap(positionY, positionHeight, maxYForLine, maxHeightForLine)) {
                    writeLine(normalize(line));
                    line.clear();
                    lastLineStartPosition = handleLineSeparation(current, lastPosition, lastLineStartPosition, maxHeightForLine);
                    expectedStartOfNextWordX = -3.4028235E38F;
                    maxYForLine = -3.4028235E38F;
                    maxHeightForLine = -1.0F;
                    minYTopForLine = 3.4028235E38F;
                }

                if (expectedStartOfNextWordX != -3.4028235E38F && expectedStartOfNextWordX < positionX && lastPosition.getTextPosition().getUnicode() != null && !lastPosition.getTextPosition().getUnicode().endsWith(" ")) {
                    line.add(TextDrawExtractor.LineItem.WORD_SEPARATOR);
                }
            }

            if (positionY >= maxYForLine) {
                maxYForLine = positionY;
            }

            endOfLastTextX = positionX + positionWidth;
            if (characterValue != null) {
                /*
                if(startOfPage && lastPosition == null) {
                    this.writeParagraphStart();
                }
                */
                line.add(new TextDrawExtractor.LineItem(position));
            }

            maxHeightForLine = Math.max(maxHeightForLine, positionHeight);
            minYTopForLine = Math.min(minYTopForLine, positionY - positionHeight);
            lastPosition = current;
            /*
            if(startOfPage) {
                current.setParagraphStart();
                current.setLineStart();
                lastLineStartPosition = current;
                startOfPage = false;
            }
            */
            lastWordSpacing = wordSpacing;
        }
        if (line.size() > 0) writeLine(normalize(line));
        textList.clear();
    }

    void writeDraw(List<String> drawList) throws IOException {
        for (String s : drawList) {
            writer.write(s);
            writer.write("\n");
        }
        drawList.clear();
    }

    boolean overlap(float y1, float height1, float y2, float height2) {
        return within(y1, y2, 0.1F) || y2 <= y1 && y2 >= y1 - height1 || y1 <= y2 && y1 >= y2 - height2;
    }

    boolean within(float first, float second, float variance) {
        return second < first + variance && second > first - variance;
    }

    List<WordWithTextPositions> normalize(List<LineItem> line) {
        List<WordWithTextPositions> normalized = new LinkedList<>();
        StringBuilder lineBuilder = new StringBuilder();
        List<TextPosition> wordPositions = new ArrayList<>();

        LineItem item;
        for(Iterator i$ = line.iterator(); i$.hasNext(); lineBuilder = normalizeAdd(normalized, lineBuilder, wordPositions, item)) {
            item = (LineItem)i$.next();
        }

        if(lineBuilder.length() > 0) {
            normalized.add(createWord(lineBuilder.toString(), wordPositions));
        }

        return normalized;
    }

    StringBuilder normalizeAdd(List<WordWithTextPositions> normalized, StringBuilder lineBuilder, List<TextPosition> wordPositions, LineItem item) {
        if(item.isWordSeparator()) {
            normalized.add(createWord(lineBuilder.toString(), new ArrayList<>(wordPositions)));
            lineBuilder = new StringBuilder();
            wordPositions.clear();
        } else {
            TextPosition text = item.getTextPosition();
            lineBuilder.append(text.getUnicode());
            wordPositions.add(text);
        }

        return lineBuilder;
    }

    WordWithTextPositions createWord(String word, List<TextPosition> wordPositions) {
        return new WordWithTextPositions(normalizeWord(word), wordPositions);
    }

    String normalizeWord(String word) {
        StringBuilder builder = null;
        int p = 0;
        int q = 0;

        for (int strLength = word.length(); q < strLength; ++q) {
            char c = word.charAt(q);
            if ('ﬀ' <= c && c <= '\ufdff' || 'ﹰ' <= c && c <= '\ufeff') {
                if (builder == null) {
                    builder = new StringBuilder(strLength * 2);
                }

                builder.append(word.substring(p, q));
                if (c != 'ﷲ' || q <= 0 || word.charAt(q - 1) != 1575 && word.charAt(q - 1) != 'ﺍ') {
                    builder.append(Normalizer.normalize(word.substring(q, q + 1), Normalizer.Form.NFKC).trim());
                } else {
                    builder.append("لله");
                }

                p = q + 1;
            }
        }

        if (builder == null) {
//            return handleDirection(word);
            return word;
        } else {
            builder.append(word.substring(p, q));
//            return handleDirection(builder.toString());
            return builder.toString();
        }
    }

    PositionWrapper handleLineSeparation(PositionWrapper current, PositionWrapper lastPosition, PositionWrapper lastLineStartPosition, float maxHeightForLine) throws IOException {
        current.setLineStart();
//        this.isParagraphSeparation(current, lastPosition, lastLineStartPosition, maxHeightForLine);
        if(current.isParagraphStart()) {
            if(lastPosition.isArticleStart()) {
                if(lastPosition.isLineStart()) {
                    //writeLineSeparator();
                }

//                this.writeParagraphStart();
            } else {
                //writeLineSeparator();
//                this.writeParagraphSeparator();
            }
        } else {
            //writeLineSeparator();
        }

        return current;
    }

    void writeLine(List<WordWithTextPositions> line) throws IOException {
        int numberOfStrings = line.size();
        for (int i = 0; i < numberOfStrings; ++i) {
            WordWithTextPositions word = line.get(i);
            writeString(word.getText(), word.getTextPositions());
            //if (i < numberOfStrings - 1) writeWordSeparator();
        }
    }

    void writeString(String text, List<TextPosition> textPositions) throws IOException {
        for (TextPosition p: textPositions) {
            List<String> l = new ArrayList<>();
            l.add(String.valueOf(pageIndex));
            l.add(p.getUnicode());
            l.add(String.valueOf(p.getXDirAdj()));
            l.add(String.valueOf(p.getYDirAdj()));
            l.add(String.valueOf(p.getWidthDirAdj()));
            l.add(String.valueOf(p.getHeightDir()));
            l.add(p.getFont().getName());
            l.add(String.valueOf(p.getFontSize()));
            l.add(String.valueOf(p.getWidthOfSpace()));
            writer.write(String.join("\t", l));
            writer.write("\n");
        }
    }

    //void writeLineSeparator() throws IOException { writer.write("[EOL]\n"); }
    //void writeWordSeparator() throws IOException { writer.write("[EOT]\n"); }

    void addOps(Object... ops) throws IOException {
        List<String> l = new ArrayList<>();
        l.add(String.valueOf(pageIndex));
        for (Object op : ops) l.add(String.valueOf(op));
        buffer.add(String.join("\t", l));
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) throws IOException {
        // copied from "allenai/pdffigures2/GraphicBBDetector.scala"
        moveTo((float)p0.getX(), (float)p0.getY());
        lineTo((float)p1.getX(), (float)p1.getY());
        moveTo((float)p2.getX(), (float)p2.getY());
        lineTo((float)p3.getX(), (float)p3.getY());
        closePath();
    }

    @Override
    public void drawImage(PDImage pdImage) throws IOException {
        assert false;
    }

    @Override
    public void clip(int i) throws IOException { addOps("[CLIP]", i); }

    @Override
    public void moveTo(float x, float y) throws IOException { addOps("[MOVE_TO]", x, getPageHeight()-y); }

    @Override
    public void lineTo(float x, float y) throws IOException { addOps("[LINE_TO]", x, getPageHeight()-y); }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) throws IOException {
        addOps("[CURVE_TO]", x1, y1, x2, y2, x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() throws IOException {
        return new Point2D.Float(0, 0);
    }

    @Override
    public void closePath() throws IOException { addOps("[CLOSE_PATH]"); }

    @Override
    public void endPath() throws IOException { addOps("[END_PATH]"); }

    @Override
    public void strokePath() throws IOException { addOps("[STROKE_PATH]"); }

    @Override
    public void fillPath(int i) throws IOException { addOps("[FILL_PATH]"); }

    @Override
    public void fillAndStrokePath(int i) throws IOException { addOps("[FILL_AND_STROKE_PATH]", i); }

    @Override
    public void shadingFill(COSName cosName) throws IOException { addOps("[SHADING_FILL]", cosName); }

    @Override
    public void showFontGlyph(Matrix textRenderingMatrix, PDFont font, int code, String unicode, Vector displacement) throws IOException {
        // from LegacyPDFStreamEngine.showGlyph
        PDGraphicsState state = this.getGraphicsState();
        Matrix ctm = state.getCurrentTransformationMatrix();
        float fontSize = state.getTextState().getFontSize();
        float horizontalScaling = state.getTextState().getHorizontalScaling() / 100.0F;
        Matrix textMatrix = this.getTextMatrix();
        BoundingBox bbox = font.getBoundingBox();
        if (bbox.getLowerLeftY() < -32768.0F) {
            bbox.setLowerLeftY(-(bbox.getLowerLeftY() + 65536.0F));
        }

        float glyphHeight = bbox.getHeight() / 2.0F;
        PDFontDescriptor fontDescriptor = font.getFontDescriptor();
        float height;
        if (fontDescriptor != null) {
            height = fontDescriptor.getCapHeight();
            if (height != 0.0F && (height < glyphHeight || glyphHeight == 0.0F)) {
                glyphHeight = height;
            }
        }

        height = glyphHeight / 1000.0F;

        float displacementX = displacement.getX();
        if (font.isVertical()) {
            displacementX = font.getWidth(code) / 1000.0F;
            TrueTypeFont ttf = null;
            if (font instanceof PDTrueTypeFont) {
                ttf = ((PDTrueTypeFont)font).getTrueTypeFont();
            } else if (font instanceof PDType0Font) {
                PDCIDFont cidFont = ((PDType0Font)font).getDescendantFont();
                if(cidFont instanceof PDCIDFontType2) {
                    ttf = ((PDCIDFontType2)cidFont).getTrueTypeFont();
                }
            }

            if (ttf != null && ttf.getUnitsPerEm() != 1000) {
                displacementX *= 1000.0F / (float)ttf.getUnitsPerEm();
            }
        }

        float tx = displacementX * fontSize * horizontalScaling;
        float ty = displacement.getY() * fontSize;
        Matrix td = Matrix.getTranslateInstance(tx, ty);
        Matrix nextTextRenderingMatrix = td.multiply(textMatrix).multiply(ctm);
        float nextX = nextTextRenderingMatrix.getTranslateX();
        float nextY = nextTextRenderingMatrix.getTranslateY();
        float dxDisplay = nextX - textRenderingMatrix.getTranslateX();
        float dyDisplay = height * textRenderingMatrix.getScalingFactorY();
        float glyphSpaceToTextSpaceFactor = 0.001F;
        if (font instanceof PDType3Font) {
            glyphSpaceToTextSpaceFactor = font.getFontMatrix().getScaleX();
        }

        float spaceWidthText = 0.0F;

        try {
            spaceWidthText = font.getSpaceWidth() * glyphSpaceToTextSpaceFactor;
        } catch (Throwable e) {
            LOG.warn(e, e);
        }

        if (spaceWidthText == 0.0F) {
            spaceWidthText = font.getAverageFontWidth() * glyphSpaceToTextSpaceFactor;
            spaceWidthText *= 0.8F;
        }

        if (spaceWidthText == 0.0F) {
            spaceWidthText = 1.0F;
        }

        float spaceWidthDisplay = spaceWidthText * textRenderingMatrix.getScalingFactorX();
        unicode = font.toUnicode(code, this.glyphList);
        if (unicode == null) {
            if (!(font instanceof PDSimpleFont)) return;
            char c = (char)code;
            unicode = new String(new char[]{c});
        }

        Matrix translatedTextRenderingMatrix;
        if (this.translateMatrix == null) {
            translatedTextRenderingMatrix = textRenderingMatrix;
        } else {
            translatedTextRenderingMatrix = Matrix.concatenate(this.translateMatrix, textRenderingMatrix);
            nextX -= this.pageSize.getLowerLeftX();
            nextY -= this.pageSize.getLowerLeftY();
        }

        TextPosition p = new TextPosition(pageRotation, pageSize.getWidth(), pageSize.getHeight(), translatedTextRenderingMatrix,
                nextX, nextY, Math.abs(dyDisplay), dxDisplay, Math.abs(spaceWidthDisplay),
                unicode, new int[]{code}, font, fontSize, (int) (fontSize * textMatrix.getScalingFactorX()));
        buffer.add(p);
    }

    static final class PositionWrapper {
        private boolean isLineStart = false;
        private boolean isParagraphStart = false;
        private boolean isPageBreak = false;
        private boolean isHangingIndent = false;
        private boolean isArticleStart = false;
        private TextPosition position = null;

        PositionWrapper(TextPosition position) { this.position = position; }

        public TextPosition getTextPosition() { return this.position; }

        public boolean isLineStart() { return this.isLineStart; }

        public void setLineStart() { this.isLineStart = true; }

        public boolean isParagraphStart() { return this.isParagraphStart; }

        public void setParagraphStart() { this.isParagraphStart = true; }

        public boolean isArticleStart() { return this.isArticleStart; }

        public void setArticleStart() { this.isArticleStart = true; }

        public boolean isPageBreak() { return this.isPageBreak; }

        public void setPageBreak() { this.isPageBreak = true; }

        public boolean isHangingIndent() { return this.isHangingIndent; }

        public void setHangingIndent() { this.isHangingIndent = true; }
    }

    static final class WordWithTextPositions {
        String text;
        List<TextPosition> textPositions;

        WordWithTextPositions(String word, List<TextPosition> positions) {
            this.text = word;
            this.textPositions = positions;
        }

        public String getText() { return this.text; }

        public List<TextPosition> getTextPositions() { return this.textPositions; }
    }

    static final class LineItem {
        public static LineItem WORD_SEPARATOR = new LineItem();
        private final TextPosition textPosition;

        public static LineItem getWordSeparator() { return WORD_SEPARATOR; }

        private LineItem() { this.textPosition = null; }

        LineItem(TextPosition textPosition) { this.textPosition = textPosition; }

        public TextPosition getTextPosition() { return this.textPosition; }

        public boolean isWordSeparator() { return this.textPosition == null; }
    }
}
