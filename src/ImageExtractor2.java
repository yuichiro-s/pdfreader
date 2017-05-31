import java.io.IOException;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;

public class ImageExtractor2 extends PDFStreamEngine {
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
        String outPath = path.toString().replace(".pdf", ".image");
        ImageExtractor2 ie = new ImageExtractor2();
        ie.output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), "UTF-8"));
        ie.process(doc);
        ie.output.close();
    }

    static void countFile(Path path) throws IOException {
        PDDocument doc = PDDocument.load(path.toFile());
        ImageExtractor2 ie = new ImageExtractor2();
        ie.process(doc);
        ie.output.close();
    }

    int pageNo;
    Writer output;
    ArrayList<Position> positions = new ArrayList<>();

    public ImageExtractor2() throws IOException {
        addOperator(new Concatenate());
        addOperator(new DrawObject());
        addOperator(new SetGraphicsStateParameters());
        addOperator(new Save());
        addOperator(new Restore());
        addOperator(new SetMatrix());
    }

    class Position {
        int pageNum;
        float x, y, width, height;
        Position (int pageNum, float x, float y, float width, float height) {
            this.pageNum = pageNum; this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }

    public void process(PDDocument doc) throws IOException {
        pageNo = 1;
        for (PDPage page : doc.getPages()) {
            processPage(page);
            pageNo++;
        }

        // filter adjacent images
        ArrayList<Position> positions_tmp = new ArrayList<>();
        LinkedHashMap<Position, Boolean> adjflgDict = new LinkedHashMap<>();

        // debug
        for (Position pos :positions) adjflgDict.put(pos, false);

        for (Position pos :positions) {
            if (adjflgDict.get(pos)) continue;

            boolean isAdjacent = false;
            for (Position pos_cmp :positions) {
                boolean same_page = pos.pageNum == pos_cmp.pageNum;
                if (!same_page) continue;

                boolean same_x = pos.x == pos_cmp.x;
                boolean same_y = pos.y == pos_cmp.y;
                boolean same_w = pos.width == pos_cmp.width;
                boolean same_h = pos.height == pos_cmp.height;
                if (same_x && same_y && same_w && same_h) continue;

                float threshold = 2;
                boolean near_bottom = 0 < pos.y - (pos_cmp.y + pos_cmp.height) && pos.y - (pos_cmp.y + pos_cmp.height) < threshold;
                boolean near_top = 0 < (pos.y + pos.height) - pos_cmp.y && (pos.y + pos.height) - pos_cmp.y < threshold;
                boolean near_right = 0 < (pos.x + pos.width) - pos_cmp.x && (pos.x + pos.width) - pos_cmp.x < threshold;
                boolean near_left = 0 < pos.x - (pos_cmp.x + pos_cmp.width) && pos.x - (pos_cmp.x + pos_cmp.width) < threshold;
                isAdjacent = ((same_x && (near_bottom || near_top)) || (same_y && (near_left || near_right)));
                if (isAdjacent) {
                    adjflgDict.put(pos, true);
                    adjflgDict.put(pos_cmp, true);
                    break;
                }
            }
        }
        for (Position key :adjflgDict.keySet()) {
            if (!adjflgDict.get(key)) positions_tmp.add(key);
        }
        positions = positions_tmp;

        // filter small image
        positions_tmp = new ArrayList<>();
        for (Position pos :positions) {
            if (!(pos.width < 50 || pos.height < 50)) {
                positions_tmp.add(pos);
            }
        }
        positions = positions_tmp;

        // filter same coordinate
        positions_tmp = new ArrayList<>();
        LinkedHashMap<Position, Boolean> sameposflgDict = new LinkedHashMap<>();
        for (Position pos :positions) {sameposflgDict.put(pos, false);}
        for (Position pos :positions) {
            if (sameposflgDict.get(pos)) continue;

            for (Position pos_cmp :positions) {
                boolean samePag = pos.pageNum == pos_cmp.pageNum;
                boolean sameLoc = pos.x == pos_cmp.x && pos.y == pos_cmp.y && pos.width == pos_cmp.width && pos.height == pos_cmp.height;

                if (!samePag && sameLoc) {
                    sameposflgDict.put(pos, true);
                    sameposflgDict.put(pos_cmp, true);
                    break;
                }
            }
        }
        for (Position key :sameposflgDict.keySet()) {
            if (!sameposflgDict.get(key)) positions_tmp.add(key);
        }
        positions = positions_tmp;

        // output
        for (Position pos :positions) {
            output.write(String.valueOf(pos.pageNum)); output.write("\t");
            output.write(pos.x + "\t" + pos.y + "\t");
            output.write(pos.width + "\t" + pos.height);
            output.write("\n");
        }
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();
        if("Do".equals(operation)) {
            COSName objectName = (COSName)operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);

            if (xobject instanceof PDImageXObject) {
                PDImageXObject image = (PDImageXObject)xobject;

                Matrix ctmNew = getGraphicsState().getCurrentTransformationMatrix();
                float x = ctmNew.getTranslateX();
                float y = ctmNew.getTranslateY();
                float width = ctmNew.getScalingFactorX();
                float height = ctmNew.getScalingFactorY();
                Position position = new Position(pageNo, x, y, width, height);
                positions.add(position);
            }
            else if(xobject instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject)xobject;
                showForm(form);
            }
        }
        else {
            super.processOperator(operator, operands);
        }
    }
}